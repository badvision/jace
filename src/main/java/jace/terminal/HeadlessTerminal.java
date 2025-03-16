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

import jace.Emulator;
import jace.core.Utility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import javafx.embed.swing.JFXPanel;

/**
 * Command-line focused Terminal that properly initializes the emulator
 * This allows running in command-line mode while still having
 * access to the emulator's core functionality
 */
public class HeadlessTerminal extends JaceTerminal {
    
    // Flag to prevent initializing emulator when used from UI
    private boolean uiMode = false;
    
    // Flag to track JavaFX initialization
    private static boolean jfxInitialized = false;
    
    /**
     * Initialize JavaFX toolkit
     * This is required for ROM disassembly to work properly
     */
    private static void initJavaFX() {
        if (!jfxInitialized) {
            try {
                // Initialize JavaFX toolkit with a dummy panel
                new JFXPanel();
                jfxInitialized = true;
                System.out.println("JavaFX toolkit initialized");
            } catch (Exception e) {
                System.err.println("Warning: Failed to initialize JavaFX toolkit: " + e.getMessage());
                System.err.println("ROM disassembly commands may not work properly.");
            }
        }
    }
    
    /**
     * Creates a new HeadlessTerminal instance using standard input/output
     */
    public HeadlessTerminal() {
        super(new BufferedReader(new InputStreamReader(System.in)), System.out);
    }
    
    /**
     * Creates a new HeadlessTerminal with custom input/output streams
     * @param reader Input reader
     * @param output Output stream
     */
    public HeadlessTerminal(BufferedReader reader, PrintStream output) {
        super(reader, output);
    }
    
    /**
     * Override to properly initialize the emulator
     * This ensures commands that interact with the emulator work properly
     * 
     * This is also safe to call during tests since it checks if we're in a test environment
     */
    @Override
    public void initializeEmulator() {
        // Initialize JavaFX first
        initJavaFX();
        
        // If we're in UI mode, the emulator is already running
        if (uiMode) {
            getOutput().println("Using existing emulator instance from UI");
            return;
        }
        
        // Don't initialize the emulator if we're running in a test environment
        // This allows tests to set up their own emulator state
        if (isTestEnvironment()) {
            getOutput().println("Test environment detected, skipping emulator initialization");
            return;
        }
        
        // Initialize the emulator normally - JavaFX should be available
        // when running with mvn javafx:run
        super.initializeEmulator();
        getOutput().println("Emulator initialized in command-line focused mode");
    }
    
    /**
     * Check if we're running in a test environment
     * @return true if running in a test environment
     */
    private boolean isTestEnvironment() {
        // Check for JUnit or test-related system properties
        for (String propName : System.getProperties().stringPropertyNames()) {
            if (propName.contains("junit") || propName.contains("test")) {
                return true;
            }
        }
        
        // Check for test classes in the stack trace
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("org.junit") || 
                element.getClassName().endsWith("Test")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Set a flag to indicate that the emulator is already running
     * This prevents the Terminal from trying to initialize a new emulator
     * which can cause conflicts when running from the UI
     */
    public void setEmulatorAlreadyRunning() {
        uiMode = true;
        getOutput().println("Using existing emulator instance");
    }
    
    /**
     * Main entry point for command-line focused Terminal operation
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        // Initialize JavaFX first
        initJavaFX();
        
        // Start the Terminal
        HeadlessTerminal terminal = new HeadlessTerminal();
        
        // Run the Terminal
        terminal.run();
        
        // Clean exit when done
        System.exit(0);
    }
} 