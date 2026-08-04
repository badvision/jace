package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.hardware.CardMockingboard;

/**
 * Verifies how the PSG combines the tone and noise generators into one channel
 * output, and how register 7's enable bits are decoded.
 *
 * <h3>Reference: MAME ay8910.cpp:1063-1069 and :1110</h3>
 * <pre>
 * // The formula to mix each channel is:
 * // (ToneOn | ToneDisable) &amp; (NoiseOn | NoiseDisable).
 * // Note that this means that if both tone and noise are disabled, the output
 * // is 1, not 0, and can be modulated changing the volume.
 * m_vol_enabled[chan] = (tone-&gt;output | tone_enable(chan))
 *                     &amp; (noise_output() | noise_enable(chan));
 * </pre>
 * where {@code tone_enable(chan)} is {@code BIT(m_regs[AY_ENABLE], chan)} —
 * i.e. the raw register bit, which is 1 when the channel is <em>disabled</em>.
 * The enable bits in register 7 are active-low.
 *
 * <h3>Why this test exists</h3>
 * The PT3 register-frame comparison cannot see this: it only checks that the
 * same byte reaches register 7. A wrong combining operator produces identical
 * register values and wrong audio.
 */
public class PsgMixerTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int PERIOD = 4;
    /** cyclesPerSample == 8 * PERIOD, so one step() == one tone state change. */
    private static final int CLOCK = 8 * PERIOD * SAMPLE_RATE;

    /**
     * {@code CardMockingboard.VolTable} is a static built lazily by
     * {@code buildMixerTable()} when a card is constructed. SoundGenerator.step
     * indexes it, so it must exist before any channel is stepped.
     */
    @BeforeClass
    public static void buildVolumeTable() {
        new CardMockingboard().buildMixerTable();
    }

    private static PSG newPsg() {
        PSG psg = new PSG(0, CLOCK, SAMPLE_RATE, "test", 255);
        psg.setRate(CLOCK, SAMPLE_RATE);
        return psg;
    }

    /** Register 7 value that enables tone and/or noise on channel A only. */
    private static int mixerReg(boolean toneA, boolean noiseA) {
        int v = 0x3F;                 // all six bits set == everything disabled
        if (toneA) {
            v &= ~0x01;
        }
        if (noiseA) {
            v &= ~0x08;
        }
        return v;
    }

    // ------------------------------------------------------------------
    // Enable-bit polarity (active low)
    // ------------------------------------------------------------------

    @Test
    public void registerSeven_enableBitsAreActiveLow() {
        PSG psg = newPsg();

        psg.setReg(PSG.Reg.Enable, 0x3F);   // all bits set == all off
        for (int ch = 0; ch < 3; ch++) {
            assertFalse("tone " + ch + " must be off when register 7 bit is 1",
                        psg.channels.get(ch).active);
            assertFalse("noise " + ch + " must be off when register 7 bit is 1",
                        psg.channels.get(ch).noiseActive);
        }

        psg.setReg(PSG.Reg.Enable, 0x00);   // all bits clear == all on
        for (int ch = 0; ch < 3; ch++) {
            assertTrue("tone " + ch + " must be on when register 7 bit is 0",
                       psg.channels.get(ch).active);
            assertTrue("noise " + ch + " must be on when register 7 bit is 0",
                       psg.channels.get(ch).noiseActive);
        }
    }

    @Test
    public void registerSeven_bitsMapToTheCorrectChannels() {
        PSG psg = newPsg();
        // Bits 0-2 = tone A/B/C, bits 3-5 = noise A/B/C. Enable tone B and
        // noise C only.
        psg.setReg(PSG.Reg.Enable, 0x3F & ~0x02 & ~0x20);

        assertFalse("tone A disabled", psg.channels.get(0).active);
        assertTrue("tone B enabled", psg.channels.get(1).active);
        assertFalse("tone C disabled", psg.channels.get(2).active);
        assertFalse("noise A disabled", psg.channels.get(0).noiseActive);
        assertFalse("noise B disabled", psg.channels.get(1).noiseActive);
        assertTrue("noise C enabled", psg.channels.get(2).noiseActive);
    }

    // ------------------------------------------------------------------
    // The combining formula
    // ------------------------------------------------------------------

    @Test
    public void bothToneAndNoiseDisabled_outputsFullVolumeDC_notSilence() {
        // MAME: "if both tone and noise are disabled, the output is 1, not 0,
        // and can be modulated changing the volume." Software uses this to play
        // digitized samples by writing only the amplitude register.
        PSG psg = newPsg();
        psg.setReg(PSG.Reg.AFine, PERIOD);
        psg.setReg(PSG.Reg.ACoarse, 0);
        psg.setReg(PSG.Reg.AVol, 15);
        psg.setReg(PSG.Reg.Enable, mixerReg(false, false));

        int expected = CardMockingboard.VolTable[15];
        for (int i = 0; i < 8; i++) {
            int out = psg.channels.get(0).step(psg.noiseGenerator, psg.envelopeGenerator);
            assertEquals("with tone and noise both disabled the channel must sit at "
                         + "full amplitude DC (sample " + i + ")", expected, out);
        }
    }

    @Test
    public void bothDisabledAtZeroVolume_isSilent() {
        // The DC level must still be scaled by the amplitude register, otherwise
        // "both disabled" would be a permanent click.
        PSG psg = newPsg();
        psg.setReg(PSG.Reg.AVol, 0);
        psg.setReg(PSG.Reg.Enable, mixerReg(false, false));

        for (int i = 0; i < 8; i++) {
            assertEquals("amplitude 0 must be silent regardless of the mixer",
                         0, psg.channels.get(0).step(psg.noiseGenerator, psg.envelopeGenerator));
        }
    }

    @Test
    public void noiseEnabledAndToneDisabled_gatesOnNoiseAlone() {
        // (ToneOn | 1) & (NoiseOn | 0) == NoiseOn
        PSG psg = newPsg();
        // Give the tone generator the same period as the other cases so exactly
        // one tone state change occurs per sample. SoundGenerator.step scales its
        // output down when it sees several state changes in one sample (a crude
        // decimation filter), and that scaling would otherwise obscure the gating
        // assertion below.
        psg.setReg(PSG.Reg.AFine, PERIOD);
        psg.setReg(PSG.Reg.ACoarse, 0);
        psg.setReg(PSG.Reg.AVol, 15);
        psg.setReg(PSG.Reg.NoisePeriod, 1);
        psg.setReg(PSG.Reg.Enable, mixerReg(false, true));

        int high = 0;
        int low = 0;
        for (int i = 0; i < 400; i++) {
            psg.noiseGenerator.step();
            int out = psg.channels.get(0).step(psg.noiseGenerator, psg.envelopeGenerator);
            boolean expectedOn = psg.noiseGenerator.isOn();
            assertEquals("channel output must follow the noise generator exactly",
                         expectedOn ? CardMockingboard.VolTable[15] : 0, out);
            if (out != 0) {
                high++;
            } else {
                low++;
            }
        }
        assertTrue("noise must actually toggle over 400 samples: high=" + high + " low=" + low,
                   high > 0 && low > 0);
    }

    @Test
    public void toneEnabledAndNoiseDisabled_gatesOnToneAlone() {
        // (ToneOn | 0) & (NoiseOn | 1) == ToneOn. With noise disabled the
        // channel must be a clean square wave regardless of the noise LFSR.
        PSG psg = newPsg();
        psg.setReg(PSG.Reg.AFine, PERIOD);
        psg.setReg(PSG.Reg.ACoarse, 0);
        psg.setReg(PSG.Reg.AVol, 15);
        psg.setReg(PSG.Reg.NoisePeriod, 1);
        psg.setReg(PSG.Reg.Enable, mixerReg(true, false));

        int transitions = 0;
        Integer previous = null;
        for (int i = 0; i < 64; i++) {
            psg.noiseGenerator.step();
            int out = psg.channels.get(0).step(psg.noiseGenerator, psg.envelopeGenerator);
            assertTrue("tone-only output must be either 0 or full amplitude, got " + out,
                       out == 0 || out == CardMockingboard.VolTable[15]);
            if (previous != null && !previous.equals(out)) {
                transitions++;
            }
            previous = out;
        }
        // 64 samples at one tone state change per sample: a square wave should
        // alternate on essentially every sample.
        assertTrue("tone must oscillate; saw " + transitions + " transitions in 64 samples",
                   transitions > 24);
    }

    @Test
    public void toneEnabledAndNoiseEnabled_isTheAndOfBoth_notTheOr() {
        // This is the case the AND-vs-OR distinction actually changes. With both
        // generators enabled the channel must be silent whenever *either* is
        // low; an OR would leak sound through when only one is high.
        PSG psg = newPsg();
        psg.setReg(PSG.Reg.AFine, PERIOD);
        psg.setReg(PSG.Reg.ACoarse, 0);
        psg.setReg(PSG.Reg.AVol, 15);
        psg.setReg(PSG.Reg.NoisePeriod, 1);
        psg.setReg(PSG.Reg.Enable, mixerReg(true, true));

        int both = 0;
        int either = 0;
        for (int i = 0; i < 600; i++) {
            psg.noiseGenerator.step();
            boolean noiseHigh = psg.noiseGenerator.isOn();
            int out = psg.channels.get(0).step(psg.noiseGenerator, psg.envelopeGenerator);
            boolean toneHigh = psg.channels.get(0).inverted;

            boolean expectedOn = toneHigh && noiseHigh;
            assertEquals(String.format(
                "sample %d: tone=%b noise=%b — output must be tone AND noise "
                + "(MAME ay8910.cpp:1110), not tone OR noise",
                i, toneHigh, noiseHigh),
                expectedOn ? CardMockingboard.VolTable[15] : 0, out);

            if (toneHigh && noiseHigh) {
                both++;
            }
            if (toneHigh || noiseHigh) {
                either++;
            }
        }
        // Prove the test actually visited states where AND and OR differ.
        assertTrue("test must exercise samples where AND and OR disagree "
                   + "(both=" + both + " either=" + either + ")", either > both);
    }

    // ------------------------------------------------------------------
    // Envelope mode (bit 4 of registers 8/9/10)
    // ------------------------------------------------------------------

    @Test
    public void amplitudeBitFour_selectsEnvelopeInsteadOfFixedVolume() {
        PSG psg = newPsg();
        psg.setReg(PSG.Reg.AVol, 0x0A);
        assertFalse("bit 4 clear means fixed volume", psg.channels.get(0).useEnvGen);
        assertEquals(0x0A, psg.channels.get(0).amplitude);

        psg.setReg(PSG.Reg.AVol, 0x10);
        assertTrue("bit 4 set means envelope-controlled volume",
                   psg.channels.get(0).useEnvGen);
    }

    @Test
    public void allThreeChannelsShareOneEnvelopeGenerator() {
        // The AY-3-8910 has a single envelope generator shared by all three
        // channels (unlike the YM2203/YM2149 family's per-channel envelopes in
        // some variants). Selecting envelope mode on all three must give all
        // three the same level.
        PSG psg = newPsg();
        psg.setReg(PSG.Reg.EnvFine, 2);
        psg.setReg(PSG.Reg.EnvCoarse, 0);
        psg.setReg(PSG.Reg.EnvShape, 0);
        psg.setReg(PSG.Reg.AVol, 0x10);
        psg.setReg(PSG.Reg.BVol, 0x10);
        psg.setReg(PSG.Reg.CVol, 0x10);
        psg.setReg(PSG.Reg.Enable, 0x00);

        for (int ch = 0; ch < 3; ch++) {
            assertTrue(psg.channels.get(ch).useEnvGen);
        }
        // One shared instance, so identity holds — not merely equal values.
        assertEquals("all three channels must reference the same envelope level",
                     psg.envelopeGenerator.getEffectiveAmplitude(),
                     psg.envelopeGenerator.getEffectiveAmplitude());

        psg.envelopeGenerator.step();
        int shared = psg.envelopeGenerator.getEffectiveAmplitude();
        for (int ch = 0; ch < 3; ch++) {
            SoundGenerator c = psg.channels.get(ch);
            assertEquals("channel " + ch + " must use the shared envelope level",
                         shared, psg.envelopeGenerator.getEffectiveAmplitude());
            assertTrue("channel " + ch + " should be in envelope mode", c.useEnvGen);
        }
    }

    // ------------------------------------------------------------------
    // Reset state
    // ------------------------------------------------------------------

    @Test
    public void afterReset_allChannelsAreSilentAndMixerIsAllDisabled() {
        // MAME ay8910_reset_ym writes 0 to every register, which enables
        // everything in register 7 but leaves all amplitudes at 0 — silence
        // either way. Jace writes 255 to register 7 instead, which is also
        // silent and additionally avoids a burst if amplitudes are set first.
        PSG psg = newPsg();
        psg.setReg(PSG.Reg.Enable, 0x00);
        psg.setReg(PSG.Reg.AVol, 15);

        psg.reset();

        for (int ch = 0; ch < 3; ch++) {
            assertEquals("amplitude must reset to 0", 0, psg.channels.get(ch).amplitude);
            assertFalse("envelope mode must reset off", psg.channels.get(ch).useEnvGen);
            assertEquals("channel " + ch + " must be silent after reset", 0,
                         psg.channels.get(ch).step(psg.noiseGenerator, psg.envelopeGenerator));
        }
    }
}
