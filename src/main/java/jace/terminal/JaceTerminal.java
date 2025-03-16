/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import jace.Emulator;
import jace.apple2e.Apple2e;

/**
 * Terminal (Read-Eval-Print Loop) for headless testing of the Jace emulator.
 * Provides a command-line interface with multiple modes to interact with the emulator.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class JaceTerminal {
    private final BufferedReader reader;
    private final PrintStream output;
    private boolean running = true;
    protected TerminalMode currentMode;
    private final Map<String, TerminalMode> modes = new HashMap<>();
    private EmulatorInterface emulator;
    
    /**
     * Creates a new Terminal instance using standard input/output
     */
    public JaceTerminal() {
        this(new BufferedReader(new InputStreamReader(System.in)), System.out);
    }
    
    /**
     * Creates a new Terminal instance with custom input/output streams
     * @param reader Input reader
     * @param output Output stream
     */
    public JaceTerminal(BufferedReader reader, PrintStream output) {
        this.reader = reader;
        this.output = output;
        
        // Register the default modes
        modes.put("main", new MainMode(this));
        modes.put("monitor", new MonitorMode(this));
        modes.put("assembler", new AssemblerMode(this));
        modes.put("debugger", new DebuggerMode(this));
        
        // Set initial mode
        setMode("main");
    }
    
    /**
     * Creates a new Terminal instance with custom input/output streams and an emulator
     * @param reader Input reader
     * @param output Output stream
     * @param emulator The emulator interface to use
     */
    public JaceTerminal(BufferedReader reader, PrintStream output, EmulatorInterface emulator) {
        this(reader, output);
        this.emulator = emulator;
    }
    
    /**
     * Changes the current Terminal mode
     * @param modeName Name of the mode to switch to
     * @return true if mode was changed, false if mode not found
     */
    public boolean setMode(String modeName) {
        TerminalMode mode = modes.get(modeName.toLowerCase());
        if (mode != null) {
            currentMode = mode;
            output.println("Switched to " + mode.getName() + " mode");
            mode.printHelp();
            
            // Notify UI about mode change if we're in UI mode
            updateUIWithCurrentMode();
            
            return true;
        }
        return false;
    }
    
    /**
     * Notify UI about mode changes - can be overridden in UI-aware implementations
     */
    protected void updateUIWithCurrentMode() {
        // By default, does nothing
        // Override in UI-specific implementations to update UI
    }
    
    /**
     * Access to the PrintStream for output
     * @return the output stream
     */
    public PrintStream getOutput() {
        return output;
    }
    
    /**
     * Main Terminal loop - reads, evaluates, and prints until exit
     */
    public void run() {
        // Initialize the emulator if not already done
        if (emulator == null) {
            initializeEmulator();
        }
        
        // Print welcome message
        output.println("Jace Emulator Terminal");
        output.println("Type ? for help, exit to quit");
        
        // Main Terminal loop
        running = true;
        while (running) {
            // Print the prompt
            output.print(currentMode.getPrompt());
            output.flush();
            
            try {
                // Read command
                String command = reader.readLine();
                if (command == null) {
                    // End of stream, exit
                    break;
                }
                
                command = command.trim();
                if (command.isEmpty()) {
                    continue;
                }
                
                // Exit command works in any mode
                if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
                    stop();
                    break;
                }
                
                // Help command works in any mode
                if (command.equals("?") || command.equalsIgnoreCase("help")) {
                    if (command.contains(" ")) {
                        String[] parts = command.split("\\s+", 2);
                        if (parts.length > 1) {
                            if (!currentMode.printCommandHelp(parts[1])) {
                                output.println("No help available for: " + parts[1]);
                            }
                            continue;
                        }
                    }
                    currentMode.printHelp();
                    continue;
                }
                
                // Process command in current mode
                if (!currentMode.processCommand(command)) {
                    output.println("Unknown command: " + command);
                }
            } catch (IOException e) {
                output.println("Error reading input: " + e.getMessage());
                break;
            }
        }
        
        output.println("Exiting Terminal");
    }
    
    /**
     * Stops the Terminal
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Initialize the emulator - can be overridden for testing
     * This implementation is safe to call during tests, as it
     * will check if the emulator is already initialized
     */
    public void initializeEmulator() {
        // Check if there's already a running emulator instance
        if (Emulator.instance != null) {
            // Connect to the existing emulator
            this.emulator = new EmulatorAdapter();
            output.println("Connected to existing emulator instance");
            return;
        }
        
        // Create a real emulator adapter
        this.emulator = new EmulatorAdapter();
    }
    
    /**
     * Get the emulator instance - can be overridden for testing
     * @return The emulator interface
     */
    public EmulatorInterface getEmulator() {
        if (emulator == null) {
            initializeEmulator();
        }
        return emulator;
    }

    /**
     * Set the emulator instance - useful for testing
     * @param emulator The emulator interface to use
     */
    public void setEmulator(EmulatorInterface emulator) {
        this.emulator = emulator;
    }
    
    /**
     * Main entry point for standalone Terminal operation
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        // Start the Terminal
        JaceTerminal terminal = new JaceTerminal();
        
        // Initialize the emulator
        terminal.initializeEmulator();
        
        // Run the Terminal
        terminal.run();
    }
    
    /**
     * Adapter to provide EmulatorInterface for the real Emulator class
     */
    private static class EmulatorAdapter implements EmulatorInterface {
        @Override
        public void withComputer(Consumer<Apple2e> action) {
            Emulator.withComputer(action);
        }
        
        @Override
        public <T> T withComputer(Function<Apple2e, T> function, T defaultValue) {
            return Emulator.withComputer(function, defaultValue);
        }
        
        @Override
        public void whileSuspended(Consumer<Apple2e> action) {
            Emulator.withComputer(c -> {
                c.getMotherboard().whileSuspended(() -> action.accept(c));
            });
        }
    }
} 