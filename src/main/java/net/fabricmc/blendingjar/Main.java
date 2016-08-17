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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarFile;

public class Main {
    private static void error(String message) {
        System.err.println(message);
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            error("Arguments: blending-jar [inputClient] [inputServer] [output]");
        }

        File in1f = new File(args[0]);
        File in2f = new File(args[1]);
        File outf = new File(args[2]);

        if (!in1f.exists() || !in1f.isFile()) {
            error("Input Client file does not exist!");
        }

        if (!in2f.exists() || !in2f.isFile()) {
            error("Input Server file does not exist!");
        }

        try {
            FileInputStream in1fs = new FileInputStream(in1f);
            FileInputStream in2fs = new FileInputStream(in2f);
            FileOutputStream outfs = new FileOutputStream(outf);

            JarMerger merger = new JarMerger(in1fs, in2fs, outfs);

            System.out.println("Will it blend? That is the question!\n(Starring " + in1f.getName() + " and " + in2f.getName() + ")");

            final String theme = "doo doo do do doo DOO! DOO! do do doo doo doo DO! DO! DO! ";
            final int themeL = theme.length();

            merger.merge(new IEntryCallback() {
                private int pos = 0;

                private void printChar() {
                    System.out.print(theme.charAt(pos % themeL));
                    pos++;
                }

                @Override
                public void onProcessEntry(String entry) {
                    printChar();
                    while (theme.charAt(pos % themeL) == ' ') {
                        printChar();
                    }
                }
            });
            merger.close();

            in1fs.close();
            in2fs.close();
            outfs.close();

            System.out.println("\nOof! " + outf.getName() + " smoke! Don't breathe this!\nResult: They both blend!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
