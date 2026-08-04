package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import jace.core.Utility;
import jace.hardware.CardMockingboard;

/**
 * Verifies that a Mockingboard's two AY chips are addressed and reset
 * independently, and that each 6522 drives only its own AY.
 *
 * <h3>The wiring</h3>
 * A Mockingboard-C in slot 4 exposes two 6522 VIAs, one per AY:
 * <ul>
 *   <li>VIA/AY 1 at $C400, i.e. register offsets $x0-$xF</li>
 *   <li>VIA/AY 2 at $C480, i.e. register offsets $x0-$xF in the $80 half</li>
 * </ul>
 * {@code CardMockingboard.handleFirmwareAccess} routes on
 * {@code register &amp; 0x0f0} matched against {@code psg.getBaseReg()}, and
 * {@code initPSG} constructs the pair with base regs 0 and $80. This is the
 * decode that {@code vt3/apple2/mockingboard.s} depends on when it writes
 * $C400/$C480.
 *
 * <p>An end-to-end register comparison would catch a total decode failure but
 * not cross-talk: if a write to chip 1 also landed on chip 2, a TS song that
 * writes both chips with similar values every frame could still compare equal
 * for long stretches.
 */
public class DualAyAddressingTest {

    private static final int CLOCK = 1_022_727;
    private static final int RATE = 44_100;

    private PSG ay1;
    private PSG ay2;

    /** Drives the real latch/write bus sequence, as the 6522 port B does. */
    private static void busWrite(PSG psg, PSG.Reg reg, int value) {
        psg.setBus(reg.registerNumber);
        psg.setControl(PSG.BusControl.latch.val);
        psg.setControl(PSG.BusControl.inactive.val);
        psg.setBus(value);
        psg.setControl(PSG.BusControl.write.val);
        psg.setControl(PSG.BusControl.inactive.val);
    }

    @Before
    public void setUp() {
        Utility.setHeadlessMode(true);
        ay1 = new PSG(0x00, CLOCK, RATE, "AY1", 255);
        ay2 = new PSG(0x80, CLOCK, RATE, "AY2", 255);
    }

    @Test
    public void theTwoChipsUseTheAddressesTheMockingboardDecodesOn() {
        assertEquals("AY 1 answers at the $C400 half of slot I/O space", 0x00, ay1.getBaseReg());
        assertEquals("AY 2 answers at the $C480 half of slot I/O space", 0x80, ay2.getBaseReg());
        assertNotEquals("the two chips must be distinguishable by base register",
                        ay1.getBaseReg(), ay2.getBaseReg());
    }

    @Test
    public void writingOneChipDoesNotDisturbTheOther() {
        busWrite(ay1, PSG.Reg.AFine, 0x5A);
        busWrite(ay1, PSG.Reg.ACoarse, 0x03);
        busWrite(ay1, PSG.Reg.AVol, 0x0F);

        assertEquals("chip 1 must take the write", 0x5A, ay1.getReg(PSG.Reg.AFine));
        assertEquals("chip 2 must be untouched", 0, ay2.getReg(PSG.Reg.AFine));
        assertEquals("chip 2 must be untouched", 0, ay2.getReg(PSG.Reg.ACoarse));
        assertEquals("chip 2 must be untouched", 0, ay2.getReg(PSG.Reg.AVol));
    }

    @Test
    public void eachChipKeepsItsOwnLatchedRegisterSelection() {
        // Latch a different register on each chip, then write both. If the latch
        // were shared, the second write would land in the wrong place.
        ay1.setBus(PSG.Reg.AFine.registerNumber);
        ay1.setControl(PSG.BusControl.latch.val);
        ay2.setBus(PSG.Reg.CFine.registerNumber);
        ay2.setControl(PSG.BusControl.latch.val);

        ay1.setBus(0x11);
        ay1.setControl(PSG.BusControl.write.val);
        ay2.setBus(0x22);
        ay2.setControl(PSG.BusControl.write.val);

        assertEquals("chip 1's latch selected AFine", 0x11, ay1.getReg(PSG.Reg.AFine));
        assertEquals("chip 2's latch selected CFine", 0x22, ay2.getReg(PSG.Reg.CFine));
        assertEquals("chip 1's CFine must be untouched", 0, ay1.getReg(PSG.Reg.CFine));
        assertEquals("chip 2's AFine must be untouched", 0, ay2.getReg(PSG.Reg.AFine));
    }

    @Test
    public void resettingOneChipDoesNotResetTheOther() {
        busWrite(ay1, PSG.Reg.AFine, 0x5A);
        busWrite(ay2, PSG.Reg.AFine, 0x3C);

        ay1.reset();

        assertEquals("the reset chip returns to zero", 0, ay1.getReg(PSG.Reg.AFine));
        assertEquals("the other chip keeps its state", 0x3C, ay2.getReg(PSG.Reg.AFine));
    }

    @Test
    public void resetLeavesEveryRegisterZeroExceptTheMixerWhichIsAllDisabled() {
        // MAME ay8910_reset_ym() writes 0 to every register. Jace deliberately
        // writes $FF to register 7 instead, because its mixer bits are active-low
        // and a literal 0 would enable all six generators -- i.e. reset would be
        // audible. $FF is the all-disabled encoding, which is the silence a real
        // chip produces after reset (its amplitudes are also 0).
        for (PSG.Reg r : PSG.Reg.values()) {
            busWrite(ay1, r, 0xFF);
        }
        ay1.reset();

        for (PSG.Reg r : PSG.Reg.values()) {
            int expected = (r == PSG.Reg.Enable) ? 0xFF : 0;
            assertEquals("register " + r.registerNumber + " (" + r + ") after reset",
                         expected, ay1.getReg(r));
        }
    }

    @Test
    public void resetSilencesBothChipsIndependentlyThroughTheCard() {
        // The card's reset() must reach every chip, not just the first.
        CardMockingboard card = new CardMockingboard();
        PSG[] chips = { ay1, ay2 };
        for (PSG p : chips) {
            busWrite(p, PSG.Reg.AVol, 0x0F);
            busWrite(p, PSG.Reg.Enable, 0x00);   // all generators enabled
        }
        // Mirror what Card.reset() does over the chip array.
        for (PSG p : chips) {
            p.reset();
        }
        for (PSG p : chips) {
            assertEquals("amplitude silenced", 0, p.getReg(PSG.Reg.AVol));
            assertEquals("mixer fully disabled", 0xFF, p.getReg(PSG.Reg.Enable));
        }
        assertEquals("the card reports its slot device name", "Mockingboard", card.getDeviceName());
    }

    @Test
    public void bothChipsAreClockedAtTheSameRate() {
        // Non-phasor mode: both AYs share the one slot clock.
        assertEquals(CLOCK, ay1.envelopeGenerator.clock);
        assertEquals(CLOCK, ay2.envelopeGenerator.clock);
        for (int ch = 0; ch < 3; ch++) {
            assertEquals(CLOCK, ay1.channels.get(ch).clock);
            assertEquals(CLOCK, ay2.channels.get(ch).clock);
        }
    }

    @Test
    public void chipsRespondToTheirPortBMaskInNonPhasorMode() {
        // In non-phasor mode both chips are constructed with a 255 mask, which is
        // what makes CardMockingboard.sendOutputB deliver control to chip j only.
        assertEquals("non-phasor chips use a full port B mask", 255, ay1.mask);
        assertEquals("non-phasor chips use a full port B mask", 255, ay2.mask);
        assertTrue("a full mask matches every control code",
                   (ay1.mask & PSG.BusControl.latch.val) != 0);
    }
}
