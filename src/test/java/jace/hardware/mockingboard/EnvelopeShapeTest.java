package jace.hardware.mockingboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies the AY-3-8910 / YM2149 envelope generator against MAME's
 * {@code ay8910.cpp}, which is the reference implementation for this chip.
 *
 * <h3>Why this test exists</h3>
 * The PT3 register-frame comparison in {@code jace.hardware.Pt3PlayerRegisterTest}
 * is completely blind to envelope behavior: it only checks that the same bytes
 * reach registers 11/12/13. Identical register values still produce wrong audio
 * if the envelope generator misinterprets them.
 *
 * <h3>Reference: MAME ay8910.h, envelope_t::set_shape</h3>
 * <pre>
 * attack = (shape &amp; 0x04) ? mask : 0x00;
 * if ((shape &amp; 0x08) == 0) { hold = 1; alternate = attack; }
 * else                      { hold = shape &amp; 0x01; alternate = shape &amp; 0x02; }
 * step = mask; holding = 0; volume = (step ^ attack);
 * </pre>
 * and MAME ay8910.cpp sound_stream_update, which decrements {@code step} once
 * per envelope period and re-derives {@code volume = step ^ attack}.
 *
 * <p>The 16 shapes, from the comment block at MAME ay8910.cpp:93-104
 * ("C AtAlH"):
 * <pre>
 * 0 0 x x  \___      1 0 0 0  \\\\      1 1 0 0  ////
 * 0 1 x x  /___      1 0 0 1  \___      1 1 0 1  /```
 *                    1 0 1 0  \/\/      1 1 1 0  /\/\
 *                    1 0 1 1  \```      1 1 1 1  /___
 * </pre>
 *
 * <h3>How the generator is driven</h3>
 * {@code EnvelopeGenerator.stepsPerCycle()} is 16, so
 * {@code clocksPerPeriod = 16 * period}. Choosing clock and sample rate so that
 * {@code cyclesPerSample} exactly equals {@code clocksPerPeriod} makes each
 * {@code step()} call advance the envelope by exactly one amplitude step, which
 * is what lets us assert on an exact level sequence.
 */
public class EnvelopeShapeTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int PERIOD = 2;
    /** cyclesPerSample == 16 * PERIOD == 32, so one step() == one envelope step. */
    private static final int CLOCK = 16 * PERIOD * SAMPLE_RATE;

    /**
     * Expected amplitude sequences, generated from MAME's set_shape / step
     * algorithm. Index 0 is the level immediately after writing register 13
     * (before any clocking); each subsequent entry is one envelope step.
     */
    private static final int[][] EXPECTED = {
        //  0  \___
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  1  \___
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  2  \___
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  3  \___
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  4  /___
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  5  /___
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  6  /___
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  7  /___
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        //  8  \\\\  (sawtooth down, repeating)
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 15},
        //  9  \___
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        // 10  \/\/  (triangle, starting down)
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15},
        // 11  \```  (down then hold high)
        {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15},
        // 12  ////  (sawtooth up, repeating)
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0},
        // 13  /```  (up then hold high)
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15},
        // 14  /\/\  (triangle, starting up)
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0},
        // 15  /___
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    };

    private static final String[] SHAPE_NAMES = {
        "\\___", "\\___", "\\___", "\\___", "/___", "/___", "/___", "/___",
        "\\\\\\\\", "\\___", "\\/\\/", "\\```", "////", "/```", "/\\/\\", "/___"
    };

    private static EnvelopeGenerator generatorAtPeriod(int period) {
        EnvelopeGenerator env = new EnvelopeGenerator(CLOCK, SAMPLE_RATE);
        env.setRate(CLOCK, SAMPLE_RATE);
        env.setPeriod(period);
        return env;
    }

    private static int[] captureSequence(EnvelopeGenerator env, int length) {
        int[] actual = new int[length];
        actual[0] = env.getAmplitude();
        for (int i = 1; i < length; i++) {
            env.step();
            actual[i] = env.getAmplitude();
        }
        return actual;
    }

    // ------------------------------------------------------------------
    // All 16 shapes
    // ------------------------------------------------------------------

    @Test
    public void allSixteenShapes_produceTheMameLevelSequence() {
        StringBuilder failures = new StringBuilder();
        for (int shape = 0; shape < 16; shape++) {
            EnvelopeGenerator env = generatorAtPeriod(PERIOD);
            env.setShape(shape);
            int[] actual = captureSequence(env, EXPECTED[shape].length);
            if (!java.util.Arrays.equals(EXPECTED[shape], actual)) {
                failures.append(String.format(
                    "%nshape %2d (%s)%n  expected: %s%n  actual  : %s",
                    shape, SHAPE_NAMES[shape],
                    java.util.Arrays.toString(EXPECTED[shape]),
                    java.util.Arrays.toString(actual)));
            }
        }
        assertTrue("Envelope level sequences diverge from MAME ay8910.cpp:" + failures,
                   failures.length() == 0);
    }

    @Test
    public void shapeEight_isARepeatingDownSawtooth_notASingleRamp() {
        // Guards the CONTINUE=1/HOLD=0 path specifically: the envelope must wrap
        // back to 15 rather than sticking at 0.
        EnvelopeGenerator env = generatorAtPeriod(PERIOD);
        env.setShape(8);
        int[] actual = captureSequence(env, 33);
        assertArrayEquals(EXPECTED[8], actual);
        assertEquals("shape 8 must wrap to 15 after reaching 0", 15, actual[16]);
    }

    @Test
    public void shapeTen_isATriangle_notASawtooth() {
        // Guards ALTERNATE: the second half-cycle must ramp up, not restart high.
        EnvelopeGenerator env = generatorAtPeriod(PERIOD);
        env.setShape(10);
        int[] actual = captureSequence(env, 33);
        assertArrayEquals(EXPECTED[10], actual);
        assertEquals("shape 10 must reverse direction, not wrap", 1, actual[17]);
    }

    @Test
    public void shapeThirteen_holdsHighForever_notAtZero() {
        // Guards ALTERNATE-on-HOLD: attack is inverted when latching, so the
        // held level is 15, not 0.
        EnvelopeGenerator env = generatorAtPeriod(PERIOD);
        env.setShape(13);
        int[] actual = captureSequence(env, 33);
        assertArrayEquals(EXPECTED[13], actual);
        for (int i = 16; i < actual.length; i++) {
            assertEquals("shape 13 must hold at 15", 15, actual[i]);
        }
    }

    // ------------------------------------------------------------------
    // Restart semantics: register 13 restarts, registers 11/12 do not
    // ------------------------------------------------------------------

    @Test
    public void writingShapeRegister_restartsTheEnvelope() {
        // MAME set_shape(): "step = mask; holding = 0;" — an unconditional restart.
        EnvelopeGenerator env = generatorAtPeriod(PERIOD);
        env.setShape(0);
        for (int i = 0; i < 5; i++) {
            env.step();
        }
        assertEquals("precondition: envelope has descended", 10, env.getAmplitude());

        env.setShape(0);
        assertEquals("writing register 13 must restart the envelope at 15",
                     15, env.getAmplitude());
        assertEquals("writing register 13 must reset the sub-step counter",
                     0.0, env.counter, 0.0);
    }

    @Test
    public void writingPeriodRegisters_doesNotRestartTheEnvelope() {
        // MAME set_period() only assigns the period; it does not touch step,
        // holding or count. Restarting here would retrigger the envelope on every
        // period write, which trackers do constantly.
        EnvelopeGenerator env = generatorAtPeriod(PERIOD);
        env.setShape(0);
        for (int i = 0; i < 5; i++) {
            env.step();
        }
        int amplitudeBefore = env.getAmplitude();

        env.setPeriod(PERIOD);

        assertEquals("writing registers 11/12 must not restart the envelope",
                     amplitudeBefore, env.getAmplitude());
    }

    @Test
    public void psgRegisterWrites_routeToShapeAndPeriodWithCorrectRestartSemantics() {
        // Same assertion, driven through the register interface rather than the
        // generator directly, so the PSG.writeReg routing is covered too.
        PSG psg = new PSG(0, CLOCK, SAMPLE_RATE, "test", 255);
        psg.setRate(CLOCK, SAMPLE_RATE);
        psg.setReg(PSG.Reg.EnvFine, PERIOD);
        psg.setReg(PSG.Reg.EnvCoarse, 0);
        psg.setReg(PSG.Reg.EnvShape, 0);

        for (int i = 0; i < 5; i++) {
            psg.envelopeGenerator.step();
        }
        assertNotEquals("precondition: envelope moved off its start level",
                        15, psg.envelopeGenerator.getAmplitude());

        psg.setReg(PSG.Reg.EnvFine, PERIOD);
        assertNotEquals("register 11 write must not restart the envelope",
                        15, psg.envelopeGenerator.getAmplitude());
        psg.setReg(PSG.Reg.EnvCoarse, 0);
        assertNotEquals("register 12 write must not restart the envelope",
                        15, psg.envelopeGenerator.getAmplitude());

        psg.setReg(PSG.Reg.EnvShape, 0);
        assertEquals("register 13 write must restart the envelope",
                     15, psg.envelopeGenerator.getAmplitude());
    }

    // ------------------------------------------------------------------
    // Short envelope periods must still run
    // ------------------------------------------------------------------

    @Test
    public void envelopePeriodOne_advances() {
        // Envelope period 1 gives a full 16-step ramp every 256 clocks: at the
        // Mockingboard's ~1.02 MHz that is a ~4 kHz buzz, which is exactly the
        // "envelope bass" effect PT3 and other ZX trackers rely on. A frozen
        // envelope turns that into a flat tone.
        EnvelopeGenerator env = new EnvelopeGenerator(1, 1);
        env.setRate(16 * SAMPLE_RATE, SAMPLE_RATE);   // cyclesPerSample == 16
        env.setPeriod(1);                             // clocksPerPeriod == 16
        env.setShape(0);

        assertEquals(15, env.getAmplitude());
        env.step();
        assertEquals("envelope period 1 must advance one step per 16 clocks",
                     14, env.getAmplitude());
    }

    @Test
    public void envelopePeriodZero_advancesTwiceAsFastAsPeriodOne() {
        // MAME ay8910.cpp:90-91: "note that period = 0 is the same as period = 1
        // [for tone/noise]. However, this does NOT apply to the Envelope period.
        // In that case, period = 0 is half as period = 1."
        // EnvelopeGenerator.setPeriod(0) already encodes this by setting
        // clocksPerPeriod to stepsPerCycle()/2 == 8.
        EnvelopeGenerator env = new EnvelopeGenerator(1, 1);
        env.setRate(8 * SAMPLE_RATE, SAMPLE_RATE);    // cyclesPerSample == 8
        env.setPeriod(0);                             // clocksPerPeriod == 8
        env.setShape(0);

        assertEquals(15, env.getAmplitude());
        env.step();
        assertEquals("envelope period 0 must advance one step per 8 clocks",
                     14, env.getAmplitude());
    }
}
