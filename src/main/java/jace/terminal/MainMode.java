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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.SoftSwitches;

/**
 * Main command mode for the Terminal
 */
public class MainMode implements TerminalMode {
    private static final Logger LOG = Logger.getLogger(MainMode.class.getName());
    
    private final JaceTerminal terminal;
    private final PrintStream output;
    private final Map<String, Consumer<String[]>> commands = new HashMap<>();
    private final Map<String, String> commandAliases = new HashMap<>();
    private final Map<String, String> commandHelp = new HashMap<>();
    private boolean softSwitchLoggingEnabled = false;

    public MainMode(JaceTerminal terminal) {
        this.terminal = terminal;
        this.output = terminal.getOutput();
        initCommands();
        LOG.fine("MainMode initialized");
    }

    private void initCommands() {
        commands.put("monitor", args -> terminal.setMode("monitor"));
        commands.put("assembler", args -> terminal.setMode("assembler"));
        commands.put("debugger", args -> terminal.setMode("monitor"));
        commands.put("swlog", this::toggleSoftSwitchLogging);
        commands.put("swstate", this::showSoftSwitchState);

        commands.put("reset", args -> performReset());
        commands.put("step", this::stepCPU);
        commands.put("run", this::runCPU);

        commands.put("insertdisk", this::insertDisk);
        commands.put("ejectdisk", this::ejectDisk);

        commands.put("loadbin", this::loadBinary);
        commands.put("savebin", this::saveBinary);

        addAlias("m", "monitor");
        addAlias("a", "assembler");
        addAlias("d", "debugger");
        addAlias("sl", "swlog");
        addAlias("ss", "swstate");
        addAlias("re", "reset");
        addAlias("s", "step");
        addAlias("g", "run");
        addAlias("id", "insertdisk");
        addAlias("ed", "ejectdisk");
        addAlias("lb", "loadbin");
        addAlias("sb", "savebin");

        commandHelp.put("monitor",
                "Enters monitor mode for memory examination, manipulation, and debugging.\nUsage: monitor (or m)\nNote: All debugger commands are now integrated into monitor mode.");
        commandHelp.put("assembler", "Enters assembler mode for assembly language input.\nUsage: assembler (or a)");
        commandHelp.put("debugger", 
                "Redirects to monitor mode, which now includes all debugging functions.\nUsage: debugger (or d)\nNote: This command is kept for backward compatibility.");

        commandHelp.put("swlog", "Toggles logging of softswitch state changes.\nUsage: swlog (or sl)");
        commandHelp.put("swstate",
                "Displays the current state of all softswitches.\nUsage: swstate [switch_name] (or ss [switch_name])\n"
                        +
                        "If switch_name is provided, only shows that specific switch.");

        commandHelp.put("reset", "Resets the Apple II.\nUsage: reset (or re)");

        commandHelp.put("step",
                "Steps the CPU for a specified number of cycles.\nUsage: step [count] (or s [count])\n" +
                        "If count is omitted, steps for 1 cycle.");

        commandHelp.put("run", "Runs the CPU for a specified number of cycles or until a breakpoint is hit.\n" +
                "Usage: run [count] [#breakpoint] (or g [count] [#breakpoint])\n" +
                "If count is omitted, runs for 1,000,000 cycles.\n" +
                "If breakpoint is specified with # prefix, stops when that address is reached.");

        commandHelp.put("insertdisk",
                "Inserts a disk image into a specified drive.\nUsage: insertdisk d<drive_number> (or id d<drive_number>)\n"
                        +
                        "Example: insertdisk d1");

        commandHelp.put("ejectdisk",
                "Ejects a disk from a specified drive.\nUsage: ejectdisk d<drive_number> (or ed d<drive_number>)\n" +
                        "Example: ejectdisk d2");

        commandHelp.put("loadbin",
                "Loads a binary file at a specified memory address.\nUsage: loadbin <filename> <address> (or lb <filename> <address>)\n"
                        +
                        "Address can be decimal or hex with $ or 0x prefix.");

        commandHelp.put("savebin",
                "Saves a block of memory to a binary file.\nUsage: savebin <filename> <address> <size> (or sb <filename> <address> <size>)\n"
                        +
                        "Address and size can be decimal or hex with $ or 0x prefix.");
        
        LOG.fine("Commands initialized");
    }

    private void addAlias(String alias, String command) {
        commandAliases.put(alias, command);
    }

    @Override
    public String getName() {
        return "Main";
    }

    @Override
    public String getPrompt() {
        return "JACE>";
    }

    @Override
    public boolean processCommand(String command) {
        // Handle exit command
        if ("qq".equals(command.trim())) {
            terminal.stop();
            return true;
        }
        
        String[] parts = command.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

        if (commandAliases.containsKey(cmd)) {
            cmd = commandAliases.get(cmd);
        }

        Consumer<String[]> handler = commands.get(cmd);
        if (handler != null) {
            LOG.fine("Processing command: " + cmd);
            handler.accept(args);
            return true;
        }

        // Log the unknown command for debugging purposes
        LOG.info("Unknown command received: " + cmd);
        
        // Display error message directly here - the JaceTerminal will not print its own error
        return false;
    }

    @Override
    public void printHelp() {
        output.println("Available commands:");
        output.println("  monitor/m       - Enter Monitor mode (includes debugger functionality)");
        output.println("  assembler/a     - Enter Assembler mode");
        output.println("  qq              - Exit terminal");
        output.println();
        output.println("  swlog (sl)     - Toggle softswitch state change logging");
        output.println("  swstate (ss)   - Display current state of all softswitches");
        output.println("  reset (re)     - Reset the Apple II");
        output.println("  step (s) [count] - Step the CPU for count cycles (default: 1)");
        output.println("  run (g) [count] - Run the CPU for count cycles or until breakpoint (default: 1000000)");
        output.println("  insertdisk (id) d# - Insert disk image in drive # (1 or 2)");
        output.println("  ejectdisk (ed) d# - Eject disk from drive # (1 or 2)");
        output.println("  loadbin (lb) file addr - Load binary file at specified address (hex)");
        output.println("  savebin (sb) file addr size - Save binary data from memory to file");
        output.println("  help/?          - Show this help");
        output.println("  help/?  <cmd>   - Show detailed help for a specific command");
        output.println("  exit/quit       - Exit the Terminal");
    }

    @Override
    public boolean printCommandHelp(String command) {
        if (commandAliases.containsKey(command)) {
            command = commandAliases.get(command);
        }

        if (commandHelp.containsKey(command)) {
            output.println(commandHelp.get(command));
            return true;
        }

        return false;
    }

    // Command implementations

    private void toggleSoftSwitchLogging(String[] args) {
        softSwitchLoggingEnabled = !softSwitchLoggingEnabled;
        LOG.info("SoftSwitch logging " + (softSwitchLoggingEnabled ? "enabled" : "disabled"));
        output.println("SoftSwitch logging " + (softSwitchLoggingEnabled ? "enabled" : "disabled"));

        // TODO: Implement actual listener on SoftSwitch state changes when enabled
    }

    private void showSoftSwitchState(String[] args) {
        if (args.length > 0) {
            // Show specific softswitch state
            String switchName = args[0].toUpperCase();
            try {
                SoftSwitches sw = SoftSwitches.valueOf(switchName);
                output.println(sw.toString() + " = " + (sw.isOn() ? "ON" : "OFF"));
            } catch (IllegalArgumentException e) {
                LOG.info("Unknown softswitch requested: " + switchName);
                output.println("Unknown softswitch: " + switchName);
            }
        } else {
            // Show all softswitches
            output.println("Current SoftSwitch states:");
            for (SoftSwitches sw : SoftSwitches.values()) {
                output.println("  " + sw.toString() + " = " + (sw.isOn() ? "ON" : "OFF"));
            }
        }
    }

    private void performReset() {
        try {
            Emulator.withComputer(computer -> {
                computer.coldStart();
                output.println("Apple II reset performed");
                LOG.info("Apple II reset performed");
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error performing system reset", e);
            output.println("Error accessing computer: " + e.getMessage());
        }
    }

    private void stepCPU(String[] args) {
        int stepCount = 1;
        if (args.length > 0) {
            try {
                stepCount = Integer.parseInt(args[0]);
                if (stepCount <= 0) {
                    output.println("Step count must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                output.println("Invalid step count: " + args[0]);
                return;
            }
        }

        MOS65C02 cpu = getCPU();
        if (cpu == null) {
            output.println("CPU not available");
            return;
        }

        // Save the current program counter
        int startPC = cpu.getProgramCounter();

        // Step the CPU
        try {
            final int steps = stepCount;
            for (int i = 0; i < stepCount; i++) {
                Emulator.withComputer((computer) -> {
                    computer.pause();
                    // Execute a single instruction
                    computer.getCpu().tick();
                    computer.resume();
                });
            }
            
            // Print CPU state after stepping
            output.println("Stepped " + stepCount + " instruction" + (stepCount > 1 ? "s" : ""));
            showCPUState(cpu);
        } catch (Exception e) {
            output.println("Error during CPU step: " + e.getMessage());
        }
    }

    private void runCPU(String[] args) {
        // Default to running for 1 million cycles if no count is specified
        int cycleCount = 1_000_000;
        Integer breakpointAddress = null;

        if (args.length > 0) {
            try {
                // Check if the first argument is a breakpoint indicator
                if (args[0].startsWith("#")) {
                    breakpointAddress = Integer.parseInt(args[0].substring(1), 16);
                } else {
                    cycleCount = Integer.parseInt(args[0]);
                    
                    // Check for breakpoint as second argument
                    if (args.length > 1 && args[1].startsWith("#")) {
                        breakpointAddress = Integer.parseInt(args[1].substring(1), 16);
                    }
                }

                if (cycleCount <= 0) {
                    output.println("Cycle count must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                output.println("Invalid argument: " + (args[0].startsWith("#") ? args[0].substring(1) : args[0]));
                return;
            }
        }

        final Integer finalBreakpointAddress = breakpointAddress;
        final int finalCycleCount = cycleCount;

        try {
            Emulator.withComputer((computer) -> {
                if (finalBreakpointAddress != null) {
                    output.println("Running until PC = $" + Integer.toHexString(finalBreakpointAddress).toUpperCase() + 
                            " or " + finalCycleCount + " cycles");
                } else {
                    output.println("Running for " + finalCycleCount + " cycles");
                }

                // Track cycles with our own counter
                int currentCycles = 0;
                
                computer.resume();
                
                // Poll periodically to check breakpoint or cycle count
                while (currentCycles < finalCycleCount) {
                    if (finalBreakpointAddress != null && 
                            computer.getCpu().getProgramCounter() == finalBreakpointAddress) {
                        output.println("Breakpoint hit at $" + 
                                Integer.toHexString(finalBreakpointAddress).toUpperCase());
                        break;
                    }
                    
                    // Increment our cycle counter
                    currentCycles += 100;
                    
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                
                computer.pause();
                
                output.println("Ran for approximately " + currentCycles + " cycles");
                
                // Show CPU state after run
                MOS65C02 cpuAfterRun = (MOS65C02) computer.getCpu();
                showCPUState(cpuAfterRun);
            });
        } catch (Exception e) {
            output.println("Error during CPU run: " + e.getMessage());
        }
    }

    private void insertDisk(String[] args) {
        if (args.length < 2) {
            output.println("Usage: insertdisk <drive> <filename>");
            return;
        }

        String drive = args[0];
        String filename = args[1];

        LOG.info("Disk insertion requested for drive " + drive + ": " + filename);
        // TODO: Implement disk insertion
        output.println("Disk insertion not yet implemented");
    }

    private void ejectDisk(String[] args) {
        if (args.length < 1) {
            output.println("Usage: ejectdisk <drive>");
            return;
        }

        String drive = args[0];

        LOG.info("Disk ejection requested for drive " + drive);
        // TODO: Implement disk ejection
        output.println("Disk ejection not yet implemented");
    }

    private void loadBinary(String[] args) {
        if (args.length < 2) {
            output.println("Usage: loadbin <filename> <address>");
            return;
        }

        String filename = args[0];
        int address;

        try {
            if (args[1].startsWith("$")) {
                address = Integer.parseInt(args[1].substring(1), 16) & 0xFFFF;
            } else {
                address = Integer.parseInt(args[1]) & 0xFFFF;
            }
        } catch (NumberFormatException e) {
            LOG.info("Invalid address format: " + args[1]);
            output.println("Invalid address: " + args[1]);
            return;
        }

        LOG.info("Binary load requested: " + filename + " at $" + Integer.toHexString(address));
        // TODO: Implement binary loading
        output.println("Binary loading not yet implemented");
    }

    private void saveBinary(String[] args) {
        if (args.length < 3) {
            output.println("Usage: savebin <filename> <address> <size>");
            return;
        }

        String filename = args[0];
        int address, size;

        try {
            if (args[1].startsWith("$")) {
                address = Integer.parseInt(args[1].substring(1), 16) & 0xFFFF;
            } else {
                address = Integer.parseInt(args[1]) & 0xFFFF;
            }

            if (args[2].startsWith("$")) {
                size = Integer.parseInt(args[2].substring(1), 16) & 0xFFFF;
            } else {
                size = Integer.parseInt(args[2]) & 0xFFFF;
            }
        } catch (NumberFormatException e) {
            LOG.info("Invalid address or size format");
            output.println("Invalid address or size");
            return;
        }

        LOG.info("Binary save requested: " + filename + " from $" + 
                Integer.toHexString(address) + " size $" + Integer.toHexString(size));
        // TODO: Implement binary saving
        output.println("Binary saving not yet implemented");
    }

    private void showCPUState(MOS65C02 cpu) {
        output.println("CPU State:");
        output.println("  PC: $" + String.format("%04X", cpu.getProgramCounter()));
        output.println("  A: $" + String.format("%02X", getAccumulator(cpu) & 0xFF));
        output.println("  X: $" + String.format("%02X", getXRegister(cpu) & 0xFF));
        output.println("  Y: $" + String.format("%02X", getYRegister(cpu) & 0xFF));
        output.println("  S: $" + String.format("%02X", getStackPointer(cpu) & 0xFF));
        
        // Status flags
        StringBuilder flags = new StringBuilder();
        flags.append(isNegativeFlag(cpu) ? "N" : "n");
        flags.append(isOverflowFlag(cpu) ? "V" : "v");
        flags.append("-");
        flags.append(isBreakFlag(cpu) ? "B" : "b");
        flags.append(isDecimalFlag(cpu) ? "D" : "d");
        flags.append(isInterruptFlag(cpu) ? "I" : "i");
        flags.append(isZeroFlag(cpu) ? "Z" : "z");
        flags.append(isCarryFlag(cpu) ? "C" : "c");
        
        output.println("  Flags: " + flags.toString());
    }

    /**
     * Get the CPU from the emulator
     * This method is protected to allow overriding in tests
     * 
     * @return The CPU instance or null if not available
     */
    protected MOS65C02 getCPU() {
        try {
            return (MOS65C02) terminal.getEmulator().withComputer(c -> c.getCpu(), null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error getting CPU: {0}", e.getMessage());
            output.println("Error getting CPU: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the accumulator value from the CPU
     * @param cpu The CPU instance
     * @return The accumulator value
     */
    protected int getAccumulator(MOS65C02 cpu) {
        return cpu.getAccumulator();
    }
    
    /**
     * Set the accumulator value in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setAccumulator(MOS65C02 cpu, int value) {
        cpu.setAccumulator(value);
    }
    
    /**
     * Get the X register value from the CPU
     * @param cpu The CPU instance
     * @return The X register value
     */
    protected int getXRegister(MOS65C02 cpu) {
        return cpu.getXRegister();
    }
    
    /**
     * Set the X register value in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setXRegister(MOS65C02 cpu, int value) {
        cpu.setXRegister(value);
    }
    
    /**
     * Get the Y register value from the CPU
     * @param cpu The CPU instance
     * @return The Y register value
     */
    protected int getYRegister(MOS65C02 cpu) {
        return cpu.getYRegister();
    }
    
    /**
     * Set the Y register value in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setYRegister(MOS65C02 cpu, int value) {
        cpu.setYRegister(value);
    }
    
    /**
     * Get the stack pointer value from the CPU
     * @param cpu The CPU instance
     * @return The stack pointer value
     */
    protected int getStackPointer(MOS65C02 cpu) {
        return cpu.getStackPointer();
    }
    
    /**
     * Set the stack pointer value in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setStackPointer(MOS65C02 cpu, int value) {
        cpu.setStackPointer(value);
    }
    
    /**
     * Check if the negative flag is set in the CPU
     * @param cpu The CPU instance
     * @return True if the flag is set, false otherwise
     */
    protected boolean isNegativeFlag(MOS65C02 cpu) {
        return cpu.isNegativeFlag();
    }
    
    /**
     * Set the negative flag in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setNegativeFlag(MOS65C02 cpu, boolean value) {
        cpu.setNegativeFlag(value);
    }
    
    /**
     * Check if the overflow flag is set in the CPU
     * @param cpu The CPU instance
     * @return True if the flag is set, false otherwise
     */
    protected boolean isOverflowFlag(MOS65C02 cpu) {
        return cpu.isOverflowFlag();
    }
    
    /**
     * Set the overflow flag in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setOverflowFlag(MOS65C02 cpu, boolean value) {
        cpu.setOverflowFlag(value);
    }
    
    /**
     * Check if the break flag is set in the CPU
     * @param cpu The CPU instance
     * @return True if the flag is set, false otherwise
     */
    protected boolean isBreakFlag(MOS65C02 cpu) {
        return cpu.isBreakFlag();
    }
    
    /**
     * Set the break flag in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setBreakFlag(MOS65C02 cpu, boolean value) {
        cpu.setBreakFlag(value);
    }
    
    /**
     * Check if the decimal flag is set in the CPU
     * @param cpu The CPU instance
     * @return True if the flag is set, false otherwise
     */
    protected boolean isDecimalFlag(MOS65C02 cpu) {
        return cpu.isDecimalFlag();
    }
    
    /**
     * Set the decimal flag in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setDecimalFlag(MOS65C02 cpu, boolean value) {
        cpu.setDecimalFlag(value);
    }
    
    /**
     * Check if the interrupt flag is set in the CPU
     * @param cpu The CPU instance
     * @return True if the flag is set, false otherwise
     */
    protected boolean isInterruptFlag(MOS65C02 cpu) {
        return cpu.isInterruptFlag();
    }
    
    /**
     * Set the interrupt flag in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setInterruptFlag(MOS65C02 cpu, boolean value) {
        cpu.setInterruptFlag(value);
    }
    
    /**
     * Check if the zero flag is set in the CPU
     * @param cpu The CPU instance
     * @return True if the flag is set, false otherwise
     */
    protected boolean isZeroFlag(MOS65C02 cpu) {
        return cpu.isZeroFlag();
    }
    
    /**
     * Set the zero flag in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setZeroFlag(MOS65C02 cpu, boolean value) {
        cpu.setZeroFlag(value);
    }
    
    /**
     * Check if the carry flag is set in the CPU
     * @param cpu The CPU instance
     * @return True if the flag is set, false otherwise
     */
    protected boolean isCarryFlag(MOS65C02 cpu) {
        return cpu.isCarryFlag();
    }
    
    /**
     * Set the carry flag in the CPU
     * @param cpu The CPU instance
     * @param value The value to set
     */
    protected void setCarryFlag(MOS65C02 cpu, boolean value) {
        cpu.setCarryFlag(value);
    }
}