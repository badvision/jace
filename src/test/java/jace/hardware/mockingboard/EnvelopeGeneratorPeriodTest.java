package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Verifies the AY-3-8910 envelope generator paces at exactly half the rate of
 * the tone generator for the same period register value.
 *
 * Per the AY-3-8910 datasheet:
 *   tone frequency     = clock / (16  * TP)
 *   envelope frequency = clock / (256 * EP)
 *
 * i.e. for the same register value P, the envelope's full-cycle clock count
 * is exactly 16x the tone's half-cycle clock count (or 2x the tone's
 * full-cycle clock count, since a tone cycle is two state changes and an
 * envelope cycle is sixteen amplitude steps/state changes).
 *
 * This mirrors MAME's ay8910.cpp, which multiplies the envelope period by
 * m_step (2 for classic AY-3-8910, 1 for YM2149) before comparing against the
 * running counter -- i.e. the envelope's per-state-change clock count is
 * m_step times the tone's per-state-change clock count for the same period
 * register value.
 */
public class EnvelopeGeneratorPeriodTest {

    private static final int CLOCK = 1_000_000;
    private static final int SAMPLE_RATE = 44100;

    @Test
    public void envelopeClocksPerStateChange_isTwiceTone_forSamePeriodValue() {
        SoundGenerator tone = new SoundGenerator(CLOCK, SAMPLE_RATE);
        EnvelopeGenerator envelope = new EnvelopeGenerator(CLOCK, SAMPLE_RATE);

        for (int periodValue : new int[]{1, 2, 5, 16, 100, 4095}) {
            tone.setPeriod(periodValue);
            envelope.setPeriod(periodValue);

            assertEquals(
                "Envelope clocksPerPeriod (state-change interval) must be exactly "
                    + "2x tone's clocksPerPeriod for period register value " + periodValue,
                2 * tone.clocksPerPeriod,
                envelope.clocksPerPeriod
            );
        }
    }

    @Test
    public void envelopeAndTonePeriods_matchDatasheetFormulas_forSamePeriodValue() {
        SoundGenerator tone = new SoundGenerator(CLOCK, SAMPLE_RATE);
        EnvelopeGenerator envelope = new EnvelopeGenerator(CLOCK, SAMPLE_RATE);

        int periodValue = 100;
        tone.setPeriod(periodValue);
        envelope.setPeriod(periodValue);

        // Tone: one full square-wave cycle is two state changes (high->low->high),
        // so full-cycle clocks = 2 * clocksPerPeriod. Datasheet: 16 * TP.
        int toneFullCycleClocks = 2 * tone.clocksPerPeriod;
        assertEquals(16 * periodValue, toneFullCycleClocks);

        // Envelope: one full ramp cycle is sixteen amplitude steps (state changes),
        // so full-cycle clocks = 16 * clocksPerPeriod. Datasheet: 256 * EP.
        int envelopeFullCycleClocks = 16 * envelope.clocksPerPeriod;
        assertEquals(256 * periodValue, envelopeFullCycleClocks);
    }

    @Test
    public void envelopePeriodZero_specialCase_remainsProportionalToStepsPerCycle() {
        EnvelopeGenerator envelope = new EnvelopeGenerator(CLOCK, SAMPLE_RATE);
        envelope.setPeriod(0);
        // Special case: clocksPerPeriod = stepsPerCycle() / 2 == 16 / 2 == 8
        assertEquals(envelope.stepsPerCycle() / 2, envelope.clocksPerPeriod);
    }
}
