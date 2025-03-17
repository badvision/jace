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
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

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
    
    // Track current Terminal mode for UI display - initialize with MainMode
    private static TerminalMode currentMode = new MainMode(null);
    private static javafx.scene.control.Label modeLabel;
    
    /**
     * Set the current Terminal mode
     * Called by UITerminal when the mode changes
     *
     * @param mode New Terminal mode
     */
    public static void setCurrentMode(TerminalMode mode) {
        currentMode = mode;
        
        // Update the mode label if available
        if (modeLabel != null && mode != null) {
            Platform.runLater(() -> {
                modeLabel.setText(mode.getPrompt());
            });
        }
    }
    
    /**
     * Get the current Terminal mode
     * 
     * @return Current Terminal mode or null if not set
     */
    public static TerminalMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Open a new Terminal window
     * This handles all UI setup and connects to a Terminal instance
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
            
            // Set up piped I/O - this allows communication between the UI and Terminal
            try {
                // Create the pipes for input/output
                final PipedOutputStream uiToTerminal = new PipedOutputStream();
                final PipedInputStream terminalInput = new PipedInputStream(uiToTerminal);
                
                final PipedOutputStream terminalOutput = new PipedOutputStream();
                final PipedInputStream terminalToUi = new PipedInputStream(terminalOutput);
                
                // Create readers/writers for the Terminal
                BufferedReader reader = new BufferedReader(new InputStreamReader(terminalInput));
                
                // Create a PrintStream with a custom OutputStream that formats output
                PrintStream printStream = new PrintStream(new OutputStream() {
                    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    
                    @Override
                    public void write(int b) throws IOException {
                        // Pass through to the terminal output without any special handling
                        terminalOutput.write(b);
                        
                        // If newline, flush our buffer
                        if (b == '\n') {
                            buffer.reset();
                        } else {
                            // Add to our buffer
                            buffer.write(b);
                        }
                    }
                    
                    @Override
                    public void flush() throws IOException {
                        terminalOutput.flush();
                    }
                    
                    @Override
                    public void close() throws IOException {
                        terminalOutput.close();
                    }
                }, true);
                
                // Initialize the Terminal in a background thread - not on the JavaFX thread!
                Thread terminalThread = new Thread(() -> {
                    try {
                        // Create Terminal instance - use UI-specific implementation
                        UITerminal terminal = new UITerminal(reader, printStream);
                        
                        // Make sure it knows the emulator is already running
                        terminal.setEmulatorAlreadyRunning();
                        
                        // Notify the user - safely update text on UI thread
                        Platform.runLater(() -> {
                            try {
                                consoleOutput.setText("Initializing Terminal...\n");
                            } catch (Exception e) {
                                System.err.println("Error updating console: " + e);
                            }
                        });
                        
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
                
                // Set up a reader thread to get output from the Terminal to the UI
                Thread readerThread = new Thread(() -> {
                    try (BufferedReader uiReader = new BufferedReader(new InputStreamReader(terminalToUi))) {                        
                        String line;
                        while ((line = uiReader.readLine()) != null) {
                            final String finalLine = line;
                            // Update UI - must be on JavaFX thread
                            Platform.runLater(() -> {
                                try {
                                    // Check for pure prompt lines first (only the prompt with optional whitespace)
                                    if (currentMode != null && finalLine.trim().equals(currentMode.getPrompt())) {
                                        // Don't display pure prompt lines (they're shown in the input area)
                                        return;
                                    }
                                    
                                    // Handle lines containing both prompt and output
                                    String processedLine = finalLine;
                                    
                                    // Check if line starts with the current prompt
                                    if (currentMode != null && processedLine.startsWith(currentMode.getPrompt())) {
                                        // Extract everything after the prompt
                                        processedLine = processedLine.substring(currentMode.getPrompt().length()).trim();
                                    }
                                    
                                    // Always display the line if it's not just a prompt
                                    if (!processedLine.isEmpty()) {
                                        consoleOutput.appendText(processedLine + "\n");
                                        
                                        // Force scroll to bottom with both methods to ensure it works
                                        consoleOutput.setScrollTop(Double.MAX_VALUE);
                                        consoleOutput.positionCaret(consoleOutput.getText().length());
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error updating console: " + e);
                                }
                            });
                        }
                    } catch (IOException e) {
                        // This is expected when terminal closes
                        System.out.println("Terminal output pipe closed");
                    }
                });
                
                // Set up send button and enter key actions
                EventHandler<ActionEvent> sendAction = event -> {
                    String command = inputField.getText().trim();
                    if (!command.isEmpty()) {
                        try {
                            // Echo command to console output
                            Platform.runLater(() -> {
                                // Add the user command with a newline
                                consoleOutput.appendText("\n" + command + "\n");
                                // Force scroll to bottom with both methods to ensure it works
                                consoleOutput.setScrollTop(Double.MAX_VALUE);
                                consoleOutput.positionCaret(consoleOutput.getText().length());
                            });
                            
                            // Send command to terminal
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
                
                // Set this daemon as well
                readerThread.setDaemon(true);
                readerThread.start();
                
                // Set up window close handling - close the pipes to end threads
                terminalStage.setOnCloseRequest(event -> {
                    try {
                        uiToTerminal.close();
                        terminalOutput.close();
                    } catch (IOException e) {
                        System.err.println("Error closing terminal pipes: " + e);
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