package jace.hardware.mockingboard;

import static org.lwjgl.openal.AL10.AL_NO_ERROR;
import static org.lwjgl.openal.AL10.alGetError;

import java.io.InputStream;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.sound.sampled.Mixer;

import org.lwjgl.BufferUtils;
import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.openal.EXTEfx.*;

import jace.core.SoundMixer;
import jace.core.SoundMixer.SoundBuffer;
import jace.core.SoundMixer.SoundError;
import jace.core.TimedDevice;

public class Votrax extends TimedDevice {
    // This is a speech synthesizer based on the Votrax SC-02
    // There are 2 sound generators, one for the voice and one for the noise
    // The voice generator is a saw-tooth wave generator at a frequency determined by the voice frequency register
    // The noise generator is a pseudo-random noise generator

    // The Votrax has 5 filters that can be applied to the voice generator, controlled by a filter frequency register
    // The fifth filter takes both the voice and noise generators as input, but other filters only take the voice generator
    // There is also a final high-pass filter that can be applied to the output of the voice and noise generators

    // There is a phoneme register which controls the phoneme to be spoken (0-63)
    // There is a duration register which controls the duration of the phoneme (0-3)
    // There is a rate register which controls the rate of speech (0-15)
    // There is an inflection register which controls the inflection of the voice (0-15)

    // For each phoneme there are 8 bytes that control the filters and sound generator levels
    byte[] phonemeData = new byte[64 * 8];
    // Phoneme chart:
    // 00: PA (pause)
    // 01: E (mEEt)
    // 02: E1 (bEnt)
    // 03: Y (bEfore)
    // 04: Y1 (Year)
    // 05: AY (plEAse)
    // 06: IE (anY)
    // 07: I (sIx)
    // 08: A (mAde)
    // 09: A1 (cAre)
    // 0a: EH (nEst)
    // 0b: EH1 (bElt)
    // 0c: AE (dAd)
    // 0d: AE1 (After)
    // 0e: AH (gOt)
    // 0f: AH1 (fAther)
    // 10: AW (Office)
    // 11: O (stOre)
    // 12: OU (bOAt)
    // 13: OO (lOOk)
    // 14: IU (yOU)
    // 15: IU1 (cOUld)
    // 16: U (tUne)
    // 17: U1 (cartOOn)
    // 18: UH (wOnder)
    // 19: UH1 (lOve)
    // 1a: UH2 (whAt)
    // 1b: UH3 (nUt)
    // 1c: ER (bIRd)
    // 1d: R (Roof)
    // 1e: R01 (Rug)
    // 1f: R2 (mutteR -- German)
    // 20: L (Lift)
    // 21: L1 (pLay)
    // 22: LF (faLL)
    // 23: W (Water)
    // 24: B (Bag)
    // 25: D (paiD)
    // 26: KV (taG)
    // 27: P (Pen)
    // 28: T (Tart)
    // 29: K (Kit)
    // 2a: HV - Hold Vocal
    // 2b: HVC - Hold Vocal Closure
    // 2c: HF - (Heart)
    // 2d: HFC - Hold Frictave Closure
    // 2e: HN - Hold Nasal
    // 2f: Z (Zero)
    // 30: S (Same)
    // 31: J (meaSure)
    // 32: SCH (SHip)
    // 33: V (Very)
    // 34: F (Four)
    // 35: THV (THere)
    // 36: TH (wiTH)
    // 37: M (More)
    // 38: N (NiNe)
    // 39: NG (raNG)
    // 3a: :A (mAErchen -- German)
    // 3b: :OH (lOwe - French)
    // 3c: :U (fUEnf -- German)
    // 3d: :UH (menU -- French)
    // 3e: E2 (bittE -- German)
    // 3f: LB (Lube)

    public void loadPhonemeData() {
        InputStream romFile = Votrax.class.getResourceAsStream("jace/data/sc01a.bin");
        if (romFile == null) {
            throw new RuntimeException("Cannot find Votrax SC-01A ROM");
        }
        // Load into phonemeData
        try {
            if (romFile.read(phonemeData) != phonemeData.length) {
                throw new RuntimeException("Bad Votrax SC-01A ROM size");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error loading Votrax SC-01A ROM", ex);
        }
    }

    public static abstract class Generator {
        public int clockFrequency = 1;
        public int sampleRate = 1;
        public double samplesPerClock;
        public double sampleCounter = 0.0;
        public void setClockFrequency(int clockFrequency) {
            this.clockFrequency = clockFrequency;
            this.updateFrequency();
        }
        public void setSampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            this.updateFrequency();
        }
        public void updateFrequency() {
            this.sampleCounter = 0.0;
            this.samplesPerClock = clockFrequency / sampleRate;
        }
        public abstract int getBufferDuration();

        public void fillBuffer(ShortBuffer buffer) {
            for (int i = 0; i < getBufferDuration(); i++) {
                buffer.put((short) (doGenerate() * 32767));
            }
        }

        public abstract double doGenerate();
    }

    public static class SawGenerator extends Generator {
        double pitch=440.0;
        double sample = 0.0;
        double direction = 1.0;
        double changePerSample = 0.0;
        public void setPitch(double frequency) {
            this.pitch = frequency;
            this.updateFrequency();
        }
        public void setDirection(double direction) {
            this.direction = direction;
            this.updateFrequency();
        }
        public void updateFrequency() {
            super.updateFrequency();
            changePerSample = direction * 2.0 * pitch / sampleRate;
        }

        @Override
        // Let's generate 10 loops of the sawtooth wave
        public int getBufferDuration() {
            return (int) (10.0 * sampleRate / pitch);
        }

        public double doGenerate() {
            sample += changePerSample;
            if (sample < -1.0) {
                sample += 2.0;
            } else if (sample > 1.0) {
                sample -= 2.0;
            }
            return sample;
        }
    }

    public static class NoiseGenerator extends Generator {
        double sample = 0.0;
        public double doGenerate() {
            return Math.random() * 2.0 - 1.0;
        }
        @Override
        // Let's generate 10 seconds of noise
        public int getBufferDuration() {
            return sampleRate * 10;
        }
    }
    
    public float mixerGain = 32767.0f;
    public int[] filters = new int[5];
    public SawGenerator formantGenerator = new SawGenerator();
    public NoiseGenerator noiseGenerator = new NoiseGenerator();
    public static int FORMANT = 0;
    public static int NOISE = 1;
    // private Thread playbackThread = null;

    public Votrax() throws Exception {
        // loadPhonemeData();
        formantGenerator.setSampleRate(44100);
        formantGenerator.setPitch(100);
        noiseGenerator.setSampleRate(44100);
   }

   int formantSource = -1;
   int noiseSource = -1;

   public void resume() {
        // Create a buffer for the sawtooth wave
        ShortBuffer formantLoop = BufferUtils.createShortBuffer(formantGenerator.getBufferDuration());
        // Create a buffer for the noise
        ShortBuffer noiseLoop = BufferUtils.createShortBuffer(noiseGenerator.getBufferDuration());
        // Fill the buffers
        formantGenerator.fillBuffer(formantLoop);
        noiseGenerator.fillBuffer(noiseLoop);

        // Create a source for the formant generator
        formantSource = alGenSources();
        alSourcei(formantSource, AL_BUFFER, formantLoop.get(0));
        alSourcei(formantSource, AL_LOOPING, AL_TRUE);
        alSourcePlay(formantSource);
        // Create a source for the noise generator
        noiseSource = alGenSources();
        alSourcei(noiseSource, AL_BUFFER, noiseLoop.get(0));
        alSourcei(noiseSource, AL_LOOPING, AL_TRUE);
        alSourcePlay(noiseSource);
        
        try {
            createFilters();
        } catch (Exception e) {
            e.printStackTrace();
            suspend();
        }
        super.resume();
        // if (playbackThread != null && !playbackThread.isAlive()) {
        //     return;
        // }
        // playbackThread = new Thread(() -> {
        //     SoundBuffer soundBuffer = null;
        //     try {
        //         soundBuffer = SoundMixer.createBuffer(false);
        //         while (isRunning()) {
        //             try {
        //                 soundBuffer.playSample((short) (mixer.doGenerate() * mixerGain));
        //             } catch (Exception e) {
        //                 e.printStackTrace();
        //                 suspend();
        //             }
        //         }
        //     } catch (InterruptedException | ExecutionException | SoundError e) {
        //         e.printStackTrace();
        //         suspend();
        //     } finally {
        //         try {
        //             soundBuffer.shutdown();
        //         } catch (InterruptedException | ExecutionException | SoundError e) {
        //             e.printStackTrace();
        //         }
        //     }
        // });
        // playbackThread.start();
    }

    private void createFilters() throws Exception {
        // TODO: Consider filter values from here: https://modwiggler.com/forum/viewtopic.php?t=234128
        // Bark scale: 60, 150, 250, 350, 450, 570, 700, 840, 1000, 1170, 1370, 1600, 1850, 2150, 2500, 2900
        // Roland SVC-350:  150, 220, 350, 500, 760, 1100, 1600, 2200, 3600, 5200. 6000 highpass filter
        // EMS Vocoder System 3000: 125, 185, 270, 350, 430, 530, 630, 780, 950, 1150, 1380, 2070, 2780, 3800, 6400

        for (int i = 0; i < 5; i++) {
            alGetError();
            filters[i] = alGenFilters();
            if (alGetError() != AL_NO_ERROR) {
                throw new Exception("Failed to create filter " + i);
            }
            if (alIsFilter(filters[i])) {
                // Set Filter type to Band-Pass and set parameters
                alFilteri(filters[i], AL_FILTER_TYPE, AL_FILTER_BANDPASS);
                if (alGetError() != AL_NO_ERROR) {
                    System.out.println("Band pass filter not supported.");
                } else {
                    alFilterf(filters[i], AL_BANDPASS_GAIN, 0.5f);
                    alFilterf(filters[i], AL_BANDPASS_GAINHF, 0.5f);
                    System.out.println("Band pass filter "+i+" created.");
                    // Now add an aux send for the noise and formant sources to go to this filter
                    // Inspiration: https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/openal/EFXTest.java
                    alSource3i(formantSource, AL_AUXILIARY_SEND_FILTER, filters[i], 0, 0);
                }
            }            
        }
    }

    public boolean suspend() {
        destroyFilters();

        // playbackThread = null;
        return super.suspend();
    }

    private void destroyFilters() {
        for (int i = 0; i < 5; i++) {
            if (alIsFilter(filters[i])) {
                alDeleteFilters(filters[i]);
            }
        }
    }

    @Override
    public String getShortName() {
        return "Votrax";
    }

    @Override
    protected String getDeviceName() {
        return "Votrax SC-02 / SSI-263";
    }

    @Override
    public void tick() {
    }
}