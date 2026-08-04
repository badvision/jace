package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.hardware.CardMockingboard;
import jace.hardware.mockingboard.PSG.BusControl;
import jace.hardware.mockingboard.PSG.Reg;

/**
 * Verifies the AY bus-control state machine — how BDIR/BC2/BC1 select between
 * latching a register address, writing data, reading data, and idling.
 *
 * <h3>Reference: AppleWin Mockingboard.cpp</h3>
 * The Mockingboard wires the 6522's port B bits 0-2 to the AY's BC1, BDIR and
 * RESET. BC2 is tied to +5V, so the AY's 3-bit function code is
 * {@code (BDIR << 2) | (BC2 << 1) | BC1} with BC2 always 1:
 * <pre>
 *   4 = 100 = inactive     5 = 101 = read data
 *   6 = 110 = write data   7 = 111 = latch address
 * </pre>
 * MAME ay8910.cpp:111-116 confirms the redundancy: "Much of the redundancy can
 * be finessed by tying BC2 to Vcc; AY-3-8913 and AY8930 do this internally."
 *
 * <h3>Why this test exists</h3>
 * These four codes are the only legal values. The previous version of this test
 * passed 0, 1, 2 and 3 — all of which {@code BusControl.fromInt} rejects as
 * null, so every case returned early and the tests asserted nothing at all.
 */
public class PSGTest {

    /**
     * The AY oscillator clock: the Apple II slot bus clock, 14318181.8 / 14. This
     * is deliberately NOT {@code TimedDevice.NTSC_1MHZ} (1020484), which is the
     * 6502's stretched-cycle average and clocks CPU-counting things only. See
     * {@link MockingboardClockTest} and Jace's CLAUDE.md before changing it — the
     * two numbers have been confused repeatedly.
     */
    private static final int CLOCK = 1_022_727;
    private static final int SAMPLE_RATE = 44100;

    private PSG psg;

    @BeforeClass
    public static void buildVolumeTable() {
        new CardMockingboard().buildMixerTable();
    }

    @Before
    public void setUp() {
        psg = new PSG(0, CLOCK, SAMPLE_RATE, "name", 255);
        psg.setRate(CLOCK, SAMPLE_RATE);
    }

    /** Drive the real bus sequence a Mockingboard driver uses for one write. */
    private void busWrite(int register, int value) {
        psg.setBus(register);
        psg.setControl(BusControl.latch.val);
        psg.setControl(BusControl.inactive.val);
        psg.setBus(value);
        psg.setControl(BusControl.write.val);
        psg.setControl(BusControl.inactive.val);
    }

    // ------------------------------------------------------------------
    // Function-code decoding
    // ------------------------------------------------------------------

    @Test
    public void busControlCodesMatchTheHardwareEncoding() {
        // (BDIR << 2) | (BC2 << 1) | BC1, with BC2 tied high.
        assertEquals("inactive == BDIR=0 BC1=0", 4, BusControl.inactive.val);
        assertEquals("read     == BDIR=0 BC1=1", 5, BusControl.read.val);
        assertEquals("write    == BDIR=1 BC1=0", 6, BusControl.write.val);
        assertEquals("latch    == BDIR=1 BC1=1", 7, BusControl.latch.val);
    }

    @Test
    public void codesWithBusControlTwoLow_areNotValidFunctions() {
        // With BC2 hardwired high, codes 0-3 cannot occur on a Mockingboard.
        for (int code = 0; code < 4; code++) {
            assertEquals("code " + code + " has BC2 low and must not decode",
                         null, BusControl.fromInt(code));
        }
    }

    // ------------------------------------------------------------------
    // Latch / write / read
    // ------------------------------------------------------------------

    @Test
    public void latchSelectsTheRegisterAndWriteStoresTheValue() {
        busWrite(Reg.AFine.registerNumber, 0x5A);
        assertEquals(0x5A, psg.getReg(Reg.AFine));
    }

    @Test
    public void latchUsesOnlyTheLowFourBitsOfTheBus() {
        // The AY latches a 4-bit register address; the upper nibble is ignored.
        busWrite(0xF0 | Reg.BFine.registerNumber, 0x33);
        assertEquals("upper nibble of the latched address must be ignored",
                     0x33, psg.getReg(Reg.BFine));
    }

    @Test
    public void latchedRegisterPersistsAcrossMultipleWrites() {
        // Real drivers latch once and then write repeatedly.
        psg.setBus(Reg.CFine.registerNumber);
        psg.setControl(BusControl.latch.val);
        psg.setControl(BusControl.inactive.val);

        for (int value : new int[]{0x01, 0x7F, 0xC0}) {
            psg.setBus(value);
            psg.setControl(BusControl.write.val);
            psg.setControl(BusControl.inactive.val);
            assertEquals(value, psg.getReg(Reg.CFine));
        }
    }

    @Test
    public void readReturnsThePreviouslyWrittenValueOnTheBus() {
        busWrite(Reg.Enable.registerNumber, 0x3E);

        psg.setBus(Reg.Enable.registerNumber);
        psg.setControl(BusControl.latch.val);
        psg.setBus(0x00);                       // clobber the bus first
        psg.setControl(BusControl.read.val);

        assertEquals("read must drive the register value onto the bus",
                     0x3E, psg.bus);
    }

    @Test
    public void inactiveDoesNotDisturbTheSelectedRegisterOrItsValue() {
        busWrite(Reg.AVol.registerNumber, 0x0C);
        for (int i = 0; i < 5; i++) {
            psg.setControl(BusControl.inactive.val);
        }
        assertEquals(0x0C, psg.getReg(Reg.AVol));
    }

    @Test
    public void writeWithoutAPrecedingLatchTargetsTheLastLatchedRegister() {
        busWrite(Reg.AVol.registerNumber, 0x05);
        // No new latch: the selected register is still AVol.
        psg.setBus(0x0B);
        psg.setControl(BusControl.write.val);
        assertEquals(0x0B, psg.getReg(Reg.AVol));
    }

    @Test
    public void invalidControlCodeIsIgnoredEntirely() {
        busWrite(Reg.AVol.registerNumber, 0x07);
        psg.setBus(0xFF);
        psg.setControl(0);                      // BC2 low: not a real function
        assertEquals("an undecodable control code must not alter any register",
                     0x07, psg.getReg(Reg.AVol));
    }

    // ------------------------------------------------------------------
    // Register masking
    // ------------------------------------------------------------------

    @Test
    public void registersMaskToTheirHardwareWidths() {
        busWrite(Reg.ACoarse.registerNumber, 0xFF);
        assertEquals("coarse tone register is 4 bits", 0x0F, psg.getReg(Reg.ACoarse));

        busWrite(Reg.NoisePeriod.registerNumber, 0xFF);
        assertEquals("noise period register is 5 bits", 0x1F, psg.getReg(Reg.NoisePeriod));

        busWrite(Reg.EnvShape.registerNumber, 0xFF);
        assertEquals("envelope shape register is 4 bits", 0x0F, psg.getReg(Reg.EnvShape));

        busWrite(Reg.AVol.registerNumber, 0xFF);
        assertEquals("amplitude register is 5 bits (4 volume + envelope select)",
                     0x1F, psg.getReg(Reg.AVol));
    }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    @Test
    public void resetSilencesTheChipAndDisablesTheMixer() {
        busWrite(Reg.Enable.registerNumber, 0x00);   // everything on
        busWrite(Reg.AVol.registerNumber, 0x0F);

        psg.reset();

        assertEquals("register 7 must come up with everything disabled",
                     0xFF, psg.getReg(Reg.Enable));
        assertEquals("amplitudes must reset to zero", 0, psg.getReg(Reg.AVol));

        AtomicInteger out = new AtomicInteger();
        psg.update(out, true, out, false, out, false);
        assertEquals("a freshly reset chip must be silent", 0, out.get());
    }

    // ------------------------------------------------------------------
    // update() mixing contract
    // ------------------------------------------------------------------

    @Test
    public void updateClearsOrAccumulatesAccordingToTheClearFlags() {
        busWrite(Reg.AFine.registerNumber, 0x40);
        busWrite(Reg.AVol.registerNumber, 0x0F);
        busWrite(Reg.BVol.registerNumber, 0x0F);
        busWrite(Reg.CVol.registerNumber, 0x0F);
        busWrite(Reg.Enable.registerNumber, 0x38);   // all three tones enabled

        AtomicInteger single = new AtomicInteger();
        psg.update(single, true, single, true, single, true);

        AtomicInteger summed = new AtomicInteger();
        psg.update(summed, true, summed, false, summed, false);

        assertNotEquals("accumulating three channels must differ from clearing each time",
                        single.get(), summed.get());
    }

    @Test
    public void updateSeededWithAPriorValue_addsRatherThanOverwritesWhenNotClearing() {
        busWrite(Reg.Enable.registerNumber, 0xFF);   // silent
        busWrite(Reg.AVol.registerNumber, 0x00);

        AtomicInteger buffer = new AtomicInteger(1234);
        psg.update(buffer, false, buffer, false, buffer, false);
        assertEquals("a silent chip must leave an accumulating buffer untouched",
                     1234, buffer.get());

        buffer.set(1234);
        psg.update(buffer, true, buffer, false, buffer, false);
        assertEquals("clearA must discard the buffer's prior contents",
                     0, buffer.get());
    }
}
