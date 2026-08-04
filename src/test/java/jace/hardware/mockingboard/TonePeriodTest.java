package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.hardware.CardMockingboard;

/**
 * Verifies the PSG tone generator's period handling.
 *
 * <h3>Reference: MAME ay8910.cpp:1076 and :90-91</h3>
 * <pre>
 * const int period = std::max&lt;int&gt;(1, tone-&gt;period);
 * </pre>
 * <pre>
 * // Also, note that period = 0 is the same as period = 1. This is mentioned
 * // in the YM2203 data sheets.
 * </pre>
 * A period register of 0 must therefore produce the <em>highest</em> tone the
 * chip can make, not silence.
 *
 * <h3>Rate: AY-3-8910 datasheet</h3>
 * Tone frequency = clock / (16 * TP), where TP is the 12-bit value from the
 * coarse/fine register pair. One full square-wave cycle is two state changes,
 * so a state change occurs every 8 * TP master clocks — which is exactly
 * {@code SoundGenerator.stepsPerCycle() == 8}.
 */
public class TonePeriodTest {

    private static final int SAMPLE_RATE = 44100;

    @BeforeClass
    public static void buildVolumeTable() {
        new CardMockingboard().buildMixerTable();
    }

    // ------------------------------------------------------------------
    // 12-bit period assembly from the coarse/fine pair
    // ------------------------------------------------------------------

    @Test
    public void tonePeriodIsTwelveBits_fineIsLowByte_coarseIsHighNibble() {
        PSG psg = new PSG(0, SAMPLE_RATE, SAMPLE_RATE, "test", 255);
        psg.setReg(PSG.Reg.AFine, 0x34);
        psg.setReg(PSG.Reg.ACoarse, 0x0C);
        assertEquals("period must be fine | (coarse << 8)", 0xC34, psg.channels.get(0).period);
    }

    @Test
    public void toneCoarseRegisterMasksToFourBits() {
        PSG psg = new PSG(0, SAMPLE_RATE, SAMPLE_RATE, "test", 255);
        psg.setReg(PSG.Reg.AFine, 0xFF);
        psg.setReg(PSG.Reg.ACoarse, 0xFF);
        assertEquals("coarse register is 4 bits, so the maximum period is 0xFFF",
                     0xFFF, psg.channels.get(0).period);
    }

    @Test
    public void eachChannelHasItsOwnPeriodPair() {
        PSG psg = new PSG(0, SAMPLE_RATE, SAMPLE_RATE, "test", 255);
        psg.setReg(PSG.Reg.AFine, 0x11);
        psg.setReg(PSG.Reg.ACoarse, 0x01);
        psg.setReg(PSG.Reg.BFine, 0x22);
        psg.setReg(PSG.Reg.BCoarse, 0x02);
        psg.setReg(PSG.Reg.CFine, 0x33);
        psg.setReg(PSG.Reg.CCoarse, 0x03);

        assertEquals(0x111, psg.channels.get(0).period);
        assertEquals(0x222, psg.channels.get(1).period);
        assertEquals(0x333, psg.channels.get(2).period);
    }

    // ------------------------------------------------------------------
    // Rate
    // ------------------------------------------------------------------

    @Test
    public void toneStateChangeIntervalIsEightClocksPerPeriodUnit() {
        // Datasheet: tone frequency = clock / (16 * TP). Two state changes per
        // cycle, so 8 * TP clocks per state change.
        SoundGenerator tone = new SoundGenerator(SAMPLE_RATE, SAMPLE_RATE);
        for (int tp : new int[]{1, 2, 5, 100, 0xFFF}) {
            tone.setPeriod(tp);
            assertEquals("period " + tp + " must give 8*TP clocks per state change",
                         8 * tp, tone.clocksPerPeriod);
        }
    }

    // ------------------------------------------------------------------
    // Period 0 and 1 must produce sound, not silence
    // ------------------------------------------------------------------

    @Test
    public void tonePeriodOne_oscillates() {
        // Period 1 at the Mockingboard's ~1.02 MHz is ~64 kHz — inaudible on its
        // own, but it must still toggle: software uses very short periods to make
        // a channel act as a DC source or to drive the noise/envelope path, and
        // freezing it changes the mix.
        SoundGenerator tone = new SoundGenerator(1, 1);
        tone.setRate(8 * SAMPLE_RATE, SAMPLE_RATE);  // cyclesPerSample == 8
        tone.setPeriod(1);                           // clocksPerPeriod == 8
        assertTrue("period 1 must produce state changes, not silence",
                   oscillates(tone));
    }

    @Test
    public void tonePeriodZero_behavesAsPeriodOne() {
        // MAME: std::max<int>(1, tone->period). Period 0 is the highest tone the
        // chip can make, not silence.
        SoundGenerator zero = new SoundGenerator(1, 1);
        zero.setRate(8 * SAMPLE_RATE, SAMPLE_RATE);
        zero.setPeriod(0);
        assertTrue("period 0 must behave as period 1 and oscillate, not go silent",
                   oscillates(zero));

        SoundGenerator one = new SoundGenerator(1, 1);
        one.setRate(8 * SAMPLE_RATE, SAMPLE_RATE);
        one.setPeriod(1);
        assertEquals("period 0 must give the same state-change interval as period 1",
                     one.clocksPerPeriod, zero.clocksPerPeriod);
    }

    @Test
    public void toneAtPeriodTwo_oscillatesAtTheExpectedRate() {
        // A control case well clear of the 0/1 boundary, to show the harness
        // itself detects oscillation correctly.
        SoundGenerator tone = new SoundGenerator(1, 1);
        tone.setRate(8 * 2 * SAMPLE_RATE, SAMPLE_RATE);  // cyclesPerSample == 16
        tone.setPeriod(2);                               // clocksPerPeriod == 16
        assertTrue(oscillates(tone));
    }

    /** True if the generator's square-wave phase changes within 32 samples. */
    private static boolean oscillates(SoundGenerator tone) {
        NoiseGenerator noise = new NoiseGenerator(SAMPLE_RATE, SAMPLE_RATE);
        EnvelopeGenerator env = new EnvelopeGenerator(SAMPLE_RATE, SAMPLE_RATE);
        boolean initial = tone.inverted;
        for (int i = 0; i < 32; i++) {
            tone.step(noise, env);
            if (tone.inverted != initial) {
                return true;
            }
        }
        return false;
    }
}
