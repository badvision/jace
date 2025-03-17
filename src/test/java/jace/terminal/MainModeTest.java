package jace.terminal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.RAMEvent;
import jace.core.PagedMemory;

public class MainModeTest {
    // Logger setup
    private static final Logger LOG = Logger.getLogger(MainModeTest.class.getName());
    
    // Control test output verbosity via system property:
    // -Djace.test.debug=true to enable debug logs
    private static final String DEBUG_PROPERTY = "jace.test.debug";
    private static final boolean DEBUG_MODE = Boolean.getBoolean(DEBUG_PROPERTY);
    
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    
    private TestableMainMode mainMode;
    private JaceTerminal mockTerminal;
    private EmulatorInterface mockEmulator;
    private Computer mockComputer;
    private RAM128k mockRam;
    private MOS65C02 mockCpu;
    private PagedMemory mockPagedMemory;
    
    // Track memory writes
    private byte[] memoryValues = new byte[65536];
    
    // Create a testable subclass that allows us to override the getCPU method
    static class TestableMainMode extends MainMode {
        private MOS65C02 testCpu;
        
        public TestableMainMode(JaceTerminal terminal) {
            super(terminal);
        }
        
        public void setTestCpu(MOS65C02 cpu) {
            this.testCpu = cpu;
        }
        
        @Override
        protected MOS65C02 getCPU() {
            return testCpu != null ? testCpu : super.getCPU();
        }
    }
    
    @BeforeClass
    public static void setupLogging() {
        // Configure logging based on system property
        Level logLevel = DEBUG_MODE ? Level.FINE : Level.INFO;
        LOG.setLevel(logLevel);
        
        // Ensure handlers use our level
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(logLevel);
            }
        }
        
        if (DEBUG_MODE) {
            LOG.info("Debug mode enabled - verbose output will be displayed");
        }
    }
    
    @Before
    public void setUp() {
        // Setup mocks
        mockTerminal = mock(JaceTerminal.class);
        mockEmulator = mock(EmulatorInterface.class);
        mockComputer = mock(Computer.class);
        mockRam = mock(RAM128k.class);
        mockCpu = mock(MOS65C02.class);
        mockPagedMemory = mock(PagedMemory.class);
        
        // Wire mocks together
        when(mockTerminal.getOutput()).thenReturn(new PrintStream(outContent));
        when(mockTerminal.getEmulator()).thenReturn(mockEmulator);
        
        // Set up the computer mock to return RAM and CPU
        when(mockEmulator.withComputer(any(), any())).thenAnswer(invocation -> {
            Function<Computer, Object> function = invocation.getArgument(0);
            return function.apply(mockComputer);
        });
        when(mockComputer.getMemory()).thenReturn(mockRam);
        when(mockComputer.getCpu()).thenReturn(mockCpu);
        
        // Create MainMode instance with mocked terminal
        mainMode = new TestableMainMode(mockTerminal);
        
        LOG.fine("Test setup complete");
    }
    
    @After
    public void tearDown() {
        // Reset the test CPU to null after each test
        if (mainMode != null) {
            mainMode.setTestCpu(null);
        }
        outContent.reset();
        LOG.fine("Test cleaned up");
    }
    
    /**
     * Helper method to log test output when in debug mode
     */
    private void logOutput(String output) {
        if (DEBUG_MODE) {
            LOG.fine("Command output:\n" + output);
        }
    }
    
    @Test
    public void testMainModeName() {
        // Test that MainMode returns the correct name
        assertEquals("Main", mainMode.getName());
    }
    
    @Test
    public void testMainModePrompt() {
        // Test that MainMode returns the correct prompt
        assertEquals("JACE> ", mainMode.getPrompt());
    }
    
    @Test
    public void testHelpCommand() {
        // Test the help output
        mainMode.printHelp();
        
        // Get the output
        String output = outContent.toString();
        logOutput(output);
        
        // Verify the output contains expected help text
        assertTrue("Help text should list available commands", 
                output.contains("Available commands:") && 
                output.contains("monitor") && 
                output.contains("assembler"));
    }
    
    @Test
    public void testCommandHelp() {
        // Test displaying help for a specific command
        boolean result = mainMode.printCommandHelp("monitor");
        
        // Get the output
        String output = outContent.toString();
        logOutput(output);
        
        // Verify the output contains expected help for the monitor command
        assertTrue("Command help should be displayed", 
                output.contains("monitor") && 
                result == true);
    }
    
    @Test
    public void testMonitorCommand() {
        // Test the monitor command
        boolean result = mainMode.processCommand("monitor");
        
        // Verify setMode was called with "monitor"
        verify(mockTerminal).setMode("monitor");
        assertTrue("Command should be processed successfully", result);
    }
    
    @Test
    public void testAssemblerCommand() {
        // Test the assembler command
        boolean result = mainMode.processCommand("assembler");
        
        // Verify setMode was called with "assembler"
        verify(mockTerminal).setMode("assembler");
        assertTrue("Command should be processed successfully", result);
    }
    
    @Test
    public void testDebuggerCommand() {
        // Test the debugger command
        boolean result = mainMode.processCommand("debugger");
        
        // Verify setMode was called with "monitor" since debugger was integrated into monitor mode
        verify(mockTerminal).setMode("monitor");
        assertTrue("Command should be processed successfully", result);
    }
    
    @Test
    public void testCommandAliases() {
        // Test the monitor command alias
        boolean result = mainMode.processCommand("m");
        
        // Verify setMode was called with "monitor"
        verify(mockTerminal).setMode("monitor");
        assertTrue("Command should be processed successfully", result);
    }
    
    @Test
    public void testRegistersCommand() {
        LOG.fine("Starting testRegistersCommand");
        
        // Set up CPU with register values
        mockCpu.A = 0xAA;
        mockCpu.X = 0xBB;
        mockCpu.Y = 0xCC;
        mockCpu.STACK = 0xDD;
        when(mockCpu.getProgramCounter()).thenReturn(0xEEFF);
        
        // Set up CPU flags
        mockCpu.Z = true;
        mockCpu.C = 1;
        mockCpu.I = true;
        mockCpu.D = true;
        
        // Use our testable subclass to directly set the CPU
        mainMode.setTestCpu(mockCpu);
        
        // Test the registers command
        boolean result = mainMode.processCommand("registers");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Get the output
        String output = outContent.toString();
        logOutput(output);
        
        // Verify the output contains register values with the correct format
        assertTrue("Output should include register values", 
                output.contains("CPU Registers:") && 
                output.contains("A: $") && 
                output.contains("X: $") && 
                output.contains("Y: $") && 
                output.contains("PC: $") && 
                output.contains("S: $") &&
                output.contains("Flags:"));
    }
    
    @Test
    public void testUnknownCommand() {
        // Test an unknown command
        boolean result = mainMode.processCommand("unknowncommand");
        
        // Verify the result is false (command not recognized)
        assertFalse("Unknown command should return false", result);
        
        // Verify error message is displayed
        String output = outContent.toString();
        logOutput(output);
        assertTrue("Output should include error message", output.contains("Unknown command"));
    }
    
    @Test
    public void testSetRegisterNoArgs() {
        // Test setregister with no arguments
        boolean result = mainMode.processCommand("setregister");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Get the output
        String output = outContent.toString();
        logOutput(output);
        
        // Verify the output shows usage info
        assertTrue("Output should show usage information", 
                output.contains("Usage: setregister") && 
                output.contains("Registers:"));
    }
    
    @Test
    public void testSetRegisterAccumulator() {
        // Create a fresh mock CPU for each test to avoid state leakage
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting accumulator (A) register
        boolean result = mainMode.processCommand("setregister A $42");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Verify the A register was set to the correct value (0x42)
        assertEquals("A register should be set to 0x42", 0x42, testCpu.A);
        
        // Get the output
        String output = outContent.toString();
        logOutput(output);
        
        // Verify the output confirms the register was set
        assertTrue("Output should confirm register was set", 
                output.contains("Register A set to"));
    }
    
    @Test
    public void testSetRegisterX() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting X index register
        boolean result = mainMode.processCommand("setregister X 255");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Verify the X register was set to the correct value (255)
        assertEquals("X register should be set to 255", 255, testCpu.X);
        
        // Check output confirmation
        assertTrue(outContent.toString().contains("Register X set to"));
    }
    
    @Test
    public void testSetRegisterY() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting Y index register - use hex instead of binary
        boolean result = mainMode.processCommand("setregister Y $AA");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Verify the Y register was set to the correct value (0xAA = 170)
        assertEquals("Y register should be set to 0xAA", 0xAA, testCpu.Y);
        
        // Check output confirmation
        assertTrue(outContent.toString().contains("Register Y set to"));
    }
    
    @Test
    public void testSetRegisterPC() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting PC (program counter)
        boolean result = mainMode.processCommand("setregister PC $C000");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Verify setProgramCounter was called with the correct value (0xC000)
        verify(testCpu).setProgramCounter(0xC000);
        
        // Check output confirmation
        assertTrue(outContent.toString().contains("Register PC set to"));
    }
    
    @Test
    public void testSetRegisterS() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting S (stack pointer)
        boolean result = mainMode.processCommand("setregister S $FF");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Verify the STACK register was set to the correct value (0xFF)
        assertEquals("STACK register should be set to 0xFF", 0xFF, testCpu.STACK);
        
        // Check output confirmation
        assertTrue(outContent.toString().contains("Register S set to"));
    }
    
    @Test
    public void testSetRegisterFlags() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting all flag registers
        // N flag (Negative)
        mainMode.processCommand("setregister N 1");
        assertEquals("N flag should be set to true", true, testCpu.N);
        
        // Reset output for next test
        outContent.reset();
        
        // V flag (Overflow)
        mainMode.processCommand("setregister V true");
        assertEquals("V flag should be set to true", true, testCpu.V);
        
        // Reset output for next test
        outContent.reset();
        
        // B flag (Break)
        mainMode.processCommand("setregister B 0");
        assertEquals("B flag should be set to false", false, testCpu.B);
        
        // Reset output for next test
        outContent.reset();
        
        // D flag (Decimal)
        mainMode.processCommand("setregister D false");
        assertEquals("D flag should be set to false", false, testCpu.D);
        
        // Reset output for next test
        outContent.reset();
        
        // I flag (Interrupt disable)
        mainMode.processCommand("setregister I 1");
        assertEquals("I flag should be set to true", true, testCpu.I);
        
        // Reset output for next test
        outContent.reset();
        
        // Z flag (Zero)
        mainMode.processCommand("setregister Z true");
        assertEquals("Z flag should be set to true", true, testCpu.Z);
        
        // Reset output for next test
        outContent.reset();
        
        // C flag (Carry)
        mainMode.processCommand("setregister C 1");
        assertEquals("C flag should be set to 1", 1, testCpu.C);
    }
    
    @Test
    public void testSetRegisterInvalidRegister() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting an invalid register
        boolean result = mainMode.processCommand("setregister INVALID 42");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Check error message
        assertTrue(outContent.toString().contains("Unknown register"));
    }
    
    @Test
    public void testSetRegisterInvalidValue() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting a register with an invalid value
        boolean result = mainMode.processCommand("setregister A INVALID");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Check error message
        assertTrue(outContent.toString().contains("Invalid value format"));
    }
    
    @Test
    public void testSetRegisterCpuUnavailable() {
        // Create a new TestableMainMode that always returns null for CPU
        TestableMainMode testMode = new TestableMainMode(mockTerminal) {
            @Override
            protected MOS65C02 getCPU() {
                return null;
            }
        };
        
        // Test setting a register when CPU is unavailable
        boolean result = testMode.processCommand("setregister A 42");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Get the output
        String output = outContent.toString();
        logOutput(output);
        
        // Check error message
        assertTrue("Output should indicate CPU not available", 
                output.contains("CPU not available"));
    }
    
    @Test
    public void testSetRegisterAlias() {
        // Create a fresh mock CPU for each test
        MOS65C02 testCpu = mock(MOS65C02.class);
        mainMode.setTestCpu(testCpu);
        
        // Test setting a register using the alias
        boolean result = mainMode.processCommand("sr A $42");
        
        // Verify the result
        assertTrue("Command should be processed successfully", result);
        
        // Verify the A register was set to the correct value (0x42)
        assertEquals("A register should be set to 0x42", 0x42, testCpu.A);
    }
} 