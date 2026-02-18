/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

public class ProcessHelper {

    public static Process startApplication(String executable, String... args) throws IOException {
        final ArrayList<String> command = new ArrayList<>();
        command.add(executable);
        if (args != null) {
            command.addAll(Arrays.asList(args));
        }
        return new ProcessBuilder(command).inheritIO().start();
    }

    public static ArrayList<String> startApplicationAndGetOutput(String executable, String... args)
            throws IOException, InterruptedException {
        final ArrayList<String> command = new ArrayList<>();
        command.add(executable);
        if (args != null) {
            command.addAll(Arrays.asList(args));
        }
        Process process = new ProcessBuilder(command).start();
        ArrayList<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }
        process.waitFor();
        return output;
    }
}
