package jace.terminal;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import jace.apple2e.MOS65C02;

/**
 * A testable version of MonitorMode that can be used in unit tests
 * by mocking CPU interactions without requiring a real emulator.
 */
public class TestableMonitorMode extends MonitorMode {
    // Mock CPU state
    private int mockA = 0;
    private int mockX = 0;
    private int mockY = 0;
    private int mockPC = 0;
    private int mockStack = 0xFF;
    private boolean mockN = false;
    private boolean mockV = false;
    private boolean mockB = false;
    private boolean mockD = false;
    private boolean mockI = false;
    private boolean mockZ = false;
    private int mockC = 0;
    
    private final MockCPU mockCpu = new MockCPU();
    private final JaceTerminal mockTerminal;
    
    /**
     * Creates a new TestableMonitorMode with an output stream 
     * that can be examined in tests
     */
    public TestableMonitorMode() {
        this(new ByteArrayOutputStream());
    }
    
    /**
     * Creates a new TestableMonitorMode with the specified output stream
     */
    public TestableMonitorMode(ByteArrayOutputStream outputStream) {
        this(new MockTerminal(outputStream));
    }
    
    /**
     * Creates a new TestableMonitorMode with the specified terminal
     */
    public TestableMonitorMode(JaceTerminal terminal) {
        super(terminal);
        this.mockTerminal = terminal;
    }
    
    /**
     * Helper class to simulate a CPU for testing
     */
    private class MockCPU extends MOS65C02 {
        @Override
        public int getProgramCounter() {
            return mockPC;
        }
        
        @Override
        public void setProgramCounter(int pc) {
            mockPC = pc;
        }
        
        @Override
        public int getAccumulator() {
            return mockA;
        }
        
        @Override
        public void setAccumulator(int value) {
            mockA = value;
        }
        
        @Override
        public int getXRegister() {
            return mockX;
        }
        
        @Override
        public void setXRegister(int value) {
            mockX = value;
        }
        
        @Override
        public int getYRegister() {
            return mockY;
        }
        
        @Override
        public void setYRegister(int value) {
            mockY = value;
        }
        
        @Override
        public int getStackPointer() {
            return mockStack;
        }
        
        @Override
        public void setStackPointer(int value) {
            mockStack = value;
        }
        
        @Override
        public boolean isNegativeFlag() {
            return mockN;
        }
        
        @Override
        public void setNegativeFlag(boolean value) {
            mockN = value;
        }
        
        @Override
        public boolean isOverflowFlag() {
            return mockV;
        }
        
        @Override
        public void setOverflowFlag(boolean value) {
            mockV = value;
        }
        
        @Override
        public boolean isBreakFlag() {
            return mockB;
        }
        
        @Override
        public void setBreakFlag(boolean value) {
            mockB = value;
        }
        
        @Override
        public boolean isDecimalFlag() {
            return mockD;
        }
        
        @Override
        public void setDecimalFlag(boolean value) {
            mockD = value;
        }
        
        @Override
        public boolean isInterruptFlag() {
            return mockI;
        }
        
        @Override
        public void setInterruptFlag(boolean value) {
            mockI = value;
        }
        
        @Override
        public boolean isZeroFlag() {
            return mockZ;
        }
        
        @Override
        public void setZeroFlag(boolean value) {
            mockZ = value;
        }
        
        @Override
        public boolean isCarryFlag() {
            return mockC > 0;
        }
        
        @Override
        public void setCarryFlag(boolean value) {
            mockC = value ? 1 : 0;
        }
        
        @Override
        public void tick() {
            // Do nothing
        }
        
        @Override
        public void reset() {
            // Do nothing
        }
        
        @Override
        public void generateInterrupt() {
            // Do nothing
        }
        
        @Override
        public void pushPC() {
            // Do nothing
        }
        
        @Override
        public void JSR(int address) {
            // Do nothing
        }
        
        @Override
        protected void executeOpcode() {
            // Do nothing
        }
        
        @Override
        protected String getDeviceName() {
            return "MockCPU";
        }
        
        @Override
        public void clearState() {
            // Do nothing
        }
    }
    
    /**
     * Mock terminal for testing
     */
    private static class MockTerminal extends JaceTerminal {
        public MockTerminal(ByteArrayOutputStream outputStream) {
            super(new BufferedReader(new InputStreamReader(System.in)), new PrintStream(outputStream));
        }
    }
    
    /**
     * Override the getCpu method to return our mock CPU
     */
    @Override
    public MOS65C02 getCpu() {
        return mockCpu;
    }
    
    /**
     * Get the test output stream
     */
    public PrintStream getTestOutput() {
        return mockTerminal.getOutput();
    }
    
    /**
     * Get the terminal
     */
    public JaceTerminal getTerminal() {
        return mockTerminal;
    }
    
    // Accessor methods for mock CPU state for testing
    
    public int getMockA() {
        return mockA;
    }
    
    public void setMockA(int mockA) {
        this.mockA = mockA;
    }
    
    public int getMockX() {
        return mockX;
    }
    
    public void setMockX(int mockX) {
        this.mockX = mockX;
    }
    
    public int getMockY() {
        return mockY;
    }
    
    public void setMockY(int mockY) {
        this.mockY = mockY;
    }
    
    public int getMockPC() {
        return mockPC;
    }
    
    public void setMockPC(int mockPC) {
        this.mockPC = mockPC;
    }
    
    public int getMockStack() {
        return mockStack;
    }
    
    public void setMockStack(int mockStack) {
        this.mockStack = mockStack;
    }
    
    public boolean isMockN() {
        return mockN;
    }
    
    public void setMockN(boolean mockN) {
        this.mockN = mockN;
    }
    
    public boolean isMockV() {
        return mockV;
    }
    
    public void setMockV(boolean mockV) {
        this.mockV = mockV;
    }
    
    public boolean isMockB() {
        return mockB;
    }
    
    public void setMockB(boolean mockB) {
        this.mockB = mockB;
    }
    
    public boolean isMockD() {
        return mockD;
    }
    
    public void setMockD(boolean mockD) {
        this.mockD = mockD;
    }
    
    public boolean isMockI() {
        return mockI;
    }
    
    public void setMockI(boolean mockI) {
        this.mockI = mockI;
    }
    
    public boolean isMockZ() {
        return mockZ;
    }
    
    public void setMockZ(boolean mockZ) {
        this.mockZ = mockZ;
    }
    
    public int getMockC() {
        return mockC;
    }
    
    public void setMockC(int mockC) {
        this.mockC = mockC;
    }
} 