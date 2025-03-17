package jace.terminal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jace.apple2e.MOS65C02;

/**
 * Tests for the setRegister method in MonitorMode using proper mocking
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
        // Execute the setRegister method with "A $42" (set A to 0x42)
        monitorMode.setRegister(new String[] {"A", "$42"});
        
        // Verify the A register was set correctly using the proper API
        verify(mockCpu).setAccumulator(0x42);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register A set to $42"));
    }
    
    @Test
    public void testSetRegisterX() {
        // Execute the setRegister method with "X $FF" (set X to 0xFF)
        monitorMode.setRegister(new String[] {"X", "$FF"});
        
        // Verify the X register was set correctly
        verify(mockCpu).setXRegister(0xFF);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register X set to $FF"));
    }
    
    @Test
    public void testSetRegisterY() {
        // Execute the setRegister method with "Y 128" (set Y to 128)
        monitorMode.setRegister(new String[] {"Y", "128"});
        
        // Verify the Y register was set correctly
        verify(mockCpu).setYRegister(128);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register Y set to 128"));
    }
    
    @Test
    public void testSetRegisterPC() {
        // Execute the setRegister method with "PC $C000" (set PC to 0xC000)
        monitorMode.setRegister(new String[] {"PC", "$C000"});
        
        // Verify the PC register was set correctly
        verify(mockCpu).setProgramCounter(0xC000);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register PC set to $C000"));
    }
    
    @Test
    public void testSetRegisterStack() {
        // Execute the setRegister method with "S $F0" (set stack to 0xF0)
        monitorMode.setRegister(new String[] {"S", "$F0"});
        
        // Verify the stack pointer was set correctly
        verify(mockCpu).setStackPointer(0xF0);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register S set to $F0"));
    }
    
    @Test
    public void testSetNegativeFlag() {
        // Execute the setRegister method with "N 1" (set negative flag to true)
        monitorMode.setRegister(new String[] {"N", "1"});
        
        // Verify the negative flag was set correctly
        verify(mockCpu).setNegativeFlag(true);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register N set to 1"));
    }
    
    @Test
    public void testSetOverflowFlag() {
        // Execute the setRegister method with "V 0" (set overflow flag to false)
        monitorMode.setRegister(new String[] {"V", "0"});
        
        // Verify the overflow flag was set correctly
        verify(mockCpu).setOverflowFlag(false);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register V set to 0"));
    }
    
    @Test
    public void testSetBreakFlag() {
        // Execute the setRegister method with "B 1" (set break flag to true)
        monitorMode.setRegister(new String[] {"B", "1"});
        
        // Verify the break flag was set correctly
        verify(mockCpu).setBreakFlag(true);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register B set to 1"));
    }
    
    @Test
    public void testSetDecimalFlag() {
        // Execute the setRegister method with "D 0" (set decimal flag to false)
        monitorMode.setRegister(new String[] {"D", "0"});
        
        // Verify the decimal flag was set correctly
        verify(mockCpu).setDecimalFlag(false);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register D set to 0"));
    }
    
    @Test
    public void testSetInterruptFlag() {
        // Execute the setRegister method with "I 1" (set interrupt flag to true)
        monitorMode.setRegister(new String[] {"I", "1"});
        
        // Verify the interrupt flag was set correctly
        verify(mockCpu).setInterruptFlag(true);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register I set to 1"));
    }
    
    @Test
    public void testSetZeroFlag() {
        // Execute the setRegister method with "Z 0" (set zero flag to false)
        monitorMode.setRegister(new String[] {"Z", "0"});
        
        // Verify the zero flag was set correctly
        verify(mockCpu).setZeroFlag(false);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register Z set to 0"));
    }
    
    @Test
    public void testSetCarryFlag() {
        // Execute the setRegister method with "C 1" (set carry flag to true)
        monitorMode.setRegister(new String[] {"C", "1"});
        
        // Verify the carry flag was set correctly
        verify(mockCpu).setCarryFlag(true);
        
        // Verify output message
        assertTrue(outputStream.toString().contains("Register C set to 1"));
    }
    
    @Test
    public void testSetRegisterInvalidRegister() {
        // Execute the setRegister method with an invalid register name
        monitorMode.setRegister(new String[] {"INVALID", "1"});
        
        // Verify no registers were modified
        verifyNoInteractions(mockCpu);
        
        // Verify error message
        assertTrue(outputStream.toString().contains("Unknown register: INVALID"));
    }
    
    @Test
    public void testSetRegisterInvalidValue() {
        // Execute the setRegister method with an invalid value
        monitorMode.setRegister(new String[] {"A", "INVALID"});
        
        // Verify no registers were modified
        verifyNoInteractions(mockCpu);
        
        // Verify error message
        assertTrue(outputStream.toString().contains("Invalid value format: INVALID"));
    }
} 