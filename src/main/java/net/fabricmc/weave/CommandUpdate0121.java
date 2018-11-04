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
import cuchaz.enigma.bytecode.AccessFlags;
import cuchaz.enigma.mapping.*;
import cuchaz.enigma.mapping.entry.ClassDefEntry;
import cuchaz.enigma.mapping.entry.ClassEntry;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import cuchaz.enigma.mapping.entry.MethodEntry;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

public class CommandUpdate0121 extends Command {
	public CommandUpdate0121() {
		super("updateEnigma0121");
	}

	@Override
	public String getHelpString() {
		return "<jar> <mapping> (fix argument ordering change in 0.12.1)";
	}

	@Override
	public boolean isArgumentCountValid(int count) {
		return count == 2;
	}

	public int getVariableOffset(AccessFlags classAccess, MethodDefEntry methodEntry) {
		if (classAccess.isEnum() && methodEntry.getName().startsWith("<")) {
			return 3;
		} else {
			return methodEntry.getAccess().isStatic() ? 0 : 1;
		}
	}

	@Override
	public void run(String[] args) throws Exception {
		File fileJarIn = new File(args[0]);
		File fileMappings = new File(args[1]);

		System.out.println("Reading JAR...");
		Deobfuscator deobfuscator = new Deobfuscator(new JarFile(fileJarIn));
		for (ClassEntry c : deobfuscator.getJarIndex().getObfClassEntries()) {
			deobfuscator.deobfuscateEntry(c);
		}

		System.out.println("Reading mappings...");
		Mappings mappings = (new MappingsEnigmaReader()).read(fileMappings);
		deobfuscator.setMappings(mappings);

		System.out.println("Fixing argument order (dropping locals)...");
		for (MethodDefEntry entry : deobfuscator.getJarIndex().getObfBehaviorEntries()) {
			ClassMapping mapping = deobfuscator.getMappings().getClassByObf(entry.getOwnerClassEntry());
			if (mapping == null) continue;

			MethodMapping methodMapping = mapping.getMethodByObf(entry);
			if (methodMapping == null) continue;

			System.out.println(entry + " " + methodMapping.getDeobfName());
			Map<Integer, LocalVariableMapping> lvm = new HashMap<>();
			for (LocalVariableMapping m : methodMapping.arguments()) {
				lvm.put(m.getIndex(), m);
			}
			for (int i : lvm.keySet()) {
				mapping.removeArgumentName(methodMapping.getObfName(), methodMapping.getObfDesc(), i);
			}

			MethodDescriptor descriptor = entry.getDesc();

			int oldPos = 0;
			int newPos = getVariableOffset(deobfuscator.getJarIndex().getAccessFlags(entry.getOwnerClassEntry()), entry);
			for (TypeDescriptor type : descriptor.getArgumentDescs()) {
				if (lvm.containsKey(oldPos)) {
					mapping.setArgumentName(methodMapping.getObfName(), methodMapping.getObfDesc(), newPos, lvm.get(oldPos).getName());
					System.out.println(oldPos + " -> " + newPos + ": " + lvm.get(oldPos).getName());
				}
				oldPos++;
				newPos += type.getSize();
			}
		}

		System.out.println("Writing fixed mappings...");
		new MappingsEnigmaWriter().write(fileMappings, mappings, true);
	}
}
