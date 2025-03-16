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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.Apple2e;
import jace.cheat.Cheats;
import jace.core.Computer;
import jace.core.Debugger;
import jace.core.RAM;
import jace.core.RAMEvent;
import jace.core.RAMListener;

/**
 * Debugger mode for the Terminal - provides advanced debugging capabilities
 */
public class DebuggerMode implements TerminalMode {
    private final JaceTerminal terminal;
    private final PrintStream output;
    private final List<Watch> watches = new ArrayList<>();
    private final Map<Integer, Integer> cheats = new HashMap<>();
    private Debugger debugger;
    private boolean isPaused = false;
    private AtomicBoolean isStepping = new AtomicBoolean(false);

    // Memory address modes (same as monitor)
    public enum MemoryMode {
        MAIN,   // Use main memory bank
        AUX,    // Use auxiliary memory bank
        ACTIVE  // Use active memory configuration
    }

    // Regex patterns for commands
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^([Mm]|[Xx])?([0-9A-Fa-f]{1,4})$");
    
    /**
     * Watch class to track memory changes
     */
    private class Watch {
        private final int address;
        private final String name;
        private final MemoryMode mode;
        private final RAMListener listener;
        
        public Watch(String name, int address, MemoryMode mode) {
            this.name = name;
            this.address = address;
            this.mode = mode;
            
            // Create a RAM listener to watch this address
            Boolean auxFlag = null;
            if (mode == MemoryMode.MAIN) {
                auxFlag = false;
            } else if (mode == MemoryMode.AUX) {
                auxFlag = true;
            }
            
            final Boolean finalAuxFlag = auxFlag;
            
            listener = Emulator.withMemory(ram -> {
                return ram.observe("Watch: " + name, RAMEvent.TYPE.ANY, address, finalAuxFlag, 
                    event -> {
                        output.printf("Watch [%s] $%04X: $%02X -> $%02X%n", 
                            name, address, event.getOldValue() & 0xFF, event.getNewValue() & 0xFF);
                    });
            }, null);
        }
        
        public void remove() {
            if (listener != null) {
                Emulator.withMemory(ram -> ram.removeListener(listener));
            }
        }
        
        @Override
        public String toString() {
            String modePrefix = mode == MemoryMode.MAIN ? "M" : mode == MemoryMode.AUX ? "X" : "";
            return String.format("%s: %s$%04X", name, modePrefix, address);
        }
    }
    
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
                    // If we're paused at a breakpoint, show the current instruction
                    if (isActive() && cpu != null) {
                        int pc = cpu.getProgramCounter();
                        // Check if it's a breakpoint
                        if (getBreakpoints().contains(pc)) {
                            output.printf("Breakpoint hit at $%04X%n", pc);
                            displayCurrentInstruction();
                        }
                    }
                }
            }
        };
        
        // Just use our own debugger, don't try to access the UI logic one
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
        if ("exit".equalsIgnoreCase(command) || "quit".equalsIgnoreCase(command) || "q".equals(command)) {
            terminal.setMode("main");
            return true;
        }
        
        // Check for quit terminal command
        if ("qq".equals(command)) {
            terminal.stop();
            return true;
        }
        
        // Check for monitor command
        if ("monitor".equalsIgnoreCase(command) || "mon".equalsIgnoreCase(command)) {
            terminal.setMode("monitor");
            return true;
        }
        
        // Process commands
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        switch (cmd) {
            case "pause":
                pauseEmulation();
                return true;
            case "resume":
                resumeEmulation();
                return true;
            case "cpu":
                showCpuState();
                return true;
            case "break":
            case "b":
                return handleBreakpoint(args);
            case "list":
            case "l":
                if (args.isEmpty()) {
                    listBreakpoints();
                    return true;
                }
                return false;
            case "watch":
            case "w":
                return handleWatch(args);
            case "watchlist":
            case "wl":
                listWatches();
                return true;
            case "step":
            case "s":
                if (args.isEmpty()) {
                    stepInstruction();
                } else {
                    try {
                        int count = Integer.parseInt(args.trim());
                        if (count <= 0) count = 1;
                        stepInstruction(count);
                    } catch (NumberFormatException e) {
                        output.println("Invalid step count: " + args);
                    }
                }
                return true;
            case "runto":
            case "r":
                return handleRunTo(args);
            case "cheat":
            case "c":
                return handleCheat(args);
            case "cheatlist":
            case "cl":
                listCheats();
                return true;
            case "help":
            case "?":
                printHelp();
                return true;
            default:
                // Try to parse as a monitor-style examine command
                if (isExamineCommand(command)) {
                    examineMemory(command);
                    return true;
                }
                return false;
        }
    }
    
    private boolean isExamineCommand(String command) {
        Matcher matcher = ADDRESS_PATTERN.matcher(command);
        return matcher.matches();
    }
    
    private void examineMemory(String command) {
        try {
            Matcher matcher = ADDRESS_PATTERN.matcher(command);
            if (matcher.matches()) {
                String modePrefix = matcher.group(1);
                String addrStr = matcher.group(2);
                
                MemoryMode mode = MemoryMode.ACTIVE;
                if (modePrefix != null) {
                    if (modePrefix.equalsIgnoreCase("M")) {
                        mode = MemoryMode.MAIN;
                    } else if (modePrefix.equalsIgnoreCase("X")) {
                        mode = MemoryMode.AUX;
                    }
                }
                
                int address = Integer.parseInt(addrStr, 16);
                byte value = readMemory(address, mode);
                
                String modeIndicator = mode == MemoryMode.MAIN ? "M" : mode == MemoryMode.AUX ? "X" : "";
                output.printf("%s%04X: %02X%n", modeIndicator, address, value & 0xFF);
            }
        } catch (NumberFormatException e) {
            output.println("Invalid address format");
        }
    }
    
    private byte readMemory(int address, MemoryMode mode) {
        return Emulator.withMemory(ram -> {
            Boolean auxFlag = null;
            if (mode == MemoryMode.MAIN) {
                auxFlag = false;
            } else if (mode == MemoryMode.AUX) {
                auxFlag = true;
            }
            return (byte) ram.read(address, RAMEvent.TYPE.READ_DATA, true, auxFlag);
        }, (byte) 0);
    }
    
    private void pauseEmulation() {
        isPaused = true;
        Emulator.withComputer(c -> c.getMotherboard().suspend());
        debugger.setActive(true);
        output.println("Emulation paused");
        displayCurrentInstruction();
    }
    
    /**
     * Displays the current CPU state in the format:
     * addr: disassembled instruction [padded] A:XX X:XX Y:XX S:XX [flags]
     */
    private void displayCurrentInstruction() {
        displayCurrentInstruction(0, 0);
    }
    
    /**
     * Displays the current CPU state with optional step counter
     * 
     * @param stepNum The current step number (1-based) or 0 if not stepping
     * @param totalSteps The total number of steps or 0 if not stepping
     */
    private void displayCurrentInstruction(int stepNum, int totalSteps) {
        Emulator.withComputer(computer -> {
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            if (cpu != null) {
                int pc = cpu.getProgramCounter();
                String disasm = cpu.disassemble(pc);
                
                // Calculate padding (min 2 spaces, but with less total width)
                int padding = Math.max(2, 20 - disasm.length());
                StringBuilder paddingStr = new StringBuilder();
                for (int i = 0; i < padding; i++) {
                    paddingStr.append(" ");
                }
                
                // Build the output string
                String stepInfo = (stepNum > 0 && totalSteps > 0) ? String.format(" (%d/%d)", stepNum, totalSteps) : "";
                
                // Ensure we always show the full 4-digit address
                output.printf("%04X: %s%sA:%02X X:%02X Y:%02X S:%02X [%s]%s%n", 
                    pc, disasm, paddingStr, 
                    cpu.A & 0xFF, cpu.X & 0xFF, cpu.Y & 0xFF, cpu.STACK & 0xFF, 
                    cpu.getFlags(), stepInfo);
            }
        });
    }
    
    private void resumeEmulation() {
        isPaused = false;
        Emulator.withComputer(c -> c.getMotherboard().resume());
        debugger.setActive(false);
        output.println("Emulation resumed");
    }
    
    private void showCpuState() {
        Emulator.withComputer(computer -> {
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            if (cpu != null) {
                output.println("CPU State:");
                output.printf("PC=$%04X  A=$%02X  X=$%02X  Y=$%02X  SP=$%02X%n", 
                    cpu.getProgramCounter(), cpu.A, cpu.X, cpu.Y, cpu.STACK);
                output.printf("Flags: %s%n", cpu.getFlags());
                
                // Also display current instruction
                displayCurrentInstruction();
            }
        });
    }
    
    private boolean handleBreakpoint(String args) {
        if (args.isEmpty()) {
            output.println("Usage: break <address> or break remove <address> or break clear");
            return true;
        }
        
        String[] parts = args.split("\\s+");
        
        if (parts[0].equalsIgnoreCase("remove") || parts[0].equalsIgnoreCase("r")) {
            if (parts.length < 2) {
                output.println("Usage: break remove <address>");
                return true;
            }
            
            try {
                int address = parseAddress(parts[1]);
                removeBreakpoint(address);
                return true;
            } catch (NumberFormatException e) {
                output.println("Invalid address: " + parts[1]);
                return true;
            }
        } else if (parts[0].equalsIgnoreCase("clear") || parts[0].equalsIgnoreCase("c")) {
            clearBreakpoints();
            return true;
        } else {
            try {
                int address = parseAddress(parts[0]);
                addBreakpoint(address);
                return true;
            } catch (NumberFormatException e) {
                output.println("Invalid address: " + parts[0]);
                return true;
            }
        }
    }
    
    private void addBreakpoint(int address) {
        if (!debugger.getBreakpoints().contains(address)) {
            debugger.getBreakpoints().add(address);
            output.printf("Breakpoint added at $%04X%n", address);
        } else {
            output.printf("Breakpoint already exists at $%04X%n", address);
        }
    }
    
    private void removeBreakpoint(int address) {
        if (debugger.getBreakpoints().contains(address)) {
            debugger.getBreakpoints().remove(Integer.valueOf(address));
            output.printf("Breakpoint removed from $%04X%n", address);
        } else {
            output.printf("No breakpoint found at $%04X%n", address);
        }
    }
    
    private void clearBreakpoints() {
        debugger.getBreakpoints().clear();
        output.println("All breakpoints cleared");
    }
    
    private void listBreakpoints() {
        List<Integer> breakpoints = debugger.getBreakpoints();
        if (breakpoints.isEmpty()) {
            output.println("No breakpoints set");
            return;
        }
        
        output.println("Breakpoints:");
        for (int bp : breakpoints) {
            output.printf("  $%04X%n", bp);
        }
    }
    
    private boolean handleWatch(String args) {
        if (args.isEmpty()) {
            output.println("Usage: watch <address> [name] or watch remove <address|name> or watch clear");
            return true;
        }
        
        String[] parts = args.split("\\s+", 3);
        
        if (parts[0].equalsIgnoreCase("remove") || parts[0].equalsIgnoreCase("r")) {
            if (parts.length < 2) {
                output.println("Usage: watch remove <address|name>");
                return true;
            }
            
            // Check if it's an address or name
            try {
                int address = parseAddress(parts[1]);
                removeWatchByAddress(address);
            } catch (NumberFormatException e) {
                // Try as a name
                removeWatchByName(parts[1]);
            }
            return true;
        } else if (parts[0].equalsIgnoreCase("clear") || parts[0].equalsIgnoreCase("c")) {
            clearWatches();
            return true;
        } else {
            try {
                // Parse address and optional mode prefix
                Matcher matcher = ADDRESS_PATTERN.matcher(parts[0]);
                if (!matcher.matches()) {
                    output.println("Invalid address format");
                    return true;
                }
                
                String modePrefix = matcher.group(1);
                String addrStr = matcher.group(2);
                
                MemoryMode mode = MemoryMode.ACTIVE;
                if (modePrefix != null) {
                    if (modePrefix.equalsIgnoreCase("M")) {
                        mode = MemoryMode.MAIN;
                    } else if (modePrefix.equalsIgnoreCase("X")) {
                        mode = MemoryMode.AUX;
                    }
                }
                
                int address = Integer.parseInt(addrStr, 16);
                
                // Use address as name if not provided
                String name = (parts.length > 1) ? parts[1] : String.format("$%04X", address);
                
                addWatch(name, address, mode);
                return true;
            } catch (NumberFormatException e) {
                output.println("Invalid address: " + parts[0]);
                return true;
            }
        }
    }
    
    private void addWatch(String name, int address, MemoryMode mode) {
        watches.add(new Watch(name, address, mode));
        output.printf("Watch added for %s at $%04X%n", name, address);
    }
    
    private void removeWatchByAddress(int address) {
        boolean removed = false;
        for (int i = watches.size() - 1; i >= 0; i--) {
            Watch watch = watches.get(i);
            if (watch.address == address) {
                watch.remove();
                watches.remove(i);
                removed = true;
            }
        }
        
        if (removed) {
            output.printf("Watch(es) removed for address $%04X%n", address);
        } else {
            output.printf("No watch found for address $%04X%n", address);
        }
    }
    
    private void removeWatchByName(String name) {
        boolean removed = false;
        for (int i = watches.size() - 1; i >= 0; i--) {
            Watch watch = watches.get(i);
            if (watch.name.equals(name)) {
                watch.remove();
                watches.remove(i);
                removed = true;
                break;
            }
        }
        
        if (removed) {
            output.printf("Watch removed: %s%n", name);
        } else {
            output.printf("No watch found with name: %s%n", name);
        }
    }
    
    private void clearWatches() {
        for (Watch watch : watches) {
            watch.remove();
        }
        watches.clear();
        output.println("All watches cleared");
    }
    
    private void listWatches() {
        if (watches.isEmpty()) {
            output.println("No watches set");
            return;
        }
        
        output.println("Watches:");
        for (Watch watch : watches) {
            output.println("  " + watch);
        }
    }
    
    private boolean handleCheat(String args) {
        if (args.isEmpty()) {
            output.println("Usage: cheat <address> <value> or cheat remove <address> or cheat clear");
            return true;
        }
        
        String[] parts = args.split("\\s+");
        
        if (parts[0].equalsIgnoreCase("remove") || parts[0].equalsIgnoreCase("r")) {
            if (parts.length < 2) {
                output.println("Usage: cheat remove <address>");
                return true;
            }
            
            try {
                int address = parseAddress(parts[1]);
                removeCheat(address);
                return true;
            } catch (NumberFormatException e) {
                output.println("Invalid address: " + parts[1]);
                return true;
            }
        } else if (parts[0].equalsIgnoreCase("clear") || parts[0].equalsIgnoreCase("c")) {
            clearCheats();
            return true;
        } else {
            if (parts.length < 2) {
                output.println("Usage: cheat <address> <value>");
                return true;
            }
            
            try {
                // Parse address with optional mode prefix
                Matcher matcher = ADDRESS_PATTERN.matcher(parts[0]);
                if (!matcher.matches()) {
                    output.println("Invalid address format");
                    return true;
                }
                
                String modePrefix = matcher.group(1);
                String addrStr = matcher.group(2);
                
                // Currently ignoring mode for cheats as they work at a lower level
                // This would need to be implemented in the cheat system
                
                int address = Integer.parseInt(addrStr, 16);
                int value = Integer.parseInt(parts[1], 16) & 0xFF;
                
                addCheat(address, value);
                return true;
            } catch (NumberFormatException e) {
                output.println("Invalid address or value");
                return true;
            }
        }
    }
    
    private void addCheat(int address, int value) {
        cheats.put(address, value);
        
        // Implement the cheat using RAMListener
        Emulator.withMemory(ram -> {
            ram.observe("Cheat:" + address, RAMEvent.TYPE.READ, address,
                event -> event.setNewValue(value));
        });
        
        output.printf("Cheat added: $%04X = $%02X%n", address, value);
    }
    
    private void removeCheat(int address) {
        if (cheats.containsKey(address)) {
            cheats.remove(address);
            
            // Remove the cheat listener by recreating it and then removing it
            final int cheatValue = 0; // Value doesn't matter for removal
            final String cheatName = "Cheat:" + address;
            
            Emulator.withMemory(ram -> {
                // Create a new listener with the same name to find and remove the old one
                RAMListener listener = ram.observe(cheatName, RAMEvent.TYPE.READ, address, 
                    event -> {});
                ram.removeListener(listener);
            });
            
            output.printf("Cheat removed from $%04X%n", address);
        } else {
            output.printf("No cheat found at $%04X%n", address);
        }
    }
    
    private void clearCheats() {
        if (cheats.isEmpty()) {
            output.println("No cheats to clear");
            return;
        }
        
        // Remove each cheat individually
        List<Integer> addresses = new ArrayList<>(cheats.keySet());
        cheats.clear();
        
        for (int address : addresses) {
            Emulator.withMemory(ram -> {
                // Create a new listener with the same name to find and remove the old one
                String cheatName = "Cheat:" + address;
                RAMListener listener = ram.observe(cheatName, RAMEvent.TYPE.READ, address, 
                    event -> {});
                ram.removeListener(listener);
            });
        }
        
        output.println("All cheats cleared");
    }
    
    private void listCheats() {
        if (cheats.isEmpty()) {
            output.println("No cheats active");
            return;
        }
        
        output.println("Active cheats:");
        for (Map.Entry<Integer, Integer> entry : cheats.entrySet()) {
            output.printf("  $%04X = $%02X%n", entry.getKey(), entry.getValue());
        }
    }
    
    private void stepInstruction() {
        stepInstruction(1);
    }
    
    private void stepInstruction(int count) {
        if (!isPaused) {
            pauseEmulation();
            return;
        }
        
        isStepping.set(true);
        
        Emulator.withComputer(computer -> {
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            
            for (int i = 0; i < count; i++) {
                // Execute a single instruction
                debugger.step = true;
                computer.getMotherboard().resume();
                
                // Wait until the step is actually performed
                try {
                    // Give the CPU a chance to execute the step
                    Thread.sleep(10);
                    
                    // Then pause again
                    computer.getMotherboard().suspend();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // Show current state with step count info
                displayCurrentInstruction(i + 1, count);
            }
        });
        
        isStepping.set(false);
    }
    
    private boolean handleRunTo(String args) {
        if (args.isEmpty()) {
            output.println("Usage: runto <address>");
            return true;
        }
        
        try {
            int address = parseAddress(args);
            runToAddress(address);
            return true;
        } catch (NumberFormatException e) {
            output.println("Invalid address: " + args);
            return true;
        }
    }
    
    private void runToAddress(int address) {
        // Add temporary breakpoint
        boolean breakpointAlreadyExists = debugger.getBreakpoints().contains(address);
        if (!breakpointAlreadyExists) {
            addBreakpoint(address);
        }
        
        // Resume emulation
        if (isPaused) {
            // Start a monitoring thread that will check if we've hit the breakpoint
            Thread monitor = new Thread(() -> {
                boolean running = true;
                while (running) {
                    try {
                        Thread.sleep(100); // Check every 100ms
                        
                        // Check if we've reached the breakpoint
                        boolean hitBreakpoint = Emulator.withComputer(computer -> {
                            MOS65C02 cpu = (MOS65C02) computer.getCpu();
                            return cpu.getProgramCounter() == address;
                        }, false);
                        
                        if (hitBreakpoint) {
                            // We've hit the breakpoint, pause and show state
                            Emulator.withComputer(computer -> {
                                computer.getMotherboard().suspend();
                                isPaused = true;
                                debugger.setActive(true);
                                
                                output.printf("Breakpoint reached at $%04X%n", address);
                                displayCurrentInstruction();
                            });
                            
                            running = false;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                // Remove the temporary breakpoint if we added it
                if (!breakpointAlreadyExists) {
                    removeBreakpoint(address);
                }
            });
            
            monitor.setDaemon(true);
            monitor.start();
            
            // Resume emulation
            resumeEmulation();
            output.printf("Running to $%04X...%n", address);
        } else {
            output.printf("Breakpoint set at $%04X%n", address);
        }
    }
    
    private int parseAddress(String addrStr) {
        // First check if it has a mode prefix
        Matcher matcher = ADDRESS_PATTERN.matcher(addrStr);
        if (matcher.matches()) {
            String modePrefix = matcher.group(1); // Not used for address parsing
            addrStr = matcher.group(2);
        }
        
        return Integer.parseInt(addrStr, 16);
    }
    
    @Override
    public void printHelp() {
        output.println("Debugger Mode Commands:");
        output.println("  pause        - Pause emulation");
        output.println("  resume       - Resume emulation");
        output.println("  cpu          - Display CPU state");
        output.println("  monitor/mon  - Switch to monitor mode");
        output.println();
        output.println("  break/b <addr>      - Add breakpoint at address");
        output.println("  break remove <addr> - Remove breakpoint");
        output.println("  break clear        - Remove all breakpoints");
        output.println("  list/l             - List all breakpoints");
        output.println();
        output.println("  watch/w <addr> [name] - Add memory watch");
        output.println("  watch remove <addr|name> - Remove watch");
        output.println("  watch clear        - Remove all watches");
        output.println("  watchlist/wl       - List all watches");
        output.println();
        output.println("  cheat/c <addr> <value> - Add memory cheat");
        output.println("  cheat remove <addr>    - Remove cheat");
        output.println("  cheat clear           - Remove all cheats");
        output.println("  cheatlist/cl          - List all cheats");
        output.println();
        output.println("  step/s [count]   - Step one or more CPU instructions");
        output.println("  runto/r <addr>   - Run until CPU reaches address");
        output.println();
        output.println("  <addr>       - Examine memory at address");
        output.println("                 Use M prefix for main memory, X for aux");
        output.println("                 Example: M2000 or X300");
        output.println();
        output.println("  exit/quit/q  - Return to main menu");
        output.println("  qq           - Exit terminal");
        output.println("  ?/help       - Show this help");
    }
} 