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

public class CommandCompose extends Command {
    public CommandCompose() {
        super("compose");
    }

    @Override
    public String getHelpString() {
        return "<left-format> <left> <right-format> <right> <result-format> <result> <keep-mode>";
    }

    @Override
    public boolean isArgumentCountValid(int count) {
        return count == 7;
    }

    @Override
    public void run(String[] args) throws IOException, MappingParseException {
        EntryTree<EntryMapping> left = MappingUtil.read(args[0], Paths.get(args[1]));
        EntryTree<EntryMapping> right = MappingUtil.read(args[2], Paths.get(args[3]));
        EntryTree<EntryMapping> result = MappingUtil.compose(left, right, args[6].equals("left") || args[6].equals("both"), args[6].equals("right") || args[6].equals("both"));

        Path output = Paths.get(args[5]);
        MappingUtil.delete(output);
        MappingUtil.write(result, args[4], output);
    }
}
