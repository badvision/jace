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
import java.io.PrintStream;

import jace.Emulator;

/**
 * UI-specific Terminal implementation that communicates mode changes back to the UI
 */
public class UITerminal extends HeadlessTerminal {
    
    /**
     * Creates a UI-aware Terminal
     * @param reader Input reader
     * @param output Output stream
     */
    public UITerminal(BufferedReader reader, PrintStream output) {
        super(reader, output);
        // Always mark as UI mode
        setEmulatorAlreadyRunning();
        
        // Directly connect to the existing emulator instance
        connectToEmulator();
    }
    
    /**
     * Ensure the emulator connection is established.
     * This method can be called at key points to ensure the connection
     * is never lost.
     */
    private void connectToEmulator() {
        if (Emulator.instance != null) {
            // Force initialization of emulator connection
            if (getEmulator() != null) {
                getOutput().println("Connected to existing Jace instance");
            } else {
                getOutput().println("Failed to connect to Jace instance even though it exists");
            }
        } else {
            getOutput().println("WARNING: No running Jace instance found. Monitor commands may not work properly.");
        }
    }
    
    /**
     * Override to update UI when mode changes
     */
    @Override
    protected void updateUIWithCurrentMode() {
        // Ensure emulator connection is maintained
        if (getEmulator() == null) {
            connectToEmulator();
        }
        
        // Update the UI with the current mode - now using TerminalUIController
        TerminalUIController.setCurrentMode(currentMode);
    }
    
    /**
     * Override run to ensure emulator connection is maintained
     */
    @Override
    public void run() {
        // Make one final check for emulator connection before starting
        if (getEmulator() == null) {
            connectToEmulator();
        }
        
        // Run the terminal as usual
        super.run();
    }
} 