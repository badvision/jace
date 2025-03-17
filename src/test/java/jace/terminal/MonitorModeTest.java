package jace.terminal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.core.Computer;

/**
 * Tests for the MonitorMode class functionality
 * 
 * This test uses a direct approach with mocks to test the MonitorMode class.
 */
public class MonitorModeTest {
    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;
    private PrintStream testOutput;
    
    @Mock
    private JaceTerminal mockTerminal;
    
    @Mock
    private MOS65C02 mockCpu;
    
    @Mock
    private Computer mockComputer;
    
    /**
     * A testable subclass of MonitorMode that exposes protected methods
     * and bypasses the Emulator call to use our mocked CPU.
     */
    public class TestableMonitorMode extends MonitorMode {
        public TestableMonitorMode(JaceTerminal terminal) {
            super(terminal);
        }
        
        // Override getCpu to return our mock
        @Override
        public MOS65C02 getCpu() {
            return mockCpu;
        }
    }
    
    private TestableMonitorMode monitorMode;
    
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
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Save original System.out and redirect to our capture stream
        originalOut = System.out;
        outContent = new ByteArrayOutputStream();
        testOutput = new PrintStream(outContent);
        System.setOut(testOutput);
        
        // Configure the mock terminal
        when(mockTerminal.getOutput()).thenReturn(testOutput);
        
        // Configure mock CPU values
        when(mockCpu.getAccumulator()).thenReturn(0x42);
        when(mockCpu.getXRegister()).thenReturn(0x43);
        when(mockCpu.getYRegister()).thenReturn(0x44);
        when(mockCpu.getProgramCounter()).thenReturn(0xC000);
        when(mockCpu.getStackPointer()).thenReturn(0xFF);
        when(mockCpu.isNegativeFlag()).thenReturn(true);
        when(mockCpu.isOverflowFlag()).thenReturn(false);
        when(mockCpu.isBreakFlag()).thenReturn(true);
        when(mockCpu.isDecimalFlag()).thenReturn(false);
        when(mockCpu.isInterruptFlag()).thenReturn(true);
        when(mockCpu.isZeroFlag()).thenReturn(false);
        when(mockCpu.isCarryFlag()).thenReturn(true);
        when(mockCpu.disassemble(0xC000)).thenReturn("JMP $C000");
        
        // Configure mock computer
        when(mockComputer.getCpu()).thenReturn(mockCpu);
        
        // Create testable instance
        monitorMode = new TestableMonitorMode(mockTerminal);
        
        // Set the output field to our test stream
        Field outputField = MonitorMode.class.getDeclaredField("output");
        outputField.setAccessible(true);
        outputField.set(monitorMode, testOutput);
    }
    
    @After
    public void tearDown() {
        System.setOut(originalOut);
    }
    
    /**
     * Test the name of the monitor mode
     */
    @Test
    public void testName() {
        assertEquals("Monitor", monitorMode.getName());
    }
    
    /**
     * Test the prompt of the monitor mode
     */
    @Test
    public void testPrompt() {
        assertEquals("*", monitorMode.getPrompt());
    }
    
    /**
     * Test that the registers command is processed and outputs correctly
     */
    @Test
    public void testRegistersCommand() {
        // Process the registers command
        boolean result = monitorMode.processCommand("registers");
        
        // Verify command was processed
        assertTrue("registers command should be processed", result);
        
        // Get the output
        String output = outContent.toString();
        
        // Verify the output contains expected register values
        assertTrue("Output should show registers heading", output.contains("CPU Registers:"));
        assertTrue("Output should show A register with correct value", output.contains("A: $42"));
        assertTrue("Output should show X register with correct value", output.contains("X: $43"));
        assertTrue("Output should show Y register with correct value", output.contains("Y: $44"));
        assertTrue("Output should show PC register with correct value", output.contains("PC: $C000"));
        assertTrue("Output should show S register with correct value", output.contains("S: $FF"));
    }
    
    /**
     * Test that the help command works
     */
    @Test
    public void testHelpCommand() {
        // Process the help command through the JaceTerminal interface
        monitorMode.printHelp();
        
        // Get the output
        String output = outContent.toString();
        
        // Verify the output contains essential help text
        assertTrue("Help output should mention monitor mode commands", 
                output.contains("Monitor Mode Commands:"));
        assertTrue("Help output should include memory commands", 
                output.contains("Memory Examination:"));
    }
    
    /**
     * Test that an unknown command returns false
     */
    @Test
    public void testUnknownCommand() {
        // Process an unknown command
        boolean result = monitorMode.processCommand("nonexistentcommand");
        
        // Verify command was not processed
        assertFalse("Unknown command should not be processed", result);
    }
    
    /**
     * Test the back command returns to main mode
     */
    @Test
    public void testBackCommand() {
        // Process the back command
        boolean result = monitorMode.processCommand("back");
        
        // Verify command was processed
        assertTrue("back command should be processed", result);
        
        // Verify terminal.setMode was called with "main"
        verify(mockTerminal).setMode("main");
    }
    
    /**
     * Test that command aliases work
     */
    @Test
    public void testCommandAliases() {
        // Process a command using its alias
        boolean result = monitorMode.processCommand("reg");
        
        // Verify command was processed
        assertTrue("reg alias should be processed", result);
        
        // Get the output
        String output = outContent.toString();
        
        // Verify the output contains register info
        assertTrue("Output should show registers heading", output.contains("CPU Registers:"));
    }
    
    /**
     * Test command-specific help
     */
    @Test
    public void testCommandSpecificHelp() {
        // Get help for a specific command
        boolean result = monitorMode.printCommandHelp("registers");
        
        // Verify help was displayed
        assertTrue("Command help should be displayed", result);
        
        // Get the output
        String output = outContent.toString();
        
        // Verify the output contains help for registers command
        assertTrue("Output should contain help for registers command", 
                output.contains("registers") && output.contains("Display"));
    }
} 