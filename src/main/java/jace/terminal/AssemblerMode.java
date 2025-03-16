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

import java.io.PrintStream;

/**
 * Assembler mode for the Terminal - handles assembly language input
 * This is a stub that will be implemented in the future
 */
public class AssemblerMode implements TerminalMode {
    private final JaceTerminal terminal;
    private final PrintStream output;
    
    public AssemblerMode(JaceTerminal terminal) {
        this.terminal = terminal;
        this.output = terminal.getOutput();
    }
    
    @Override
    public String getName() {
        return "Assembler";
    }
    
    @Override
    public String getPrompt() {
        return "ASM> ";
    }
    
    @Override
    public boolean processCommand(String command) {
        command = command.trim();
        
        // Check for exit command
        if ("exit".equalsIgnoreCase(command) || "quit".equalsIgnoreCase(command)) {
            terminal.setMode("main");
            return true;
        }
        
        output.println("Assembler mode not yet implemented");
        return false;
    }
    
    @Override
    public void printHelp() {
        output.println("Assembler Mode Commands:");
        output.println("  exit/quit     - Exit assembler mode");
        output.println("  ?/help        - Show this help");
        output.println();
        output.println("Assembler mode is not yet implemented");
    }
} 