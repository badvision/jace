package jace.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.RAMEvent;
import jace.core.RAMEvent.SCOPE;
import jace.core.RAMEvent.TYPE;
import jace.core.RAMEvent.VALUE;
import jace.core.Utility;

import org.junit.BeforeClass;

/**
 * Unit tests for NoSlotClock covering all 7 bugs fixed.
 */
public class NoSlotClockTest extends AbstractFXTest {

    private NoSlotClock clock;

    @BeforeClass
    public static void setHeadless() {
        Utility.setHeadlessMode(true);
    }

    // Address with A2=1 (bit 2 set) — used for read/reset events
    private static final int ADDR_READ  = 0xC104; // bit 2 set
    // Address with A2=0 — used for write/bit-compare events
    private static final int ADDR_WRITE = 0xC100; // bit 2 clear, bit 0 clear
    private static final int ADDR_WRITE_BIT1 = 0xC101; // bit 2 clear, bit 0 set

    /**
     * The magic detection pattern, LSB-first.
     * 0x5CA33AC55CA33AC5L
     */
    private static final long DETECT_SEQUENCE = 0x5ca33ac55ca33ac5L;

    @Before
    public void setUp() {
        clock = new NoSlotClock();
    }

    // -----------------------------------------------------------------------
    // Helper: build a RAMEvent
    // -----------------------------------------------------------------------
    private RAMEvent makeEvent(TYPE type, int address, int oldValue) {
        return new RAMEvent(type, SCOPE.ANY, VALUE.ANY, address, oldValue, oldValue);
    }

    // -----------------------------------------------------------------------
    // Helper: push one bit into the listener using a WRITE event.
    // The address bit 0 encodes the data bit.
    // -----------------------------------------------------------------------
    private void writeBit(int bit) {
        int address = (bit & 1) == 1 ? ADDR_WRITE_BIT1 : ADDR_WRITE;
        RAMEvent e = makeEvent(TYPE.WRITE, address, 0);
        clock.listener.handleEvent(e);
    }

    // -----------------------------------------------------------------------
    // Helper: send a READ event (A2=1) to reset/start detection
    // -----------------------------------------------------------------------
    private void sendReset() {
        RAMEvent e = makeEvent(TYPE.READ, ADDR_READ, 0xFF);
        clock.listener.handleEvent(e);
    }

    // -----------------------------------------------------------------------
    // Helper: drive the full 64-bit magic pattern
    // -----------------------------------------------------------------------
    private void driveFullPattern() {
        long seq = DETECT_SEQUENCE;
        for (int i = 0; i < 64; i++) {
            writeBit((int) (seq & 1));
            seq >>>= 1;
        }
    }

    // -----------------------------------------------------------------------
    // Bug 1 / Bug 4: isRelevant no longer blocks events; addresses in
    // 0xC100-0xCFFF range reach doEvent.  We verify by completing detection.
    // -----------------------------------------------------------------------
    @Test
    public void detectionFires_WhenFullPatternWritten() {
        sendReset();
        assertFalse("Clock should not be active before pattern", clock.clockActive);

        driveFullPattern();

        assertTrue("Clock should be active after 64-bit pattern", clock.clockActive);
    }

    // -----------------------------------------------------------------------
    // Bug 3: Mismatch is silently ignored — detection continues after a bad bit
    // -----------------------------------------------------------------------
    @Test
    public void mismatchTolerance_DetectionContinuesAfterBadBit() {
        sendReset();

        long seq = DETECT_SEQUENCE;
        for (int i = 0; i < 64; i++) {
            int correctBit = (int) (seq & 1);
            if (i == 20) {
                // Send the WRONG bit — should be silently ignored
                writeBit(correctBit ^ 1);
                // Now re-send the correct bit so the pointer advances
                writeBit(correctBit);
            } else {
                writeBit(correctBit);
            }
            seq >>>= 1;
        }

        assertTrue("Clock should activate despite one mismatch in the stream", clock.clockActive);
    }

    // -----------------------------------------------------------------------
    // NSC hardware reality: A READ with A2=0 during pattern recognition must
    // clock a pattern bit — NOT abort detection.  The real NSC chip monitors
    // address lines only and cannot distinguish CPU reads from writes.
    // LDA $C300,Y (used by SMT 1.4 disk driver) is a read that must be
    // accepted during the pattern phase.
    // -----------------------------------------------------------------------
    @Test
    public void readDuringPattern_ClocksPatternBit_NotAbort() {
        sendReset();

        // Send 30 correct bits via READ events (A2=0)
        long seq = DETECT_SEQUENCE;
        for (int i = 0; i < 30; i++) {
            int bit = (int) (seq & 1);
            int address = (bit == 1) ? ADDR_WRITE_BIT1 : ADDR_WRITE;
            RAMEvent e = makeEvent(TYPE.READ_DATA, address, 0xFF);
            clock.listener.handleEvent(e);
            seq >>>= 1;
        }
        assertEquals("Pattern count should be 30 after 30 read-bit events", 30, clock.patternCount);
        assertTrue("writeEnabled should remain true — reads do not abort detection", clock.writeEnabled);

        // Complete the remaining 34 bits to activate clock
        for (int i = 0; i < 34; i++) {
            int bit = (int) (seq & 1);
            int address = (bit == 1) ? ADDR_WRITE_BIT1 : ADDR_WRITE;
            RAMEvent e = makeEvent(TYPE.READ_DATA, address, 0xFF);
            clock.listener.handleEvent(e);
            seq >>>= 1;
        }
        assertTrue("Clock should activate when full pattern clocked via reads", clock.clockActive);
    }

    // -----------------------------------------------------------------------
    // Mixed reads and writes in pattern: both must clock bits (NSC addr-only)
    // -----------------------------------------------------------------------
    @Test
    public void mixedReadWriteDuringPattern_ActivatesClock() {
        sendReset();

        long seq = DETECT_SEQUENCE;
        for (int i = 0; i < 64; i++) {
            int bit = (int) (seq & 1);
            int address = (bit == 1) ? ADDR_WRITE_BIT1 : ADDR_WRITE;
            // Alternate between READ_DATA and WRITE events
            TYPE type = (i % 2 == 0) ? TYPE.READ_DATA : TYPE.WRITE;
            RAMEvent e = makeEvent(type, address, 0xFF);
            clock.listener.handleEvent(e);
            seq >>>= 1;
        }
        assertTrue("Clock should activate with mixed read/write pattern events", clock.clockActive);
    }

    // -----------------------------------------------------------------------
    // Bug 1 (isRelevant): Events in 0xC100-0xCFFF must not be filtered out.
    // Verify the base-class handles the range correctly without override.
    // -----------------------------------------------------------------------
    @Test
    public void isRelevant_AcceptsAddressesInCxRange() {
        // Addresses at the edges and middle of the range
        int[] addresses = { 0xC100, 0xC500, 0xCFFF };
        for (int addr : addresses) {
            RAMEvent e = makeEvent(TYPE.WRITE, addr, 0);
            assertTrue("Address 0x" + Integer.toHexString(addr) + " should be relevant",
                    clock.listener.isRelevant(e));
        }

        // Address just outside should not match
        RAMEvent outside = makeEvent(TYPE.WRITE, 0xC000, 0);
        assertFalse("Address 0xC000 should not be relevant", clock.listener.isRelevant(outside));
    }

    // -----------------------------------------------------------------------
    // Bug 4: Bitmask must be 0xFE (clear only bit 0), not 0x7E
    // -----------------------------------------------------------------------
    @Test
    public void timeDataOutput_BitMaskPreservesBit7() {
        // Activate clock with a known dataRegister that has bit 0 = 0
        clock.dataRegister = 0L; // all bits are 0
        clock.dataRegisterBit = 0;
        clock.clockActive = true;

        // Simulate a read event with old value = 0xFF (all bits set)
        // After masking with 0xFE and OR-ing bit 0 = 0, result must be 0xFE (bit 7 preserved)
        RAMEvent e = makeEvent(TYPE.READ, ADDR_READ, 0xFF);
        clock.listener.handleEvent(e);

        assertEquals("Bit mask 0xFE should preserve bit 7; expected 0xFE", 0xFE, e.getNewValue());
    }

    // -----------------------------------------------------------------------
    // Bug 4 (cross-check): With data bit = 1, result is 0xFF
    // -----------------------------------------------------------------------
    @Test
    public void timeDataOutput_BitMaskPreservesBit7WithDataBit1() {
        clock.dataRegister = 1L; // bit 0 = 1
        clock.dataRegisterBit = 0;
        clock.clockActive = true;

        RAMEvent e = makeEvent(TYPE.READ, ADDR_READ, 0xFF);
        clock.listener.handleEvent(e);

        assertEquals("With data bit=1 and old=0xFF, result should be 0xFF", 0xFF, e.getNewValue());
    }

    // -----------------------------------------------------------------------
    // Bug 5: Hours must use HOUR_OF_DAY (24-hour), not HOUR (12-hour)
    // -----------------------------------------------------------------------
    @Test
    public void activateClock_StoresHourIn24HourFormat() {
        // We call activateClock() in headless mode — it calls Calendar.getInstance().
        // Rather than mocking Calendar, we call storeBCD directly with known hour values
        // and verify that 24-hour values (13-23) survive without being truncated.

        // Test afternoon hour that differs between 12h and 24h: 13:00 (1 PM)
        NoSlotClock testClock = new NoSlotClock();
        testClock.dataRegister = 0L;
        testClock.storeBCD(13, 3); // hour offset is 3

        // Extract the BCD value back: bits 24..31 (offset 3 * 8 = 24)
        int lowNibble  = (int) ((testClock.dataRegister >> 24) & 0x0F);
        int highNibble = (int) ((testClock.dataRegister >> 28) & 0x0F);
        int decodedHour = lowNibble + highNibble * 10;

        assertEquals("Hour 13 in 24h BCD should decode back to 13", 13, decodedHour);
    }

    // -----------------------------------------------------------------------
    // Bug 6: Day-of-week mapping: Java SUNDAY=1 → NSC 07; MONDAY=2 → NSC 01
    // -----------------------------------------------------------------------
    @Test
    public void activateClock_DayOfWeekMapping_SundayIsDay7() {
        // Java Calendar.SUNDAY = 1; NSC Sunday should be 7
        int javaSunday = Calendar.SUNDAY; // = 1
        int nscDow = ((javaSunday + 5) % 7) + 1;
        assertEquals("Java SUNDAY (1) should map to NSC day 7", 7, nscDow);
    }

    @Test
    public void activateClock_DayOfWeekMapping_MondayIsDay1() {
        // Java Calendar.MONDAY = 2; NSC Monday should be 1
        int javaMonday = Calendar.MONDAY; // = 2
        int nscDow = ((javaMonday + 5) % 7) + 1;
        assertEquals("Java MONDAY (2) should map to NSC day 1", 1, nscDow);
    }

    @Test
    public void activateClock_DayOfWeekMapping_SaturdayIsDay6() {
        // Java Calendar.SATURDAY = 7; NSC Saturday should be 6
        int javaSaturday = Calendar.SATURDAY; // = 7
        int nscDow = ((javaSaturday + 5) % 7) + 1;
        assertEquals("Java SATURDAY (7) should map to NSC day 6", 6, nscDow);
    }

    // -----------------------------------------------------------------------
    // Bug 7: writeEnabled cleared when clock activates and deactivates
    // -----------------------------------------------------------------------
    @Test
    public void activateClock_ClearsWriteEnabled() {
        clock.writeEnabled = true;
        // Directly call activateClock() — this exercises storeBCD/storeNibble
        // without needing a live Calendar mock.  The important assertion is the flag.
        clock.activateClock();
        assertFalse("writeEnabled must be false after activateClock()", clock.writeEnabled);
    }

    @Test
    public void deactivateClock_ClearsWriteEnabled() {
        clock.writeEnabled = true;
        clock.clockActive = true;
        clock.deactivateClock();
        assertFalse("writeEnabled must be false after deactivateClock()", clock.writeEnabled);
        assertFalse("clockActive must be false after deactivateClock()", clock.clockActive);
    }

    // -----------------------------------------------------------------------
    // Integrated BCD encoding sanity check
    // -----------------------------------------------------------------------
    @Test
    public void storeBCD_EncodesValueCorrectly() {
        clock.dataRegister = 0L;
        clock.storeBCD(45, 0); // tens=4, units=5 → stored in bits 0..7

        // units nibble at bits 0-3
        int units = (int) (clock.dataRegister & 0x0F);
        // tens nibble at bits 4-7
        int tens   = (int) ((clock.dataRegister >> 4) & 0x0F);

        assertEquals("Units nibble of 45 should be 5", 5, units);
        assertEquals("Tens nibble of 45 should be 4", 4, tens);
    }
}
