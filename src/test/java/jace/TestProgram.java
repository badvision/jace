package jace;

import static jace.TestUtils.runAssemblyCode;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jace.apple2e.Full65C02Test;
import jace.apple2e.MOS65C02;
import jace.core.Computer;

public class TestProgram {
    // Tests could be run in any order so it is really important that all registers/flags are preserved!
    public static enum Flag {
        CARRY_SET("BCS +", "Carry should be set"),
        CARRY_CLEAR("BCC +", "Carry should be clear"),
        ZERO_SET("BEQ +", "Zero should be set"),
        IS_ZERO("BEQ +", "Zero should be clear"),
        ZERO_CLEAR("BNE +", "Zero should be clear"),
        NOT_ZERO("BNE +", "Zero should be clear"),
        NEGATIVE("BMI +", "Negative should be set"),
        POSITIVE("BPL +", "Negative should be clear"),
        OVERFLOW_SET("BVS +", "Overflow should be set"),
        OVERFLOW_CLEAR("BVC +", "Overflow should be clear"),
        DECIMAL_SET("""
            PHP
            PHA
            PHP
            PLA
            BIT #%00001000
            BEQ ++
            PLA
            PLP
            BRA +
        ++  ; Error
        """, "Decimal should be set"),
        DECIMAL_CLEAR("""
            PHP
            PHA
            PHP
            PLA
            BIT #%00001000
            BNE ++
            PLA
            PLP
            BRA +
        ++  ; Error
        """, "Decimal should be clear"),
        INTERRUPT_SET("""
            PHP
            PHA
            PHP
            PLA
            BIT #%00000100
            BEQ ++
            PLA
            PLP
            BRA +
        ++  ; Error
        """, "Interrupt should be set"),
        INTERRUPT_CLEAR("""
            PHP
            PHA
            PHP
            PLA
            BIT #%00000100
            BNE ++
            PLA
            PLP
            BRA +
        ++  ; Error
        """, "Interrupt should be clear"),;
        String code;
        String condition;
        Flag(String code, String condition) {
            this.code = code;
            this.condition = condition;
        }
    }

    ArrayList<String> lines = new ArrayList<>();
    Consumer<Byte> timerHandler;
    Consumer<Byte> tickCountHandler;
    Consumer<Byte> errorHandler;
    Consumer<Byte> stopHandler;
    Consumer<Byte> progressHandler;
    Consumer<Byte> traceHandler;

    public TestProgram() {
        lines.add(TestProgram.UNIT_TEST_MACROS);
    }

    public TestProgram(String line1) {
        this();
        lines.add(line1);
    }

    public TestProgram(String line1, int tickCount) {
        this();
        assertTimed(line1, tickCount);
    }

    int tickCount = 0;

    int timerStart = 0;
    int timerLastMark = 0;
    int timerEnd = 0;
    int timerLastEllapsed = 0;
    int lastBreakpoint = -1;
    int maxTicks = 10000;
    boolean programCompleted = false;
    boolean programReportedError = false;
    boolean programRunning = false;
    List<String> errors = new ArrayList<>();
    List<Integer> timings = new ArrayList<>();

    ProgramException lastError = null;
    public static String UNIT_TEST_MACROS = """
        !cpu 65c02
    !macro extendedOp .code, .val {!byte $FC, .code, .val}
    !macro startTimer {+extendedOp $10, $80}
    !macro markTimer {+extendedOp $10, $81}
    !macro stopTimer {+extendedOp $10, $82}
    !macro assertTicks .ticks {+extendedOp $11, .ticks}
    !macro stop .p1, .p2 {
        +extendedOp .p1, .p2
        +extendedOp $13, $ff
        +traceOff
    -
        JMP -
    }
    !macro throwError .errorCode {+stop $12, .errorCode}
    !macro recordError .errorCode {+extendedOp $12, .errorCode}
    !macro success {+stop $14, $ff}
    !macro breakpoint .num {+extendedOp $14, .num}
    !macro traceOn {+extendedOp $15, $01}
    !macro traceOff {+extendedOp $15, $00}
    !macro resetRegs {
        LDA #0
        LDX #0
        LDY #0
        PHA
        PLP
        TXS
        PHP
    }
        +resetRegs
    """;
    public static String INDENT = "    ";

    public void attach() {
        timerHandler = this::handleTimer;
        tickCountHandler = this::countAndCompareTicks;
        errorHandler = this::recordError;
        stopHandler = b->stop();
        progressHandler = this::recordProgress;
        traceHandler = this::handleTrace;

        Emulator.withComputer(c-> {
            MOS65C02 cpu = (MOS65C02) c.getCpu();
            cpu.registerExtendedCommandHandler(0x10, timerHandler);
            cpu.registerExtendedCommandHandler(0x11, tickCountHandler);
            cpu.registerExtendedCommandHandler(0x12, errorHandler);
            cpu.registerExtendedCommandHandler(0x13, stopHandler);
            cpu.registerExtendedCommandHandler(0x14, progressHandler);
            cpu.registerExtendedCommandHandler(0x15, traceHandler);
        });
    }

    public void detach() {
        Emulator.withComputer(c-> {
            MOS65C02 cpu = (MOS65C02) c.getCpu();
            cpu.unregisterExtendedCommandHandler(timerHandler);
            cpu.unregisterExtendedCommandHandler(tickCountHandler);
            cpu.unregisterExtendedCommandHandler(errorHandler);
            cpu.unregisterExtendedCommandHandler(stopHandler);
            cpu.unregisterExtendedCommandHandler(progressHandler);
            cpu.unregisterExtendedCommandHandler(traceHandler);
        });
    }

    private void handleTimer(byte val) {
        switch (val) {
            case (byte)0x80:
                timerStart = tickCount;
                timerLastMark = tickCount;
                break;
            case (byte)0x82:
                timerEnd = tickCount;
                // Fall through
            case (byte)0x81:
                // Don't count the time spent on the timer commands!
                timerLastEllapsed = (tickCount - timerLastMark) - 4;
                timerLastMark = tickCount;
                break;
            default:
                lastError = new ProgramException("Unknown timer command %s".formatted(val), lastBreakpoint);
                stop();
        }
    }

    private void countAndCompareTicks(byte val) {
        int expectedTickCountNum = val;
        int expectedTickCount = timings.get(expectedTickCountNum);
        String errorMessage = lastError != null ? lastError.getProgramLocation() : "";
        if (timerLastEllapsed != expectedTickCount) {
            lastError = new ProgramException("Expected %s ticks, instead counted %s <<%s>>".formatted(expectedTickCount, timerLastEllapsed, errorMessage), lastBreakpoint);
            stop();
        }
    }

    private void recordError(byte v) {
        int val = v & 0x0ff;

        if (val >= 0 && val < errors.size()) {
            lastError = new ProgramException(errors.get(val), lastBreakpoint);
        } else if (val == 255) {
            lastError = null;
        } else {
            lastError = new ProgramException("Error %s".formatted(val), lastBreakpoint);
        }
    }

    public TestProgram defineError(String error) {
        errors.add(error);
        return this;
    }

    private void stop() {
        programReportedError = lastError != null;
        programRunning = false;
    }

    private void recordProgress(byte val) {
        if (val == (byte)0xff) {
            programCompleted = true;
            return;
        } else {
            lastBreakpoint = val;
        }
    }

    private void handleTrace(byte val) {
        if (val == (byte)0x01) {
            System.out.println("Trace on");
            Full65C02Test.cpu.setTraceEnabled(true);
        } else {
            System.out.println("Trace off");
            Full65C02Test.cpu.setTraceEnabled(false);
        }
    }

    public TestProgram assertTimed(String line, int ticks) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace[2].toString();
        int errNum = errors.size();
        errors.add("Expected %s ticks for %s <<%s>>".formatted(ticks, line, caller));
        int timingNum = timings.size();
        timings.add(ticks);
        lines.add(INDENT+"+startTimer");
        lines.add(line);
        lines.add(INDENT+"+markTimer");
        lines.add(INDENT+"+recordError %s".formatted(errNum));
        lines.add(INDENT+"+assertTicks %s ; Check for %s cycles".formatted(timingNum, ticks));
        lines.add(INDENT+"+recordError %s".formatted(255));
        return this;
    }

    public TestProgram add(String line) {
        lines.add(line);
        return this;
    }

    public TestProgram assertFlags(TestProgram.Flag... flags) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace[2].toString();
        for (TestProgram.Flag flag : flags)
            _test(TestProgram.INDENT + flag.code, flag.condition + "<<" + caller + ">>");
        return this;
    }

    /* Test the A register for a specific value */
    public TestProgram assertA(int val) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace[2].toString();
        String condition = "A != %s <<%s>>".formatted(Integer.toHexString(val), caller);
        _test("""
            PHP
            CMP #%s
            BNE ++
            PLP
            BRA +
        ++  ; Error
        """.formatted(val), condition + " in " + caller);
        return this;
    }

    /* Test X register for a specific value */
    public TestProgram assertX(int val) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace[2].toString();
        String condition = "X != %s <<%s>>".formatted(Integer.toHexString(val), caller);
        _test("""
            PHP
            CPX #%s
            BNE ++
            PLP
            BRA +
        ++  ; Error
        """.formatted(val), condition);
        return this;
    }

    /* Test Y register for a specific value */
    public TestProgram assertY(int val) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace[2].toString();
        String condition = "Y != %s <<%s>>".formatted(Integer.toHexString(val), caller);
        _test("""
            PHP
            CPY #%s
            BNE ++
            PLP
            BRA +
        ++  ; Error
        """.formatted(val), condition);
        return this;
    }

    /* Test an address for a specific value.  If the value is incorrect, leave it in A for inspection */
    public TestProgram assertAddrVal(int address, int val) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace[2].toString();
        String condition = "$%s != %s <<%s>>".formatted(Integer.toHexString(address), Integer.toHexString(val), caller);
        _test("""
            PHP
            PHA
            LDA $%s
            CMP #%s
            BNE ++
            PLA
            PLP
            BRA +
        ++  ; Error
            LDA $%s
        """.formatted(Integer.toHexString(address), val, val), condition);
        return this;
    }
    
    /* Use provided code to test a condition.  If successful it should jump or branch to +
     * If unsuccessful the error condition will be reported.
     * 
     * @param code The code to test
     * @param condition The condition to report if the test fails
     */
    public TestProgram test(String code, String condition) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace[2].toString();
        _test(code, condition + "<<" + caller + ">>");
        return this;
    }

    private void _test(String code, String condition) {
        int errorNum = errors.size();
        errors.add(condition);
        lines.add("""
            ; << Test %s
        %s
            +throwError %s
        +   ; >> Test """.formatted(condition, code, errorNum));
    }
    
    /**
     * Note the current breakpoint, helpful in understanding error locations
     * 
     * @param num Error number to report in any error that occurs
     */
    public TestProgram breakpoint(int num) {
        lines.add(INDENT + "+breakpoint %s".formatted(num));
        return this;
    }

    /**
     * Reset the registers to 0, clear the stack, and clear flags
     * 
     * @return
     */
    public TestProgram resetRegisters() {
        lines.add(INDENT + "+resetRegs");
        return this;
    }

    /**
     * Turn on or off tracing
     * 
     * @param state True to turn on tracing, false to turn it off
     */
    public TestProgram setTrace(boolean state) {
        lines.add(INDENT +"+trace%s".formatted(state ? "On" : "Off"));
        return this;
    }

    /**
     * Render the program as unassembled code
     * 
     * @return The program as a string
     */
    public String build() {
        lines.add(INDENT +"+success");
        return String.join("\n", lines);
    }

    /**
     * Run the program for a specific number of ticks, or until it completes (whichever comes first)
     * 
     * @param ticks The number of ticks to run the program
     * @throws ProgramException If the program reports an error
     */
    public void runForTicks(int ticks) throws ProgramException {
        this.maxTicks = ticks;
        run();
    }

    /**
     * Run the program until it completes, reports an error, or reaches the maximum number of ticks
     * 
     * @throws ProgramException If the program reports an error
     */
    public void run() throws ProgramException {
        Computer computer = Emulator.withComputer(c->c, null);
        MOS65C02 cpu = (MOS65C02) computer.getCpu();
        
        attach();
        programRunning = true;
        String program = build();
        // We have to run the program more carefully so just load it first
        try {
            runAssemblyCode(program, 0);
        } catch (Exception e) {
            throw new ProgramException(e.getMessage() + "\n" + program, -1);
        }
        cpu.resume();
        try {
            for (int i=0; i < maxTicks; i++) {
                cpu.doTick();
                tickCount++;
                if (programReportedError) {
                    throw lastError;
                }
                if (!programRunning) {
                    break;
                }
            }
        } finally {
            cpu.suspend();        
            detach();
        }
        assertFalse("Test reported an error", programReportedError);
        assertFalse("Program never ended fully after " + tickCount + " ticks; got to breakpoint " + lastBreakpoint, programRunning);
        assertTrue("Test did not complete fully", programCompleted);
    }
}