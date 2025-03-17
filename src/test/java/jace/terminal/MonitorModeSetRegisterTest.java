package jace.terminal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyInt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jace.apple2e.MOS65C02;

/**
 * Tests for the registers command handling in MonitorMode using proper mocking
 */
public class MonitorModeSetRegisterTest {
    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;
    
    @Mock
    private MOS65C02 mockCpu;
    
    @Mock
    private JaceTerminal mockTerminal;
    
    @Mock
    private EmulatorInterface mockEmulator;
    
    private MonitorMode monitorMode;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream);
        
        // Setup mocks
        when(mockTerminal.getOutput()).thenReturn(printStream);
        when(mockTerminal.getEmulator()).thenReturn(mockEmulator);
        
        // Setup CPU mock with disassemble method mocked to prevent NPE
        when(mockCpu.getAccumulator()).thenReturn(0x42);
        when(mockCpu.getXRegister()).thenReturn(0x43);
        when(mockCpu.getYRegister()).thenReturn(0x44);
        when(mockCpu.getProgramCounter()).thenReturn(0xC000);
        when(mockCpu.getStackPointer()).thenReturn(0xFF);
        when(mockCpu.disassemble(anyInt())).thenReturn("JMP $C000"); // Mock disassemble
        when(mockCpu.getFlags()).thenReturn("Nv-BdIzC");
        
        // Create a MonitorMode that overrides getCpu to return our mock
        monitorMode = new MonitorMode(mockTerminal) {
            @Override
            public MOS65C02 getCpu() {
                return mockCpu;
            }
        };
    }
    
    @Test
    public void testSetRegisterAccumulator() {
        // Execute the handleRegisters method with "A $42" (set A to 0x42)
        monitorMode.handleRegisters(new String[] {"A", "$42"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setAccumulator(0x42);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register A set to $42"));
    }
    
    @Test
    public void testSetRegisterX() {
        // Execute the handleRegisters method with "X $FF" (set X to 0xFF)
        monitorMode.handleRegisters(new String[] {"X", "$FF"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setXRegister(0xFF);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register X set to $FF"));
    }
    
    @Test
    public void testSetRegisterY() {
        // Execute the handleRegisters method with "Y 128" (set Y to 128)
        monitorMode.handleRegisters(new String[] {"Y", "128"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setYRegister(128);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register Y set to 128"));
    }
    
    @Test
    public void testSetRegisterPC() {
        // Execute the handleRegisters method with "PC $C000" (set PC to 0xC000)
        monitorMode.handleRegisters(new String[] {"PC", "$C000"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setProgramCounter(0xC000);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register PC set to $C000"));
    }
    
    @Test
    public void testSetRegisterStack() {
        // Execute the handleRegisters method with "S $F0" (set stack to 0xF0)
        monitorMode.handleRegisters(new String[] {"S", "$F0"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setStackPointer(0xF0);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register S set to $F0"));
    }
    
    @Test
    public void testSetNegativeFlag() {
        // Execute the handleRegisters method with "N 1" (set negative flag to true)
        monitorMode.handleRegisters(new String[] {"N", "1"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setNegativeFlag(true);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register N set to 1"));
    }
    
    @Test
    public void testSetOverflowFlag() {
        // Execute the handleRegisters method with "V 0" (set overflow flag to false)
        monitorMode.handleRegisters(new String[] {"V", "0"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setOverflowFlag(false);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register V set to 0"));
    }
    
    @Test
    public void testSetBreakFlag() {
        // Execute the handleRegisters method with "B 1" (set break flag to true)
        monitorMode.handleRegisters(new String[] {"B", "1"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setBreakFlag(true);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register B set to 1"));
    }
    
    @Test
    public void testSetDecimalFlag() {
        // Execute the handleRegisters method with "D 0" (set decimal flag to false)
        monitorMode.handleRegisters(new String[] {"D", "0"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setDecimalFlag(false);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register D set to 0"));
    }
    
    @Test
    public void testSetInterruptFlag() {
        // Execute the handleRegisters method with "I 1" (set interrupt flag to true)
        monitorMode.handleRegisters(new String[] {"I", "1"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setInterruptFlag(true);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register I set to 1"));
    }
    
    @Test
    public void testSetZeroFlag() {
        // Execute the handleRegisters method with "Z 0" (set zero flag to false)
        monitorMode.handleRegisters(new String[] {"Z", "0"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setZeroFlag(false);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register Z set to 0"));
    }
    
    @Test
    public void testSetCarryFlag() {
        // Execute the handleRegisters method with "C 1" (set carry flag to true)
        monitorMode.handleRegisters(new String[] {"C", "1"});
        
        // Verify CPU method was called with correct value
        verify(mockCpu).setCarryFlag(true);
        
        // Verify output contains confirmation message
        assertTrue(outputStream.toString().contains("Register C set to 1"));
    }
    
    @Test
    public void testSetRegisterInvalidRegister() {
        // Execute the handleRegisters method with an invalid register name
        monitorMode.handleRegisters(new String[] {"INVALID", "1"});
        
        // Verify output contains error message
        assertTrue(outputStream.toString().contains("Unknown register: INVALID"));
    }
    
    @Test
    public void testSetRegisterInvalidValue() {
        // Execute the handleRegisters method with an invalid value
        monitorMode.handleRegisters(new String[] {"A", "INVALID"});
        
        // Verify output contains error message
        assertTrue(outputStream.toString().contains("Invalid value format: INVALID"));
    }
    
    @Test
    public void testDisplayRegisterWithNoArgs() {
        // Execute the handleRegisters method with no arguments - should display registers
        monitorMode.handleRegisters(new String[] {});
        
        // Verify showRegisters functionality was invoked
        verify(mockCpu, atLeastOnce()).getAccumulator();
        verify(mockCpu, atLeastOnce()).getXRegister();
        verify(mockCpu, atLeastOnce()).getYRegister();
        verify(mockCpu, atLeastOnce()).getProgramCounter();
        verify(mockCpu, atLeastOnce()).getStackPointer();
        
        // Verify individual flag methods are called instead of getFlags()
        verify(mockCpu, atLeastOnce()).isNegativeFlag();
        verify(mockCpu, atLeastOnce()).isOverflowFlag();
        verify(mockCpu, atLeastOnce()).isBreakFlag();
        verify(mockCpu, atLeastOnce()).isDecimalFlag();
        verify(mockCpu, atLeastOnce()).isInterruptFlag();
        verify(mockCpu, atLeastOnce()).isZeroFlag();
        verify(mockCpu, atLeastOnce()).isCarryFlag();
        
        // Verify the output contains register headers
        String output = outputStream.toString();
        assertTrue(output.contains("CPU Registers:"));
    }
} 