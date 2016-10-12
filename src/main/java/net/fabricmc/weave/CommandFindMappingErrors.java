package net.fabricmc.weave;

import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.analysis.Access;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.mapping.*;

import java.io.File;
import java.util.*;
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

    private boolean isRefValid(Access entryAcc, EntryReference ref, Deobfuscator deobfuscator) {
        EntryReference refDeobf = deobfuscator.deobfuscateReference(ref);
        boolean samePackage = refDeobf.context.getClassEntry().getPackageName().equals(refDeobf.entry.getClassEntry().getPackageName());
        if (samePackage) {
            return true;
        } else if (entryAcc == Access.Protected) {
            ClassEntry c = ref.context.getClassEntry();
            while (c != null) {
                if (c.equals(ref.entry.getClassEntry())) {
                    return true;
                }
                c = deobfuscator.getJarIndex().getTranslationIndex().getSuperclass(c);
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
            Access entryAcc = idx.getAccess(entry);
            if (entryAcc != Access.Public && entryAcc != Access.Private) {
                for (EntryReference<FieldEntry, BehaviorEntry> ref : idx.getFieldReferences(entry)) {
                    boolean valid = isRefValid(entryAcc, ref, deobfuscator);

                    if (!valid) {
                        EntryReference<FieldEntry, BehaviorEntry> refDeobf = deobfuscator.deobfuscateReference(ref);
                        addError(errorStrings, "ERROR: Must be in one package: " + refDeobf.context.getClassEntry() + " and " + refDeobf.entry.getClassEntry(), "field " + refDeobf.entry.getName());
                    }
                }
            }
        }

        for (BehaviorEntry entry : idx.getObfBehaviorEntries()) {
            Access entryAcc = idx.getAccess(entry);
            if (entryAcc != Access.Public && entryAcc != Access.Private) {
                for (EntryReference<BehaviorEntry, BehaviorEntry> ref : idx.getBehaviorReferences(entry)) {
                    boolean valid = isRefValid(entryAcc, ref, deobfuscator);

                    if (!valid) {
                        EntryReference<BehaviorEntry, BehaviorEntry> refDeobf = deobfuscator.deobfuscateReference(ref);
                        addError(errorStrings, "ERROR: Must be in one package: " + refDeobf.context.getClassEntry() + " and " + refDeobf.entry.getClassEntry(), "method " + refDeobf.entry.getName());
                    }
                }
            }
        }

        for (String s : errorStrings.keySet()) {
            System.out.println(s + " (" + String.join(", ", errorStrings.get(s)) + ")");
        }
    }
}
