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
import java.util.ArrayList;
import java.util.List;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.core.Debugger;

/**
 * Debugger mode for the Terminal - provides advanced debugging capabilities
 * This is a stub that will be implemented in the future
 */
public class DebuggerMode implements TerminalMode {
    private final JaceTerminal terminal;
    private final PrintStream output;
    private final List<Integer> breakpoints = new ArrayList<>();
    private Debugger debugger;
    
    public DebuggerMode(JaceTerminal terminal) {
        this.terminal = terminal;
        this.output = terminal.getOutput();
        initDebugger();
    }
    
    private void initDebugger() {
        debugger = new Debugger() {
            @Override
            public void updateStatus() {
                MOS65C02 cpu = (MOS65C02) Emulator.withComputer(c->c.getCpu(), null);
                if (cpu != null) {
                    // Update UI with CPU state if needed
                }
            }
        };
    }
    
    @Override
    public String getName() {
        return "Debugger";
    }
    
    @Override
    public String getPrompt() {
        return "DEBUG> ";
    }
    
    @Override
    public boolean processCommand(String command) {
        command = command.trim();
        
        // Check for exit command
        if ("exit".equalsIgnoreCase(command) || "quit".equalsIgnoreCase(command)) {
            terminal.setMode("main");
            return true;
        }
        
        // Basic commands to be implemented
        if (command.startsWith("break ")) {
            output.println("Breakpoint functionality not yet implemented");
            return true;
        } else if (command.equals("continue") || command.equals("c")) {
            output.println("Continue execution not yet implemented");
            return true;
        } else if (command.equals("step") || command.equals("s")) {
            output.println("Step execution not yet implemented");
            return true;
        } else if (command.equals("list") || command.equals("l")) {
            output.println("Listing breakpoints not yet implemented");
            return true;
        }
        
        output.println("Debugger mode not fully implemented yet");
        return false;
    }
    
    @Override
    public void printHelp() {
        output.println("Debugger Mode Commands:");
        output.println("  break <addr>  - Set breakpoint at address");
        output.println("  continue/c    - Continue execution");
        output.println("  step/s        - Step one instruction");
        output.println("  list/l        - List breakpoints");
        output.println("  exit/quit     - Exit debugger mode");
        output.println("  ?/help        - Show this help");
        output.println();
        output.println("Debugger mode is not yet fully implemented");
    }
} 