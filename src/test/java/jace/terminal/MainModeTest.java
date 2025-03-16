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
        
        // Verify setMode was called with "debugger"
        verify(mockTerminal).setMode("debugger");
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
} 