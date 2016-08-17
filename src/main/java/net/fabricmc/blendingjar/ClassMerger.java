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

package net.fabricmc.blendingjar;

import net.fabricmc.api.Side;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassMerger {
    private abstract class Merger<T> {
        private final Map<String, T> entriesClient, entriesServer;
        private final Set<String> entryNames;

        public Merger(List<T> entriesClient, List<T> entriesServer) {
            this.entriesClient = toMap(entriesClient);
            this.entriesServer = toMap(entriesServer);

            this.entryNames = new HashSet<>();
            this.entryNames.addAll(this.entriesClient.keySet());
            this.entryNames.addAll(this.entriesServer.keySet());
        }

        public abstract String getName(T entry);
        public abstract void applySide(T entry, Side side);

        private final Map<String, T> toMap(List<T> entries) {
            Map<String, T> map = new HashMap<String, T>();
            for (T entry : entries) {
                map.put(getName(entry), entry);
            }
            return map;
        }

        public void merge(List<T> list) {
            for (String s : entryNames) {
                if (entriesClient.containsKey(s) && entriesServer.containsKey(s)) {
                    list.add(entriesClient.get(s));
                } else if (entriesClient.containsKey(s)) {
                    applySide(entriesClient.get(s), Side.CLIENT);
                    list.add(entriesClient.get(s));
                } else if (entriesServer.containsKey(s)) {
                    applySide(entriesServer.get(s), Side.SERVER);
                    list.add(entriesServer.get(s));
                }
            }
        }
    }

    private void visitSideAnnotation(AnnotationVisitor av, Side side) {
        av.visitEnum("value", "Lnet/fabricmc/api/Side;", side.name().toUpperCase());
        av.visitEnd();
    }

    private class SidedClassVisitor extends ClassVisitor {
        private final Side side;

        public SidedClassVisitor(int api, ClassVisitor cv, Side side) {
            super(api, cv);
            this.side = side;
        }

        @Override
        public void visitEnd() {
            AnnotationVisitor av = cv.visitAnnotation("Lnet/fabricmc/api/Sided;", true);
            visitSideAnnotation(av, side);
            super.visitEnd();
        }
    }

    public ClassMerger() {

    }

    public byte[] addSideInformation(byte[] classSided, Side side) {
        ClassReader reader = new ClassReader(classSided);
        ClassWriter writer = new ClassWriter(0);

        reader.accept(new SidedClassVisitor(Opcodes.ASM5, writer, side), 0);

        return writer.toByteArray();
    }

    public byte[] merge(byte[] classClient, byte[] classServer) {
        ClassReader readerC = new ClassReader(classClient);
        ClassReader readerS = new ClassReader(classServer);
        ClassWriter writer = new ClassWriter(0);

        ClassNode nodeC = new ClassNode(Opcodes.ASM5);
        readerC.accept(nodeC, 0);

        ClassNode nodeS = new ClassNode(Opcodes.ASM5);
        readerS.accept(nodeS, 0);

        ClassNode nodeOut = new ClassNode(Opcodes.ASM5);
        nodeOut.version = nodeC.version;
        nodeOut.access = nodeC.access;
        nodeOut.name = nodeC.name;
        nodeOut.signature = nodeC.signature;
        nodeOut.interfaces = nodeC.interfaces;
        nodeOut.superName = nodeC.superName;
        nodeOut.sourceFile = nodeC.sourceFile;
        nodeOut.sourceDebug = nodeC.sourceDebug;
        nodeOut.outerClass = nodeC.outerClass;
        nodeOut.outerMethod = nodeC.outerMethod;
        nodeOut.outerMethodDesc = nodeC.outerMethodDesc;

        nodeOut.invisibleAnnotations = nodeC.invisibleAnnotations;
        nodeOut.invisibleTypeAnnotations = nodeC.invisibleTypeAnnotations;
        nodeOut.visibleAnnotations = nodeC.visibleAnnotations;
        nodeOut.visibleTypeAnnotations = nodeC.visibleTypeAnnotations;
        nodeOut.attrs = nodeC.attrs;

        nodeOut.innerClasses = nodeC.innerClasses;
        /* new Merger<InnerClassNode>(nodeC.innerClasses, nodeS.innerClasses) {
            @Override
            public String getName(InnerClassNode entry) {
                return entry.name;
            }

            @Override
            public void applySide(InnerClassNode entry, Side side) {
            }
        }.merge(nodeOut.innerClasses); */

        nodeOut.fields = nodeC.fields;
        /* new Merger<FieldNode>(nodeC.fields, nodeS.fields) {
            @Override
            public String getName(FieldNode entry) {
                return entry.name + "," + entry.desc + "," + entry.signature;
            }

            @Override
            public void applySide(FieldNode entry, Side side) {
                AnnotationVisitor av = entry.visitAnnotation("Lnet/fabricmc/api/Sided;", true);
                visitSideAnnotation(av, side);
            }
        }.merge(nodeOut.fields); */

        new Merger<MethodNode>(nodeC.methods, nodeS.methods) {
            @Override
            public String getName(MethodNode entry) {
                return entry.name + "," + entry.desc + "," + entry.signature;
            }

            @Override
            public void applySide(MethodNode entry, Side side) {
                AnnotationVisitor av = entry.visitAnnotation("Lnet/fabricmc/api/Sided;", true);
                visitSideAnnotation(av, side);
            }
        }.merge(nodeOut.methods);

        nodeOut.accept(writer);
        return writer.toByteArray();
    }
}
