package jace.terminal;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

import jace.apple2e.MOS65C02;

/**
 * Tests for the MonitorMode CPU register access functionality using the TestableMonitorMode.
 * This class tests the direct manipulation of CPU registers via getters/setters.
 */
public class MonitorModeCPUAccessTest {
    private TestableMonitorMode monitorMode;
    private ByteArrayOutputStream outputStream;
    
    @Before
    public void setUp() {
        outputStream = new ByteArrayOutputStream();
        monitorMode = new TestableMonitorMode(outputStream);
    }
    
    /**
     * Test the CPU register getters and setters work correctly
     */
    @Test
    public void testCPUAccessors() {
        // Test setting and getting CPU state
        monitorMode.setMockA(0x42);
        assertEquals(0x42, monitorMode.getMockA());
        
        monitorMode.setMockX(0xFF);
        assertEquals(0xFF, monitorMode.getMockX());
        
        monitorMode.setMockY(0x80);
        assertEquals(0x80, monitorMode.getMockY());
        
        monitorMode.setMockPC(0xC000);
        assertEquals(0xC000, monitorMode.getMockPC());
        
        monitorMode.setMockStack(0xF0);
        assertEquals(0xF0, monitorMode.getMockStack());
        
        monitorMode.setMockN(true);
        assertTrue(monitorMode.isMockN());
        
        monitorMode.setMockV(false);
        assertFalse(monitorMode.isMockV());
        
        monitorMode.setMockB(true);
        assertTrue(monitorMode.isMockB());
        
        monitorMode.setMockD(false);
        assertFalse(monitorMode.isMockD());
        
        monitorMode.setMockI(true);
        assertTrue(monitorMode.isMockI());
        
        monitorMode.setMockZ(false);
        assertFalse(monitorMode.isMockZ());
        
        monitorMode.setMockC(1);
        assertEquals(1, monitorMode.getMockC());
    }
    
    /**
     * Test CPU register access methods correctly pass through to the mock CPU
     */
    @Test
    public void testCPURegisterMethods() throws Exception {
        // Set up mock CPU state
        monitorMode.setMockA(0x42);
        monitorMode.setMockX(0xFF);
        monitorMode.setMockY(0x80);
        monitorMode.setMockPC(0xC000);
        monitorMode.setMockStack(0xF0);
        monitorMode.setMockN(true);
        monitorMode.setMockV(false);
        monitorMode.setMockB(true);
        monitorMode.setMockD(false);
        monitorMode.setMockI(true);
        monitorMode.setMockZ(false);
        monitorMode.setMockC(1);
        
        // Get the CPU
        MOS65C02 cpu = monitorMode.getCpu();
        
        // Test the getters
        assertEquals(0x42, cpu.getAccumulator());
        assertEquals(0xFF, cpu.getXRegister());
        assertEquals(0x80, cpu.getYRegister());
        assertEquals(0xF0, cpu.getStackPointer());
        assertTrue(cpu.isNegativeFlag());
        assertFalse(cpu.isOverflowFlag());
        assertTrue(cpu.isBreakFlag());
        assertFalse(cpu.isDecimalFlag());
        assertTrue(cpu.isInterruptFlag());
        assertFalse(cpu.isZeroFlag());
        assertTrue(cpu.isCarryFlag());
        
        // Test the setters
        cpu.setAccumulator(0x33);
        assertEquals(0x33, monitorMode.getMockA());
        
        cpu.setXRegister(0x44);
        assertEquals(0x44, monitorMode.getMockX());
        
        cpu.setYRegister(0x55);
        assertEquals(0x55, monitorMode.getMockY());
        
        cpu.setStackPointer(0xCC);
        assertEquals(0xCC, monitorMode.getMockStack());
        
        cpu.setNegativeFlag(false);
        assertFalse(monitorMode.isMockN());
        
        cpu.setZeroFlag(true);
        assertTrue(monitorMode.isMockZ());
        
        cpu.setCarryFlag(false);
        assertEquals(0, monitorMode.getMockC());
    }
    
    /**
     * Test the registers command works correctly with our mock CPU
     */
    @Test
    public void testRegistersCommandMethod() {
        // Set A register
        monitorMode.handleRegisters(new String[] {"A", "$42"});
        assertEquals(0x42, monitorMode.getMockA());
        
        // Set X register
        monitorMode.handleRegisters(new String[] {"X", "$FF"});
        assertEquals(0xFF, monitorMode.getMockX());
        
        // Set Y register
        monitorMode.handleRegisters(new String[] {"Y", "128"});
        assertEquals(128, monitorMode.getMockY());
        
        // Set PC
        monitorMode.handleRegisters(new String[] {"PC", "$C000"});
        assertEquals(0xC000, monitorMode.getMockPC());
        
        // Set Stack
        monitorMode.handleRegisters(new String[] {"S", "$F0"});
        assertEquals(0xF0, monitorMode.getMockStack());
        
        // Set N flag
        monitorMode.handleRegisters(new String[] {"N", "1"});
        assertTrue(monitorMode.isMockN());
        
        // Set Z flag
        monitorMode.handleRegisters(new String[] {"Z", "0"});
        assertFalse(monitorMode.isMockZ());
        
        // Set C flag
        monitorMode.handleRegisters(new String[] {"C", "1"});
        assertEquals(1, monitorMode.getMockC());
    }
} 