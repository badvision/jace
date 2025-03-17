package jace.terminal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.core.Computer;

/**
 * Test for MonitorMode's showRegisters method.
 * This test uses the actual MonitorMode class with mocked CPU.
 */
public class MonitorModeShowRegistersTest {
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
     * A testable subclass of MonitorMode that exposes the showRegisters method
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
    
    private TestableMonitorMode testableMonitorMode;
    
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
        testableMonitorMode = new TestableMonitorMode(mockTerminal);
        
        // Mock the emulator call
        mockEmulator();
        
        // Set the output field to our test stream
        Field outputField = MonitorMode.class.getDeclaredField("output");
        outputField.setAccessible(true);
        outputField.set(testableMonitorMode, testOutput);
    }
    
    /**
     * Helper method to mock the static Emulator.withComputer method
     */
    private void mockEmulator() throws Exception {
        // Create a field for the mocked Emulator instance and set it
        Field emulatorField = Emulator.class.getDeclaredField("instance");
        emulatorField.setAccessible(true);
        
        // We can't directly mock the Emulator class easily in JUnit 4
        // So we'll use our subclass technique to bypass the Emulator call
    }
    
    @After
    public void tearDown() {
        System.setOut(originalOut);
    }
    
    /**
     * Test that showRegisters displays CPU register values correctly by using
     * the actual implementation with our mocked CPU values.
     */
    @Test
    public void testShowRegisters() {
        // Call the actual method - directly because we've made it public in our subclass
        testableMonitorMode.showRegisters();
        
        // Get the output
        String output = outContent.toString();
        
        // Debug: print the actual output to see what's happening
        System.err.println("DEBUG - Actual output from showRegisters:\n" + output);
        
        // Verify the output contains expected register values
        assertTrue("Output should show registers heading", output.contains("CPU Registers:"));
        assertTrue("Output should show A register with correct value", output.contains("A: $42"));
        assertTrue("Output should show X register with correct value", output.contains("X: $43"));
        assertTrue("Output should show Y register with correct value", output.contains("Y: $44"));
        assertTrue("Output should show PC register with correct value", output.contains("PC: $C000"));
        assertTrue("Output should show S register with correct value", output.contains("S: $FF"));
        
        // Verify flags are displayed correctly (actual flag output is "Flags: Nv-BdIzC")
        assertTrue("Output should contain Flags header", output.contains("Flags:"));
        
        // Check the specific flag values are represented
        String flagLine = "";
        for (String line : output.split("\n")) {
            if (line.contains("Flags:")) {
                flagLine = line;
                break;
            }
        }
        
        // Check specific flags we set in the mock
        assertTrue("Negative flag should be set (uppercase N)", flagLine.contains("N"));
        assertTrue("Break flag should be set (uppercase B)", flagLine.contains("B"));
        assertTrue("Interrupt flag should be set (uppercase I)", flagLine.contains("I"));
        assertTrue("Carry flag should be set (uppercase C)", flagLine.contains("C"));
        
        // Check flags we unset in the mock
        assertTrue("Overflow flag should be unset (lowercase v)", flagLine.contains("v"));
        assertTrue("Decimal flag should be unset (lowercase d)", flagLine.contains("d"));
        assertTrue("Zero flag should be unset (lowercase z)", flagLine.contains("z"));
    }
} 