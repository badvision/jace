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
    }
    
    /**
     * Override to update UI when mode changes
     */
    @Override
    protected void updateUIWithCurrentMode() {
        // Update the UI with the current mode - now using TerminalUIController
        TerminalUIController.setCurrentMode(currentMode);
    }
} 