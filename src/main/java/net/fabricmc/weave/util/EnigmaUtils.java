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

package net.fabricmc.weave.util;

import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.bytecode.AccessFlags;
import cuchaz.enigma.mapping.MethodDescriptor;
import cuchaz.enigma.mapping.TypeDescriptor;
import cuchaz.enigma.mapping.entry.*;
import net.fabricmc.weave.CommandFindMappingErrors;
import net.fabricmc.weave.CommandTinyify;
import net.fabricmc.weave.CommandUpdate0121;
import net.fabricmc.weave.Main;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class EnigmaUtils {
    private static void addAllPotentialAncestors(JarIndex jarIndex, Set<ClassEntry> classEntries, ClassEntry classObfEntry) {
        for (ClassEntry interfaceEntry : jarIndex.getTranslationIndex().getInterfaces(classObfEntry)) {
            if (classEntries.add(interfaceEntry)) {
                addAllPotentialAncestors(jarIndex, classEntries, interfaceEntry);
            }
        }

        ClassEntry superClassEntry1 = jarIndex.getTranslationIndex().getSuperclass(classObfEntry);
        if (superClassEntry1 != null && classEntries.add(superClassEntry1)) {
            addAllPotentialAncestors(jarIndex, classEntries, superClassEntry1);
        }

    }

    public static boolean isMethodProvider(JarIndex jarIndex, ClassEntry classObfEntry, MethodEntry methodEntry) {
        AccessFlags flags = jarIndex.getAccessFlags(methodEntry);
        if (flags.isPrivate() || flags.isStatic()) {
            return true;
        }

        Set<ClassEntry> classEntries = new LinkedHashSet<>();
        addAllPotentialAncestors(jarIndex, classEntries, classObfEntry);

        for (ClassEntry parentEntry : classEntries) {
            MethodEntry ancestorMethodEntry = new MethodEntry(parentEntry, methodEntry.getName(), methodEntry.getDesc());
            if (jarIndex.containsObfMethod(ancestorMethodEntry)) {
                return false;
            }
        }

        return true;
    }

    public static String[] serializeEntry(Entry entry, boolean removeNone, String... extraFields) {
        String[] data = null;

        if (entry instanceof FieldEntry) {
            data = new String[4 + extraFields.length];
            data[0] = "FIELD";
            data[1] = entry.getClassName();
            data[2] = ((FieldEntry) entry).getDesc().toString();
            data[3] = entry.getName();

            if (removeNone) {
                data[1] = Utils.NONE_PREFIX_REMOVER.map(data[1]);
                data[2] = Utils.NONE_PREFIX_REMOVER.mapDesc(data[2]);
            }
        } else if (entry instanceof MethodEntry) {
            data = new String[4 + extraFields.length];
            data[0] = "METHOD";
            data[1] = entry.getClassName();
            data[2] = ((MethodEntry) entry).getDesc().toString();
            data[3] = entry.getName();

            if (removeNone) {
                data[1] = Utils.NONE_PREFIX_REMOVER.map(data[1]);
                data[2] = Utils.NONE_PREFIX_REMOVER.mapMethodDesc(data[2]);
            }
        } else if (entry instanceof ClassEntry) {
            data = new String[2 + extraFields.length];
            data[0] = "CLASS";
            data[1] = entry.getClassName();

            if (removeNone) {
                data[1] = Utils.NONE_PREFIX_REMOVER.map(data[1]);
            }
        }

        if (data != null) {
            System.arraycopy(extraFields, 0, data, data.length - extraFields.length, extraFields.length);
        }

        return data;
    }

    public static Entry deserializeEntry(String[] data) {
        if (data.length > 0) {
            if (data[0].equals("FIELD") && data.length >= 4) {
                return new FieldEntry(new ClassEntry(data[1]), data[3], new TypeDescriptor(data[2]));
            } else if (data[0].equals("METHOD") && data.length >= 4) {
                return new MethodEntry(new ClassEntry(data[1]), data[3], new MethodDescriptor(data[2]));
            } else if (data[0].equals("CLASS") && data.length >= 2) {
                return new ClassEntry(data[1]);
            }
        }

        return null;
    }

    public static void addEnigmaCommands() {
        Main.addCommand(new CommandUpdate0121());
        Main.addCommand(new CommandTinyify());
        Main.addCommand(new CommandFindMappingErrors());
    }
}
