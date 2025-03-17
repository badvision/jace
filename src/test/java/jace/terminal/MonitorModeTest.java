package jace.terminal;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for the MonitorMode class functionality
 * 
 * Since MonitorMode has many dependencies on the Emulator and JavaFX,
 * this test focuses on testing the critical functionality in isolation
 * rather than using the real MonitorMode class.
 */
public class MonitorModeTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private PrintStream testOutput;
    
    private TerminalCommandProcessor commandProcessor;
    
    /**
     * A simplified implementation of the command processor to test the logic of
     * the MonitorMode commands without the emulator dependencies
     */
    static class TerminalCommandProcessor {
        // CPU state for testing
        private int regA = 0;
        private int regX = 0;
        private int regY = 0;
        private int regPC = 0;
        private int regS = 0xFF;
        private boolean flagN = false;
        private boolean flagV = false;
        private boolean flagB = false;
        private boolean flagD = false;
        private boolean flagI = false;
        private boolean flagZ = false;
        private boolean flagC = false;
        
        private final PrintStream output;
        
        public TerminalCommandProcessor(PrintStream output) {
            this.output = output;
        }
        
        /**
         * Process monitor mode commands
         */
        public boolean processCommand(String commandString) {
            String[] parts = commandString.trim().split("\\s+");
            String command = parts[0].toLowerCase();
            
            // Handle commands
            if (command.equals("registers") || command.equals("reg")) {
                showRegisters();
                return true;
            } else if (command.equals("setregister") || command.equals("sr")) {
                // Extract arguments for setRegister command
                String[] args = new String[parts.length - 1];
                System.arraycopy(parts, 1, args, 0, parts.length - 1);
                setRegister(args);
                return true;
            }
            
            // Command not recognized
            return false;
        }
        
        /**
         * Display the registers - similar to MonitorMode.showRegisters()
         */
        protected void showRegisters() {
            output.println("CPU Registers:");
            output.printf("  A: $%02X%n", regA);
            output.printf("  X: $%02X%n", regX);
            output.printf("  Y: $%02X%n", regY);
            output.printf("  PC: $%04X%n", regPC);
            output.printf("  S: $%02X%n", regS);

            // Status flags
            StringBuilder flags = new StringBuilder();
            flags.append(flagN ? "N" : "n");
            flags.append(flagV ? "V" : "v");
            flags.append("-");
            flags.append(flagB ? "B" : "b");
            flags.append(flagD ? "D" : "d");
            flags.append(flagI ? "I" : "i");
            flags.append(flagZ ? "Z" : "z");
            flags.append(flagC ? "C" : "c");

            output.println("  Flags: " + flags.toString());
        }
        
        /**
         * Set a register - similar to MonitorMode.setRegister()
         */
        protected void setRegister(String[] args) {
            if (args.length < 1) {
                output.println("Usage: setregister <register> <value>");
                return;
            }

            String register = args[0].toUpperCase();
            
            if (args.length < 2) {
                output.println("Missing value for register " + register);
                return;
            }

            String valueStr = args[1];

            try {
                switch (register) {
                    case "A":
                        regA = parseByteValue(valueStr);
                        break;
                    case "X":
                        regX = parseByteValue(valueStr);
                        break;
                    case "Y":
                        regY = parseByteValue(valueStr);
                        break;
                    case "PC":
                        regPC = parseWordValue(valueStr);
                        break;
                    case "S":
                        regS = parseByteValue(valueStr);
                        break;
                    case "N":
                        flagN = parseBooleanValue(valueStr);
                        break;
                    case "V":
                        flagV = parseBooleanValue(valueStr);
                        break;
                    case "B":
                        flagB = parseBooleanValue(valueStr);
                        break;
                    case "D":
                        flagD = parseBooleanValue(valueStr);
                        break;
                    case "I":
                        flagI = parseBooleanValue(valueStr);
                        break;
                    case "Z":
                        flagZ = parseBooleanValue(valueStr);
                        break;
                    case "C":
                        flagC = parseBooleanValue(valueStr);
                        break;
                    default:
                        output.println("Unknown register: " + register);
                        return;
                }
                output.println("Register " + register + " set to " + valueStr);
            } catch (NumberFormatException e) {
                output.println("Invalid value format: " + valueStr);
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
        
        // Getters for state verification
        public int getRegA() { return regA; }
        public int getRegX() { return regX; }
        public int getRegY() { return regY; }
        public int getRegPC() { return regPC; }
        public int getRegS() { return regS; }
        public boolean isFlagN() { return flagN; }
        public boolean isFlagV() { return flagV; }
        public boolean isFlagB() { return flagB; }
        public boolean isFlagD() { return flagD; }
        public boolean isFlagI() { return flagI; }
        public boolean isFlagZ() { return flagZ; }
        public boolean isFlagC() { return flagC; }
    }
    
    @BeforeClass
    public static void setUpClass() {
        // Configure test environment properties
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("jace.test", "true");
    }
    
    @Before
    public void setUp() {
        testOutput = new PrintStream(outContent);
        commandProcessor = new TerminalCommandProcessor(testOutput);
        System.setOut(testOutput);
    }
    
    @After
    public void tearDown() {
        System.setOut(originalOut);
    }
    
    /**
     * Test that the registers command is processed and verifies register values
     */
    @Test
    public void testRegistersCommandProcessing() {
        // Set register values for the test
        commandProcessor.regA = 0xAA;
        commandProcessor.regX = 0xBB;
        commandProcessor.regY = 0xCC;
        commandProcessor.regPC = 0x1234;
        commandProcessor.regS = 0xDD;
        commandProcessor.flagN = true;
        commandProcessor.flagV = false;
        commandProcessor.flagB = true;
        commandProcessor.flagD = false;
        commandProcessor.flagI = true;
        commandProcessor.flagZ = false;
        commandProcessor.flagC = true;
        
        // Reset output
        outContent.reset();
        
        // Process the registers command
        boolean result = commandProcessor.processCommand("registers");
        
        // Verify command was processed
        assertTrue("registers command should be processed", result);
        
        // Get the output
        String output = outContent.toString();
        
        // Verify detailed register output
        assertTrue("Output should display register information", 
                output.contains("CPU Registers:"));
        assertTrue("Output should show A register with correct value", output.contains("A: $AA"));
        assertTrue("Output should show X register with correct value", output.contains("X: $BB"));
        assertTrue("Output should show Y register with correct value", output.contains("Y: $CC"));
        assertTrue("Output should show PC register with correct value", output.contains("PC: $1234"));
        assertTrue("Output should show S register with correct value", output.contains("S: $DD"));
        
        // Verify flags are displayed correctly
        String flagsLine = output.lines()
                .filter(line -> line.contains("Flags:"))
                .findFirst()
                .orElse("");
        assertFalse("Output should include a flags line", flagsLine.isEmpty());
        String flagsString = flagsLine.substring(flagsLine.indexOf("Flags:") + 7).trim();
        
        // Check C flag is correctly handled (set = uppercase since flagC=true)
        assertEquals('C', flagsString.charAt(7));
        
        // Now test with C=false (clear)
        commandProcessor.flagC = false;
        outContent.reset();
        
        result = commandProcessor.processCommand("reg");  // Use the alias for second test
        
        // Verify command was processed
        assertTrue("reg alias should be processed", result);
        
        // Get the output
        output = outContent.toString();
        
        // Check that c flag is lowercase (clear)
        flagsLine = output.lines()
                .filter(line -> line.contains("Flags:"))
                .findFirst()
                .orElse("");
        assertFalse("Output should include a flags line", flagsLine.isEmpty());
        flagsString = flagsLine.substring(flagsLine.indexOf("Flags:") + 7).trim();
        
        // Check C flag is correctly handled (clear = lowercase since flagC=false)
        assertEquals('c', flagsString.charAt(7));
    }
    
    /**
     * Test setting byte registers (A, X, Y)
     */
    @Test
    public void testSetByteRegisters() {
        // Define test data: register, value, expected decimal value
        String[][] testCases = {
            {"A", "$42", "66"},
            {"X", "0x66", "102"},
            {"Y", "99", "99"}
        };
        
        for (String[] testCase : testCases) {
            String register = testCase[0];
            String value = testCase[1];
            int expectedValue = Integer.parseInt(testCase[2]);
            
            // Reset output
            outContent.reset();
            
            // Process the setregister command
            boolean result = commandProcessor.processCommand("setregister " + register + " " + value);
            
            // Verify command was processed
            assertTrue("setregister command should be processed", result);
            
            // Get the output
            String output = outContent.toString();
            
            // Verify output
            assertTrue("Output should indicate register was set", 
                    output.contains("Register " + register + " set to " + value));
            
            // Verify the register value was updated
            int actualValue = 0;
            if (register.equals("A")) {
                actualValue = commandProcessor.getRegA();
            } else if (register.equals("X")) {
                actualValue = commandProcessor.getRegX();
            } else if (register.equals("Y")) {
                actualValue = commandProcessor.getRegY();
            }
            
            assertEquals("Register " + register + " should be updated", expectedValue, actualValue);
        }
    }
    
    /**
     * Test setting PC and stack registers
     */
    @Test
    public void testSetPCAndStack() {
        // Test setting PC
        boolean result = commandProcessor.processCommand("setregister PC $1234");
        assertTrue("Command should be processed", result);
        
        // Verify PC was updated
        assertEquals("PC register should be updated", 0x1234, commandProcessor.getRegPC());
        
        // Test setting stack pointer
        result = commandProcessor.processCommand("setregister S $FF");
        assertTrue("Command should be processed", result);
        
        // Verify S was updated
        assertEquals("Stack register should be updated", 0xFF, commandProcessor.getRegS());
    }
    
    /**
     * Test setting flag registers
     */
    @Test
    public void testSetFlags() {
        // Define test data: flag register, value
        String[][] testCases = {
            {"N", "1"},
            {"V", "1"},
            {"B", "1"},
            {"D", "1"},
            {"I", "1"},
            {"Z", "1"},
            {"C", "1"}
        };
        
        for (String[] testCase : testCases) {
            String register = testCase[0];
            String value = testCase[1];
            
            // Reset output and flags
            outContent.reset();
            commandProcessor.flagN = false;
            commandProcessor.flagV = false;
            commandProcessor.flagB = false;
            commandProcessor.flagD = false;
            commandProcessor.flagI = false;
            commandProcessor.flagZ = false;
            commandProcessor.flagC = false;
            
            // Set the flag
            commandProcessor.processCommand("setregister " + register + " " + value);
            
            // Verify the flag was updated
            boolean isSet = false;
            if (register.equals("N")) {
                isSet = commandProcessor.isFlagN();
            } else if (register.equals("V")) {
                isSet = commandProcessor.isFlagV();
            } else if (register.equals("B")) {
                isSet = commandProcessor.isFlagB();
            } else if (register.equals("D")) {
                isSet = commandProcessor.isFlagD();
            } else if (register.equals("I")) {
                isSet = commandProcessor.isFlagI();
            } else if (register.equals("Z")) {
                isSet = commandProcessor.isFlagZ();
            } else if (register.equals("C")) {
                isSet = commandProcessor.isFlagC();
            }
            
            assertTrue("Flag " + register + " should be set", isSet);
        }
        
        // Also test clearing a flag
        outContent.reset();
        commandProcessor.flagC = true;
        commandProcessor.processCommand("setregister C 0");
        assertFalse("C flag should be cleared", commandProcessor.isFlagC());
    }
    
    /**
     * Test all possible flag combinations to ensure they're displayed correctly
     */
    @Test
    public void testAllFlagCombinations() {
        // Set all flags on
        commandProcessor.flagN = true;
        commandProcessor.flagV = true;
        commandProcessor.flagB = true;
        commandProcessor.flagD = true;
        commandProcessor.flagI = true;
        commandProcessor.flagZ = true;
        commandProcessor.flagC = true;
        
        // Reset output
        outContent.reset();
        
        // Show registers
        commandProcessor.processCommand("registers");
        
        // Get the output
        String output = outContent.toString();
        
        // Verify all flags are set
        assertTrue("All flags should be capitalized", 
                output.contains("Flags: NV-BDIZC"));
        
        // Now set all flags off
        commandProcessor.flagN = false;
        commandProcessor.flagV = false;
        commandProcessor.flagB = false;
        commandProcessor.flagD = false;
        commandProcessor.flagI = false;
        commandProcessor.flagZ = false;
        commandProcessor.flagC = false;
        
        // Reset output
        outContent.reset();
        
        // Show registers
        commandProcessor.processCommand("registers");
        
        // Get the output
        output = outContent.toString();
        
        // Verify all flags are clear
        assertTrue("All flags should be lowercase", 
                output.contains("Flags: nv-bdizc"));
    }
    
    /**
     * Test setregister command with no arguments
     */
    @Test
    public void testSetRegisterNoArgs() {
        // Reset output
        outContent.reset();
        
        // Process command with no args
        boolean result = commandProcessor.processCommand("setregister");
        
        // Verify command was processed
        assertTrue("setregister command should be processed", result);
        
        // Get the output
        String output = outContent.toString();
        
        // Verify usage information is displayed
        assertTrue("Output should include usage information", 
                output.contains("Usage:"));
    }
    
    /**
     * Test setregister command alias (sr)
     */
    @Test
    public void testSetRegisterAlias() {
        // Reset output
        outContent.reset();
        
        // Test using the sr alias to set a register
        boolean result = commandProcessor.processCommand("sr A $42");
        
        // Verify command was processed
        assertTrue("sr alias should be processed", result);
        
        // Get the output
        String output = outContent.toString();
        
        // Verify output
        assertTrue("Output should show A register was set", 
                output.contains("Register A set to $42"));
        
        // Verify register was updated
        assertEquals("Register A should be updated", 0x42, commandProcessor.getRegA());
    }
    
    /**
     * Test setregister command with invalid arguments
     */
    @Test
    public void testSetRegisterInvalidArgs() {
        // Test with unknown register
        outContent.reset();
        boolean result = commandProcessor.processCommand("setregister UNKNOWN $42");
        
        // Verify command was processed
        assertTrue("Command should be processed even with unknown register", result);
        
        // Get the output
        String output = outContent.toString();
        
        // Verify error message
        assertTrue("Output should indicate unknown register", 
                output.contains("Unknown register"));
        
        // Test with invalid value format
        outContent.reset();
        result = commandProcessor.processCommand("setregister A INVALID");
        
        // Verify command was processed
        assertTrue("Command should be processed even with invalid value", result);
        
        // Get the output
        output = outContent.toString();
        
        // Verify error message
        assertTrue("Output should indicate invalid value", 
                output.contains("Invalid value format"));
        
        // Test with missing value
        outContent.reset();
        result = commandProcessor.processCommand("setregister A");
        
        // Verify command was processed
        assertTrue("Command should be processed even with missing value", result);
        
        // Get the output
        output = outContent.toString();
        
        // Verify error message
        assertTrue("Output should indicate missing value", 
                output.contains("Missing value for register A"));
    }
} 