package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.hardware.CardMockingboard;

/**
 * Verifies {@link TimedGenerator}'s clock-to-sample-rate conversion, which sets
 * the pitch of every PSG generator.
 *
 * <h3>Why this matters</h3>
 * {@code cyclesPerSample} converts master clocks to output samples. The
 * Mockingboard's AY runs at the 1.0227 MHz slot bus clock and Jace renders at
 * 44100 Hz, giving 23.1910&hellip; clocks per sample. Truncating that to 23 makes
 * every tone 0.8% flat — about 14 cents, which is audible as detuning between the
 * Mockingboard and any other pitched source.
 *
 * <p>These assertions are about not losing the fraction, so they hold at any clock;
 * the constant below is the AY's real clock rather than the CPU average purely so
 * that nobody copies the wrong number out of this file.
 */
public class TimedGeneratorRateTest {

    /**
     * The AY oscillator clock: the Apple II slot bus clock, 14318181.8 / 14. NOT
     * {@code TimedDevice.NTSC_1MHZ} (1020484), which is the 6502's stretched-cycle
     * average. See {@link MockingboardClockTest} and Jace's CLAUDE.md.
     */
    private static final int MOCKINGBOARD_CLOCK = 1_022_727;
    private static final int SAMPLE_RATE = 44100;

    /** SoundGenerator.step indexes CardMockingboard.VolTable, a lazily-built static. */
    @BeforeClass
    public static void buildVolumeTable() {
        new CardMockingboard().buildMixerTable();
    }

    @Test
    public void cyclesPerSampleKeepsFractionalPrecision() {
        TimedGenerator gen = new TimedGenerator(MOCKINGBOARD_CLOCK, SAMPLE_RATE);
        gen.setRate(MOCKINGBOARD_CLOCK, SAMPLE_RATE);
        assertEquals("clocks per sample must not be truncated to an integer",
                     (double) MOCKINGBOARD_CLOCK / SAMPLE_RATE,
                     gen.cyclesPerSample, 1e-9);
    }

    @Test
    public void constructorAppliesItsClockAndSampleRateArguments() {
        // The constructor takes a clock and sample rate; a generator built with
        // them must be usable without a separate setRate call.
        TimedGenerator gen = new TimedGenerator(MOCKINGBOARD_CLOCK, SAMPLE_RATE);
        assertEquals("constructor must apply its clock argument",
                     MOCKINGBOARD_CLOCK, gen.clock);
        assertEquals("constructor must apply its sample-rate argument",
                     SAMPLE_RATE, gen.sampleRate);
        assertEquals("constructor must derive cyclesPerSample from its arguments",
                     (double) MOCKINGBOARD_CLOCK / SAMPLE_RATE,
                     gen.cyclesPerSample, 1e-9);
    }

    @Test
    public void zeroSampleRateFallsBackToFortyFourOneHundred() {
        TimedGenerator gen = new TimedGenerator(MOCKINGBOARD_CLOCK, 0);
        assertEquals(44100, gen.sampleRate);
    }

    @Test
    public void measuredToneFrequencyMatchesTheDatasheetFormula() {
        // Datasheet: tone frequency = clock / (16 * TP). Count state changes over
        // one second of samples and compare with 2 * that frequency (two state
        // changes per cycle).
        int tonePeriod = 100;
        SoundGenerator tone = new SoundGenerator(MOCKINGBOARD_CLOCK, SAMPLE_RATE);
        tone.setRate(MOCKINGBOARD_CLOCK, SAMPLE_RATE);
        tone.setPeriod(tonePeriod);

        NoiseGenerator noise = new NoiseGenerator(MOCKINGBOARD_CLOCK, SAMPLE_RATE);
        EnvelopeGenerator env = new EnvelopeGenerator(MOCKINGBOARD_CLOCK, SAMPLE_RATE);

        boolean previous = tone.inverted;
        int stateChanges = 0;
        for (int i = 0; i < SAMPLE_RATE; i++) {
            tone.step(noise, env);
            if (tone.inverted != previous) {
                stateChanges++;
                previous = tone.inverted;
            }
        }

        double expectedFrequency = MOCKINGBOARD_CLOCK / (16.0 * tonePeriod);
        double measuredFrequency = stateChanges / 2.0;
        assertEquals(String.format(
            "measured %.2f Hz vs datasheet %.2f Hz (clock/(16*TP)) — "
            + "a rate conversion error detunes everything",
            measuredFrequency, expectedFrequency),
            expectedFrequency, measuredFrequency, expectedFrequency * 0.001);
    }
}
