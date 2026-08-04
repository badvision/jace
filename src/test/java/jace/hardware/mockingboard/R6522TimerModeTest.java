package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import jace.core.Utility;

/**
 * Verifies 6522 Timer 1 latch/counter semantics and the one-shot vs. free-run
 * distinction, plus the T1LH/T1CH write asymmetry.
 *
 * <h3>Reference behaviour (MAME 6522via.cpp)</h3>
 * <pre>
 *   case VIA_T1CL:
 *   case VIA_T1LL:   m_t1ll = data;                       // latch low only
 *   case VIA_T1LH:   m_t1lh = data; clear_int(INT_T1);    // latch high, NO start
 *   case VIA_T1CH:   m_t1ch = m_t1lh = data;
 *                    m_t1cl = m_t1ll;                     // counter &lt;- latch
 *                    clear_int(INT_T1);
 *                    m_t1-&gt;adjust(TIMER1_VALUE + IFR_DELAY);
 *                    m_t1_active = 1;                     // START
 *
 *   TIMER_CALLBACK t1_tick:
 *     if (T1_CONTINUOUS(m_acr))  reload from the latch and keep going
 *     else                       m_t1_active = 0;         // one-shot stops
 * </pre>
 * The load-critical points are: a write to T1CH is what starts the timer and
 * copies the latch into the counter; a write to T1LH sets the high latch but
 * does <em>not</em> start it or touch the counter; and free-run reloads from the
 * latch on expiry while one-shot stops.
 *
 * <p>A register-frame comparison cannot see any of this. A timer with the wrong
 * period still produces correct register <em>values</em>, just at the wrong
 * <em>rate</em>; and a one-shot that wrongly free-runs produces extra
 * interrupts that a lock-step frame oracle silently absorbs.
 */
public class R6522TimerModeTest {

    private R6522 via;

    private static R6522 newVia() {
        return new R6522() {
            @Override public String getShortName() { return "test-via"; }
            @Override public void sendOutputA(int value) { /* not under test */ }
            @Override public void sendOutputB(int value) { /* not under test */ }
            @Override public int receiveOutputA() { return 0; }
            @Override public int receiveOutputB() { return 0; }
        };
    }

    private void write(R6522.Register r, int value) {
        via.writeRegister(r.val, value);
    }

    private int read(R6522.Register r) {
        return via.readRegister(r.val);
    }

    /** Programs the T1 latch and starts the timer, as a driver does. */
    private void startTimer1(int latch, boolean freeRun) {
        write(R6522.Register.ACR, freeRun ? 0x40 : 0x00);
        write(R6522.Register.IER, 0xC0);              // enable T1
        write(R6522.Register.T1CL, latch & 0xff);     // low latch
        write(R6522.Register.T1CH, (latch >> 8) & 0xff); // high latch + start
    }

    /** Ticks until T1's flag sets, acknowledges it, and returns the tick count. */
    private int ticksToNextTimer1Fire(int limit) {
        for (int i = 1; i <= limit; i++) {
            via.tick();
            if (via.timer1IRQ) {
                write(R6522.Register.IFR, 0x40);
                return i;
            }
        }
        return -1;
    }

    @Before
    public void setUp() {
        Utility.setHeadlessMode(true);
        via = newVia();
    }

    @Test
    public void writingTimer1HighCounterStartsTheTimerAndLoadsTheCounterFromTheLatch() {
        write(R6522.Register.T1CL, 0x34);
        write(R6522.Register.T1CH, 0x12);

        assertEquals("T1CL/T1CH writes assemble a 16-bit latch", 0x1234, via.timer1latch);
        assertEquals("writing T1CH copies the latch into the counter",
                     0x1234, via.timer1counter);
        assertTrue("writing T1CH starts the timer", via.timer1running);
    }

    @Test
    public void writingTimer1HighLatchDoesNotStartTheTimerOrLoadTheCounter() {
        // Establish a known stopped state with a distinct counter value.
        startTimer1(0x0005, false);
        for (int i = 0; i < 10; i++) {
            via.tick();
        }
        assertFalse("precondition: the one-shot must have stopped", via.timer1running);
        int counterBefore = via.timer1counter;

        write(R6522.Register.T1LH, 0x7F);

        assertEquals("T1LH updates the high byte of the latch",
                     0x7F00, via.timer1latch & 0xff00);
        assertEquals("T1LH must not disturb the counter", counterBefore, via.timer1counter);
        assertFalse("T1LH must not start the timer", via.timer1running);
    }

    @Test
    public void writingTimer1HighLatchClearsThePendingFlag() {
        // MAME: case VIA_T1LH -> clear_int(INT_T1).
        startTimer1(5, true);
        assertTrue("precondition: the timer must fire", ticksToNextTimer1Fire(20) > 0);
        via.timer1IRQ = true;   // re-raise without waiting

        write(R6522.Register.T1LH, 0x00);

        assertFalse("a T1LH write acknowledges the T1 interrupt", via.timer1IRQ);
    }

    @Test
    public void oneShotTimerStopsAfterASingleExpiry() {
        startTimer1(5, false);
        assertTrue("the one-shot must fire once", ticksToNextTimer1Fire(20) > 0);

        assertFalse("a one-shot timer stops after expiring", via.timer1running);
        assertEquals("and must not fire again", -1, ticksToNextTimer1Fire(50));
    }

    @Test
    public void freeRunTimerReloadsFromTheLatchAndKeepsFiringAtAConstantPeriod() {
        startTimer1(10, true);

        int first = ticksToNextTimer1Fire(40);
        int second = ticksToNextTimer1Fire(40);
        int third = ticksToNextTimer1Fire(40);

        assertTrue("the free-run timer must fire repeatedly",
                   first > 0 && second > 0 && third > 0);
        assertEquals("the free-run interval must be constant", second, third);
        assertTrue("the free-run timer must keep running", via.timer1running);
    }

    @Test
    public void freeRunPeriodIsTheLatchPlusOneClocks() {
        // The counter runs latch, latch-1, ... 0, -1 and reloads on the tick that
        // takes it below zero, so a latch of N yields N+1 clocks per period.
        // That is the same N+1 the 6522 datasheet gives for the continuous mode
        // interrupt interval (MAME models it as TIMER1_VALUE + IFR_DELAY, where
        // the extra IFR_DELAY is the flag-visibility lag rather than the period).
        startTimer1(10, true);
        ticksToNextTimer1Fire(40);   // discard the first, which includes the load
        assertEquals("latch 10 gives an 11-clock period", 11, ticksToNextTimer1Fire(40));

        via = newVia();
        startTimer1(100, true);
        ticksToNextTimer1Fire(400);
        assertEquals("latch 100 gives a 101-clock period", 101, ticksToNextTimer1Fire(400));
    }

    @Test
    public void switchingAcrToFreeRunRestartsAStoppedTimer() {
        // MAME: writing ACR with T1_CONTINUOUS set re-adjusts the timer and sets
        // m_t1_active = 1, so a stopped one-shot resumes counting.
        startTimer1(5, false);
        ticksToNextTimer1Fire(20);
        assertFalse("precondition: the one-shot must have stopped", via.timer1running);

        write(R6522.Register.ACR, 0x40);

        assertTrue("selecting free-run mode makes the timer active again", via.timer1running);
        assertTrue("and it must fire again", ticksToNextTimer1Fire(20) > 0);
    }

    @Test
    public void timer1CounterIsReadableAsTwoBytesWhileRunning() {
        startTimer1(0x0200, true);
        for (int i = 0; i < 0x80; i++) {
            via.tick();
        }

        int counter = (read(R6522.Register.T1CH) << 8) | read(R6522.Register.T1CL);
        assertEquals("the counter readback must match the internal counter",
                     via.timer1counter, counter);
        assertTrue("and must have counted down from the latch",
                   counter < 0x0200 && counter > 0);
    }

    @Test
    public void timer1LatchIsReadableIndependentlyOfTheCounter() {
        startTimer1(0x0200, true);
        for (int i = 0; i < 0x80; i++) {
            via.tick();
        }

        int latch = (read(R6522.Register.T1LH) << 8) | read(R6522.Register.T1LL);
        assertEquals("T1LL/T1LH read the latch, not the counter", 0x0200, latch);
        assertTrue("the counter has moved away from the latch",
                   via.timer1counter != latch);
    }

    @Test
    public void timer2IsAlwaysOneShotRegardlessOfAcr() {
        // The 6522's T2 has no free-run mode in the timed-interrupt configuration
        // MAME's t2_tick sets m_t2_active = 0 unconditionally, and ACR bit 5
        // selects pulse counting on PB6, which the Mockingboard does not wire up.
        write(R6522.Register.ACR, 0x40);   // T1 free-run; must not affect T2
        write(R6522.Register.IER, 0xC0 | 0x20);
        write(R6522.Register.T2CL, 5);
        write(R6522.Register.T2CH, 0);
        assertTrue("precondition: T2 must start", via.timer2running);

        for (int i = 0; i < 20; i++) {
            via.tick();
        }

        assertFalse("T2 is one-shot: it stops after expiring", via.timer2running);
    }
}
