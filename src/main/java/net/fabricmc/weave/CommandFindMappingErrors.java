/*
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.analysis.Access;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.bytecode.AccessFlags;
import cuchaz.enigma.mapping.Mappings;
import cuchaz.enigma.mapping.MappingsEnigmaReader;
import cuchaz.enigma.mapping.entry.ClassEntry;
import cuchaz.enigma.mapping.entry.FieldEntry;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import cuchaz.enigma.mapping.entry.MethodEntry;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarFile;

public class CommandFindMappingErrors extends Command {
    public CommandFindMappingErrors() {
        super("findMappingErrors");
    }

    @Override
    public String getHelpString() {
        return "<obf jar> <mappings>";
    }

    @Override
    public boolean isArgumentCountValid(int count) {
        return count == 2;
    }

    private void addError(SortedMap<String, Set<String>> errorStrings, String error, String cause) {
        if (!errorStrings.containsKey(error)) {
            errorStrings.put(error, new HashSet<>());
        }
        errorStrings.get(error).add(cause);
    }

    private boolean isRefValid(AccessFlags entryAcc, EntryReference ref, Deobfuscator deobfuscator) {
        EntryReference refDeobf = deobfuscator.deobfuscateReference(ref);
        String packageCtx = refDeobf.context.getOwnerClassEntry().getPackageName();
        String packageEntry = refDeobf.entry.getOwnerClassEntry().getPackageName();
        boolean samePackage = (packageCtx == null && packageEntry == null) || (packageCtx != null && packageCtx.equals(packageEntry));
        if (samePackage) {
            return true;
        } else if (entryAcc.isProtected()) {
            // TODO: Is this valid?
            for (ClassEntry ctx : ref.context.getOwnerClassEntry().getClassChain()) {
                ClassEntry c = ctx;
                while (c != null) {
                    if (c.equals(ref.entry.getOwnerClassEntry())) {
                        return true;
                    }
                    c = deobfuscator.getJarIndex().getTranslationIndex().getSuperclass(c);
                }
            }
        }

        return false;
    }

    @Override
    public void run(String[] args) throws Exception {
        File fileJarIn = new File(args[0]);
        File fileMappings = new File(args[1]);

        System.out.println("Reading JAR...");
        Deobfuscator deobfuscator = new Deobfuscator(new JarFile(fileJarIn));
        System.out.println("Reading mappings...");
        Mappings mappings = (new MappingsEnigmaReader()).read(fileMappings);
        deobfuscator.setMappings(mappings);

        JarIndex idx = deobfuscator.getJarIndex();
        SortedMap<String, Set<String>> errorStrings = new TreeMap<>();

        for (FieldEntry entry : idx.getObfFieldEntries()) {
            AccessFlags entryAcc = idx.getAccessFlags(entry);
            if (!entryAcc.isPublic() && !entryAcc.isPrivate()) {
                for (EntryReference<FieldEntry, MethodDefEntry> ref : idx.getFieldReferences(entry)) {
                    boolean valid = isRefValid(entryAcc, ref, deobfuscator);

                    if (!valid) {
                        EntryReference<FieldEntry, MethodDefEntry> refDeobf = deobfuscator.deobfuscateReference(ref);
                        addError(errorStrings, "ERROR: Must be in one package: " + refDeobf.context.getOwnerClassEntry() + " and " + refDeobf.entry.getOwnerClassEntry(), "field " + refDeobf.entry.getName());
                    }
                }
            }
        }

        for (MethodEntry entry : idx.getObfBehaviorEntries()) {
            AccessFlags entryAcc = idx.getAccessFlags(entry);
            if (!entryAcc.isPublic() && !entryAcc.isPrivate()) {
                for (EntryReference<MethodEntry, MethodDefEntry> ref : idx.getMethodsReferencing(entry)) {
                    boolean valid = isRefValid(entryAcc, ref, deobfuscator);

                    if (!valid) {
                        EntryReference<MethodEntry, MethodDefEntry> refDeobf = deobfuscator.deobfuscateReference(ref);
                        addError(errorStrings, "ERROR: Must be in one package: " + refDeobf.context.getOwnerClassEntry() + " and " + refDeobf.entry.getOwnerClassEntry(), "method " + refDeobf.entry.getName());
                    }
                }
            }
        }

        for (String s : errorStrings.keySet()) {
            System.out.println(s + " (" + String.join(", ", errorStrings.get(s)) + ")");
        }
    }
}
