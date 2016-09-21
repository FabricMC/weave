/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.weave;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.analysis.TranslationIndex;
import cuchaz.enigma.mapping.*;
import net.fabricmc.weave.util.Utils;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

public class CommandIntermediary extends Command {
    private static final Joiner TAB_JOINER = Joiner.on('\t');
    private final Map<Entry, Entry> obfMatches = new HashMap<>();
    private final Map<Entry, String> intermediaryMap = new HashMap<>();
    private final Multiset<String> counters = HashMultiset.create();

    public CommandIntermediary() {
        super("intermediary");
    }

    @Override
    public String getHelpString() {
        return "<intermediary-file> <current-jar> [old-mappings] <new-mappings>";
    }

    @Override
    public boolean isArgumentCountValid(int count) {
        return count >= 3 && count <= 4;
    }

    private void write(Writer writer, String[] data) throws IOException {
        writer.write(TAB_JOINER.join(data) + "\n");
    }

    private final Map<Type, Type> adaptedTypes = new HashMap<>();
    private final Map<Signature, Signature> adaptedSignatures = new HashMap<>();

    private Type adaptType(Mappings oldMap, Mappings newMap, Type oldObfType) {
        Type newObfType = adaptedTypes.get(oldObfType);
        if (newObfType == null) {
            Type oldDeobfType = oldMap.getTranslator(TranslationDirection.Deobfuscating, new TranslationIndex()).translateType(oldObfType);
            newObfType = newMap.getTranslator(TranslationDirection.Obfuscating, new TranslationIndex()).translateType(oldDeobfType);
            adaptedTypes.put(oldObfType, newObfType);
        }
        return newObfType;
    }

    private Signature adaptSignature(Mappings oldMap, Mappings newMap, Signature oldObfType) {
        Signature newObfType = adaptedSignatures.get(oldObfType);
        if (newObfType == null) {
            Signature oldDeobfType = oldMap.getTranslator(TranslationDirection.Deobfuscating, new TranslationIndex()).translateSignature(oldObfType);
            newObfType = newMap.getTranslator(TranslationDirection.Obfuscating, new TranslationIndex()).translateSignature(oldDeobfType);
            adaptedSignatures.put(oldObfType, newObfType);
        }
        return newObfType;
    }

    private void addExistingEntries(Mappings oldMap, Mappings newMap, ClassMapping oldObfClass) {
        ClassMapping newObfClass = newMap.getClassByDeobf(oldObfClass.getDeobfName());
        if (newObfClass != null) {
            if (newMap.containsDeobfClass(oldObfClass.getDeobfName())) {
                System.out.println("Found class match: " + newObfClass.getDeobfName());
                obfMatches.put(oldObfClass.getObfEntry(), newObfClass.getObfEntry());
            }

            for (FieldMapping f : oldObfClass.fields()) {
                if (f.getDeobfName() == null) continue;

                Type newObfType = adaptType(oldMap, newMap, f.getObfType());
                if (newMap.containsDeobfField(newObfClass.getObfEntry(), f.getDeobfName(), newObfType)) {
                    System.out.println("Found field match: " + newObfClass.getDeobfName() + "." + f.getDeobfName());
                    obfMatches.put(f.getObfEntry(oldObfClass.getObfEntry()), newObfClass.getFieldByDeobf(f.getDeobfName(), newObfType).getObfEntry(newObfClass.getObfEntry()));
                }
            }

            for (MethodMapping m : oldObfClass.methods()) {
                if (m.getDeobfName() == null) continue;

                Signature newObfSignature = adaptSignature(oldMap, newMap, m.getObfSignature());
                if (newMap.containsDeobfMethod(newObfClass.getObfEntry(), m.getDeobfName(), newObfSignature)) {
                    System.out.println("Found method match: " + newObfClass.getDeobfName() + "." + m.getDeobfName());
                    obfMatches.put(m.getObfEntry(oldObfClass.getObfEntry()), newObfClass.getMethodByDeobf(m.getDeobfName(), newObfSignature).getObfEntry(newObfClass.getObfEntry()));
                }
            }

            for (ClassMapping m : oldObfClass.innerClasses()) {
                addExistingEntries(oldMap, newMap, m);
            }
        }
    }

    private String getName(Entry entry) {
        String name = intermediaryMap.get(entry);
        if (name == null) {
            String type = (entry instanceof ClassEntry ? "class" : (entry instanceof FieldEntry ? "field" : (entry instanceof BehaviorEntry ? "method" : "other")));
            counters.add(type, 1);
            name = type + "_" + counters.count(type);
            intermediaryMap.put(entry, name);
        }
        return name;
    }

    private void writeClass(JarIndex index, Writer writer, ClassEntry classEntry) throws IOException {
        write(writer, Utils.serializeEntry(classEntry, false, getName(classEntry)));

        for (FieldEntry entry : index.getObfFieldEntries(classEntry)) {
            write(writer, Utils.serializeEntry(entry, false, getName(entry)));
        }

        for (BehaviorEntry entry : index.getObfBehaviorEntries(classEntry)) {
            if (entry instanceof MethodEntry && Utils.isBehaviorProvider(index, classEntry, entry)) {
                write(writer, Utils.serializeEntry(entry, false, getName(entry)));
            }
        }

        for (ClassEntry entry : index.getInnerClasses(classEntry)) {
            writeClass(index, writer, entry);
        }
    }

    private void addExistingEntries(Mappings oldMap, Mappings newMap) {
        for (String c : oldMap.getAllObfClassNames()) {
            ClassMapping oldObfClass = oldMap.getClassByObf(c);
            if (oldObfClass != null) {
                addExistingEntries(oldMap, newMap, oldObfClass);
            }
        }
    }

    @Override
    public void run(String[] args) throws Exception {
        File outIntermediary = new File(args[0]);
        File currentJarFile = new File(args[1]);
        File currentMapFile = new File(args[args.length > 3 ? 3 : 2]);
        File oldMapFile = args.length > 3 ? new File(args[2]) : null;

        if (!currentMapFile.exists()) {
            throw new FileNotFoundException("Current mappings cannot be found!");
        }

        if (!currentJarFile.exists() || !currentJarFile.isFile()) {
            throw new FileNotFoundException("Current JAR file cannot be found!");
        }

        if (oldMapFile != null) {
            if (!oldMapFile.exists()) {
                throw new FileNotFoundException("Old mappings cannot be found!");
            }/* else if (!outIntermediary.exists()) {
                throw new FileNotFoundException("Cannot update intermediary map without existing intermediary file!");
            } */
        }

        System.out.println("Reading current mappings...");
        Mappings currentMap = (new MappingsEnigmaReader()).read(currentMapFile);

        System.out.println("Reading current JAR file...");
        JarIndex index = new JarIndex();
        index.indexJar(new JarFile(currentJarFile), true);

        if (oldMapFile != null) {
            System.out.println("Reading old mappings...");
            Mappings oldMap = (new MappingsEnigmaReader()).read(oldMapFile);

            System.out.println("Building associations...");
            addExistingEntries(oldMap, currentMap);

            System.out.println("Found " + obfMatches.size() + " associations.");

            System.out.println("Reading old intermediary file...");
            // TODO
            /*
            BufferedReader reader = Files.newReader(, Charsets.UTF_8);
            reader.lines().forEach(line -> {
                List<String> dataList = Utils.TAB_SPLITTER.splitToList(line);
                String[] data = dataList.toArray(new String[dataList.size()]);
                if (data.length >= 3 && data[0].equals("#COUNTER")) {
                    counters.setCount(data[1].toLowerCase(), new Integer(data[2]));
                } else {
                    Entry entry = Utils.deserializeEntry(data);
                    if (entry != null) {
                        if (obfMatches.containsKey(entry)) {
                            intermediaryMap.put(obfMatches.getOrDefault(entry, entry), data[data.length - 1]);
                        }
                    }
                }
            });
            */
        }

        System.out.println("Writing intermediary mappings...");
        Writer writer = Files.newWriter(outIntermediary, Charsets.UTF_8);
        for (ClassEntry entry : index.getObfClassEntries()) {
            writeClass(index, writer, entry);
        }
        for (String s : counters.elementSet()) {
            write(writer, new String[]{"#COUNTER", s, String.valueOf(counters.count(s))});
        }
        writer.close();
    }
}
