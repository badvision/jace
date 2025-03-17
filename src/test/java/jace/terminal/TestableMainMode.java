package jace.terminal;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import jace.Emulator;
import jace.apple2e.MOS65C02;

/**
 * A testable version of MainMode that can be used in unit tests
 * by mocking CPU interactions without requiring a real emulator.
 */
public class TestableMainMode extends MainMode {
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
    private ByteArrayOutputStream outputStream;
    
    /**
     * Creates a new TestableMainMode with an output stream 
     * that can be examined in tests
     */
    public TestableMainMode() {
        this(new ByteArrayOutputStream());
    }
    
    /**
     * Creates a new TestableMainMode with the specified output stream
     */
    public TestableMainMode(ByteArrayOutputStream outputStream) {
        this(new MockTerminal(outputStream));
        this.outputStream = outputStream;
    }
    
    /**
     * Creates a new TestableMainMode with the specified terminal
     */
    public TestableMainMode(JaceTerminal terminal) {
        super(terminal);
        this.mockTerminal = terminal;
        this.outputStream = new ByteArrayOutputStream();
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
        
        @Override
        public EmulatorInterface getEmulator() {
            return new MockEmulator();
        }
    }
    
    /**
     * Mock emulator interface
     */
    private static class MockEmulator implements EmulatorInterface {
        @Override
        public void withComputer(java.util.function.Consumer<jace.apple2e.Apple2e> action) {
            // No-op for testing
        }
        
        @Override
        public <T> T withComputer(java.util.function.Function<jace.apple2e.Apple2e, T> function, T defaultValue) {
            return defaultValue;
        }
        
        @Override
        public void whileSuspended(java.util.function.Consumer<jace.apple2e.Apple2e> action) {
            // No-op for testing
        }
    }
    
    /**
     * Override the getCPU method to return our mock CPU
     */
    @Override
    protected MOS65C02 getCPU() {
        return mockCpu;
    }
    
    /**
     * Get the test output stream
     */
    public ByteArrayOutputStream getOutputStream() {
        return outputStream;
    }
    
    /**
     * Get the terminal
     */
    public JaceTerminal getTerminal() {
        return mockTerminal;
    }
    
    // Override the accessor methods to use our mock state
    
    @Override
    protected int getAccumulator(MOS65C02 cpu) {
        return mockA;
    }
    
    @Override
    protected void setAccumulator(MOS65C02 cpu, int value) {
        mockA = value;
    }
    
    @Override
    protected int getXRegister(MOS65C02 cpu) {
        return mockX;
    }
    
    @Override
    protected void setXRegister(MOS65C02 cpu, int value) {
        mockX = value;
    }
    
    @Override
    protected int getYRegister(MOS65C02 cpu) {
        return mockY;
    }
    
    @Override
    protected void setYRegister(MOS65C02 cpu, int value) {
        mockY = value;
    }
    
    @Override
    protected int getStackPointer(MOS65C02 cpu) {
        return mockStack;
    }
    
    @Override
    protected void setStackPointer(MOS65C02 cpu, int value) {
        mockStack = value;
    }
    
    @Override
    protected boolean isNegativeFlag(MOS65C02 cpu) {
        return mockN;
    }
    
    @Override
    protected void setNegativeFlag(MOS65C02 cpu, boolean value) {
        mockN = value;
    }
    
    @Override
    protected boolean isOverflowFlag(MOS65C02 cpu) {
        return mockV;
    }
    
    @Override
    protected void setOverflowFlag(MOS65C02 cpu, boolean value) {
        mockV = value;
    }
    
    @Override
    protected boolean isBreakFlag(MOS65C02 cpu) {
        return mockB;
    }
    
    @Override
    protected void setBreakFlag(MOS65C02 cpu, boolean value) {
        mockB = value;
    }
    
    @Override
    protected boolean isDecimalFlag(MOS65C02 cpu) {
        return mockD;
    }
    
    @Override
    protected void setDecimalFlag(MOS65C02 cpu, boolean value) {
        mockD = value;
    }
    
    @Override
    protected boolean isInterruptFlag(MOS65C02 cpu) {
        return mockI;
    }
    
    @Override
    protected void setInterruptFlag(MOS65C02 cpu, boolean value) {
        mockI = value;
    }
    
    @Override
    protected boolean isZeroFlag(MOS65C02 cpu) {
        return mockZ;
    }
    
    @Override
    protected void setZeroFlag(MOS65C02 cpu, boolean value) {
        mockZ = value;
    }
    
    @Override
    protected boolean isCarryFlag(MOS65C02 cpu) {
        return mockC > 0;
    }
    
    @Override
    protected void setCarryFlag(MOS65C02 cpu, boolean value) {
        mockC = value ? 1 : 0;
    }
    
    // Getters and setters for the mock state for test verification
    
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