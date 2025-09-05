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
import jace.core.Motherboard;
import jace.core.Device;
import jace.core.RAMListener;
import jace.core.RAMEvent;
import jace.core.Keyboard;

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
        commands.put("key", this::simulateKeypress);
        commands.put("help", this::showHelp);

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
        addAlias("k", "key");
        addAlias("?", "help");

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

        commandHelp.put("key",
                "Simulates keypresses or types a string.\nUsage: key <value1> [value2] [value3] ... (or k <value1> [value2] ...)\n" +
                        "Each value can be:\n" +
                        "- A full string in double quotes (e.g. \"Hello World\")\n" +
                        "- A single non-digit character (e.g. a)\n" +
                        "- A quoted character (e.g. '9' or \"9\")\n" +
                        "- A hex value with $ prefix (e.g. $41)\n" +
                        "- A decimal number (e.g. 65)\n" +
                        "- An escape sequence: \\n (CR, code 13), \\t (Tab, code 9)\n\n" +
                        "Multiple values will be processed sequentially.\n" +
                        "Strings support escape sequences (e.g. \"Hello\\nWorld\" types \"Hello\", CR, \"World\").");
        
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
    // Since all softswitches are in the C0xx range, just look for reads or writes
    // and then after about 1ms, compare switches to previous state and log any changes
    RAMListener softSwitchListener = null;
    private Map<SoftSwitches, Boolean> currentSoftSwitchState = new HashMap<>();

    private Map<SoftSwitches, Boolean> getSoftSwitchState() {
        Map<SoftSwitches, Boolean> state = new HashMap<>();
        for (SoftSwitches sw : SoftSwitches.values()) {
            if (sw != SoftSwitches.VBL) {
                state.put(sw, sw.isOn());
            }
        }
        return state;
    }

    private void toggleSoftSwitchLogging(String[] args) {
        softSwitchLoggingEnabled = !softSwitchLoggingEnabled;
        LOG.info("SoftSwitch logging " + (softSwitchLoggingEnabled ? "enabled" : "disabled"));
        output.println("SoftSwitch logging " + (softSwitchLoggingEnabled ? "enabled" : "disabled"));
        if (softSwitchLoggingEnabled) {
            // Track current state
            currentSoftSwitchState = getSoftSwitchState();
            // Now register a ram listener for the C000-C0FF range
            softSwitchListener = new RAMListener("SoftSwitchLogger", RAMEvent.TYPE.ANY, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(0xC000);
                    setScopeEnd(0xC0FF);
                }

                @Override
                protected void doEvent(RAMEvent event) {
                    // Don't immediately check - give other state changes a chance to happen
                    Emulator.whileSuspended(computer -> {
                        // Schedule a task to check for softswitch state changes after 5ms
                        Runnable task = () -> checkSoftSwitchChanges();
                        java.util.Timer timer = new java.util.Timer("SoftSwitchCheck", true);
                        timer.schedule(new java.util.TimerTask() {
                            @Override
                            public void run() {
                                task.run();
                                timer.cancel();
                            }
                        }, 5);
                    });
                }
            };
            Emulator.withMemory(m -> m.addListener(softSwitchListener));
        } else {
            Emulator.withMemory(m -> m.removeListener(softSwitchListener));
            softSwitchListener = null;
        }
    }

    synchronized private void checkSoftSwitchChanges() {
        Map<SoftSwitches, Boolean> newState = getSoftSwitchState();
        for (SoftSwitches sw : SoftSwitches.values()) {
            Boolean oldValue = currentSoftSwitchState.get(sw);
            Boolean newValue = newState.get(sw);
            
            // Check if the state has changed
            if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
                String message = sw.name() + "->" + (newValue ? "ON" : "OFF");
                LOG.info(message);
                output.println(message);
            }
        }
        
        // Update current state
        currentSoftSwitchState = newState;
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

        // Get CPU directly from emulator instead of using getCPU() which can hang
        MOS65C02 cpu = Emulator.withComputer(c -> (MOS65C02) c.getCpu(), null);
        if (cpu == null) {
            output.println("CPU not available");
            return;
        }

        // Save the current program counter
        int startPC = cpu.getProgramCounter();

        // Step all devices
        try {
            final int steps = stepCount;
            LOG.info("About to enter Emulator.withComputer for stepping");
            
            Emulator.withComputer((computer) -> {
                LOG.info("Inside withComputer - getting motherboard");
                Motherboard motherboard = computer.getMotherboard();
                
                LOG.info("Motherboard state before: running=" + motherboard.isRunning() + " paused=" + motherboard.isPaused());
                
                // Ensure motherboard is suspended (no free-running timer)
                LOG.info("Calling motherboard.suspend()");
                motherboard.suspend();
                
                LOG.info("Motherboard state after suspend: running=" + motherboard.isRunning() + " paused=" + motherboard.isPaused());
                
                // Use resumeInThread to put devices in running state without starting timer thread
                // This allows manual stepping while devices are in proper state for tick cascade
                LOG.info("Calling motherboard.resumeInThread()");
                motherboard.resumeInThread();
                
                LOG.info("Motherboard state after resumeInThread: running=" + motherboard.isRunning() + " paused=" + motherboard.isPaused());
                
                for (int i = 0; i < steps; i++) {
                    try {
                        LOG.info("Executing step " + (i+1) + " of " + steps);
                        
                        // Check motherboard child devices before tick
                        int childCount = 0;
                        for (Device child : motherboard.getChildren()) {
                            childCount++;
                            LOG.info("  Child device: " + child.getShortName() + 
                                   " (running=" + child.isRunning() + 
                                   ", paused=" + child.isPaused() + ")");
                        }
                        LOG.info("Motherboard child device count before step " + (i+1) + ": " + childCount);
                        
                        // Tick the motherboard - this will cascade to all child devices
                        // (CPU, cards, speaker, etc.) that are running and not paused
                        LOG.info("About to call motherboard.doTick() for step " + (i+1));
                        motherboard.doTick();
                        LOG.info("motherboard.doTick() completed for step " + (i+1));
                        
                        // Check motherboard child devices after tick
                        int childCountAfter = 0;
                        for (Device child : motherboard.getChildren()) {
                            childCountAfter++;
                        }
                        LOG.info("Motherboard child device count after step " + (i+1) + ": " + childCountAfter);
                        
                        LOG.info("Step " + (i+1) + " completed");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error executing step " + (i+1), e);
                        output.println("Error executing step: " + e.getMessage());
                        break;
                    }
                }
                
                LOG.info("All steps completed, calling final suspend");
                // Keep motherboard suspended to prevent free-running
                motherboard.suspend();
                LOG.info("Final suspend completed");
            });
            
            LOG.info("Exited Emulator.withComputer");
            
            // Print CPU state after stepping
            output.println("Stepped " + stepCount + " instruction" + (stepCount > 1 ? "s" : ""));
            // Get fresh CPU reference and show state
            MOS65C02 finalCpu = Emulator.withComputer(c -> (MOS65C02) c.getCpu(), null);
            if (finalCpu != null) {
                showCPUState(finalCpu);
            }
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
            address = parseHexAddress(args[1]);
        } catch (NumberFormatException e) {
            LOG.info("Invalid address format: " + args[1]);
            output.println("Invalid address: " + args[1]);
            return;
        }

        LOG.info("Binary load requested: " + filename + " at $" + Integer.toHexString(address));
        
        try {
            // Read the binary file
            java.nio.file.Path filePath = java.nio.file.Paths.get(filename);
            if (!java.nio.file.Files.exists(filePath)) {
                output.println("File not found: " + filename);
                return;
            }
            
            byte[] fileData = java.nio.file.Files.readAllBytes(filePath);
            if (fileData.length == 0) {
                output.println("File is empty: " + filename);
                return;
            }
            
            // Check if the data will fit in memory
            if (address + fileData.length > 0x10000) {
                output.println("File too large: would exceed memory bounds");
                return;
            }
            
            // Ensure emulator is fully initialized before attempting memory operations
            LOG.info("Ensuring emulator is fully initialized...");
            
            // Force a complete emulator initialization cycle
            try {
                Thread.sleep(100); // Give initialization time to complete
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Check if emulator is running and properly initialized
            boolean emulatorReady = Emulator.withComputer(computer -> {
                if (computer == null) {
                    LOG.warning("Computer is null - emulator not fully initialized");
                    return false;
                }
                
                if (computer.getMemory() == null) {
                    LOG.warning("Memory system is null - emulator not fully initialized");
                    return false;
                }
                
                LOG.info("Computer type: " + computer.getClass().getSimpleName());
                LOG.info("Memory type: " + computer.getMemory().getClass().getSimpleName());
                
                // Force memory reconfiguration to ensure it's in a known state
                if (computer.getMemory() instanceof jace.apple2e.RAM128k) {
                    jace.apple2e.RAM128k ram128k = (jace.apple2e.RAM128k) computer.getMemory();
                    LOG.info("Pre-config memory state: " + ram128k.getState());
                    ram128k.configureActiveMemory();
                    LOG.info("Post-config memory state: " + ram128k.getState());
                }
                
                return true;
            }, false);
            
            if (!emulatorReady) {
                output.println("Error: Emulator not fully initialized. Cannot load binary.");
                return;
            }
            
            // Load the data into emulator memory
            Emulator.withMemory(memory -> {
                LOG.info("Loading " + fileData.length + " bytes starting at $" + Integer.toHexString(address).toUpperCase());
                
                for (int i = 0; i < fileData.length; i++) {
                    int addr = (address + i) & 0xFFFF;
                    byte value = (byte) (fileData[i] & 0xFF);
                    
                    // Write to main memory (auxFlag = false) like MonitorMode does
                    memory.write(addr, value, true, false);
                }
                
                LOG.info("Memory write operations completed");
            });
            
            output.println("Loaded " + fileData.length + " bytes from " + filename + 
                          " to $" + Integer.toHexString(address).toUpperCase());
            LOG.info("Successfully loaded " + fileData.length + " bytes to $" + Integer.toHexString(address));
            
        } catch (java.io.IOException e) {
            LOG.log(Level.WARNING, "Error reading file: " + filename, e);
            output.println("Error reading file: " + e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error loading binary: " + filename, e);
            output.println("Error loading binary: " + e.getMessage());
        }
    }

    private void saveBinary(String[] args) {
        if (args.length < 3) {
            output.println("Usage: savebin <filename> <address> <size>");
            return;
        }

        String filename = args[0];
        int address, size;

        try {
            address = parseHexAddress(args[1]);
            size = parseHexAddress(args[2]);
        } catch (NumberFormatException e) {
            LOG.info("Invalid address or size format");
            output.println("Invalid address or size");
            return;
        }

        LOG.info("Binary save requested: " + filename + " from $" + 
                Integer.toHexString(address) + " size $" + Integer.toHexString(size));
        
        try {
            // Check if size is valid
            if (size <= 0) {
                output.println("Size must be positive");
                return;
            }
            
            // Check if the memory range is valid
            if (address + size > 0x10000) {
                output.println("Memory range would exceed address space");
                return;
            }
            
            // Ensure emulator is fully initialized before attempting memory operations
            LOG.info("Ensuring emulator is fully initialized...");
            
            // Check if emulator is running and properly initialized
            boolean emulatorReady = Emulator.withComputer(computer -> {
                if (computer == null) {
                    LOG.warning("Computer is null - emulator not fully initialized");
                    return false;
                }
                
                if (computer.getMemory() == null) {
                    LOG.warning("Memory system is null - emulator not fully initialized");
                    return false;
                }
                
                LOG.info("Computer type: " + computer.getClass().getSimpleName());
                LOG.info("Memory type: " + computer.getMemory().getClass().getSimpleName());
                
                return true;
            }, false);
            
            if (!emulatorReady) {
                output.println("Error: Emulator not fully initialized. Cannot save binary.");
                return;
            }
            
            // Read data from emulator memory
            byte[] dataToSave = new byte[size];
            Emulator.withMemory(memory -> {
                LOG.info("Reading " + size + " bytes starting at $" + Integer.toHexString(address).toUpperCase());
                
                for (int i = 0; i < size; i++) {
                    int addr = (address + i) & 0xFFFF;
                    // Read from main memory (auxFlag = false) like loadBinary does
                    byte value = memory.read(addr, RAMEvent.TYPE.READ_DATA, true, false);
                    dataToSave[i] = value;
                }
                
                LOG.info("Memory read operations completed");
            });
            
            // Write the data to file
            java.nio.file.Path filePath = java.nio.file.Paths.get(filename);
            java.nio.file.Files.write(filePath, dataToSave);
            
            output.println("Saved " + size + " bytes from $" + Integer.toHexString(address).toUpperCase() + 
                          " to " + filename);
            LOG.info("Successfully saved " + size + " bytes from $" + Integer.toHexString(address) + " to " + filename);
            
        } catch (java.io.IOException e) {
            LOG.log(Level.WARNING, "Error writing file: " + filename, e);
            output.println("Error writing file: " + e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error saving binary: " + filename, e);
            output.println("Error saving binary: " + e.getMessage());
        }
    }

    private void simulateKeypress(String[] args) {
        if (args.length < 1) {
            output.println("Usage: key <value>");
            return;
        }

        // Check if the first argument is a full string in double quotes
        if (args[0].startsWith("\"")) {
            // Handle a quoted string that might span multiple args due to spaces
            String fullInput = String.join(" ", args);
            if (fullInput.startsWith("\"") && fullInput.endsWith("\"") && fullInput.length() > 3) {
                String textToType = fullInput.substring(1, fullInput.length() - 1);
                processStringInput(textToType);
                return;
            }
        }
        
        // Handle multiple arguments as separate key inputs
        for (String arg : args) {
            // Handle a quoted string for a single argument
            if (arg.startsWith("\"") && arg.endsWith("\"") && arg.length() > 3) {
                String textToType = arg.substring(1, arg.length() - 1);
                processStringInput(textToType);
                continue;
            }
            
            // Handle a single key input
            processSingleKeyInput(arg);
        }
    }
    
    /**
     * Process a string of characters to simulate typing
     * @param text The text to type
     */
    private void processStringInput(String text) {
        output.println("Typing string: \"" + text + "\"");
        LOG.info("Simulating string input: " + text);
        
        // Process the string character by character, handling escape sequences
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            byte keyCode;
            
            // Handle escape sequences
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                i++; // Skip the next character as we're handling it now
                
                switch (next) {
                    case 'n':
                        keyCode = 13; // Carriage return for Apple II
                        break;
                    case 't':
                        keyCode = 9;  // Tab
                        break;
                    case '\\':
                        keyCode = '\\'; // Backslash
                        break;
                    default:
                        // If it's not a recognized escape, just use it as is
                        keyCode = (byte)(next & 0xFF);
                        break;
                }
            } else {
                keyCode = (byte)(c & 0xFF);
            }
            
            simulateKeypressInternal(keyCode);
            
            // Add a small delay between keypresses
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Process a single key input
     * @param input The key input string
     */
    private void processSingleKeyInput(String input) {
        try {
            byte keyCode;
            
            // Check if it's a hex value with $ prefix
            if (input.startsWith("$")) {
                keyCode = (byte)(Integer.parseInt(input.substring(1), 16) & 0xFF);
            }
            // Check if it's a quoted character (handles 'a' or "a")
            else if ((input.startsWith("'") && input.endsWith("'") && input.length() == 3) || 
                     (input.startsWith("\"") && input.endsWith("\"") && input.length() == 3)) {
                keyCode = (byte)(input.charAt(1) & 0xFF);
            }
            // Handle escape sequences like \n or \t
            else if (input.startsWith("\\") && input.length() == 2) {
                switch (input.charAt(1)) {
                    case 'n':
                        keyCode = 13; // Carriage return for Apple II
                        break;
                    case 't':
                        keyCode = 9;  // Tab
                        break;
                    default:
                        // If it's not a recognized escape, just use the second character
                        keyCode = (byte)(input.charAt(1) & 0xFF);
                        break;
                }
            }
            // Check if it's a single character that's not a digit (to avoid ambiguity)
            else if (input.length() == 1 && !Character.isDigit(input.charAt(0))) {
                keyCode = (byte)(input.charAt(0) & 0xFF);
            }
            // Otherwise try parsing as a decimal number
            else {
                keyCode = (byte)(Integer.parseInt(input) & 0xFF);
            }

            simulateKeypressInternal(keyCode);
        } catch (NumberFormatException e) {
            LOG.info("Invalid key value: " + input);
            output.println("Invalid key value: " + input);
        }
    }

    private void simulateKeypressInternal(byte keyCode) {
        LOG.info("Simulating keypress with code: " + keyCode);
        
        // Send the keypress to the emulator
        Emulator.withComputer(computer -> {
            Keyboard.pressKey(keyCode);
            output.println("Key pressed: " + String.format("$%02X", keyCode & 0xFF) + 
                           (keyCode >= 32 && keyCode < 127 ? " ('" + (char)keyCode + "')" : ""));
        });
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

    private void showHelp(String[] args) {
        if (args.length > 0) {
            // Show help for specific command
            if (!printCommandHelp(args[0])) {
                output.println("No help available for command: " + args[0]);
            }
        } else {
            // Show general help
            printHelp();
        }
    }
    
    /**
     * Parse a hex address string that may have a $ prefix
     * @param addrStr The address string (e.g., "$2000", "2000", "0x2000")
     * @return The parsed address as an integer
     * @throws NumberFormatException if the address is not valid
     */
    private int parseHexAddress(String addrStr) throws NumberFormatException {
        String hexStr = addrStr;
        
        // Handle $ prefix (Apple II convention)
        if (hexStr.startsWith("$")) {
            hexStr = hexStr.substring(1);
        }
        // Handle 0x prefix (C convention)  
        else if (hexStr.startsWith("0x") || hexStr.startsWith("0X")) {
            hexStr = hexStr.substring(2);
        }
        
        // Parse as hex and mask to 16-bit address space
        return Integer.parseInt(hexStr, 16) & 0xFFFF;
    }
}