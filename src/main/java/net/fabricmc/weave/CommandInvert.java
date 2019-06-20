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

import cuchaz.enigma.throwables.MappingParseException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTree;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CommandInvert extends Command {
    public CommandInvert() {
        super("invert");
    }

    @Override
    public String getHelpString() {
        return "<source-format> <source> <result-format> <result>";
    }

    @Override
    public boolean isArgumentCountValid(int count) {
        return count == 4;
    }

    @Override
    public void run(String[] args) throws IOException, MappingParseException {
        EntryTree<EntryMapping> source = MappingUtil.read(args[0], Paths.get(args[1]));
        EntryTree<EntryMapping> result = MappingUtil.invert(source);

        Path output = Paths.get(args[3]);
        MappingUtil.delete(output);
        MappingUtil.write(result, args[2], output);
    }
}
