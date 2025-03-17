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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import jace.Emulator;
import jace.apple2e.Apple2e;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Controller class for managing Terminal UI windows
 * This is separate from the core Terminal logic to maintain separation of concerns
 */
public class TerminalUIController {
    
    /**
     * Custom PrintStream that directly updates the UI TextArea
     * This eliminates the need for pipes which can be closed unexpectedly during debugging
     */
    private static class TextAreaPrintStream extends PrintStream {
        private final TextArea textArea;
        private final StringBuilder lineBuffer = new StringBuilder();
        private final AtomicBoolean updateScheduled = new AtomicBoolean(false);
        private final ConcurrentLinkedQueue<String> outputQueue = new ConcurrentLinkedQueue<>();
        
        public TextAreaPrintStream(TextArea textArea) {
            super(new ByteArrayOutputStream(), true); // Autoflush
            this.textArea = textArea;
        }
        
        @Override
        public void write(int b) {
            // Convert to char
            char c = (char) b;
            
            // If newline, process the buffered line
            if (c == '\n') {
                commitLine();
            } else {
                // Add to buffer
                lineBuffer.append(c);
            }
        }
        
        @Override
        public void write(byte[] buf, int off, int len) {
            // For performance, handle byte arrays directly
            String str = new String(buf, off, len);
            
            // Check for newlines
            int lastNewline = str.lastIndexOf('\n');
            if (lastNewline >= 0) {
                // Split by last newline
                String beforeNewline = str.substring(0, lastNewline);
                String afterNewline = str.substring(lastNewline + 1);
                
                // Process everything before the last newline
                lineBuffer.append(beforeNewline);
                commitLine();
                
                // Start a new buffer with anything after the last newline
                lineBuffer.append(afterNewline);
            } else {
                // No newlines, just append to buffer
                lineBuffer.append(str);
            }
        }
        
        private void commitLine() {
            if (lineBuffer.length() > 0) {
                // Get the processed line
                String processedLine = processLine(lineBuffer.toString());
                
                // Add to queue if not empty
                if (!processedLine.isEmpty()) {
                    outputQueue.add(processedLine);
                    scheduleUpdate();
                }
                
                // Clear the buffer
                lineBuffer.setLength(0);
            }
        }
        
        private String processLine(String line) {
            // Skip processing if the line is empty
            if (line.trim().isEmpty()) {
                return "";
            }
            
            // Get the current mode
            TerminalMode mode = getCurrentMode();
            
            // Check for pure prompt lines (only the prompt with optional whitespace)
            if (mode != null && line.trim().equals(mode.getPrompt())) {
                // Don't display pure prompt lines (they're shown in the input area)
                return "";
            }
            
            // Check if line starts with the current prompt
            if (mode != null && line.startsWith(mode.getPrompt())) {
                // Extract everything after the prompt
                return line.substring(mode.getPrompt().length()).trim();
            }
            
            // Otherwise, return the line as-is
            return line;
        }
        
        private void scheduleUpdate() {
            // Only schedule if not already scheduled
            if (updateScheduled.compareAndSet(false, true)) {
                Platform.runLater(this::updateTextArea);
            }
        }
        
        private void updateTextArea() {
            try {
                // Process all queued output
                String line;
                while ((line = outputQueue.poll()) != null) {
                    textArea.appendText(line + "\n");
                }
                
                // Force scroll to bottom
                textArea.setScrollTop(Double.MAX_VALUE);
                textArea.positionCaret(textArea.getText().length());
            } catch (Exception e) {
                System.err.println("Error updating console: " + e);
            } finally {
                // Reset the scheduled flag
                updateScheduled.set(false);
                
                // If more items were added during processing, schedule another update
                if (!outputQueue.isEmpty()) {
                    scheduleUpdate();
                }
            }
        }
        
        @Override
        public void flush() {
            // Flush any buffered content
            commitLine();
        }
        
        @Override
        public void close() {
            // Nothing special needed for close
            super.close();
        }
    }
    
    /**
     * Custom EmulatorAdapter implementation that works with our UI Terminal
     */
    private static class UIEmulatorAdapter implements EmulatorInterface {
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
    
    /**
     * Current mode for UI tracking
     */
    private static TerminalMode currentMode;
    
    /**
     * Terminal UI-specific implementation that maintains connection with UI
     */
    private static class UITerminal extends jace.terminal.UITerminal {
        public UITerminal(BufferedReader reader, PrintStream output) {
            super(reader, output);
        }
        
        // Override setMode to update UI with mode changes
        @Override
        public boolean setMode(String mode) {
            boolean result = super.setMode(mode);
            if (result) {
                // Use static method to update UI
                TerminalUIController.setCurrentMode(getCurrentMode());
            }
            return result;
        }
        
        @Override
        public EmulatorInterface getEmulator() {
            // Force connection to the existing emulator
            if (super.getEmulator() == null) {
                setEmulator(new UIEmulatorAdapter());
            }
            return super.getEmulator();
        }
        
        @Override
        public void stop() {
            super.stop();
            // Additional UI-specific cleanup could go here
        }
    }
    
    /**
     * An abstract class implementing TerminalMode to simplify custom mode creation
     */
    private abstract static class AbstractTerminalMode implements TerminalMode {
        private final JaceTerminal terminal;
        
        public AbstractTerminalMode(JaceTerminal terminal) {
            this.terminal = terminal;
        }
        
        protected JaceTerminal getTerminal() {
            return terminal;
        }
    }
    
    /**
     * UI Terminal Mode for linking with the UI
     */
    private static class UITerminalMode extends AbstractTerminalMode {
        public UITerminalMode() {
            super(null);
        }
        
        @Override
        public String getName() { return "Main"; }
        
        @Override
        public String getPrompt() { return "JACE> "; }
        
        @Override
        public boolean processCommand(String command) { return false; }
        
        @Override
        public void printHelp() {}
        
        @Override
        public boolean printCommandHelp(String command) { return false; }
    }
    
    /**
     * Label for displaying current mode
     */
    private static javafx.scene.control.Label modeLabel;
    
    /**
     * Sets the current mode and updates the UI
     * @param mode The new terminal mode
     */
    public static void setCurrentMode(TerminalMode mode) {
        currentMode = mode;
        
        // Update UI on JavaFX thread
        if (modeLabel != null) {
            Platform.runLater(() -> {
                try {
                    if (mode != null) {
                        modeLabel.setText(mode.getPrompt());
                    } else {
                        modeLabel.setText("MAIN>");
                    }
                } catch (Exception e) {
                    System.err.println("Error updating mode label: " + e);
                }
            });
        }
    }
    
    /**
     * Gets the current terminal mode
     * @return The current terminal mode
     */
    public static TerminalMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Opens a new Terminal window
     */
    public static void openTerminalWindow() {
        // Make sure we run UI creation on the JavaFX thread
        Platform.runLater(() -> {
            // Create the stage for our Terminal window
            Stage terminalStage = new Stage(StageStyle.DECORATED);
            terminalStage.setTitle("Jace Terminal");
            
            // Set up the console window - output area
            TextArea consoleOutput = new TextArea();
            consoleOutput.setEditable(false);
            consoleOutput.setWrapText(true);
            consoleOutput.setStyle("-fx-font-family: 'monospace';");
            
            // Set up input field and send button
            TextField inputField = new TextField();
            inputField.setPromptText("Enter command...");
            
            Button sendButton = new Button("Send");
            
            // Add a label to display the current mode 
            modeLabel = new javafx.scene.control.Label("MAIN>");
            modeLabel.setStyle("-fx-font-family: 'monospace'; -fx-font-weight: bold;");
            
            // Arrange input components horizontally
            HBox inputBox = new HBox(5, modeLabel, inputField, sendButton);
            inputBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(inputField, Priority.ALWAYS);
            
            // Main layout with output area and input box
            BorderPane layout = new BorderPane();
            layout.setCenter(consoleOutput);
            layout.setBottom(inputBox);
            layout.setPadding(new Insets(10));
            
            // Set up the scene
            Scene scene = new Scene(layout, 650, 400);
            terminalStage.setScene(scene);
            
            try {
                // Create a pipe for input only - this is much simpler than before
                final PipedOutputStream uiToTerminal = new PipedOutputStream();
                final PipedInputStream terminalInput = new PipedInputStream(uiToTerminal, 8192);
                
                // Create readers for the Terminal
                BufferedReader reader = new BufferedReader(new InputStreamReader(terminalInput));
                
                // Create a custom PrintStream that updates the UI directly
                PrintStream printStream = new TextAreaPrintStream(consoleOutput);
                
                // Initialize the Terminal in a background thread - not on the JavaFX thread!
                Thread terminalThread = new Thread(() -> {
                    try {
                        // Create Terminal instance - use UI-specific implementation
                        UITerminal terminal = new UITerminal(reader, printStream);
                        
                        // Make sure it knows the emulator is already running
                        terminal.setEmulatorAlreadyRunning();
                        
                        // Clear the console and show initializing message
                        Platform.runLater(() -> {
                            consoleOutput.clear();
                            consoleOutput.appendText("Initializing Terminal...\n");
                        });
                        
                        // Force a small delay to ensure UI is updated before terminal starts
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        
                        // Run the Terminal - this will block until exit
                        terminal.run();
                        
                        // When done, close the window
                        Platform.runLater(() -> {
                            if (terminalStage.isShowing()) {
                                terminalStage.close();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Show error in UI
                        Platform.runLater(() -> {
                            consoleOutput.appendText("\nError: " + e.getMessage() + "\n");
                        });
                    }
                });
                
                // Set the thread as daemon so it doesn't prevent app exit
                terminalThread.setDaemon(true);
                terminalThread.start();
                
                // Set up send button and enter key actions
                EventHandler<ActionEvent> sendAction = event -> {
                    String command = inputField.getText().trim();
                    if (!command.isEmpty()) {
                        try {
                            // Echo command to console output - include the prompt
                            String displayPrefix = (currentMode != null) ? currentMode.getPrompt() : "?";
                            final String displayCommand = displayPrefix + command;
                            
                            Platform.runLater(() -> {
                                consoleOutput.appendText(displayCommand + "\n");
                                consoleOutput.setScrollTop(Double.MAX_VALUE);
                                consoleOutput.positionCaret(consoleOutput.getText().length());
                            });
                            
                            // Send command to terminal without echoing (the terminal will handle output)
                            uiToTerminal.write((command + "\n").getBytes());
                            uiToTerminal.flush();
                            
                            // Clear input field
                            inputField.clear();
                        } catch (IOException e) {
                            System.err.println("Error sending command: " + e);
                            Platform.runLater(() -> {
                                consoleOutput.appendText("\nError sending command: " + e.getMessage() + "\n");
                            });
                        }
                    }
                };
                
                // Connect send button and enter key to action
                sendButton.setOnAction(sendAction);
                inputField.setOnAction(sendAction);
                
                // Set up window close handling - close input pipe to end thread
                terminalStage.setOnCloseRequest(event -> {
                    try {
                        uiToTerminal.close();
                    } catch (IOException e) {
                        System.err.println("Error closing terminal pipe: " + e);
                    }
                });
                
            } catch (IOException e) {
                consoleOutput.setText("Error setting up terminal: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Show the window
            terminalStage.show();
            
            // Set focus to input field
            inputField.requestFocus();
        });
    }
} 