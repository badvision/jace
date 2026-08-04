package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import jace.core.TimedDevice;
import jace.hardware.CardMockingboard;

/**
 * Verifies the clock rate Jace feeds its AY-3-8910 emulation.
 *
 * <h3>Two numbers, two different quantities — not two candidate answers</h3>
 * These are <em>not</em> alternative estimates of one thing, and this test exists
 * because that has been misread more than once.
 *
 * <p><b>1022727 Hz — the hardware clock the card actually receives.</b> The Apple
 * II master oscillator is the NTSC colorburst &times;4, 14318181.8 Hz, and the bus
 * clock wired to the peripheral slots is that divided by 14:
 * <pre>
 *     14318181.8 / 14 = 1022727 Hz
 * </pre>
 * This is the number an AY on a Mockingboard is clocked at, and it is the only one
 * of the two derived from the crystal. It is what {@code CardMockingboard.CLOCK_SPEED}
 * must be.
 *
 * <p><b>1020484 Hz — the rate Jace's emulated machine actually runs at.</b>
 * {@code TimedDevice.NTSC_1MHZ} is the 6502's <em>average</em> throughput once the
 * video logic's stretched cycle (one long cycle per 65-cycle scanline) is amortized
 * in: {@code crystal * 65 / (65*14 + 2)}, matching AppleWin's
 * {@code CLK_6502_NTSC = (_14M_NTSC * 65.0) / (65.0*14.0+2.0)}. Jace applies this
 * average globally, so strictly speaking the emulated machine — card included —
 * runs 0.22% slow. That is a known and deliberately accepted inaccuracy.
 *
 * <p><b>The rule that follows.</b> The card must not be <em>clocked</em> from the
 * CPU average. Deriving the AY oscillator from 1020484 double-counts the stretch:
 * the emulated timebase is already 0.22% slow, and detuning the oscillator by the
 * same 0.22% (about 3.8 cents flat) on top of it is a second, independent error.
 * Pitch is scaled against 1022727; everything that counts <em>CPU cycles</em> — this
 * card's tick pacing and the 6522's timers, which see the same stretched bus
 * &Phi;2 the CPU does — correctly uses {@code NTSC_1MHZ}.
 *
 * <p>Do not collapse these into one constant, and do not "correct" CLOCK_SPEED to
 * match NTSC_1MHZ on the grounds that Jace runs everything at the average. The
 * average is the timebase; crystal/14 is the oscillator.
 */
public class MockingboardClockTest {

    /** NTSC colorburst x4 — the Apple II master oscillator. */
    private static final double CRYSTAL_HZ = 14_318_181.8;

    /** Bus clock supplied to the peripheral slots: crystal / 14. THE CARD'S CLOCK. */
    private static final int SLOT_PHI_HZ = 1_022_727;

    @Test
    public void slotClockIsTheCrystalDividedByFourteen() {
        assertEquals("the slot clock is the 14.31818 MHz colorburst crystal / 14",
                     SLOT_PHI_HZ, (int) Math.round(CRYSTAL_HZ / 14.0));
    }

    @Test
    public void theCpuAverageIsTheStretchedCycleAmortization_notAnAlternativeSlotClock() {
        // Records WHERE the other number comes from, so it cannot be mistaken for a
        // competing measurement of the slot clock. 65 cycles per scanline, one of
        // which is stretched by 2/14 of a cycle. Same derivation as AppleWin's
        // CLK_6502_NTSC = (_14M_NTSC * 65.0) / (65.0*14.0+2.0).
        double cpuAverage = CRYSTAL_HZ * 65.0 / (65.0 * 14.0 + 2.0);
        assertEquals("NTSC_1MHZ is the stretched-cycle average, crystal*65/912",
                     TimedDevice.NTSC_1MHZ, (int) Math.round(cpuAverage));
        assertTrue("the average is necessarily slower than the unstretched bus clock",
                   TimedDevice.NTSC_1MHZ < SLOT_PHI_HZ);
    }

    @Test
    public void mockingboardClocksItsAyFromTheSlotClock_notTheCpuAverage() {
        CardMockingboard card = new CardMockingboard();
        assertEquals("the AY must be clocked at the slot clock (crystal/14), not at "
                     + "the 6502's stretched-cycle average of " + TimedDevice.NTSC_1MHZ,
                     SLOT_PHI_HZ, card.CLOCK_SPEED);
    }

    @Test
    public void theCpuAverageAndTheSlotClockAreDistinctValues() {
        // Guards against someone "simplifying" these back into one constant. Both
        // numbers are pinned, but they are pinned as different quantities: the
        // oscillator the card receives vs. the average rate Jace runs the machine at.
        assertEquals("the CPU's stretched-cycle average is a different quantity",
                     1_020_484, TimedDevice.NTSC_1MHZ);
        assertEquals("difference between the two clocks",
                     2243, SLOT_PHI_HZ - TimedDevice.NTSC_1MHZ);
    }

    @Test
    public void theAcceptedGlobalInaccuracyIsSmallEnoughToTolerateButNotToIgnore() {
        // Jace runs the whole machine at the average, so the emulated timebase is
        // already this much slow. Recorded so the size of the accepted error is a
        // fact in the suite and not folk knowledge: ~0.22%, ~3.8 cents. Deriving the
        // AY clock from the average too would apply it a second time.
        double errorFraction = 1.0 - (double) TimedDevice.NTSC_1MHZ / SLOT_PHI_HZ;
        assertEquals("Jace's global timebase runs ~0.22% slow", 0.0022, errorFraction, 0.0002);

        double cents = 1200 * Math.log((double) TimedDevice.NTSC_1MHZ / SLOT_PHI_HZ)
                       / Math.log(2);
        assertEquals("which is ~3.8 cents — inaudible alone, but it stacks",
                     -3.8, cents, 0.2);
    }

    @Test
    public void psgGeneratorsReceiveTheClockTheyAreConstructedWith() {
        // The clock has to actually reach every generator, not just sit in a field
        // on the card. (Built directly rather than via CardMockingboard.reconfigure,
        // which needs emulator-wide statics that a unit test has no business
        // booting.)
        PSG psg = new PSG(0, SLOT_PHI_HZ, jace.core.SoundMixer.RATE, "AY1", 255);

        for (int ch = 0; ch < 3; ch++) {
            assertEquals("channel " + ch + " must be clocked at the slot clock",
                         SLOT_PHI_HZ, psg.channels.get(ch).clock);
        }
        assertEquals("the envelope generator must be clocked at the slot clock",
                     SLOT_PHI_HZ, psg.envelopeGenerator.clock);
        assertEquals("the noise generator must be clocked at the slot clock",
                     SLOT_PHI_HZ, psg.noiseGenerator.clock);
    }

    @Test
    public void concertAToneMapsToTheExpectedPeriodRegister() {
        // A sanity check tying the clock to a musical value. The AY's tone
        // frequency is clock / (16 * TP), so 440 Hz needs TP = 1022727/(16*440).
        int expectedPeriod = (int) Math.round(SLOT_PHI_HZ / (16.0 * 440.0));
        assertEquals("period register for concert A at the slot clock", 145, expectedPeriod);

        // At the CPU average the same note would want a different period; playing
        // a slot-clock period on a CPU-clocked emulation is what makes it flat.
        int cpuClockPeriod = (int) Math.round(TimedDevice.NTSC_1MHZ / (16.0 * 440.0));
        double centsFlat = 1200 * Math.log((double) TimedDevice.NTSC_1MHZ / SLOT_PHI_HZ)
                           / Math.log(2);
        assertEquals("the two clocks round to the same period register here, so the "
                     + "error is purely in the playback clock", expectedPeriod, cpuClockPeriod);
        assertEquals("using the CPU average detunes everything ~3.8 cents flat",
                     -3.8, centsFlat, 0.2);
    }
}
