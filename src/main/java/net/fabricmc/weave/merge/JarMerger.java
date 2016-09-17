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

package net.fabricmc.weave.merge;

import com.google.common.io.ByteStreams;
import net.fabricmc.weave.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class JarMerger {
    public class Entry {
        public final JarEntry metadata;
        public final byte[] data;

        public Entry(JarEntry metadata, byte[] data) {
            this.metadata = new JarEntry(metadata.getName());
            this.metadata.setTime(metadata.getTime());

            this.data = data;
        }
    }

    private static final ClassMerger CLASS_MERGER = new ClassMerger();
    private final JarInputStream inputClient, inputServer;
    private final JarOutputStream output;
    private final Map<String, Entry> entriesClient, entriesServer;
    private final Set<String> entriesAll;

    public JarMerger(JarInputStream inputClient, JarInputStream inputServer, JarOutputStream output) {
        this.inputClient = inputClient;
        this.inputServer = inputServer;
        this.output = output;

        this.entriesClient = new HashMap<>();
        this.entriesServer = new HashMap<>();
        this.entriesAll = new HashSet<>();
    }

    public JarMerger(InputStream inputClient, InputStream inputServer, OutputStream output) throws IOException {
        this(new JarInputStream(inputClient), new JarInputStream(inputServer), new JarOutputStream(output));
    }

    public void close() throws IOException {
        inputClient.close();
        inputServer.close();
        output.close();
    }

    private void readToMap(Map<String, Entry> map, JarInputStream input) throws IOException {
        JarEntry entry;
        while ((entry = input.getNextJarEntry()) != null) {
            map.put(entry.getName(), new Entry(entry, ByteStreams.toByteArray(input)));
        }

        entriesAll.addAll(map.keySet());
    }

    private void add(JarOutputStream output, Entry entry) throws IOException {
        output.putNextEntry(entry.metadata);
        output.write(entry.data);
    }

    private void addClass(JarOutputStream output, Class c) throws IOException {
        String className = c.getName();
        String classPath = className.replace('.', '/') + ".class";
        InputStream stream = c.getClassLoader().getResourceAsStream(classPath);

        JarEntry entry = new JarEntry(classPath);
        entry.setTime(Utils.getTime());

        output.putNextEntry(entry);
        ByteStreams.copy(stream, output);
    }

    public void merge() throws IOException {
        readToMap(entriesClient, inputClient);
        readToMap(entriesServer, inputServer);

        for (String entry : entriesAll) {
            boolean isClass = entry.endsWith("class");
            boolean isMinecraft = entry.startsWith("net/minecraft") || !entry.contains("/");
            Entry result = null;
            String side = null;

            if (isClass && !isMinecraft) {
                // Server bundles libraries, client doesn't - skip them
                continue;
            }

            if (entriesClient.containsKey(entry) && entriesServer.containsKey(entry)) {
                Entry entry1 = entriesClient.get(entry);
                Entry entry2 = entriesServer.get(entry);
                if (Arrays.equals(entry1.data, entry2.data)) {
                    result = entriesClient.get(entry);
                } else {
                    if (isClass) {
                        JarEntry metadata = new JarEntry(entry1.metadata);
                        metadata.setLastModifiedTime(FileTime.fromMillis(Utils.getTime()));

                        result = new Entry(metadata, CLASS_MERGER.merge(entry1.data, entry2.data));
                    } else {
                        // FIXME: More heuristics?
                        result = entriesClient.get(entry);
                        result = new Entry(result.metadata, CLASS_MERGER.addSideInformation(result.data, "CLIENT"));
                    }
                }
            } else if (entriesClient.containsKey(entry)) {
                side = "CLIENT";
                result = entriesClient.get(entry);
            } else if (entriesServer.containsKey(entry)) {
                side = "SERVER";
                result = entriesServer.get(entry);
            }

            if (result != null) {
                if (isMinecraft && isClass && side != null) {
                    result = new Entry(result.metadata, CLASS_MERGER.addSideInformation(result.data, side));
                }

                add(output, result);
            }
        }
    }
}
