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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.core.RAM;
import jace.core.RAMEvent;

/**
 * Monitor mode for the Terminal - emulates the Apple II monitor
 */
public class MonitorMode implements TerminalMode {
    private final JaceTerminal terminal;
    private final PrintStream output;
    private int lastExaminedAddress = 0;
    private int lastDisassemblyAddress = 0;
    private boolean useMainMemory = true;
    private boolean useAuxMemory = false;
    
    // Regex patterns for monitor commands
    private static final Pattern EXAMINE_PATTERN = Pattern.compile("^([Mm]|[Xx])?([0-9A-Fa-f]{1,4})$");
    private static final Pattern POKE_PATTERN = Pattern.compile("^([Mm]|[Xx])?([0-9A-Fa-f]{1,4}):([0-9A-Fa-f\\s]+)$");
    private static final Pattern GO_PATTERN = Pattern.compile("^([0-9A-Fa-f]{1,4})[Gg]$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^([0-9A-Fa-f]{1,4})[Ll]$");
    private static final Pattern SINGLE_LIST_PATTERN = Pattern.compile("^[Ll]$");
    
    private final Map<String, Consumer<String[]>> commands = new HashMap<>();
    private final Map<String, String> commandAliases = new HashMap<>();
    private final Map<String, String> commandHelp = new HashMap<>();
    
    // Default number of instructions to disassemble
    private static final int DEFAULT_DISASM_COUNT = 20;
    
    public MonitorMode(JaceTerminal terminal) {
        this.terminal = terminal;
        this.output = terminal.getOutput();
        initCommands();
    }
    
    private void initCommands() {
        // Define commands with their implementations
        commands.put("examine", this::examineMemory);
        commands.put("deposit", this::depositMemory);
        commands.put("fill", this::fillMemory);
        commands.put("move", this::moveMemory);
        commands.put("compare", this::compareMemory);
        commands.put("search", this::searchMemory);
        commands.put("disasm", this::disassembleMemory);
        commands.put("back", args -> terminal.setMode("main"));
        
        // Add single-letter aliases
        addAlias("e", "examine");
        addAlias("d", "deposit");
        addAlias("f", "fill");
        addAlias("m", "move");
        addAlias("c", "compare");
        addAlias("s", "search");
        addAlias("l", "disasm");  // 'l' for 'list'
        addAlias("b", "back");
        
        // Command-specific help
        commandHelp.put("examine", "Displays memory contents at the specified address.\n" +
                "Usage: examine addr [count] (or e addr [count])\n" +
                "  addr  - Memory address in hex\n" +
                "  count - Number of bytes to display (default: 16)\n" +
                "Examples:\n" +
                "  examine 2000    - Show 16 bytes starting at $2000\n" +
                "  e C000 32       - Show 32 bytes starting at $C000");
        
        commandHelp.put("deposit", "Writes values to memory at the specified address.\n" +
                "Usage: deposit addr value [value2...] (or d addr value [value2...])\n" +
                "  addr  - Memory address in hex\n" +
                "  value - Byte value(s) in hex\n" +
                "Examples:\n" +
                "  deposit 300 A9 FF 85 06   - Write bytes A9, FF, 85, 06 starting at $300\n" +
                "  d 2000 00                 - Write byte 00 at $2000");
        
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
        
        commandHelp.put("search", "Searches for a sequence of bytes in memory.\n" +
                "Usage: search start end value [value2...] (or s start end value [value2...])\n" +
                "  start - Starting address in hex\n" +
                "  end   - Ending address in hex\n" +
                "  value - Byte value(s) in hex to search for\n" +
                "Examples:\n" +
                "  search 800 8FF A9 FF    - Search for A9 FF from $800 to $8FF\n" +
                "  s 0 FFFF 20 00 BF       - Search for 20 00 BF in entire memory");
        
        commandHelp.put("disasm", "Disassembles memory starting at the specified address.\n" +
                "Usage: disasm addr [count] (or l addr [count])\n" +
                "  addr  - Starting address in hex\n" +
                "  count - Number of instructions to disassemble (default: " + DEFAULT_DISASM_COUNT + ")\n" +
                "Traditional Apple II syntax also supported:\n" +
                "  XXXXL - Disassemble " + DEFAULT_DISASM_COUNT + " instructions starting at XXXX\n" +
                "  L     - Continue disassembly from where last disassembly left off\n" + 
                "Examples:\n" +
                "  disasm 300     - Disassemble 20 instructions starting at $300\n" +
                "  300L           - Disassemble 20 instructions starting at $300 (Apple II style)\n" +
                "  L              - Continue disassembly from where last left off");
        
        commandHelp.put("back", "Returns to main mode.\nUsage: back (or b)");
    }
    
    private void addAlias(String alias, String command) {
        commandAliases.put(alias, command);
    }
    
    @Override
    public String getName() {
        return "Monitor";
    }
    
    @Override
    public String getPrompt() {
        return "MONITOR> ";
    }
    
    @Override
    public boolean processCommand(String command) {
        // Special case for the single L command to continue disassembly
        if (SINGLE_LIST_PATTERN.matcher(command).matches()) {
            // Continue disassembly from last address
            disassembleCode(lastDisassemblyAddress, DEFAULT_DISASM_COUNT);
            return true;
        }
        
        String[] parts = command.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
        
        // Check if it's an alias and resolve to the actual command
        if (commandAliases.containsKey(cmd)) {
            cmd = commandAliases.get(cmd);
        }
        
        // Check if it's a standard command
        Consumer<String[]> handler = commands.get(cmd);
        if (handler != null) {
            handler.accept(args);
            return true;
        }
        
        // Check for traditional monitor syntax patterns
        if (EXAMINE_PATTERN.matcher(command).matches()) {
            Matcher m = EXAMINE_PATTERN.matcher(command);
            if (m.find()) {
                String bankSpec = m.group(1);
                String addrStr = m.group(2);
                int addr = Integer.parseInt(addrStr, 16);
                
                if (bankSpec != null) {
                    useMainMemory = bankSpec.equalsIgnoreCase("M");
                    useAuxMemory = bankSpec.equalsIgnoreCase("X");
                }
                
                hexDump(addr, 16);
                return true;
            }
        } else if (POKE_PATTERN.matcher(command).matches()) {
            Matcher m = POKE_PATTERN.matcher(command);
            if (m.find()) {
                String bankSpec = m.group(1);
                String addrStr = m.group(2);
                String valuesStr = m.group(3);
                
                int addr = Integer.parseInt(addrStr, 16);
                String[] valueTokens = valuesStr.trim().split("\\s+");
                
                if (bankSpec != null) {
                    useMainMemory = bankSpec.equalsIgnoreCase("M");
                    useAuxMemory = bankSpec.equalsIgnoreCase("X");
                }
                
                for (String token : valueTokens) {
                    byte value = (byte) Integer.parseInt(token, 16);
                    writeMemory(addr++, value);
                }
                return true;
            }
        } else if (GO_PATTERN.matcher(command).matches()) {
            Matcher m = GO_PATTERN.matcher(command);
            if (m.find()) {
                String addrStr = m.group(1);
                int addr = Integer.parseInt(addrStr, 16);
                // Execute code at address
                Emulator.withComputer(c -> c.getCpu().setProgramCounter(addr));
                output.println("Execution started at $" + Integer.toHexString(addr).toUpperCase());
                return true;
            }
        } else if (LIST_PATTERN.matcher(command).matches()) {
            Matcher m = LIST_PATTERN.matcher(command);
            if (m.find()) {
                String addrStr = m.group(1);
                int addr = Integer.parseInt(addrStr, 16);
                // Disassemble code at address
                disassembleCode(addr, DEFAULT_DISASM_COUNT);
                return true;
            }
        }
        
        output.println("Unknown command: " + command);
        return false;
    }
    
    @Override
    public void printHelp() {
        output.println("Apple II Monitor Mode");
        output.println("This mode emulates the Apple II monitor, allowing you to examine and modify memory.");
        output.println("");
        output.println("Commands:");
        output.println("  examine addr [count]     - Examine memory (or e addr [count])");
        output.println("  deposit addr val [val2]  - Deposit values in memory (or d addr val [val2])");
        output.println("  fill start end val       - Fill memory with a value (or f start end val)");
        output.println("  move src dest count      - Move a block of memory (or m src dest count)");
        output.println("  compare src dest count   - Compare memory blocks (or c src dest count)");
        output.println("  search start end val     - Search for bytes (or s start end val)");
        output.println("  disasm addr [count]      - Disassemble code (or l addr [count])");
        output.println("  back                     - Return to main mode (or b)");
        output.println("");
        output.println("Apple II style syntax is also supported:");
        output.println("  XXXX                     - Examine 16 bytes at address XXXX");
        output.println("  XXXX:YY ZZ...            - Store bytes YY, ZZ, etc. starting at XXXX");
        output.println("  XXXXG                    - Execute code at XXXX");
        output.println("  XXXXL                    - Disassemble code at XXXX");
        output.println("  L                        - Continue disassembly from last location");
        output.println("");
        output.println("Use help <command> for detailed help on a specific command.");
        output.println("  exit/quit                  - Exit the Terminal");
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
    
    private void examineMemory(String[] args) {
        if (args.length < 1) {
            output.println("Usage: examine addr [count]");
            return;
        }
        
        try {
            int address = parseAddress(args[0]);
            int count = args.length > 1 ? parseCount(args[1]) : 16; // Default to 16 bytes
            
            hexDump(address, count);
        } catch (NumberFormatException e) {
            output.println("Invalid address or count format");
        }
    }
    
    private void depositMemory(String[] args) {
        if (args.length < 2) {
            output.println("Usage: deposit addr value [value2...]");
            return;
        }
        
        try {
            int address = parseAddress(args[0]);
            
            for (int i = 1; i < args.length; i++) {
                byte value = (byte) parseByteValue(args[i]);
                writeMemory(address++, value);
            }
            
            // Show the result
            hexDump(parseAddress(args[0]), args.length - 1);
        } catch (NumberFormatException e) {
            output.println("Invalid address or value format");
        }
    }
    
    private void fillMemory(String[] args) {
        if (args.length < 3) {
            output.println("Usage: fill start end value");
            return;
        }
        
        try {
            int start = parseAddress(args[0]);
            int end = parseAddress(args[1]);
            byte value = (byte) parseByteValue(args[2]);
            
            if (start > end) {
                output.println("Start address must be less than or equal to end address");
                return;
            }
            
            // Fill memory
            for (int addr = start; addr <= end; addr++) {
                writeMemory(addr, value);
            }
            
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
            int count = parseCount(args[2]);
            
            // Check for overlapping regions and determine direction
            boolean forwardCopy = src <= dest;
            
            if (forwardCopy) {
                // Copy from end to beginning to avoid overwriting source
                for (int i = count - 1; i >= 0; i--) {
                    byte value = readMemory(src + i);
                    writeMemory(dest + i, value);
                }
            } else {
                // Copy from beginning to end
                for (int i = 0; i < count; i++) {
                    byte value = readMemory(src + i);
                    writeMemory(dest + i, value);
                }
            }
            
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
            int count = parseCount(args[2]);
            
            int diffCount = 0;
            
            output.println("Comparing $" + Integer.toHexString(src).toUpperCase() + 
                    " with $" + Integer.toHexString(dest).toUpperCase() + 
                    " for " + count + " bytes");
            
            for (int i = 0; i < count; i++) {
                byte srcVal = readMemory(src + i);
                byte destVal = readMemory(dest + i);
                
                if (srcVal != destVal) {
                    diffCount++;
                    output.println("  $" + Integer.toHexString(src + i).toUpperCase() + 
                            ": $" + String.format("%02X", srcVal & 0xFF) + 
                            "  $" + Integer.toHexString(dest + i).toUpperCase() + 
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
            byte[] pattern = new byte[args.length - 2];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) parseByteValue(args[i + 2]);
            }
            
            output.println("Searching for pattern from $" + 
                    Integer.toHexString(start).toUpperCase() + " to $" + 
                    Integer.toHexString(end).toUpperCase());
            
            int foundCount = 0;
            for (int addr = start; addr <= end - pattern.length + 1; addr++) {
                boolean match = true;
                for (int i = 0; i < pattern.length; i++) {
                    if (readMemory(addr + i) != pattern[i]) {
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
    
    private void disassembleMemory(String[] args) {
        if (args.length < 1) {
            // If no args, continue from last address
            disassembleCode(lastDisassemblyAddress, DEFAULT_DISASM_COUNT);
            return;
        }
        
        try {
            int address = parseAddress(args[0]);
            int count = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_DISASM_COUNT;
            
            disassembleCode(address, count);
        } catch (NumberFormatException e) {
            output.println("Invalid address or count format");
        }
    }
    
    // Helper methods
    
    private void hexDump(int startAddress, int byteCount) {
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
                byte value = readMemory(addr);
                
                hexValues.append(String.format("%02X ", value & 0xFF));
                
                // For ASCII representation, use '.' for non-printable characters
                char c = (char)(value & 0xFF);
                if (c >= 32 && c < 127) {
                    asciiValues.append(c);
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
    
    private void disassembleCode(int startAddress, int instructionCount) {
        MOS65C02 cpu = getCPU();
        if (cpu == null) {
            output.println("CPU not available");
            return;
        }
        
        int address = startAddress;
        for (int i = 0; i < instructionCount; i++) {
            String disasm = disassembleInstruction(address);
            output.println(String.format("%04X: %s", address, disasm));
            
            // Find length of instruction (assumes disassembly of form "AA BB CC MNEMONIC")
            String[] parts = disasm.trim().split("\\s+");
            int byteCount = 1; // Default to 1 byte
            for (int j = 0; j < parts.length; j++) {
                if (parts[j].matches("[0-9A-Fa-f]{2}")) {
                    byteCount++;
                } else {
                    break;
                }
            }
            
            address = (address + byteCount) & 0xFFFF;
        }
        
        lastDisassemblyAddress = address;
    }
    
    private String disassembleInstruction(int address) {
        try {
            MOS65C02 cpu = getCPU();
            if (cpu != null) {
                int saved = cpu.getProgramCounter();
                cpu.setProgramCounter(address);
                String disasm = cpu.disassemble();
                cpu.setProgramCounter(saved);
                return disasm;
            }
        } catch (Exception e) {
            output.println("Error disassembling: " + e.getMessage());
        }
        
        // Fallback: just show the byte if we can't disassemble
        byte value = readMemory(address);
        return String.format("%02X                ???", value & 0xFF);
    }
    
    private byte readMemory(int address) {
        try {
            RAM ram = terminal.getEmulator().withComputer(c -> c.getMemory(), null);
            if (ram != null) {
                if (ram instanceof RAM128k) {
                    RAM128k ram128k = (RAM128k) ram;
                    if (useAuxMemory) {
                        return ram128k.getAuxVideoMemory().getMemoryPage(address)[address & 0xFF];
                    } else {
                        return ram128k.getMainMemory().getMemoryPage(address)[address & 0xFF];
                    }
                } else {
                    return ram.read(address, RAMEvent.TYPE.READ, false, false);
                }
            }
        } catch (Exception e) {
            output.println("Error reading memory: " + e.getMessage());
        }
        
        return 0;
    }
    
    private void writeMemory(int address, byte value) {
        try {
            terminal.getEmulator().withComputer(c -> {
                RAM ram = c.getMemory();
                if (ram instanceof RAM128k) {
                    RAM128k ram128k = (RAM128k) ram;
                    if (useAuxMemory) {
                        byte[] page = ram128k.getAuxVideoMemory().getMemoryPage(address);
                        page[address & 0xFF] = value;
                    } else {
                        byte[] page = ram128k.getMainMemory().getMemoryPage(address);
                        page[address & 0xFF] = value;
                    }
                } else {
                    ram.write(address, value, true, false);
                }
            });
        } catch (Exception e) {
            output.println("Error writing memory: " + e.getMessage());
        }
    }
    
    private int parseAddress(String addrStr) {
        if (addrStr.startsWith("$")) {
            return Integer.parseInt(addrStr.substring(1), 16) & 0xFFFF;
        } else if (addrStr.startsWith("0x")) {
            return Integer.parseInt(addrStr.substring(2), 16) & 0xFFFF;
        } else {
            return Integer.parseInt(addrStr, 16) & 0xFFFF;
        }
    }
    
    private int parseCount(String countStr) {
        if (countStr.startsWith("$")) {
            return Integer.parseInt(countStr.substring(1), 16);
        } else if (countStr.startsWith("0x")) {
            return Integer.parseInt(countStr.substring(2), 16);
        } else {
            return Integer.parseInt(countStr);
        }
    }
    
    private int parseByteValue(String valueStr) {
        if (valueStr.startsWith("$")) {
            return Integer.parseInt(valueStr.substring(1), 16) & 0xFF;
        } else if (valueStr.startsWith("0x")) {
            return Integer.parseInt(valueStr.substring(2), 16) & 0xFF;
        } else {
            return Integer.parseInt(valueStr, 16) & 0xFF;
        }
    }
    
    private MOS65C02 getCPU() {
        try {
            return (MOS65C02) terminal.getEmulator().withComputer(c -> c.getCpu(), null);
        } catch (Exception e) {
            output.println("Error getting CPU: " + e.getMessage());
            return null;
        }
    }
} 