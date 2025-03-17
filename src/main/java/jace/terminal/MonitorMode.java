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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.core.Debugger;
import jace.core.RAMEvent;
import jace.core.RAMListener;

/**
 * Monitor mode for the Terminal - emulates the Apple II monitor with debugger capabilities
 */
public class MonitorMode implements TerminalMode {
    private final JaceTerminal terminal;
    final PrintStream output;
    private int lastExaminedAddress;
    private int lastDisassemblyAddress = 0;
    
    // Debugger state and components
    private boolean isPaused = false;
    private AtomicBoolean isStepping = new AtomicBoolean(false);
    private Debugger debugger;
    
    // Static collections for persistence between terminal sessions
    private static final List<Integer> persistentBreakpoints = new ArrayList<>();
    private static final Map<String, PersistentWatch> persistentWatches = new HashMap<>();
    private static final Map<Integer, PersistentCheat> persistentCheats = new HashMap<>();
    private static final Logger LOG = Logger.getLogger(MonitorMode.class.getName());
    
    
    // Keep track of active monitors to manage resources
    private static final List<MonitorMode> activeMonitors = new ArrayList<>();
    
    private static final List<Watch> watches = new ArrayList<>();
    private static final Map<Integer, Cheat> cheats = new HashMap<>();
    
    private MemoryMode memoryMode = MemoryMode.ACTIVE;
    
    // Regex patterns for monitor commands
    private static final Pattern EXAMINE_PATTERN = Pattern.compile("^([Mm]|[Xx])?([0-9A-Fa-f]{1,4})$");
    private static final Pattern POKE_PATTERN = Pattern.compile("^([Mm]|[Xx])?([0-9A-Fa-f]{1,4}):([0-9A-Fa-f\\s]+)$");
    private static final Pattern GO_PATTERN = Pattern.compile("^([Mm]|[Xx])?([0-9A-Fa-f]{1,4})[Gg]$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^([0-9A-Fa-f]{1,4})[Ll]$");
    private static final Pattern SINGLE_LIST_PATTERN = Pattern.compile("^[Ll]$");
    private static final Pattern RANGE_PATTERN = Pattern.compile("^([Mm]|[Xx])?([0-9A-Fa-f]{1,4})\\.([0-9A-Fa-f]{1,4})$");
    
    private final Map<String, Consumer<String[]>> commands = new HashMap<>();
    private final Map<String, String> commandAliases = new HashMap<>();
    private final Map<String, String> commandHelp = new HashMap<>();
    
    // Add a new map for pattern-based commands
    private final List<Map.Entry<Pattern, Consumer<Matcher>>> patternCommands = new ArrayList<>();
    
    // Default number of instructions to disassemble
    private static final int DEFAULT_DISASM_COUNT = 20;
    
    public MonitorMode(JaceTerminal terminal) {
        this.terminal = terminal;
        this.output = terminal.getOutput();
        
        // Register this instance
        synchronized(activeMonitors) {
            activeMonitors.add(this);
        }
        
        initCommands();
        initDebugger();
        
        // Restore persistent state
        restorePersistedState();
    }

    /**
     * Get the CPU from the emulator
     * This method is made public to allow overriding in tests
     * 
     * @return The CPU instance or null if not available
     */
    public MOS65C02 getCpu() {
        return (MOS65C02) Emulator.withComputer(computer -> computer.getCpu(), null);
    }
    
    private void initDebugger() {
        debugger = new Debugger() {
            @Override
            public void updateStatus() {
                if (!isActive()) {
                    return;
                }
                                
                MOS65C02 cpu = getCpu();
                if (cpu != null) {
                    int pc = cpu.getProgramCounter();
                    
                    // Check if it's a breakpoint - always notify the user from debugger
                    // for compatibility with all memory configurations
                    if (getBreakpoints().contains(pc)) {
                        // Pause the execution immediately
                        Emulator.withComputer(c -> {
                            c.getMotherboard().suspend();
                        });
                        
                        // Update our internal state
                        isPaused = true;
                        
                        // Notify the user
                        output.printf("Breakpoint hit at $%04X%n", pc);
                        displayCurrentInstruction();
                        output.flush();  // Flush to ensure UI updates immediately
                    }
                }
            }
        };
        
        // Ensure the debugger is properly initialized
        debugger.setActive(false);
    }
    
    /**
     * Checks if a breakpoint at the given address is handled by a RAM event listener
     * This method is kept for reference but not used anymore
     * 
     * @param address The address to check
     * @return true if there is a RAM event breakpoint at this address
     */
    private boolean hasRAMEventBreakpoint(int address) {
        // This method is not used anymore but kept for reference
        return false;
    }
    
    /**
     * Displays the current CPU state in the format:
     * addr: disassembled instruction [padded] A:XX X:XX Y:XX S:XX [flags]
     */
    void displayCurrentInstruction() {
        displayCurrentInstruction(0, 0);
    }
    
    /**
     * Displays the current CPU state with optional step counter
     * 
     * @param stepNum The current step number (1-based) or 0 if not stepping
     * @param totalSteps The total number of steps or 0 if not stepping
     */
    private void displayCurrentInstruction(int stepNum, int totalSteps) {
        MOS65C02 cpu = getCpu();
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
    }
    
    private void initCommands() {
        // Define commands with their implementations
        commands.put("fill", this::fillMemory);
        commands.put("move", this::moveMemory);
        commands.put("compare", this::compareMemory);
        commands.put("find", this::searchMemory);
        commands.put("back", args -> terminal.setMode("main"));
        commands.put("debug", args -> {
            output.println("Debugger functionality is now integrated into monitor mode.");
            output.println("Type 'help' to see available commands.");
        });
        commands.put("quit", args -> terminal.setMode("main"));
        
        // Debugger commands
        commands.put("pause", args -> pauseEmulation());
        commands.put("resume", args -> resumeEmulation());
        commands.put("cpu", args -> showCpuState());
        commands.put("registers", this::handleRegisters);
        commands.put("break", this::handleBreakpoint);
        commands.put("breaklist", args -> listBreakpoints());
        commands.put("step", this::handleStep);
        commands.put("watch", this::handleWatch);
        commands.put("watchlist", args -> listWatches());
        commands.put("runto", this::handleRunTo);
        commands.put("cheat", this::handleCheat);
        commands.put("cheatlist", args -> listCheats());
        
        // Add pattern-based command handlers
        addPatternCommand(SINGLE_LIST_PATTERN, this::handleSingleListCommand);
        addPatternCommand(GO_PATTERN, this::handleGoCommand);
        addPatternCommand(LIST_PATTERN, this::handleListCommand);
        addPatternCommand(RANGE_PATTERN, this::handleRangeCommand);
        addPatternCommand(POKE_PATTERN, this::handlePokeCommand);
        addPatternCommand(EXAMINE_PATTERN, this::handleExamineCommand);
        
        // Add single-letter aliases
        addAlias("f", "fill");
        addAlias("m", "move");
        addAlias("c", "compare");
        addAlias("dbg", "debug");
        
        // Debugger aliases - shorter forms
        addAlias("p", "pause");
        addAlias("r", "resume");
        addAlias("reg", "registers");
        addAlias("b", "break");
        addAlias("bl", "breaklist");
        addAlias("s", "step");
        addAlias("w", "watch");
        addAlias("wl", "watchlist");
        addAlias("rt", "runto");
        addAlias("c", "cheat");
        addAlias("cl", "cheatlist");
        
        // Add the setregister alias for backward compatibility
        addAlias("sr", "registers");
        addAlias("setregister", "registers");
        
        commandHelp.put("fill", "Fills a range of memory with a specific value.\n" +
                "Usage: fill start end value (or f start end value)\n" +
                "  start - Starting address in hex\n" +
                "  end   - Ending address in hex\n" +
                "  value - Byte value in hex\n" +
                "Examples:\n" +
                "  fill 2000 27FF 00   - Fill memory from $2000 to $27FF with 00\n" +
                "  f 800 8FF EA        - Fill memory from $800 to $8FF with EA (NOP)");
        
        commandHelp.put("move", "Copies a block of memory from one location to another.\n" +
                "Usage: move src dest count (or m src dest count)\n" +
                "  src   - Source address in hex\n" +
                "  dest  - Destination address in hex\n" +
                "  count - Number of bytes to copy in hex\n" +
                "Examples:\n" +
                "  move 2000 4000 800      - Copy 2048 bytes from $2000 to $4000\n" +
                "  m 300 800 100           - Copy 256 bytes from $300 to $800");
        
        commandHelp.put("compare", "Compares two blocks of memory.\n" +
                "Usage: compare src dest count (or c src dest count)\n" +
                "  src   - First address in hex\n" +
                "  dest  - Second address in hex\n" +
                "  count - Number of bytes to compare in hex\n" +
                "Examples:\n" +
                "  compare 2000 4000 100   - Compare 256 bytes at $2000 with $4000\n" +
                "  c 300 800 40            - Compare 64 bytes at $300 with $800");
        
        commandHelp.put("find", "Searches for a sequence of bytes in memory.\n" +
                "Usage: find start end value [value2...] (or f start end value [value2...])\n" +
                "  start - Starting address in hex\n" +
                "  end   - Ending address in hex\n" +
                "  value - Byte value(s) in hex to search for\n" +
                "Examples:\n" +
                "  find 800 8FF A9 FF    - Search for A9 FF from $800 to $8FF\n" +
                "  f 0 FFFF 20 00 BF       - Search for 20 00 BF in entire memory");
        
        commandHelp.put("back", "Returns to main mode.\nUsage: back (or b)");
        
        commandHelp.put("debug", "Informs that debugger functionality is now integrated into monitor mode.\nUsage: debug\n" +
                         "Note: All debugger commands are directly available in monitor mode.");
        
        commandHelp.put("pause", "Pauses the emulation.\nUsage: pause (or p)");
        commandHelp.put("resume", "Resumes the emulation.\nUsage: resume (or r)");
        commandHelp.put("cpu", "Displays the current CPU state.\nUsage: cpu");
        commandHelp.put("registers", "Displays or sets CPU register values.\n" +
                "Usage: registers (or reg)             - Display all registers\n" +
                "       registers <register> <value>   - Set register value\n" +
                "  Registers: A, X, Y, PC, S, N, V, B, D, I, Z, C\n" +
                "  Values can be decimal, hex with $ prefix, or hex with 0x prefix\n" +
                "Examples:\n" +
                "  registers         - Show all register values\n" +
                "  registers A $FF   - Set accumulator to $FF\n" +
                "  reg PC $C000      - Set program counter to $C000\n" +
                "  reg X 42          - Set X register to decimal 42");
        commandHelp.put("break", "Manages breakpoints.\n" +
                "Usage: break addr              - Add a breakpoint at address\n" +
                "       break -addr             - Remove a breakpoint at address\n" +
                "       break clear             - Remove all breakpoints");
        commandHelp.put("step", "Steps through CPU instructions.\n" +
                "Usage: step [count]            - Step through [count] instructions (default: 1)");
        commandHelp.put("watch", "Monitors memory addresses for changes.\n" +
                "Usage: watch addr [name]       - Add a watch at address\n" +
                "       watch -addr             - Remove a watch at address\n" +
                "       watch -name             - Remove a watch by name\n" +
                "       watch clear             - Remove all watches");
        commandHelp.put("runto", "Runs until reaching a specific address.\n" +
                "Usage: runto addr              - Run until PC reaches address");
        commandHelp.put("cheat", "Manages memory value overrides.\n" +
                "Usage: cheat addr value        - Force memory at address to always return value\n" +
                "       cheat -addr             - Remove a cheat at address\n" +
                "       cheat clear             - Remove all cheats");
    }
    
    private void addAlias(String alias, String command) {
        commandAliases.put(alias, command);
    }
    
    private void addPatternCommand(Pattern pattern, Consumer<Matcher> handler) {
        patternCommands.add(new AbstractMap.SimpleEntry<>(pattern, handler));
    }
    
    @Override
    public String getName() {
        return "Monitor";
    }
    
    @Override
    public String getPrompt() {
        return "*";
    }
    
    @Override
    public boolean processCommand(String command) {
        // Handle q to return to main menu and qq to exit
        command = command.trim();
        
        if ("q".equals(command)) {
            terminal.setMode("main");
            return true;
        }
        
        if ("qq".equals(command)) {
            terminal.stop();
            return true;
        }
        
        // First check for registered commands
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
        
        // Check for command alias
        if (commandAliases.containsKey(cmd)) {
            cmd = commandAliases.get(cmd);
        }
        
        // Look up and execute the command
        Consumer<String[]> handler = commands.get(cmd);
        if (handler != null) {
            handler.accept(args);
            return true;
        }
        
        // If no registered command matched, try pattern-based commands
        for (Map.Entry<Pattern, Consumer<Matcher>> entry : patternCommands) {
            Pattern pattern = entry.getKey();
            Consumer<Matcher> handler2 = entry.getValue();
            
            Matcher matcher = pattern.matcher(command);
            if (matcher.matches()) {
                handler2.accept(matcher);
                return true;
            }
        }
        
        // No matching command found
        LOG.log(Level.INFO, "Unknown command: {0}", command);
        return false;
    }
    
    // Debugger methods
    private void pauseEmulation() {
        isPaused = true;
        Emulator.withComputer(c -> c.getMotherboard().suspend());
        debugger.setActive(true);
        output.println("Emulation paused");
        displayCurrentInstruction();
        output.flush();  // Flush to ensure UI updates immediately
    }
    
    private void resumeEmulation() {
        isPaused = false;
        Emulator.withComputer(c -> c.getMotherboard().resume());
        // Only deactivate the debugger if there are no breakpoints set
        if (debugger.getBreakpoints().isEmpty()) {
            debugger.setActive(false);
        } else {
            // Keep debugger active to detect breakpoints
            debugger.setActive(true);
        }
        output.println("Emulation resumed");
    }
    
    private void showCpuState() {
        MOS65C02 cpu = getCpu();
        if (cpu != null) {
            output.println("CPU State:");
            output.printf("PC=$%04X  A=$%02X  X=$%02X  Y=$%02X  SP=$%02X%n", 
                cpu.getProgramCounter(), cpu.A, cpu.X, cpu.Y, cpu.STACK);
            output.printf("Flags: %s%n", cpu.getFlags());
            
            // Also display current instruction
            displayCurrentInstruction();
        }
    }
    
    private void handleBreakpoint(String[] args) {
        if (args.length == 0) {
            output.println("Usage: break <address> or break -<address> or break clear");
            return;
        }
        
        if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("c")) {
            clearBreakpoints();
        } else {
            try {
                String addrStr = args[0];
                // Check for removal syntax with '-' prefix
                if (addrStr.startsWith("-")) {
                    int address = parseAddress(addrStr.substring(1));
                    removeBreakpoint(address);
                } else {
                    int address = parseAddress(addrStr);
                    addBreakpoint(address);
                }
            } catch (NumberFormatException e) {
                output.println("Invalid address: " + args[0]);
            }
        }
    }
    
    private void addBreakpoint(int address) {
        if (!debugger.getBreakpoints().contains(address)) {
            debugger.getBreakpoints().add(address);
            
            // Add to persistent collection
            if (!persistentBreakpoints.contains(address)) {
                persistentBreakpoints.add(address);
            }
            
            // Register a RAM event listener for execution at this address
            RAMEvent.RAMEventHandler breakpointHandler = new RAMEvent.RAMEventHandler() {
                @Override
                public void handleEvent(RAMEvent e) {
                    // Don't process if we're already handling this breakpoint to avoid recursion
                    if (isStepping.get()) {
                        return;
                    }
                    
                    // Pause the emulation - this is critical!
                    Emulator.withComputer(c -> {
                        c.getMotherboard().suspend();
                    });
                    
                    // Update our internal state
                    isPaused = true;
                    
                    // Display CPU state and message
                    MOS65C02 cpu = getCpu();
                    if (cpu != null) {
                        int pc = cpu.getProgramCounter();
                        String disasm = cpu.disassemble(pc);
                        
                        // Show detailed breakpoint information
                        output.printf("Breakpoint hit at $%04X: %s A:%02X X:%02X Y:%02X S:%02X [%s]%n", 
                            pc, disasm, 
                            cpu.A & 0xFF, cpu.X & 0xFF, cpu.Y & 0xFF, cpu.STACK & 0xFF, 
                            cpu.getFlags());
                        
                        // Flush to ensure output is visible immediately
                        output.flush();
                    }
                }
            };
            
            // Create and register the RAM listener for execute events
            final String listenerName = "Breakpoint at $" + Integer.toHexString(address).toUpperCase();
            
            // Use observe method to create and register the listener
            Emulator.withComputer(computer -> {
                // Register for BOTH main and auxiliary memory to ensure breakpoints work in all memory configurations
                computer.getMemory().observe(listenerName, RAMEvent.TYPE.EXECUTE, address, false, breakpointHandler);
                computer.getMemory().observe(listenerName + " (aux)", RAMEvent.TYPE.EXECUTE, address, true, breakpointHandler);
            });
            
            // Ensure the debugger is active
            debugger.setActive(true);
            
            output.printf("Breakpoint set at $%04X%n", address);
        } else {
            output.printf("Breakpoint already exists at $%04X%n", address);
        }
    }
    
    private void removeBreakpoint(int address) {
        if (debugger.getBreakpoints().contains(address)) {
            debugger.getBreakpoints().remove(Integer.valueOf(address));
            
            // Remove from persistent collection
            persistentBreakpoints.remove(Integer.valueOf(address));
            
            // Remove RAM event listeners for this breakpoint (for both main and aux memory)
            final String listenerName = "Breakpoint at $" + Integer.toHexString(address).toUpperCase();
            final String auxListenerName = listenerName + " (aux)";
            
            // Create a temporary listener with a matching name to ensure we find and remove it
            Emulator.withComputer(computer -> {
                // Remove main memory listener
                RAMListener tempListener = computer.getMemory().observe(
                    listenerName, 
                    RAMEvent.TYPE.EXECUTE, 
                    address, 
                    false,
                    e -> { /* No-op */ }
                );
                computer.getMemory().removeListener(tempListener);
                
                // Remove auxiliary memory listener
                RAMListener tempAuxListener = computer.getMemory().observe(
                    auxListenerName, 
                    RAMEvent.TYPE.EXECUTE, 
                    address, 
                    true,
                    e -> { /* No-op */ }
                );
                computer.getMemory().removeListener(tempAuxListener);
            });
            
            // If no breakpoints remain, disable the debugger
            if (debugger.getBreakpoints().isEmpty()) {
                debugger.setActive(false);
            }
            
            output.printf("Breakpoint removed from $%04X%n", address);
        } else {
            output.println("No breakpoint exists at $" + Integer.toHexString(address).toUpperCase());
        }
    }
    
    private void clearBreakpoints() {
        int count = debugger.getBreakpoints().size();
        
        if (count > 0) {
            // Remove all breakpoints from the debugger
            debugger.getBreakpoints().clear();
            
            // Clear persistent breakpoints
            List<Integer> addressesToClear = new ArrayList<>(persistentBreakpoints);
            
            // Clear our internal persistent collection
            persistentBreakpoints.clear();
            
            // Remove RAM event listeners for all breakpoints
            for (Integer address : addressesToClear) {
                // Remove breakpoint one by one (for both main and aux memory)
                final String listenerName = "Breakpoint at $" + Integer.toHexString(address).toUpperCase();
                final String auxListenerName = listenerName + " (aux)";
                
                Emulator.withComputer(computer -> {
                    // Remove main memory listener
                    RAMListener tempListener = computer.getMemory().observe(
                        listenerName, 
                        RAMEvent.TYPE.EXECUTE, 
                        address, 
                        false,
                        e -> { /* No-op */ }
                    );
                    computer.getMemory().removeListener(tempListener);
                    
                    // Remove auxiliary memory listener
                    RAMListener tempAuxListener = computer.getMemory().observe(
                        auxListenerName, 
                        RAMEvent.TYPE.EXECUTE, 
                        address, 
                        true,
                        e -> { /* No-op */ }
                    );
                    computer.getMemory().removeListener(tempAuxListener);
                });
            }
            
            // Disable the debugger
            debugger.setActive(false);
            
            output.println("All breakpoints cleared (" + count + " removed)");
        } else {
            output.println("No breakpoints to clear");
        }
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
    
    private void handleStep(String[] args) {
        int count = 1;
        if (args.length > 0) {
            try {
                count = Integer.parseInt(args[0]);
                if (count <= 0) count = 1;
            } catch (NumberFormatException e) {
                output.println("Invalid step count: " + args[0]);
                return;
            }
        }
        stepInstruction(count);
    }
    
    private void stepInstruction(int count) {
        if (!isPaused) {
            pauseEmulation();
            return;
        }
        
        isStepping.set(true);
        
        try {
            // Make sure the emulator is paused while we step
            Emulator.withComputer(computer -> {
                // Ensure motherboard is suspended while we perform manual stepping
                computer.getMotherboard().suspend();
                
                MOS65C02 cpu = getCpu();
                if (cpu == null) {
                    output.println("Error: Could not access CPU");
                    return;
                }
                
                // Keep track of the instructions stepped for output
                for (int i = 0; i < count; i++) {
                    // Get the current state before stepping
                    int currentPC = cpu.getProgramCounter();
                    String currentDisasm = cpu.disassemble(currentPC);
                    
                    // Calculate padding (min 2 spaces, but with less total width)
                    int padding = Math.max(2, 20 - currentDisasm.length());
                    StringBuilder paddingStr = new StringBuilder();
                    for (int j = 0; j < padding; j++) {
                        paddingStr.append(" ");
                    }
                    
                    // Display current instruction
                    output.printf("%04X: %s", currentPC, currentDisasm);
                    
                    // Use a special method to force a single CPU instruction
                    executeOneSingleInstruction(computer, cpu);
                    
                    // Now display the CPU state after execution
                    output.printf("%sA:%02X X:%02X Y:%02X S:%02X [%s] (%d/%d)%n", 
                        paddingStr,
                        cpu.A & 0xFF, cpu.X & 0xFF, cpu.Y & 0xFF, cpu.STACK & 0xFF, 
                        cpu.getFlags(), i + 1, count);
                    
                    output.flush(); // Ensure output is displayed immediately
                    
                    // Add a small delay between steps for readability
                    if (i < count - 1) {
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                // Ensure motherboard remains suspended after stepping
                computer.getMotherboard().suspend();
            });
        } finally {
            isStepping.set(false);
        }
    }
    
    /**
     * Executes exactly one CPU instruction using CPU's debugging mechanisms
     */
    private void executeOneSingleInstruction(jace.apple2e.Apple2e computer, MOS65C02 cpu) {
        // Since we can't access executeOpcode() directly, we need a more controlled approach
        
        // Store the current program counter before stepping
        final int originalPC = cpu.getProgramCounter();
        
        // We'll detect completion by watching for the PC to change
        final AtomicBoolean instructionComplete = new AtomicBoolean(false);
        
        // Create a special one-time execution listener
        RAMListener execListener = computer.getMemory().observe(
            "SingleStepExec", 
            RAMEvent.TYPE.EXECUTE, 
            originalPC, 
            false, // main memory
            event -> {
                // After the instruction executes, prevent any further execution
                if (!instructionComplete.get()) {
                    instructionComplete.set(true);
                    computer.getMotherboard().suspend();
                }
            }
        );
        
        try {
            // Set the debugger to step mode
            debugger.step = true;
            
            // Resume the motherboard to start execution
            computer.getMotherboard().resume();
            
            // Wait for the instruction to complete (with timeout)
            final long startTime = System.currentTimeMillis();
            final long timeout = 50; // ms
            
            while (!instructionComplete.get() && 
                  (System.currentTimeMillis() - startTime < timeout)) {
                // Use onSpinWait instead of sleep for more efficient spinning
                Thread.onSpinWait();
            }
            
            // Force suspension regardless of completion state
            computer.getMotherboard().suspend();
            
            // If we timed out, log it
            if (!instructionComplete.get()) {
                System.out.println("Warning: CPU single-step timed out");
            }
        } finally {
            // Clean up our execution listener
            computer.getMemory().removeListener(execListener);
            
            // Reset the debugger step flag
            debugger.step = false;
        }
    }
    
    private void handleWatch(String[] args) {
        if (args.length == 0) {
            output.println("Usage: watch <address> [name] or watch -<address|name> or watch clear");
            return;
        }
        
        if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("c")) {
            clearWatches();
        } else {
            try {
                String addrStr = args[0];
                // Check for removal syntax with '-' prefix
                if (addrStr.startsWith("-")) {
                    try {
                        int address = parseAddress(addrStr.substring(1));
                        removeWatchByAddress(address);
                    } catch (NumberFormatException e) {
                        // Try as a name
                        removeWatchByName(addrStr.substring(1));
                    }
                } else {
                    // Parse address with mode using our new helper class
                    AddressWithMode addrWithMode = parseAddressWithMode(addrStr);
                    
                    // Use address as name if not provided
                    String name = (args.length > 1) ? args[1] : addrWithMode.toString();
                    
                    addWatch(name, addrWithMode.getAddress(), addrWithMode.getMode());
                }
            } catch (NumberFormatException e) {
                output.println("Invalid address: " + args[0]);
            }
        }
    }
    
    private void addWatch(String name, int address, MemoryMode mode) {
        watches.add(new Watch(this, name, address, mode));
        
        // Add to persistent collection
        persistentWatches.put(name, new PersistentWatch(name, address, mode));
        
        output.printf("Watch added for %s at $%04X%n", name, address);
    }
    
    private void removeWatchByAddress(int address) {
        boolean removed = false;
        for (int i = watches.size() - 1; i >= 0; i--) {
            Watch watch = watches.get(i);
            if (watch.address == address) {
                watch.remove();
                
                // Remove from persistent collection as well
                persistentWatches.remove(watch.name);
                
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
                
                // Remove from persistent collection
                persistentWatches.remove(name);
                
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
        
        // Clear persistent collection
        persistentWatches.clear();
        
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
    
    private void handleCheat(String[] args) {
        if (args.length == 0) {
            output.println("Usage: cheat <address> <value> or cheat -<address> or cheat clear");
            return;
        }
        
        if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("c")) {
            clearCheats();
        } else {
            // Check for removal syntax with '-' prefix
            if (args[0].startsWith("-")) {
                try {
                    int address = parseAddress(args[0].substring(1));
                    removeCheat(address);
                } catch (NumberFormatException e) {
                    output.println("Invalid address: " + args[0].substring(1));
                }
            } else {
                if (args.length < 2) {
                    output.println("Usage: cheat <address> <value>");
                    return;
                }
                
                try {
                    // Parse address with mode using our new helper class
                    AddressWithMode addrWithMode = parseAddressWithMode(args[0]);
                    int value = Integer.parseInt(args[1], 16) & 0xFF;
                    
                    addCheat(addrWithMode.getAddress(), value, addrWithMode.getMode());
                } catch (NumberFormatException e) {
                    output.println("Invalid address or value");
                }
            }
        }
    }
    
    private void addCheat(int address, int value, MemoryMode mode) {
        // First remove any existing cheat at this address to avoid race conditions
        if (cheats.containsKey(address)) {
            removeCheat(address);
        }
        
        // Add the new cheat
        Cheat cheat = new Cheat(address, value, mode);
        cheats.put(address, cheat);
        
        // Add to persistent collection
        persistentCheats.put(address, new PersistentCheat(address, value, mode));
        
        // Implement the cheat using RAMListener
        Emulator.withMemory(ram -> {
            ram.observe("Cheat:" + address, RAMEvent.TYPE.READ, address, cheat.getAuxFlag(),
                event -> event.setNewValue(value));
        });
        
        output.println("Cheat added: " + cheat);
    }
    
    private void removeCheat(int address) {
        if (cheats.containsKey(address)) {
            Cheat cheat = cheats.get(address);
            cheats.remove(address);
            
            // Remove from persistent collection
            persistentCheats.remove(address);
            
            // Remove the cheat listener
            final String cheatName = "Cheat:" + address;
            
            Emulator.withMemory(ram -> {
                // Remove the specific listener based on the memory mode
                RAMListener listener = ram.observe(cheatName, RAMEvent.TYPE.READ, address, cheat.getAuxFlag(), 
                    event -> {});
                ram.removeListener(listener);
            });
            
            output.printf("Cheat removed from %s%n", cheat);
        } else {
            output.printf("No cheat found at $%04X%n", address);
        }
    }
    
    private void clearCheats() {
        if (cheats.isEmpty()) {
            output.println("No cheats to clear");
            return;
        }
        
        // Remove each cheat individually to ensure proper cleanup
        Map<Integer, Cheat> cheatsCopy = new HashMap<>(cheats);
        cheats.clear();
        
        // Clear persistent collection
        persistentCheats.clear();
        
        for (Map.Entry<Integer, Cheat> entry : cheatsCopy.entrySet()) {
            int address = entry.getKey();
            Cheat cheat = entry.getValue();
            
            Emulator.withMemory(ram -> {
                String cheatName = "Cheat:" + address;
                
                // Remove the specific listener based on the memory mode
                RAMListener listener = ram.observe(cheatName, RAMEvent.TYPE.READ, address, cheat.getAuxFlag(), 
                    event -> {});
                ram.removeListener(listener);
            });
        }
        
        output.println("All cheats cleared");
    }
    
    private void listCheats() {
        if (cheats.isEmpty()) {
            output.println("No active cheats");
            return;
        }

        output.println("Active cheats:");
        cheats.forEach((addr, cheat) -> {
            output.printf("  $%04X = $%02X (%s)%n", addr, cheat.value, cheat.mode);
        });
    }
    
    private void handleRunTo(String[] args) {
        if (args.length == 0) {
            output.println("Usage: runto <address>");
            return;
        }
        
        try {
            int address = parseAddress(args[0]);
            runToAddress(address);
        } catch (NumberFormatException e) {
            output.println("Invalid address: " + args[0]);
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
            // Make sure debugger is active to detect the breakpoint
            debugger.setActive(true);
            
            // Resume emulation
            isPaused = false;
            Emulator.withComputer(c -> c.getMotherboard().resume());
            output.printf("Running to $%04X...%n", address);
        } else {
            output.printf("Breakpoint set at $%04X%n", address);
        }
    }
    
    // End of debugger methods
    
    // Helper methods for address parsing
    private int parseAddress(String addrStr) {
        return AddressWithMode.parse(addrStr, memoryMode).getAddress();
    }
    
    private AddressWithMode parseAddressWithMode(String addrStr) {
        return AddressWithMode.parse(addrStr, memoryMode);
    }

    /**
     * Determines memory mode from prefix
     * 
     * @param modePrefix The mode prefix (M, X or null)
     * @param defaultMode The default mode to use if no prefix specified
     * @return The determined memory mode
     */
    static MemoryMode determineMemoryMode(String modePrefix, MemoryMode defaultMode) {
        if (modePrefix != null) {
            if (modePrefix.equalsIgnoreCase("M")) {
                return MemoryMode.MAIN;
            } else if (modePrefix.equalsIgnoreCase("X")) {
                return MemoryMode.AUX;
            }
        }
        return defaultMode;
    }
    
    @Override
    public void printHelp() {
        output.println("Monitor Mode Commands:");
        output.println("  help                        Show this help");
        output.println("  back | quit | q             Return to main mode");
        output.println("  ");
        output.println("Memory Examination:");
        output.println("  <addr>                      Examine memory at address");
        output.println("  <addr>.<addr>               Examine memory range");
        output.println("  ");
        output.println("Memory Modification:");
        output.println("  <addr>:<val> [<val>...]     Deposit values into memory");
        output.println("  fill | f <start> <end> <val> Fill memory range with value");
        output.println("  move | m <src> <dest> <len> Copy memory blocks");
        output.println("  ");
        output.println("Code & Execution:");
        output.println("  <addr>L                     Disassemble code at address");
        output.println("  L                           Continue disassembly from last position");
        output.println("  <addr>G                     Execute code at address");
        output.println("  ");
        output.println("Memory Analysis:");
        output.println("  compare | c <src> <dest> <len> Compare memory regions");
        output.println("  find <start> <end> <bytes>  Search memory for byte sequence");
        output.println("  ");
        output.println("Debugger Commands:");
        output.println("  pause | p                   Pause emulation");
        output.println("  resume | r                  Resume emulation");
        output.println("  cpu                         Display CPU registers and status");
        output.println("  registers/reg [register value]     Display or set CPU registers");
        output.println("  step | s [count]            Step through instruction(s)");
        output.println("  ");
        output.println("Breakpoints:");
        output.println("  break | b <addr>            Set breakpoint at address");
        output.println("  break -<addr>             Remove breakpoint at address");
        output.println("  break clear               Clear all breakpoints");
        output.println("  breaklist | bl              List all breakpoints");
        output.println("  runto | rt <addr>           Run until PC reaches address");
        output.println("  ");
        output.println("Memory Watching:");
        output.println("  watch | w <addr> [name]     Watch address for changes");
        output.println("  watch -<addr>               Remove watch at address");
        output.println("  watch -<name>               Remove watch by name");
        output.println("  watch clear                 Clear all watches");
        output.println("  watchlist | wl              List all active watches");
        output.println("  ");
        output.println("Memory Cheats:");
        output.println("  cheat | c <addr> <value>    Force memory address to always return value");
        output.println("  cheat -<addr>              Remove a cheat at address");
        output.println("  cheat clear                 Clear all cheats");
        output.println("  cheatlist | cl              List all active cheats");
        output.println("  ");
        output.println("Memory Bank Selection:");
        output.println("  [M|X]<addr>                 Access main (M) or auxiliary (X) memory");
        output.println("  Example: M2000 examines main bank at $2000");
        output.println("  Example: X300:FF deposits $FF to auxiliary bank at $300");
    }
    
    @Override
    public boolean printCommandHelp(String command) {
        switch (command.toLowerCase()) {
            case "fill":
            case "f":
                output.println("fill <start> <end> <value> - Fill memory with value");
                output.println("  <start> - Start address (in hex)");
                output.println("  <end>   - End address (in hex)");
                output.println("  <value> - Value to fill with (in hex)");
                output.println("  Example: fill 2000 3000 EA");
                return true;
            case "move":
            case "m":
                output.println("move <src> <dest> <len> - Move memory");
                output.println("  <src>  - Source address (in hex)");
                output.println("  <dest> - Destination address (in hex)");
                output.println("  <len>  - Length to move (in hex)");
                output.println("  Example: move 2000 3000 1000");
                return true;
            case "compare":
            case "c":
                output.println("compare <src> <dest> <len> - Compare memory regions");
                output.println("  <src>  - First address (in hex)");
                output.println("  <dest> - Second address (in hex)");
                output.println("  <len>  - Length to compare (in hex)");
                output.println("  Example: compare 2000 3000 1000");
                return true;
            case "search":
            case "find":
                output.println("find <start> <end> <bytes> - Search memory for byte sequence");
                output.println("  <start> - Starting address in hex");
                output.println("  <end>   - Ending address in hex");
                output.println("  <bytes> - Bytes to search for (in hex, space separated)");
                output.println("  Example: find 800 8000 A2 00 BD");
                return true;
            case "back":
            case "quit":
            case "q":
                output.println("back | quit | q - Return to main mode");
                return true;
            case "debug":
                output.println("debug - Shows debugger functionality information");
                output.println("Note: All debugger commands are directly available in monitor mode.");
                return true;
            case "pause":
            case "p":
                output.println("pause | p - Pauses the emulation");
                return true;
            case "resume":
            case "r":
                output.println("resume | r - Resumes the emulation");
                return true;
            case "cpu":
                output.println("cpu - Displays the current CPU state (registers and flags)");
                return true;
            case "registers":
            case "reg":
            case "setregister":
            case "sr":
                output.println("registers | reg [register value] - Display or set CPU registers");
                output.println("  With no arguments: Shows the current values of all CPU registers and flags");
                output.println("  With arguments: Sets the specified register to the given value");
                output.println("  Registers: A, X, Y, PC, S, N, V, B, D, I, Z, C");
                output.println("  Values can be decimal, hex with $ prefix, or hex with 0x prefix");
                output.println("Examples:");
                output.println("  registers         - Show all register values");
                output.println("  registers A $FF   - Set accumulator to $FF");
                output.println("  reg PC $C000      - Set program counter to $C000");
                output.println("  reg X 42          - Set X register to decimal 42");
                return true;
            case "break":
            case "b":
                output.println("break | b <addr> - Manages breakpoints");
                output.println("  break <addr>        - Add a breakpoint at address");
                output.println("  break -<addr>       - Remove a breakpoint at address");
                output.println("  break clear         - Remove all breakpoints");
                return true;
            case "breaklist":
            case "bl":
                output.println("breaklist | bl - Lists all active breakpoints");
                return true;
            case "step":
            case "s":
                output.println("step | s [count] - Steps through CPU instructions");
                output.println("  [count] - Optional number of steps (default: 1)");
                return true;
            case "watch":
            case "w":
                output.println("watch | w <addr> [name] - Watch address for changes");
                output.println("  watch -<addr>               Remove watch at address");
                output.println("  watch -<name>               Remove watch by name");
                output.println("  watch clear                 Clear all watches");
                return true;
            case "watchlist":
            case "wl":
                output.println("watchlist | wl - Lists all active memory watches");
                return true;
            case "runto":
            case "rt":
                output.println("runto | rt <addr> - Runs until reaching a specific address");
                output.println("  <addr> - Address to run to (in hex)");
                return true;
            case "cheat":
                output.println("cheat <addr> <value> - Manages memory value overrides");
                output.println("  cheat <addr> <value>  - Force memory at address to always return value");
                output.println("  cheat -<addr>         - Remove a cheat at address");
                output.println("  cheat clear           - Remove all cheats");
                return true;
            case "cheatlist":
            case "cl":
                output.println("cheatlist | cl - Lists all active memory cheats");
                return true;
            default:
                return false;
        }
    }
    
    // Pattern command handlers
    private void handleSingleListCommand(Matcher matcher) {
        // Continue disassembly from last address
        disassembleCode(lastDisassemblyAddress, DEFAULT_DISASM_COUNT, true);
    }
    
    private void handleGoCommand(Matcher matcher) {
        // The memory mode prefix (group 1) may be present
        String modePrefix = matcher.group(1);
        
        // Get the address from group 2 (was group 1 before)
        String addrStr = matcher.group(2);
        
        // Parse the address with the correct memory mode
        AddressWithMode addrWithMode = new AddressWithMode(
            Integer.parseInt(addrStr, 16),
            determineMemoryMode(modePrefix, memoryMode));
        
        executeCode(addrWithMode.getAddress());
    }
    
    private void handleListCommand(Matcher matcher) {
        String addrStr = matcher.group(1);
        int address = Integer.parseInt(addrStr, 16);
        disassembleCode(address, DEFAULT_DISASM_COUNT, true);
    }
    
    private void handleRangeCommand(Matcher matcher) {
        String modePrefix = matcher.group(1);
        String startStr = matcher.group(2);
        String endStr = matcher.group(3);
        
        // Determine memory mode
        MemoryMode mode = determineMemoryMode(modePrefix, memoryMode);
        
        // Parse addresses
        int startAddr = Integer.parseInt(startStr, 16);
        int endAddr = Integer.parseInt(endStr, 16);
        
        // Create AddressWithMode objects
        AddressWithMode startWithMode = new AddressWithMode(startAddr, mode);
        AddressWithMode endWithMode = new AddressWithMode(endAddr, mode);
        
        examineMemoryRange(startWithMode.getAddress(), endWithMode.getAddress(), mode);
    }
    
    private void handlePokeCommand(Matcher matcher) {
        String modePrefix = matcher.group(1);
        String addrStr = matcher.group(2);
        String valuesStr = matcher.group(3);
        
        // Parse the address with mode
        AddressWithMode addrWithMode = new AddressWithMode(
            Integer.parseInt(addrStr, 16), 
            determineMemoryMode(modePrefix, memoryMode));
        
        // Parse values
        String[] valueStrs = valuesStr.trim().split("\\s+");
        int[] values = new int[valueStrs.length];
        for (int i = 0; i < valueStrs.length; i++) {
            values[i] = Integer.parseInt(valueStrs[i], 16);
        }
        
        writeMemory(addrWithMode.getAddress(), values, addrWithMode.getMode());
    }
    
    private void handleExamineCommand(Matcher matcher) {
        String modePrefix = matcher.group(1);
        String addrStr = matcher.group(2);
        
        // Parse the address with mode
        AddressWithMode addrWithMode = new AddressWithMode(
            Integer.parseInt(addrStr, 16),
            determineMemoryMode(modePrefix, memoryMode));
        
        lastExaminedAddress = addrWithMode.getAddress();
        
        examineMemory(addrWithMode.getAddress(), 1, addrWithMode.getMode());
    }
    
    // Command implementations
    private void examineMemory(int address, int count, MemoryMode mode) {
        if (count == 1) {
            // Display single byte - use readMemory to trigger memory listeners
            byte value = readMemory(address, mode, true);
            output.println(String.format("%04X: %02X", address, value & 0xFF));
            lastExaminedAddress = address + 1;
        } else {
            // Display multiple bytes - hexDump uses readRaw
            hexDump(address, count, mode);
        }
    }
    
    private void examineMemoryRange(int startAddr, int endAddr, MemoryMode mode) {
        // Calculate number of bytes to dump (inclusive)
        int count = endAddr - startAddr + 1;
        if (count <= 0) {
            output.println("End address must be greater than or equal to start address");
            return;
        }
        
        hexDump(startAddr, count, mode);
        lastExaminedAddress = startAddr + count;
    }
    
    private void fillMemory(String[] args) {
        if (args.length < 3) {
            output.println("Usage: fill start end value");
            return;
        }
        
        try {
            int start = parseAddress(args[0]);
            int end = parseAddress(args[1]);
            int value = Integer.parseInt(args[2], 16) & 0xFF;
            
            if (start > end) {
                output.println("Start address must be less than or equal to end address");
                return;
            }
            
            // Fill memory
            int[] values = new int[end - start + 1];
            for (int i = 0; i < values.length; i++) {
                values[i] = value;
            }
            
            writeMemory(start, values, memoryMode);
            
            output.println("Filled memory from $" + Integer.toHexString(start).toUpperCase() + 
                    " to $" + Integer.toHexString(end).toUpperCase() + 
                    " with $" + Integer.toHexString(value & 0xFF).toUpperCase());
        } catch (NumberFormatException e) {
            output.println("Invalid address or value format");
        }
    }
    
    private void moveMemory(String[] args) {
        if (args.length < 3) {
            output.println("Usage: move src dest count");
            return;
        }
        
        try {
            int src = parseAddress(args[0]);
            int dest = parseAddress(args[1]);
            int count = Integer.parseInt(args[2]);
            
            // Read source bytes without triggering memory listeners
            byte[] buffer = new byte[count];
            for (int i = 0; i < count; i++) {
                int addr = src + i;
                buffer[i] = readMemory(addr, memoryMode, false);
            }
            
            // Write to destination
            int[] values = new int[count];
            for (int i = 0; i < count; i++) {
                values[i] = buffer[i] & 0xFF;
            }
            
            writeMemory(dest, values, memoryMode);
            
            output.println("Moved " + count + " bytes from $" + 
                    Integer.toHexString(src).toUpperCase() + " to $" + 
                    Integer.toHexString(dest).toUpperCase());
        } catch (NumberFormatException e) {
            output.println("Invalid address or count format");
        }
    }
    
    private void compareMemory(String[] args) {
        if (args.length < 3) {
            output.println("Usage: compare src dest count");
            return;
        }
        
        try {
            int src = parseAddress(args[0]);
            int dest = parseAddress(args[1]);
            int count = Integer.parseInt(args[2]);
            
            int diffCount = 0;
            
            output.println("Comparing $" + Integer.toHexString(src).toUpperCase() + 
                    " with $" + Integer.toHexString(dest).toUpperCase() + 
                    " for " + count + " bytes");
            
            for (int i = 0; i < count; i++) {
                int srcAddr = (src + i) & 0xFFFF;
                int destAddr = (dest + i) & 0xFFFF;
                
                // Use readMemory with triggerEvents=false to avoid triggering listeners
                byte srcVal = readMemory(srcAddr, memoryMode, false);
                byte destVal = readMemory(destAddr, memoryMode, false);
                
                if (srcVal != destVal) {
                    diffCount++;
                    output.println("  $" + Integer.toHexString(srcAddr).toUpperCase() + 
                            ": $" + String.format("%02X", srcVal & 0xFF) + 
                            "  $" + Integer.toHexString(destAddr).toUpperCase() + 
                            ": $" + String.format("%02X", destVal & 0xFF));
                }
            }
            
            if (diffCount == 0) {
                output.println("Memory regions are identical");
            } else {
                output.println("Found " + diffCount + " differences");
            }
        } catch (NumberFormatException e) {
            output.println("Invalid address or count format");
        }
    }
    
    private void searchMemory(String[] args) {
        if (args.length < 3) {
            output.println("Usage: search start end value [value2...]");
            return;
        }
        
        try {
            int start = parseAddress(args[0]);
            int end = parseAddress(args[1]);
            
            // Convert search values to byte array
            int[] pattern = new int[args.length - 2];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = Integer.parseInt(args[i + 2], 16) & 0xFF;
            }
            
            output.println("Searching for pattern from $" + 
                    Integer.toHexString(start).toUpperCase() + " to $" + 
                    Integer.toHexString(end).toUpperCase());
            
            int foundCount = 0;
            for (int addr = start; addr <= end - pattern.length + 1; addr++) {
                boolean match = true;
                for (int i = 0; i < pattern.length; i++) {
                    int currentAddr = addr + i;
                    // Use readMemory with triggerEvents=false to avoid triggering listeners
                    byte value = readMemory(currentAddr, memoryMode, false);
                    if ((value & 0xFF) != pattern[i]) {
                        match = false;
                        break;
                    }
                }
                
                if (match) {
                    foundCount++;
                    output.println("  Found at $" + Integer.toHexString(addr).toUpperCase());
                }
            }
            
            if (foundCount == 0) {
                output.println("Pattern not found");
            } else {
                output.println("Found " + foundCount + " matches");
            }
        } catch (NumberFormatException e) {
            output.println("Invalid address or value format");
        }
    }
    
    
    // Memory reading and writing methods
    
    /**
     * Reads a byte from memory with control over event triggering
     * 
     * @param address The address to read from
     * @param mode The memory mode (MAIN, AUX, or ACTIVE)
     * @param triggerEvents Whether to trigger memory listeners
     * @return The byte read from memory
     */
    byte readMemory(int address, MemoryMode mode, boolean triggerEvents) {
        return Emulator.withMemory(ram -> {
            Boolean auxFlag = determineAuxFlag(mode);
            
            if (triggerEvents) {
                // Use read() with proper parameters to trigger memory listeners
                // Handle null auxFlag by defaulting to false (main memory)
                return (byte) ram.read(address, RAMEvent.TYPE.READ_DATA, true, auxFlag != null ? auxFlag : false);
            } else {
                // Use readRaw for non-event triggering, it still respects memory mode via the memory manager
                return ram.readRaw(address);
            }
        }, (byte) 0);
    }
    
    /**
     * Reads a byte from memory with default event triggering (enabled)
     * 
     * @param address The address to read from
     * @param mode The memory mode (MAIN, AUX, or ACTIVE)
     * @return The byte read from memory
     */
    byte readMemory(int address, MemoryMode mode) {
        return readMemory(address, mode, true);
    }
    
    private void writeMemory(int address, int[] values, MemoryMode mode) {
        Emulator.withMemory(ram -> {
            Boolean auxFlag = determineAuxFlag(mode);
            
            for (int i = 0; i < values.length; i++) {
                // Use the RAM.write method with the appropriate parameters
                // Handle null auxFlag by defaulting to false (main memory)
                ram.write(address + i, (byte) values[i], true, auxFlag != null ? auxFlag : false);
            }
        });
    }
    
    /**
     * Determines auxFlag from memory mode for RAM access
     * 
     * @param mode The memory mode
     * @return The auxiliary memory flag (null for active, false for main, true for aux)
     */
    private Boolean determineAuxFlag(MemoryMode mode) {
        if (mode == MemoryMode.MAIN) {
            return false;
        } else if (mode == MemoryMode.AUX) {
            return true;
        } else {
            return null;
        }
    }
    
    private void hexDump(int startAddress, int byteCount, MemoryMode mode) {
        lastExaminedAddress = startAddress + byteCount;
        
        // Ensure we don't go beyond 64K
        if (startAddress + byteCount > 0x10000) {
            byteCount = 0x10000 - startAddress;
        }
        
        for (int offset = 0; offset < byteCount; offset += 16) {
            // Print address
            output.print(String.format("%04X: ", (startAddress + offset) & 0xFFFF));
            
            // Print hex values
            StringBuilder hexValues = new StringBuilder();
            StringBuilder asciiValues = new StringBuilder();
            
            int lineBytes = Math.min(16, byteCount - offset);
            for (int i = 0; i < lineBytes; i++) {
                int addr = (startAddress + offset + i) & 0xFFFF;
                // Use readMemory with triggerEvents=false to avoid triggering memory listeners
                byte value = readMemory(addr, mode, false);
                
                hexValues.append(String.format("%02X ", value & 0xFF));
                
                // For ASCII representation, mask high bit for Apple II character set
                int asciiValue = value & 0x7F;
                if (asciiValue >= 32 && asciiValue < 127) {
                    asciiValues.append((char) asciiValue);
                } else {
                    asciiValues.append('.');
                }

            }
            
            // Pad hex values if less than 16 bytes
            for (int i = lineBytes; i < 16; i++) {
                hexValues.append("   ");
            }
            
            output.println(hexValues + " | " + asciiValues);
        }
    }
    
    private void executeCode(int address) {
        // Simply set the program counter
        getCpu().setProgramCounter(address);
        
        // Make sure the debugger is active if there are breakpoints set
        if (!debugger.getBreakpoints().isEmpty()) {
            debugger.setActive(true);
        }
        
        // Show minimal output
        output.println("Execution started at $" + Integer.toHexString(address).toUpperCase());
        
        // Resume emulation
        if (isPaused) {
            isPaused = false;
            Emulator.withComputer(computer -> {
                computer.getMotherboard().resume();
            });
        }
    }
    
    private void disassembleCode(int startAddress, int instructionCount, boolean showMemoryBytes) {
        Emulator.withComputer(computer -> {
            // Cast the CPU to MOS65C02 to access the specialized methods
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            int address = startAddress;
            for (int i = 0; i < instructionCount; i++) {
                String disasm = cpu.disassemble(address);
                
                // Show the address (4 digits)
                output.printf("%04X: ", address);
                
                // Get the opcode to determine instruction length
                byte opcode = readMemory(address, memoryMode, false);
                int byteCount = getInstructionSize(opcode & 0xFF, cpu);
                
                // Show the memory bytes if requested
                if (showMemoryBytes) {
                    // Display the memory bytes
                    for (int j = 0; j < byteCount; j++) {
                        byte memByte = readMemory((address + j) & 0xFFFF, memoryMode, false);
                        output.printf("%02X ", memByte & 0xFF);
                    }
                    
                    // Pad with spaces to align disassembly - max is 3 bytes (9 chars with spaces)
                    for (int j = byteCount; j < 3; j++) {
                        output.print("   ");
                    }
                    output.print(" ");
                }
                
                // Show the disassembled instruction
                output.println(disasm);
                
                // Calculate next address
                address = (address + byteCount) & 0xFFFF;
            }
            
            lastDisassemblyAddress = address;
        });
    }
    
    /**
     * Gets the size of an instruction based on its opcode
     * 
     * @param opcode The opcode byte
     * @param cpu The CPU to get instruction information from
     * @return The size of the instruction in bytes
     */
    private int getInstructionSize(int opcode, MOS65C02 cpu) {
        MOS65C02.OPCODE op = MOS65C02.opcodes[opcode];
        if (op == null) {
            return 1;
        } else {
            return op.getMode().getSize();
        }
    }
    
    /**
     * Restores watches, breakpoints, and cheats from the persistent collections
     */
    private void restorePersistedState() {
        // Restore breakpoints
        if (!persistentBreakpoints.isEmpty()) {
            for (Integer bp : persistentBreakpoints) {
                debugger.getBreakpoints().add(bp);
            }
            debugger.setActive(true);
            output.println("Restored " + persistentBreakpoints.size() + " breakpoint(s)");
        }
        
        // Restore watches
        if (!persistentWatches.isEmpty()) {
            for (PersistentWatch watch : persistentWatches.values()) {
                watches.add(new Watch(this, watch.name, watch.address, watch.mode));
            }
            output.println("Restored " + persistentWatches.size() + " watch(es)");
        }
        
        // Restore cheats
        if (!persistentCheats.isEmpty()) {
            for (PersistentCheat cheat : persistentCheats.values()) {
                // Add to local map
                cheats.put(cheat.address, new Cheat(cheat.address, cheat.value, cheat.mode));
                
                // Set up the RAM listener
                final int address = cheat.address;
                final int value = cheat.value;
                final Boolean auxFlag = determineAuxFlag(cheat.mode);
                
                Emulator.withMemory(ram -> {
                    ram.observe("Cheat:" + address, RAMEvent.TYPE.READ, address, auxFlag,
                        event -> event.setNewValue(value));
                });
            }
            output.println("Restored " + persistentCheats.size() + " cheat(s)");
        }
    }
    
    // Add cleanup method
    public void cleanup() {
        // When this monitor is being destroyed, remove it from active monitors
        synchronized(activeMonitors) {
            activeMonitors.remove(this);
        }
    }
    
    /**
     * Display CPU registers in detail
     */
    protected void showRegisters() {
        MOS65C02 cpu = getCpu();
        if (cpu != null) {
            output.println("CPU Registers:");
            output.println("  A: $" + String.format("%02X", cpu.getAccumulator() & 0xFF));
            output.println("  X: $" + String.format("%02X", cpu.getXRegister() & 0xFF));
            output.println("  Y: $" + String.format("%02X", cpu.getYRegister() & 0xFF));
            output.println("  PC: $" + String.format("%04X", cpu.getProgramCounter()));
            output.println("  S: $" + String.format("%02X", cpu.getStackPointer() & 0xFF));

            // Status flags
            StringBuilder flags = new StringBuilder();
            flags.append(cpu.isNegativeFlag() ? "N" : "n");
            flags.append(cpu.isOverflowFlag() ? "V" : "v");
            flags.append("-");
            flags.append(cpu.isBreakFlag() ? "B" : "b");
            flags.append(cpu.isDecimalFlag() ? "D" : "d");
            flags.append(cpu.isInterruptFlag() ? "I" : "i");
            flags.append(cpu.isZeroFlag() ? "Z" : "z");
            flags.append(cpu.isCarryFlag() ? "C" : "c");

            output.println("  Flags: " + flags.toString());
        } else {
            output.println("CPU not available");
        }
    }

    /**
     * Handle the registers command - displays or sets CPU registers
     */
    protected void handleRegisters(String[] args) {
        if (args.length == 0) {
            // No arguments - display registers
            showRegisters();
        } else if (args.length >= 2) {
            // Arguments provided - set register
            String register = args[0].toUpperCase();
            String valueStr = args[1];

            MOS65C02 cpu = getCpu();
            if (cpu == null) {
                output.println("CPU not available");
                return;
            }

            try {
                switch (register) {
                    case "A":
                        cpu.setAccumulator(parseByteValue(valueStr));
                        break;
                    case "X":
                        cpu.setXRegister(parseByteValue(valueStr));
                        break;
                    case "Y":
                        cpu.setYRegister(parseByteValue(valueStr));
                        break;
                    case "PC":
                        cpu.setProgramCounter(parseWordValue(valueStr));
                        break;
                    case "S":
                        cpu.setStackPointer(parseByteValue(valueStr));
                        break;
                    case "N":
                        cpu.setNegativeFlag(parseBooleanValue(valueStr));
                        break;
                    case "V":
                        cpu.setOverflowFlag(parseBooleanValue(valueStr));
                        break;
                    case "B":
                        cpu.setBreakFlag(parseBooleanValue(valueStr));
                        break;
                    case "D":
                        cpu.setDecimalFlag(parseBooleanValue(valueStr));
                        break;
                    case "I":
                        cpu.setInterruptFlag(parseBooleanValue(valueStr));
                        break;
                    case "Z":
                        cpu.setZeroFlag(parseBooleanValue(valueStr));
                        break;
                    case "C":
                        cpu.setCarryFlag(parseBooleanValue(valueStr));
                        break;
                    default:
                        output.println("Unknown register: " + register);
                        return;
                }
                output.println("Register " + register + " set to " + valueStr);
            } catch (NumberFormatException e) {
                output.println("Invalid value format: " + valueStr);
            }
        } else {
            output.println("Usage: registers [register] [value]");
        }
    }

    private int parseByteValue(String value) {
        if (value.startsWith("$")) {
            return Integer.parseInt(value.substring(1), 16) & 0xFF;
        } else if (value.startsWith("0x")) {
            return Integer.parseInt(value.substring(2), 16) & 0xFF;
        } else {
            return Integer.parseInt(value) & 0xFF;
        }
    }

    private int parseWordValue(String value) {
        if (value.startsWith("$")) {
            return Integer.parseInt(value.substring(1), 16) & 0xFFFF;
        } else if (value.startsWith("0x")) {
            return Integer.parseInt(value.substring(2), 16) & 0xFFFF;
        } else {
            return Integer.parseInt(value) & 0xFFFF;
        }
    }

    private boolean parseBooleanValue(String value) {
        return "1".equals(value) ||
                "true".equalsIgnoreCase(value) ||
                "on".equalsIgnoreCase(value) ||
                "yes".equalsIgnoreCase(value);
    }
} 
