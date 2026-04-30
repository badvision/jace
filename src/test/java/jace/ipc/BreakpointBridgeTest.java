package jace.ipc;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import jace.core.Debugger;

public class BreakpointBridgeTest {

    /** Minimal concrete Debugger for testing — no emulator required. */
    private static final Debugger TEST_DEBUGGER = new Debugger() {
        @Override
        public void updateStatus() {
            // no-op
        }
    };

    private BreakpointBridge bridge;

    @Before
    public void setUp() {
        // Clear the static Debugger breakpoint list between tests
        TEST_DEBUGGER.getBreakpoints().clear();
        bridge = new BreakpointBridge(TEST_DEBUGGER);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CyreneOperation opAtPc(int pc) {
        return new CyreneOperation(
                0, 0, 0, pc, 0,
                (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0,
                0, 0, 0,
                0, 0,
                (byte) 0,
                0L, 0L,
                (byte) 0, (byte) 0);
    }

    /** Build a real Cyrene-format breakpoint line. */
    private static String bp(int id, int addr) {
        return String.format("%d;Address=00/%04X;Execute;Action:Stop;Repeat:Always\n", id, addr);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testParseEnabledAddressBreakpoint() {
        bridge.updateFromText(bp(1, 0x4000));

        assertEquals("Expected exactly one breakpoint registered", 1, bridge.count());
        assertEquals("Expected breakpoint id 1 at PC $4000",
                1, bridge.checkBreakpoint(opAtPc(0x4000)));
    }

    @Test
    public void testNonExecuteTriggerIgnored() {
        // Read trigger — not supported, must be ignored
        bridge.updateFromText("1;Address=00/4000;Read;Action:Stop;Repeat:Always\n");

        assertEquals("Non-execute breakpoint must not be registered", 0, bridge.count());
        assertEquals("No match expected",
                -1, bridge.checkBreakpoint(opAtPc(0x4000)));
    }

    @Test
    public void testClearRemovesBreakpoints() {
        bridge.updateFromText(bp(1, 0x1000) + bp(2, 0x2000));

        assertEquals(2, bridge.count());

        bridge.clear();

        assertEquals("count() must be 0 after clear()", 0, bridge.count());
        assertEquals("No match after clear()",
                -1, bridge.checkBreakpoint(opAtPc(0x1000)));
        assertEquals("No match after clear()",
                -1, bridge.checkBreakpoint(opAtPc(0x2000)));
    }

    @Test
    public void testMultipleBreakpoints() {
        bridge.updateFromText(bp(1, 0x1000) + bp(2, 0x2000) + bp(3, 0x3000));

        assertEquals(3, bridge.count());

        assertEquals(1, bridge.checkBreakpoint(opAtPc(0x1000)));
        assertEquals(2, bridge.checkBreakpoint(opAtPc(0x2000)));
        assertEquals(3, bridge.checkBreakpoint(opAtPc(0x3000)));
        assertEquals(-1, bridge.checkBreakpoint(opAtPc(0x4000)));
    }

    @Test
    public void testUpdateFromTextReplacesExistingBreakpoints() {
        bridge.updateFromText(bp(1, 0x1000));
        assertEquals(1, bridge.count());

        // Second call must replace the first set
        bridge.updateFromText(bp(2, 0x2000) + bp(3, 0x3000));
        assertEquals(2, bridge.count());
        assertEquals(-1, bridge.checkBreakpoint(opAtPc(0x1000)));
        assertEquals(2, bridge.checkBreakpoint(opAtPc(0x2000)));
    }

    @Test
    public void testMalformedLineIgnored() {
        // Not enough semicolon fields — should be silently skipped
        bridge.updateFromText("garbage\n");
        assertEquals(0, bridge.count());
    }

    @Test
    public void testNonAddressSourceIgnored() {
        // SystemCall source — not supported
        bridge.updateFromText("1;SystemCall=P8-BF00;Execute;Action:Stop;Repeat:Always\n");
        assertEquals(0, bridge.count());
    }
}
