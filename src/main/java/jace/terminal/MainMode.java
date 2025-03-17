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

        commands.put("registers", args -> showRegisters());
        commands.put("setregister", this::setRegister);

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
        addAlias("r", "registers");
        addAlias("sr", "setregister");
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

        commandHelp.put("registers", "Displays current CPU register values.\nUsage: registers (or r)");
        commandHelp.put("setregister",
                "Sets a CPU register to a specific value.\nUsage: setregister <register> <value> (or sr <register> <value>)\n"
                        +
                        "Registers: A, X, Y, PC, S, N, V, B, D, I, Z, C\n" +
                        "Values can be decimal, hex with $ prefix, or hex with 0x prefix.");

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
        return "JACE> ";
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

        LOG.info("Unknown command received: " + cmd);
        output.println("Unknown command: " + cmd);
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
        output.println("  registers (r)  - Display CPU registers");
        output.println("  setregister (sr) - Set a CPU register (A|X|Y|PC|S|P|FLAGS) value");
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

    private void showRegisters() {
        try {
            MOS65C02 cpu = getCPU();
            if (cpu != null) {
                output.println("CPU Registers:");
                output.println("  A: $" + String.format("%02X", cpu.A & 0xFF));
                output.println("  X: $" + String.format("%02X", cpu.X & 0xFF));
                output.println("  Y: $" + String.format("%02X", cpu.Y & 0xFF));
                output.println("  PC: $" + String.format("%04X", cpu.getProgramCounter()));
                output.println("  S: $" + String.format("%02X", cpu.STACK & 0xFF));

                // Status flags
                StringBuilder flags = new StringBuilder();
                flags.append(cpu.N ? "N" : "n");
                flags.append(cpu.V ? "V" : "v");
                flags.append("-");
                flags.append(cpu.B ? "B" : "b");
                flags.append(cpu.D ? "D" : "d");
                flags.append(cpu.I ? "I" : "i");
                flags.append(cpu.Z ? "Z" : "z");
                flags.append(cpu.C > 0 ? "C" : "c");

                output.println("  Flags: " + flags.toString());
            } else {
                LOG.warning("CPU not available for register display");
                output.println("CPU not available");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error displaying CPU registers", e);
            output.println("Error accessing CPU: " + e.getMessage());
        }
    }

    private void setRegister(String[] args) {
        if (args.length < 2) {
            output.println("Usage: setregister <register> <value>");
            output.println("  Registers: A, X, Y, PC, S, N, V, B, D, I, Z, C");
            return;
        }

        String register = args[0].toUpperCase();
        String valueStr = args[1];

        try {
            MOS65C02 cpu = getCPU();
            if (cpu == null) {
                LOG.warning("CPU not available for register setting");
                output.println("CPU not available");
                return;
            }

            try {
                switch (register) {
                    case "A":
                        cpu.A = parseByteValue(valueStr);
                        break;
                    case "X":
                        cpu.X = parseByteValue(valueStr);
                        break;
                    case "Y":
                        cpu.Y = parseByteValue(valueStr);
                        break;
                    case "PC":
                        cpu.setProgramCounter(parseWordValue(valueStr));
                        break;
                    case "S":
                        cpu.STACK = parseByteValue(valueStr);
                        break;
                    case "N":
                        cpu.N = parseBooleanValue(valueStr);
                        break;
                    case "V":
                        cpu.V = parseBooleanValue(valueStr);
                        break;
                    case "B":
                        cpu.B = parseBooleanValue(valueStr);
                        break;
                    case "D":
                        cpu.D = parseBooleanValue(valueStr);
                        break;
                    case "I":
                        cpu.I = parseBooleanValue(valueStr);
                        break;
                    case "Z":
                        cpu.Z = parseBooleanValue(valueStr);
                        break;
                    case "C":
                        cpu.C = parseBooleanValue(valueStr) ? 1 : 0;
                        break;
                    default:
                        LOG.info("Unknown register requested: " + register);
                        output.println("Unknown register: " + register);
                        return;
                }
                LOG.fine("Register " + register + " set to " + valueStr);
                output.println("Register " + register + " set to " + valueStr);
            } catch (NumberFormatException e) {
                LOG.info("Invalid value format for register: " + valueStr);
                output.println("Invalid value format: " + valueStr);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error setting CPU register", e);
            output.println("Error accessing CPU: " + e.getMessage());
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
        int steps = 1;
        if (args.length > 0) {
            try {
                steps = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOG.info("Invalid step count: " + args[0]);
                output.println("Invalid step count: " + args[0]);
                return;
            }
        }

        final int stepCount = steps;
        try {
            Emulator.withComputer(computer -> {
                output.println("Stepping CPU for " + stepCount + " cycles...");
                LOG.fine("Stepping CPU for " + stepCount + " cycles");
                computer.getMotherboard().whileSuspended(() -> {
                    for (int i = 0; i < stepCount; i++) {
                        computer.getCpu().tick();
                    }
                });
                output.println("CPU stepped " + stepCount + " cycles");
                showRegisters();
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error stepping CPU", e);
            output.println("Error accessing computer: " + e.getMessage());
        }
    }

    private void runCPU(String[] args) {
        int cycles = 1000000; // Default to 1 million cycles
        int breakpoint = -1; // No breakpoint by default

        if (args.length > 0) {
            try {
                cycles = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOG.info("Invalid cycle count: " + args[0]);
                output.println("Invalid cycle count: " + args[0]);
                return;
            }
        }

        if (args.length > 1) {
            try {
                if (args[1].startsWith("$")) {
                    breakpoint = Integer.parseInt(args[1].substring(1), 16) & 0xFFFF;
                } else {
                    breakpoint = Integer.parseInt(args[1]) & 0xFFFF;
                }
            } catch (NumberFormatException e) {
                LOG.info("Invalid breakpoint address: " + args[1]);
                output.println("Invalid breakpoint address: " + args[1]);
                return;
            }
        }

        final int cycleCount = cycles;
        final int breakAddr = breakpoint;

        String cycleMsg = "Running CPU for " + (cycleCount == -1 ? "unlimited" : cycleCount) + " cycles" +
                (breakAddr != -1 ? " or until PC=$" + String.format("%04X", breakAddr) : "");
        LOG.info(cycleMsg);
        output.println(cycleMsg);

        // TODO: Implement actual run logic with breakpoint support
        try {
            Emulator.withComputer(computer -> {
                computer.getMotherboard().resume();
                // This would need to be properly implemented with a separate thread and
                // monitoring
                output.println("CPU resumed, press Ctrl+C to interrupt");
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error running CPU", e);
            output.println("Error accessing computer: " + e.getMessage());
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

    /**
     * Helper method to get CPU from the emulator
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
}