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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

import jace.Emulator;
import jace.ipc.CyreneIPCServer;
import jace.terminal.MemoryMode;
import jace.apple2e.MOS65C02;
import jace.apple2e.SoftSwitches;
import jace.applesoft.ApplesoftProgram;
import jace.core.Motherboard;
import jace.core.Device;
import jace.core.RAMListener;
import jace.core.RAMEvent;
import jace.core.Keyboard;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

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
    private boolean charlogEnabled = false;
    RAMListener charlogExecListener = null;
    RAMListener charlogWriteListener = null;
    

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
        commands.put("tick", this::stepCPU);
        commands.put("run", this::runCPU);

        commands.put("insertdisk", this::insertDisk);
        commands.put("ejectdisk", this::ejectDisk);
        commands.put("bootdisk", this::bootDisk);
        commands.put("showtext", this::showTextScreen);
        commands.put("nohints", args -> disableHints());
        commands.put("speed", this::setSpeed);
        commands.put("waitkey", this::waitForKeypress);
        commands.put("type", this::typeString);
        commands.put("expect", this::expectString);

        commands.put("loadbin", this::loadBinary);
        commands.put("savebin", this::saveBinary);
        commands.put("saveauxbin", this::saveAuxBinary);
        commands.put("saveauxrambin", this::saveAuxRamBinary);
        commands.put("screenshot", this::takeScreenshot);
        commands.put("loadbasic", this::loadBasic);
        commands.put("key", this::simulateKeypress);
        commands.put("help", this::showHelp);
        commands.put("charlog", this::toggleCharLog);
        commands.put("rdb", this::cyreneCommand);

        // Monitor forwarding commands — invoke MonitorMode capabilities without a mode switch
        commands.put("go", args -> {
            MonitorMode mon = getMonitorMode(); if (mon == null) return;
            if (args.length < 1) { output.println("Usage: go <addr>"); return; }
            try { mon.executeCode(parseHexAddress(args[0])); }
            catch (NumberFormatException e) { output.println("Invalid address: " + e.getMessage()); }
        });
        commands.put("mem", args -> {
            MonitorMode mon = getMonitorMode(); if (mon == null) return;
            if (args.length < 2) { output.println("Usage: mem <start> <end>"); return; }
            try { mon.examineMemoryRange(parseHexAddress(args[0]), parseHexAddress(args[1]), MemoryMode.ACTIVE); }
            catch (NumberFormatException e) { output.println("Invalid address: " + e.getMessage()); }
        });
        commands.put("cpu", args -> { MonitorMode mon = getMonitorMode(); if (mon != null) mon.showCpuState(); });
        commands.put("registers", args -> { MonitorMode mon = getMonitorMode(); if (mon != null) mon.handleRegisters(args); });
        commands.put("break", args -> { MonitorMode mon = getMonitorMode(); if (mon != null) mon.handleBreakpoint(args); });
        commands.put("runto", args -> { MonitorMode mon = getMonitorMode(); if (mon != null) mon.handleRunTo(args); });

        addAlias("m", "monitor");
        addAlias("a", "assembler");
        addAlias("d", "debugger");
        addAlias("sl", "swlog");
        addAlias("ss", "swstate");
        addAlias("re", "reset");
        addAlias("tc", "tick");
        addAlias("g", "run");
        addAlias("id", "insertdisk");
        addAlias("ed", "ejectdisk");
        addAlias("bd", "bootdisk");
        addAlias("st", "showtext");
        addAlias("lb", "loadbin");
        addAlias("sb", "savebin");
        addAlias("sab", "saveauxbin");
        addAlias("sarb", "saveauxrambin");
        addAlias("ss2", "screenshot");
        addAlias("lbas", "loadbasic");
        addAlias("k", "key");
        addAlias("sp", "speed");
        addAlias("?", "help");
        addAlias("cl", "charlog");
        addAlias("reg", "registers");
        addAlias("bp", "break");
        addAlias("rt", "runto");
        addAlias("cy", "rdb");
        addAlias("cyrene", "rdb");

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

        commandHelp.put("tick",
                "Steps the motherboard (all devices) for a specified number of ticks.\nUsage: tick [count] (or tc [count])\n" +
                        "If count is omitted, steps for 1 tick.\n" +
                        "Note: 'tick' advances the full motherboard cascade. Use 'step' (in monitor mode) for single CPU instruction stepping.");

        commandHelp.put("go",
                "Sets the program counter to the specified address and begins execution.\nUsage: go <addr>\n" +
                        "Example: go 4000  (starts execution at $4000)");

        commandHelp.put("mem",
                "Dumps a range of memory as hex.\nUsage: mem <start> <end>\n" +
                        "Example: mem 3800 3820");

        commandHelp.put("cpu", "Displays the current CPU state (registers and flags).\nUsage: cpu");
        commandHelp.put("registers", "Shows or sets CPU register values.\nUsage: registers [reg value]\nExample: registers PC 4000");
        commandHelp.put("break", "Manages execution breakpoints.\nUsage: break           - List all active breakpoints\n       break <addr>   - Add a breakpoint\n       break -<addr>  - Remove a breakpoint\n       break clear    - Remove all breakpoints");
        commandHelp.put("runto", "Runs the CPU until it reaches the specified address.\nUsage: runto <addr> (or rt <addr>)");

        commandHelp.put("run", "Runs the CPU for a specified number of cycles or until a breakpoint is hit.\n" +
                "Usage: run [count] [#breakpoint] (or g [count] [#breakpoint])\n" +
                "If count is omitted, runs for 1,000,000 cycles.\n" +
                "If breakpoint is specified with # prefix, stops when that address is reached.");

        commandHelp.put("insertdisk",
                "Inserts a disk image into a specified drive.\nUsage: insertdisk d<drive_number> <filepath> [slot] (or id d<drive_number> <filepath> [slot])\n"
                        +
                        "Example: insertdisk d1 /path/to/disk.po\nExample: insertdisk d1 /path/to/disk.po 6");

        commandHelp.put("ejectdisk",
                "Ejects a disk from a specified drive.\nUsage: ejectdisk d<drive_number> [slot] (or ed d<drive_number> [slot])\n" +
                        "Example: ejectdisk d2\nExample: ejectdisk d1 6");

        commandHelp.put("bootdisk",
                "Inserts a disk image, boots it, and runs until PC >= $2000.\nUsage: bootdisk d<drive_number> <filepath> [slot] (or bd d<drive_number> <filepath> [slot])\n"
                        +
                        "This is a convenience command that combines insertdisk, reset, and running until the system boots.\n" +
                        "Example: bootdisk d1 /path/to/disk.po");

        commandHelp.put("showtext",
                "Displays the current text screen contents.\nUsage: showtext (or st)\n" +
                        "Automatically detects 40-column vs 80-column mode and linearizes the screen memory.");

        commandHelp.put("waitkey",
                "Waits until the Apple II reads the keyboard ($C000).\nUsage: waitkey [timeout_ms]\n" +
                        "This detects when the system is waiting for input. Default timeout: 30000ms (30 seconds).\n" +
                        "Example: waitkey 5000");

        commandHelp.put("type",
                "Types a string by synchronizing each keypress with keyboard reads.\nUsage: type <string>[, timeout_seconds]\n" +
                        "Without timeout: waits for keyboard read before each character (emulator paused between chars).\n" +
                        "With timeout: resumes emulator once, types all chars within the time limit, re-pauses if previously paused.\n" +
                        "Timeout is indicated by a trailing comma and 1-2 digit number.\n" +
                        "Example: type \"hello world\\n\"\nExample: type \"RUN\\n\", 5");

        commandHelp.put("expect",
                "Waits for a string to appear on the text screen.\nUsage: expect <string> [timeout_seconds]\n" +
                        "Polls the screen every 500ms until the string is found or timeout occurs.\n" +
                        "Default timeout: 30 seconds\n" +
                        "Example: expect \"Press any key\" 10\nExample: expect \"TEST PASSED\"");

        commandHelp.put("loadbin",
                "Loads a binary file at a specified memory address.\nUsage: loadbin <filename> <address> (or lb <filename> <address>)\n"
                        +
                        "Address can be decimal or hex with $ or 0x prefix.");

        commandHelp.put("savebin",
                "Saves a block of memory to a binary file.\nUsage: savebin <filename> <address> <size> (or sb <filename> <address> <size>)\n"
                        +
                        "Address and size can be decimal or hex with $ or 0x prefix.");

        commandHelp.put("saveauxbin",
                "Saves a block of AUXILIARY memory to a binary file.\nUsage: saveauxbin <filename> <address> <size> (or sab <filename> <address> <size>)\n"
                        +
                        "Reads from auxiliary (AUX) video memory — e.g., the aux DHGR page at $2000.\n"
                        +
                        "Address and size can be decimal or hex with $ or 0x prefix.");

        commandHelp.put("saveauxrambin",
                "Saves a block of general-purpose AUX RAM to a binary file.\nUsage: saveauxrambin <filename> <address> <size> (or sarb <filename> <address> <size>)\n"
                        +
                        "Reads from the full AUX RAM bank (getAuxMemory) — covers all addresses including $6000+ where the PLASMA heap lives.\n"
                        +
                        "Address and size can be decimal or hex with $ or 0x prefix.");

        commandHelp.put("screenshot",
                "Captures the current screen as a PNG using NTSC color rendering.\n"
                        + "Usage: screenshot <filename.png> (or ss2 <filename.png>)\n"
                        + "Reads the live NTSC framebuffer (560x192) rendered by VideoNTSC,\n"
                        + "scales 2x to 1120x384, and writes a PNG.\n"
                        + "Falls back to monochrome DHGR rendering if NTSC framebuffer is unavailable.\n"
                        + "Example: screenshot /tmp/frame.png");

        commandHelp.put("loadbasic",
                "Loads a plain-text Applesoft BASIC listing from a file and injects it into emulator RAM.\n"
                        + "Usage: loadbasic <filepath> (or lbas <filepath>)\n"
                        + "On success: prints \"Loaded N lines (M bytes) from <filepath>\"\n"
                        + "On failure: prints the error and file line number where determinable.\n"
                        + "Example: loadbasic /path/to/program.bas");

        commandHelp.put("speed",
                "Sets emulator speed.\nUsage: speed max|normal (or sp max|normal)\n" +
                "  speed max    - Remove throttle; run as fast as the host CPU allows\n" +
                "  speed normal - Restore 1 MHz throttle");

        commandHelp.put("charlog",
                "Toggles character output logging for the Z-machine character writer at $5DA3.\n" +
                        "Usage: charlog (or cl)\n" +
                        "When enabled, prints each character written by the Z-machine:\n" +
                        "  CHAR: 0xNN 'C'  (execute listener at $5DA3, A register)\n" +
                        "  WRITE $XXXX = 0xNN  (write listener on $0280-$02FF)\n" +
                        "Second call disables logging.");

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
        
        commandHelp.put("rdb",
                "Manages the Aristaeus remote debugger (TCP, port 57867).\n" +
                "Usage: rdb start   — Start listening for an Aristaeus connection\n" +
                "       rdb stop    — Stop the debug server and close all connections\n" +
                "       rdb status  — Show whether the server is running\n" +
                "Aliases: cy, cyrene\n\n" +
                "Once started, connect Aristaeus (https://github.com/badvision/aristaeus) to\n" +
                "localhost:57867 to inspect registers, memory, soft switches, set breakpoints,\n" +
                "and step through 65C02 code.\n\n" +
                "Typical workflow:\n" +
                "  rdb start\n" +
                "  run 999999999   (keep the emulator cycling while Aristaeus is connected)");

        LOG.fine("Commands initialized");
    }

    private void addAlias(String alias, String command) {
        if (commandAliases.containsKey(alias)) {
            throw new IllegalStateException(
                "Alias conflict in MainMode: '" + alias + "' already maps to '" +
                commandAliases.get(alias) + "', cannot also map to '" + command + "'");
        }
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
        // Handle exit commands
        if ("qq".equals(command.trim())) {
            terminal.stop();
            return true;
        }

        // Handle full exit (terminate JVM)
        if ("qqq".equals(command.trim()) || "exit!".equals(command.trim())) {
            terminal.stop();
            output.println("Terminating emulator...");
            System.exit(0);
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

        // Try Wozniak monitor syntax (4000G, 100.200, E000G, etc.) via MonitorMode pattern fallthrough.
        // Exclude MonitorMode's own navigation commands so they don't fire from main mode.
        String trimmed = command.trim();
        if (!trimmed.equalsIgnoreCase("q") && !trimmed.equalsIgnoreCase("back")) {
            MonitorMode mon = getMonitorMode();
            if (mon != null && mon.processCommand(trimmed)) {
                return true;
            }
        }

        LOG.info("Unknown command received: " + cmd);
        return false;
    }

    @Override
    public void printHelp() {
        output.println("Available commands:");
        output.println("  monitor/m       - Enter Monitor mode (includes debugger functionality)");
        output.println("  assembler/a     - Enter Assembler mode");
        output.println("  qq              - Exit terminal loop");
        output.println("  qqq / exit!     - Exit terminal AND terminate emulator");
        output.println();
        output.println("  swlog (sl)     - Toggle softswitch state change logging");
        output.println("  swstate (ss)   - Display current state of all softswitches");
        output.println("  reset (re)     - Reset the Apple II");
        output.println("  tick (tc) [count] - Step the motherboard for count ticks (default: 1)");
        output.println("  run (g) [count] - Run the CPU for count cycles or until breakpoint (default: 1000000)");
        output.println("  go <addr>        - Set PC to addr and begin execution (no mode switch needed)");
        output.println("  mem <start> <end> - Hex dump memory range (no mode switch needed)");
        output.println("  cpu              - Show CPU registers and flags (no mode switch needed)");
        output.println("  registers (reg)  - Show or set CPU registers (no mode switch needed)");
        output.println("  break (bp)       - Manage breakpoints (no mode switch needed)");
        output.println("  runto (rt)       - Run until PC reaches address (no mode switch needed)");
        output.println("  insertdisk (id) d# file [slot] - Insert disk image in drive # (1 or 2)");
        output.println("  ejectdisk (ed) d# [slot] - Eject disk from drive # (1 or 2)");
        output.println("  bootdisk (bd) d# file [slot] - Insert disk and boot until PC >= $2000");
        output.println("  showtext (st)  - Display current text screen contents (40/80 column)");
        output.println("  speed (sp) max|normal - Set emulator speed (max = unthrottled)");
        output.println("  waitkey [timeout] - Wait until system reads keyboard (detects input prompt)");
        output.println("  type <string> - Type string synchronized with keyboard reads");
        output.println("  expect <string> [timeout] - Wait for string to appear on screen");
        output.println("  loadbin (lb) file addr - Load binary file at specified address (hex)");
        output.println("  savebin (sb) file addr size - Save binary data from memory to file");
        output.println("  saveauxbin (sab) file addr size - Save binary data from AUXILIARY memory to file");
        output.println("  screenshot (ss2) file.png - Capture DHGR page 1 as 1120x384 PNG");
        output.println("  loadbasic (lbas) file - Load plain-text Applesoft BASIC listing into RAM");
        output.println("  key (k) value  - Simulate keypresses");
        output.println("  rdb (cy) start|stop|status - Start/stop Aristaeus remote debugger (port 57867)");
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

    private void toggleCharLog(String[] args) {
        charlogEnabled = !charlogEnabled;
        if (charlogEnabled) {
            output.println("charlog enabled");
            // Listener 1: EXECUTE at $5DA3 — reads cpu.A for the character being written
            charlogExecListener = new RAMListener("CharLogExec", RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(0x5DA3);
                    setScopeEnd(0x5DA3);
                }

                @Override
                protected void doEvent(RAMEvent event) {
                    Emulator.withComputer(c -> {
                        MOS65C02 cpu = (MOS65C02) c.getCpu();
                        int ch = cpu.A & 0x7F;
                        String printable = (ch >= 32) ? " '" + (char) ch + "'" : "";
                        output.printf("CHAR: 0x%02X%s%n", ch, printable);
                    });
                }
            };
            // Listener 2: WRITE on $0280-$02FF — backup trap on the screen-hole write range
            charlogWriteListener = new RAMListener("CharLogWrite", RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(0x0280);
                    setScopeEnd(0x02FF);
                }

                @Override
                protected void doEvent(RAMEvent event) {
                    int val = event.getNewValue() & 0x7F;
                    String printable = (val >= 32) ? " '" + (char) val + "'" : "";
                    output.printf("WRITE $%04X = 0x%02X%s%n", event.getAddress(), val, printable);
                }
            };
            Emulator.withMemory(m -> {
                m.addListener(charlogExecListener);
                m.addListener(charlogWriteListener);
            });
        } else {
            output.println("charlog disabled");
            Emulator.withMemory(m -> {
                if (charlogExecListener != null) {
                    m.removeListener(charlogExecListener);
                }
                if (charlogWriteListener != null) {
                    m.removeListener(charlogWriteListener);
                }
            });
            charlogExecListener = null;
            charlogWriteListener = null;
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

                // Calculate run time in milliseconds (Apple II runs at ~1MHz)
                // cycles / 1000 = milliseconds
                long runTimeMs = finalCycleCount / 1000;
                if (runTimeMs < 100) runTimeMs = 100; // Minimum 100ms

                computer.resume();
                long startTime = System.currentTimeMillis();

                // Poll for breakpoint or time elapsed
                while (System.currentTimeMillis() - startTime < runTimeMs) {
                    if (finalBreakpointAddress != null &&
                            computer.getCpu().getProgramCounter() == finalBreakpointAddress) {
                        output.println("Breakpoint hit at $" +
                                Integer.toHexString(finalBreakpointAddress).toUpperCase());
                        break;
                    }

                    try {
                        Thread.sleep(50); // Check every 50ms
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                computer.pause();

                long actualTimeMs = System.currentTimeMillis() - startTime;
                output.println("Ran for approximately " + (actualTimeMs * 1000) + " cycles (" + actualTimeMs + "ms)");

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
            output.println("Usage: insertdisk d<drive_number> <filepath> [slot]");
            output.println("Example: insertdisk d1 /path/to/disk.po");
            output.println("Example: insertdisk d1 /path/to/disk.po 6");
            return;
        }

        String driveSpec = args[0];
        String filename = args[1];
        int slot = args.length > 2 ? Integer.parseInt(args[2]) : 6; // Default to slot 6

        if (!driveSpec.matches("d[12]")) {
            output.println("Invalid drive specification: " + driveSpec + " (must be d1 or d2)");
            return;
        }

        int driveNumber = Integer.parseInt(driveSpec.substring(1));

        LOG.info("Disk insertion requested for slot " + slot + " drive " + driveNumber + ": " + filename);

        try {
            File diskFile = new File(filename);
            if (!diskFile.exists()) {
                output.println("File not found: " + filename);
                return;
            }

            Emulator.withMemory(memory -> {
                var cardOpt = memory.getCard(slot);
                if (cardOpt.isEmpty()) {
                    output.println("No disk controller found in slot " + slot);
                    return;
                }

                // Handle Disk ][ controller
                if (cardOpt.get() instanceof jace.hardware.CardDiskII) {
                    jace.hardware.CardDiskII diskController = (jace.hardware.CardDiskII) cardOpt.get();
                    jace.hardware.DiskIIDrive drive = driveNumber == 1 ? diskController.drive1 : diskController.drive2;

                    try {
                        drive.insertDisk(diskFile);
                        output.println("Inserted " + diskFile.getName() + " into slot " + slot + " drive " + driveNumber);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Error inserting disk", e);
                        output.println("Error inserting disk: " + e.getMessage());
                    }
                    return;
                }

                // Handle Mass Storage controller
                if (cardOpt.get() instanceof jace.hardware.massStorage.CardMassStorage) {
                    jace.hardware.massStorage.CardMassStorage massStorage =
                        (jace.hardware.massStorage.CardMassStorage) cardOpt.get();
                    jace.hardware.massStorage.MassStorageDrive drive =
                        driveNumber == 1 ? massStorage.drive1 : massStorage.drive2;

                    try {
                        // Create a MediaFile and MediaEntry to insert
                        jace.library.MediaEntry.MediaFile mediaFile = new jace.library.MediaEntry.MediaFile();
                        mediaFile.path = diskFile;
                        jace.library.MediaEntry mediaEntry = new jace.library.MediaEntry();
                        drive.insertMedia(mediaEntry, mediaFile);
                        output.println("Inserted " + diskFile.getName() + " into slot " + slot + " drive " + driveNumber);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Error inserting disk", e);
                        output.println("Error inserting disk: " + e.getMessage());
                    }
                    return;
                }

                output.println("Card in slot " + slot + " is not a supported disk controller");
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error during disk insertion", e);
            output.println("Error inserting disk: " + e.getMessage());
        }
    }

    private void ejectDisk(String[] args) {
        if (args.length < 1) {
            output.println("Usage: ejectdisk d<drive_number> [slot]");
            output.println("Example: ejectdisk d1");
            output.println("Example: ejectdisk d2 6");
            return;
        }

        String driveSpec = args[0];
        int slot = args.length > 1 ? Integer.parseInt(args[1]) : 6; // Default to slot 6

        if (!driveSpec.matches("d[12]")) {
            output.println("Invalid drive specification: " + driveSpec + " (must be d1 or d2)");
            return;
        }

        int driveNumber = Integer.parseInt(driveSpec.substring(1));

        LOG.info("Disk ejection requested for slot " + slot + " drive " + driveNumber);

        try {
            Emulator.withMemory(memory -> {
                var cardOpt = memory.getCard(slot);
                if (cardOpt.isEmpty()) {
                    output.println("No disk controller found in slot " + slot);
                    return;
                }

                if (!(cardOpt.get() instanceof jace.hardware.CardDiskII)) {
                    output.println("Card in slot " + slot + " is not a Disk ][ controller");
                    return;
                }

                jace.hardware.CardDiskII diskController = (jace.hardware.CardDiskII) cardOpt.get();
                jace.hardware.DiskIIDrive drive = driveNumber == 1 ? diskController.drive1 : diskController.drive2;

                drive.eject();
                output.println("Ejected disk from slot " + slot + " drive " + driveNumber);
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error during disk ejection", e);
            output.println("Error ejecting disk: " + e.getMessage());
        }
    }

    private void bootDisk(String[] args) {
        if (args.length < 2) {
            output.println("Usage: bootdisk d<drive_number> <filepath> [slot]");
            output.println("Example: bootdisk d1 /path/to/disk.po");
            return;
        }

        // First insert the disk
        insertDisk(args);

        // Reset the system
        output.println("Resetting system...");
        performReset();

        // Run until PC >= $2000
        output.println("Booting disk (running until PC >= $2000)...");
        try {
            final int TARGET_PC = 0x2000;
            final int MAX_CYCLES = 10_000_000; // 10 million cycles max

            Emulator.withComputer((computer) -> {
                int currentCycles = 0;
                computer.resume();

                // Poll for PC >= $2000
                while (currentCycles < MAX_CYCLES) {
                    int pc = computer.getCpu().getProgramCounter();
                    if (pc >= TARGET_PC) {
                        output.println("Boot complete - PC reached $" +
                                Integer.toHexString(pc).toUpperCase());
                        break;
                    }

                    currentCycles += 1000;

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                computer.pause();

                if (currentCycles >= MAX_CYCLES) {
                    output.println("Warning: Maximum cycles reached before PC >= $2000");
                }

                // Show CPU state
                MOS65C02 cpu = (MOS65C02) computer.getCpu();
                showCPUState(cpu);
            });
        } catch (Exception e) {
            output.println("Error during boot: " + e.getMessage());
        }
    }

    private String captureTextScreen() {
        StringBuilder screenText = new StringBuilder();

        try {
            Emulator.withMemory(memory -> {
                // Check if we're in 80-column mode
                boolean col80 = SoftSwitches._80COL.isOn();

                // Apple II text screen memory layout (interleaved rows)
                int[] rowAddresses = {
                    0x0400, 0x0480, 0x0500, 0x0580, 0x0600, 0x0680, 0x0700, 0x0780,
                    0x0428, 0x04A8, 0x0528, 0x05A8, 0x0628, 0x06A8, 0x0728, 0x07A8,
                    0x0450, 0x04D0, 0x0550, 0x05D0, 0x0650, 0x06D0, 0x0750, 0x07D0
                };

                for (int row = 0; row < 24; row++) {
                    StringBuilder line = new StringBuilder();
                    int baseAddr = rowAddresses[row];

                    if (col80) {
                        if (memory instanceof jace.apple2e.RAM128k) {
                            jace.apple2e.RAM128k ram128k = (jace.apple2e.RAM128k) memory;
                            jace.core.PagedMemory auxMem = ram128k.getAuxMemory();
                            jace.core.PagedMemory mainMem = ram128k.getMainMemory();

                            for (int col = 0; col < 40; col++) {
                                int addr = baseAddr + col;
                                byte auxByte = auxMem.getMemoryPage(addr)[addr & 0xFF];
                                char auxChar = convertAppleTextToAscii(auxByte);
                                line.append(auxChar);
                                byte mainByte = mainMem.getMemoryPage(addr)[addr & 0xFF];
                                char mainChar = convertAppleTextToAscii(mainByte);
                                line.append(mainChar);
                            }
                        }
                    } else {
                        for (int col = 0; col < 40; col++) {
                            int addr = baseAddr + col;
                            byte b = memory.read(addr, RAMEvent.TYPE.READ_DATA, true, false);
                            char c = convertAppleTextToAscii(b);
                            line.append(c);
                        }
                    }

                    screenText.append(line.toString()).append("\n");
                }
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error capturing text screen", e);
        }

        return screenText.toString();
    }

    private void showTextScreen(String[] args) {
        try {
            Emulator.withMemory(memory -> {
                boolean col80 = SoftSwitches._80COL.isOn();
                int columns = col80 ? 80 : 40;
                output.println("=== Text Screen (" + columns + " columns) ===");
            });

            String screenText = captureTextScreen();
            output.print(screenText);
            output.println("=== End of Text Screen ===");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error reading text screen", e);
            output.println("Error reading text screen: " + e.getMessage());
        }
    }

    /**
     * Convert Apple II text screen byte to ASCII character
     * Apple II uses high-bit ASCII with special character sets
     */
    private void disableHints() {
        try {
            Emulator.withComputer(computer -> {
                if (computer instanceof jace.apple2e.Apple2e) {
                    jace.apple2e.Apple2e apple = (jace.apple2e.Apple2e) computer;
                    apple.enableHints = false;
                    apple.reconfigure();
                    output.println("Helpful hints disabled");
                } else {
                    output.println("This command is only supported on Apple //e");
                }
            });
        } catch (Exception e) {
            output.println("Error disabling hints: " + e.getMessage());
        }
    }

    private void setSpeed(String[] args) {
        if (args.length < 1) {
            output.println("Usage: speed max|normal");
            return;
        }
        String mode = args[0].toLowerCase();
        boolean maxSpeed;
        if ("max".equals(mode)) {
            maxSpeed = true;
        } else if ("normal".equals(mode)) {
            maxSpeed = false;
        } else {
            output.println("Unknown speed mode: " + args[0] + " (use max or normal)");
            return;
        }
        final boolean enable = maxSpeed;
        Emulator.withComputer(computer -> {
            computer.getMotherboard().setMaxSpeed(enable);
            output.println("Speed set to " + (enable ? "max (unthrottled)" : "normal (1 MHz)"));
        });
    }

    private void waitForKeypress(String[] args) {
        int maxWaitMs = args.length > 0 ? Integer.parseInt(args[0]) : 30000; // Default 30 seconds
        waitForKeyRead(maxWaitMs, true);
    }

    private boolean waitForKeyRead(int maxWaitMs, boolean printOutput) {
        try {
            final boolean[] keyReadDetected = new boolean[1];
            final RAMListener[] listenerHolder = new RAMListener[1];

            // Create a listener for keyboard reads at $C000
            RAMListener keyListener = new RAMListener("WaitForKey", RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(0xC000);
                }

                @Override
                protected void doEvent(RAMEvent event) {
                    synchronized (keyReadDetected) {
                        keyReadDetected[0] = true;
                        keyReadDetected.notify();
                    }
                }
            };

            listenerHolder[0] = keyListener;

            Emulator.withMemory(memory -> {
                memory.addListener(keyListener);
            });

            if (printOutput) {
                output.println("Waiting for keyboard read (max " + maxWaitMs + "ms)...");
            }

            Emulator.withComputer(computer -> {
                computer.resume();

                synchronized (keyReadDetected) {
                    try {
                        keyReadDetected.wait(maxWaitMs);
                    } catch (InterruptedException e) {
                        // Interrupted, continue
                    }
                }

                computer.pause();
            });

            Emulator.withMemory(memory -> {
                memory.removeListener(keyListener);
            });

            if (printOutput) {
                if (keyReadDetected[0]) {
                    output.println("Keyboard read detected");
                } else {
                    output.println("Timeout waiting for keyboard read");
                }
            }

            return keyReadDetected[0];

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error waiting for keypress", e);
            if (printOutput) {
                output.println("Error waiting for keypress: " + e.getMessage());
            }
            return false;
        }
    }

    private void expectString(String[] args) {
        if (args.length < 1) {
            output.println("Usage: expect <string> [timeout_seconds]");
            return;
        }

        // Get the string to search for
        String searchString = String.join(" ", args);
        int timeoutSeconds = 30; // Default timeout

        // Check if last arg is a number (timeout)
        try {
            if (args.length > 1) {
                int lastArgTimeout = Integer.parseInt(args[args.length - 1]);
                timeoutSeconds = lastArgTimeout;
                // Rebuild search string without the timeout
                searchString = String.join(" ", java.util.Arrays.copyOf(args, args.length - 1));
            }
        } catch (NumberFormatException e) {
            // Last arg wasn't a number, use full string
        }

        // Remove surrounding quotes if present
        if (searchString.startsWith("\"") && searchString.endsWith("\"") && searchString.length() > 1) {
            searchString = searchString.substring(1, searchString.length() - 1);
        }

        output.println("Expecting: \"" + searchString + "\" (timeout: " + timeoutSeconds + "s)");

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        final String finalSearchString = searchString;

        try {
            final boolean[] matchFound = new boolean[1];

            Emulator.withComputer(computer -> {
                computer.resume();

                // Poll the text screen every 100ms while the emulator runs at full speed.
                // This avoids the old 500ms-burst-then-sleep throttle that caused cold-JVM
                // timeouts (the JIT hadn't warmed up so each 500ms burst advanced only a
                // tiny number of real Apple II cycles).
                long deadline = startTime + timeoutMs;
                while (System.currentTimeMillis() < deadline) {
                    String screenText = captureTextScreen();
                    if (screenText.contains(finalSearchString)) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        output.println("Match found after " + elapsed + "ms");
                        matchFound[0] = true;
                        break;
                    }
                    try {
                        Thread.sleep(100); // Check screen every 100ms; emulator runs freely
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                computer.pause();
            });

            if (!matchFound[0]) {
                output.println("Timeout waiting for: \"" + searchString + "\"");
            }

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error during expect", e);
            output.println("Error during expect: " + e.getMessage());
        }
    }

    private void typeString(String[] args) {
        if (args.length < 1) {
            output.println("Usage: type <string> [timeout_seconds]");
            return;
        }

        // Join all args first, then look for trailing ,N or , N timeout suffix
        String text = String.join(" ", args);

        // Parse optional timeout: string must end with ,\s*\d{1,2}
        int timeoutSeconds = 0; // 0 = no timeout (original per-char behavior)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(",\\s*(\\d{1,2})\\s*$").matcher(text);
        if (m.find()) {
            timeoutSeconds = Integer.parseInt(m.group(1));
            text = text.substring(0, m.start());
        }

        // Remove surrounding quotes if present
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
            text = text.substring(1, text.length() - 1);
        }

        // Process escape sequences
        text = text.replace("\\n", "\r"); // Convert \n to carriage return
        text = text.replace("\\t", "\t");

        output.println("Typing string: \"" + text + "\"" + (timeoutSeconds > 0 ? " (timeout: " + timeoutSeconds + "s)" : ""));

        if (timeoutSeconds > 0) {
            // Timeout mode: resume once, type all chars, re-pause at the end if we resumed
            final String finalText = text;
            final int finalTimeout = timeoutSeconds;
            final boolean[] wasRunning = {false};

            Emulator.withComputer(computer -> {
                wasRunning[0] = !computer.getMotherboard().isPaused();
                if (!wasRunning[0]) {
                    computer.resume();
                }
            });

            long deadline = System.currentTimeMillis() + finalTimeout * 1000L;

            for (int i = 0; i < finalText.length(); i++) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    output.println("Timeout reached at character " + i);
                    break;
                }

                final byte keyCode = (byte)(finalText.charAt(i) & 0xFF);

                // Wait for keyboard read multiple times to skip ROM routines
                for (int j = 0; j < 3; j++) {
                    long rem = deadline - System.currentTimeMillis();
                    if (rem <= 0 || !waitForKeyReadWithoutResume((int)Math.min(rem, 5000), false)) {
                        if (deadline - System.currentTimeMillis() <= 0) {
                            output.println("Timeout at character " + i);
                        } else {
                            output.println("Timeout waiting for keyboard read at character " + i + " (wait " + j + ")");
                        }
                        break;
                    }
                }

                Emulator.withComputer(computer -> {
                    Keyboard.pressKey(keyCode);
                });

                long rem = deadline - System.currentTimeMillis();
                if (rem <= 0 || !waitForKeyReadWithoutResume((int)Math.min(rem, 5000), false)) {
                    if (deadline - System.currentTimeMillis() <= 0) {
                        output.println("Timeout after key " + i);
                    } else {
                        output.println("Timeout waiting for key to be read at character " + i);
                    }
                    break;
                }
            }

            // Re-pause if we resumed
            if (!wasRunning[0]) {
                Emulator.withComputer(computer -> computer.pause());
            }

        } else {
            // Original per-char behavior: each waitForKeyRead does its own resume/pause
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                byte keyCode = (byte)(c & 0xFF);

                // Wait for keyboard read multiple times to skip ROM routines that ignore keypresses
                for (int j = 0; j < 3; j++) {
                    if (!waitForKeyRead(5000, false)) {
                        output.println("Timeout waiting for keyboard read at character " + i + " (wait " + j + ")");
                        return;
                    }
                }

                // Press the key
                Emulator.withComputer(computer -> {
                    Keyboard.pressKey(keyCode);
                });

                // Wait for the key to be read
                if (!waitForKeyRead(5000, false)) {
                    output.println("Timeout waiting for key to be read at character " + i);
                    return;
                }
            }
        }

        output.println("Typing complete");
    }

    /**
     * Waits for a keyboard read at $C000 without managing the emulator resume/pause state.
     * The emulator must already be running when this is called.
     */
    private boolean waitForKeyReadWithoutResume(int maxWaitMs, boolean printOutput) {
        try {
            final boolean[] keyReadDetected = new boolean[1];

            RAMListener keyListener = new RAMListener("WaitForKey", RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(0xC000);
                }

                @Override
                protected void doEvent(RAMEvent event) {
                    synchronized (keyReadDetected) {
                        keyReadDetected[0] = true;
                        keyReadDetected.notify();
                    }
                }
            };

            Emulator.withMemory(memory -> memory.addListener(keyListener));

            synchronized (keyReadDetected) {
                if (!keyReadDetected[0]) {
                    try {
                        keyReadDetected.wait(maxWaitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            Emulator.withMemory(memory -> memory.removeListener(keyListener));

            return keyReadDetected[0];

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error waiting for keypress", e);
            return false;
        }
    }

    private char convertAppleTextToAscii(byte b) {
        int value = b & 0xFF;

        // Strip high bit and convert to printable ASCII
        int asciiValue = value & 0x7F;

        // Handle special characters
        if (asciiValue < 32) {
            // Control characters - display as spaces or special symbols
            return ' ';
        } else if (asciiValue == 127) {
            // Delete character
            return ' ';
        }

        // Check display mode based on high bit
        if ((value & 0x80) != 0) {
            // Normal display (high bit set)
            return (char) asciiValue;
        } else if ((value & 0x40) != 0) {
            // Flashing (bit 6 set, bit 7 clear) - just display as normal
            return (char) asciiValue;
        } else {
            // Inverse (bits 6 and 7 clear) - display in brackets for visibility
            // or just return the character
            return (char) asciiValue;
        }
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

    private void loadBasic(String[] args) {
        if (args.length < 1) {
            output.println("Usage: loadbasic <filepath>");
            return;
        }

        // Join all args to support paths with spaces
        String filepath = String.join(" ", args);

        java.nio.file.Path filePath = java.nio.file.Paths.get(filepath);
        if (!java.nio.file.Files.exists(filePath)) {
            output.println("File not found: " + filepath);
            return;
        }

        java.util.List<String> fileLines;
        try {
            fileLines = java.nio.file.Files.readAllLines(filePath);
        } catch (java.io.IOException e) {
            output.println("Error reading file: " + e.getMessage());
            return;
        }

        // Validate each non-blank, non-comment line starts with a BASIC line number
        for (int i = 0; i < fileLines.size(); i++) {
            String line = fileLines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            if (ApplesoftProgram.isCommentLine(line)) {
                continue;
            }
            String trimmed = line.trim();
            if (!Character.isDigit(trimmed.charAt(0))) {
                output.println("Error at file line " + (i + 1) + ": expected BASIC line number, got: " + trimmed);
                return;
            }
        }

        ApplesoftProgram program;
        try {
            StringBuilder sb = new StringBuilder();
            for (String line : fileLines) {
                sb.append(line).append("\n");
            }
            program = ApplesoftProgram.fromString(sb.toString());
        } catch (Exception e) {
            output.println("Error tokenizing BASIC program: " + e.getMessage());
            return;
        }

        if (program.getLength() == 0) {
            output.println("No BASIC lines found in file: " + filepath);
            return;
        }

        try {
            program.run();
        } catch (Exception e) {
            output.println("Error injecting program into RAM: " + e.getMessage());
            return;
        }

        int byteCount = program.getProgramSize();
        output.println("Loaded " + program.getLength() + " lines (" + byteCount + " bytes) from " + filepath);
        LOG.info("Loaded BASIC program: " + program.getLength() + " lines from " + filepath);
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

    private void saveAuxBinary(String[] args) {
        if (args.length < 3) {
            output.println("Usage: saveauxbin <filename> <address> <size>");
            return;
        }

        String filename = args[0];
        int address, size;

        try {
            address = parseHexAddress(args[1]);
            size = parseHexAddress(args[2]);
        } catch (NumberFormatException e) {
            output.println("Invalid address or size");
            return;
        }

        try {
            if (size <= 0) {
                output.println("Size must be positive");
                return;
            }
            if (address + size > 0x10000) {
                output.println("Memory range would exceed address space");
                return;
            }

            byte[] dataToSave = new byte[size];
            boolean[] success = {false};

            Emulator.withMemory(memory -> {
                if (memory instanceof jace.apple2e.RAM128k) {
                    jace.apple2e.RAM128k ram128k = (jace.apple2e.RAM128k) memory;
                    jace.core.PagedMemory auxMem = ram128k.getAuxVideoMemory();
                    for (int i = 0; i < size; i++) {
                        int addr = (address + i) & 0xFFFF;
                        dataToSave[i] = auxMem.readByte(addr);
                    }
                    success[0] = true;
                }
            });

            if (!success[0]) {
                output.println("Error: RAM128k aux memory not available");
                return;
            }

            java.nio.file.Files.write(java.nio.file.Paths.get(filename), dataToSave);
            output.println("Saved " + size + " aux bytes from $" + Integer.toHexString(address).toUpperCase()
                    + " to " + filename);

        } catch (java.io.IOException e) {
            output.println("Error writing file: " + e.getMessage());
        } catch (Exception e) {
            output.println("Error saving aux binary: " + e.getMessage());
        }
    }

    private void saveAuxRamBinary(String[] args) {
        if (args.length < 3) {
            output.println("Usage: saveauxrambin <filename> <address> <size>");
            return;
        }

        String filename = args[0];
        int address, size;

        try {
            address = parseHexAddress(args[1]);
            size = parseHexAddress(args[2]);
        } catch (NumberFormatException e) {
            output.println("Invalid address or size");
            return;
        }

        try {
            if (size <= 0) {
                output.println("Size must be positive");
                return;
            }
            if (address + size > 0x10000) {
                output.println("Memory range would exceed address space");
                return;
            }

            byte[] dataToSave = new byte[size];
            boolean[] success = {false};

            Emulator.withMemory(memory -> {
                if (memory instanceof jace.apple2e.RAM128k) {
                    jace.apple2e.RAM128k ram128k = (jace.apple2e.RAM128k) memory;
                    jace.core.PagedMemory auxMem = ram128k.getAuxMemory();
                    for (int i = 0; i < size; i++) {
                        int addr = (address + i) & 0xFFFF;
                        dataToSave[i] = auxMem.readByte(addr);
                    }
                    success[0] = true;
                }
            });

            if (!success[0]) {
                output.println("Error: RAM128k aux memory not available");
                return;
            }

            java.nio.file.Files.write(java.nio.file.Paths.get(filename), dataToSave);
            output.println("Saved " + size + " aux RAM bytes from $" + Integer.toHexString(address).toUpperCase()
                    + " to " + filename);

        } catch (java.io.IOException e) {
            output.println("Error writing file: " + e.getMessage());
        } catch (Exception e) {
            output.println("Error saving aux RAM binary: " + e.getMessage());
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
    
    private void takeScreenshot(String[] args) {
        if (args.length < 1) {
            output.println("Usage: screenshot <filename.png>");
            return;
        }

        String filename = args[0];

        final int WIDTH  = 560;
        final int HEIGHT = 192;
        final int SCALE  = 2;

        // Attempt NTSC color capture from the live VideoNTSC render buffer.
        // The render buffer is a JavaFX WritableImage (560x192) that VideoNTSC
        // writes to during every CPU tick using the full NTSC composite color
        // pipeline. We prefer getRenderBuffer() (live pixels) over
        // getFrameBuffer() (vblank-synced copy) so the screenshot reflects the
        // most recently rendered frame even when the emulator is paused.
        Image[] frameBuffer = {null};
        Emulator.withVideo(v -> {
            frameBuffer[0] = v.getRenderBuffer();
            if (frameBuffer[0] == null) {
                frameBuffer[0] = v.getFrameBuffer();
            }
        });

        if (frameBuffer[0] != null) {
            // Color path: copy pixels from the NTSC framebuffer, scale 2x.
            BufferedImage img = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_RGB);
            PixelReader reader = frameBuffer[0].getPixelReader();
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    // getArgb returns 0xAARRGGBB; strip alpha for TYPE_INT_RGB
                    int argb = reader.getArgb(x, y);
                    int rgb  = argb & 0x00FFFFFF;
                    for (int sy = 0; sy < SCALE; sy++) {
                        for (int sx = 0; sx < SCALE; sx++) {
                            img.setRGB(x * SCALE + sx, y * SCALE + sy, rgb);
                        }
                    }
                }
            }
            try {
                ImageIO.write(img, "PNG", new File(filename));
                output.println("Screenshot saved to " + filename + " (" + (WIDTH * SCALE) + "x" + (HEIGHT * SCALE) + ") [NTSC color]");
            } catch (IOException e) {
                output.println("Error writing screenshot: " + e.getMessage());
            }
            return;
        }

        // Fallback: monochrome DHGR rendering from raw video memory.
        // Used when the NTSC framebuffer is unavailable (e.g. video disabled).
        final int PAGE_START = 0x2000;
        final int PAGE_SIZE  = 0x2000;

        byte[] mainMem = new byte[PAGE_SIZE];
        byte[] auxMem  = new byte[PAGE_SIZE];
        boolean[] success = {false};

        Emulator.withMemory(memory -> {
            if (!(memory instanceof jace.apple2e.RAM128k)) {
                return;
            }
            jace.apple2e.RAM128k ram128k = (jace.apple2e.RAM128k) memory;
            jace.core.PagedMemory auxVideo = ram128k.getAuxVideoMemory();

            for (int i = 0; i < PAGE_SIZE; i++) {
                int addr = (PAGE_START + i) & 0xFFFF;
                mainMem[i] = memory.read(addr, RAMEvent.TYPE.READ_DATA, true, false);
            }
            for (int i = 0; i < PAGE_SIZE; i++) {
                int addr = (PAGE_START + i) & 0xFFFF;
                auxMem[i] = auxVideo.readByte(addr);
            }
            success[0] = true;
        });

        if (!success[0]) {
            output.println("Error: RAM128k memory not available for screenshot");
            return;
        }

        BufferedImage img = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < HEIGHT; y++) {
            int rowAddr = ((y & 7) << 10) + (((y >> 3) & 7) * 0x80) + ((y >> 6) * 0x28);
            int px = 0;
            for (int xoff = 0; xoff < 40; xoff++) {
                int auxByte  = auxMem [rowAddr + xoff] & 0x7F;
                int mainByte = mainMem[rowAddr + xoff] & 0x7F;
                for (int bit = 0; bit < 7; bit++) {
                    int color = ((auxByte >> bit) & 1) != 0 ? 0xFFFFFF : 0x000000;
                    for (int sy = 0; sy < SCALE; sy++) {
                        for (int sx = 0; sx < SCALE; sx++) {
                            img.setRGB(px * SCALE + sx, y * SCALE + sy, color);
                        }
                    }
                    px++;
                }
                for (int bit = 0; bit < 7; bit++) {
                    int color = ((mainByte >> bit) & 1) != 0 ? 0xFFFFFF : 0x000000;
                    for (int sy = 0; sy < SCALE; sy++) {
                        for (int sx = 0; sx < SCALE; sx++) {
                            img.setRGB(px * SCALE + sx, y * SCALE + sy, color);
                        }
                    }
                    px++;
                }
            }
        }

        try {
            ImageIO.write(img, "PNG", new File(filename));
            output.println("Screenshot saved to " + filename + " (" + (WIDTH * SCALE) + "x" + (HEIGHT * SCALE) + ") [monochrome fallback]");
        } catch (IOException e) {
            output.println("Error writing screenshot: " + e.getMessage());
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

    private MonitorMode getMonitorMode() {
        TerminalMode mode = terminal.getModeByName("monitor");
        if (mode instanceof MonitorMode) {
            return (MonitorMode) mode;
        }
        output.println("Monitor mode not available");
        return null;
    }

    private void cyreneCommand(String[] args) {
        CyreneIPCServer server = CyreneIPCServer.getInstance();
        if (args.length == 0) {
            output.println("Usage: rdb <start|stop|status>");
            output.println("  rdb start   — Start the Aristaeus remote debug server");
            output.println("  rdb stop    — Stop the server");
            output.println("  rdb status  — Show server status");
            output.println("Aliases: cy, cyrene");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "start":
                server.enabled = true;
                server.start();
                output.println("Cyrene IPC server started on port " + server.port);
                break;
            case "stop":
                server.stop();
                server.enabled = false;
                output.println("Cyrene IPC server stopped");
                break;
            case "status":
                output.println("Cyrene IPC server:");
                output.println("  Enabled: " + server.enabled);
                output.println("  Port:    " + server.port);
                output.println("  Active:  " + server.isActive());
                if (server.isActive()) {
                    output.println("  Session: connected");
                } else {
                    output.println("  Session: none");
                }
                break;
            default:
                output.println("Unknown rdb sub-command: " + args[0]);
                output.println("Usage: rdb <start|stop|status>");
                break;
        }
    }
}