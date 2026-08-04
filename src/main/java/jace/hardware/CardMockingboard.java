/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.hardware;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.RAMListener;
import jace.core.SoundMixer;
import jace.core.SoundMixer.SoundBuffer;
import jace.core.SoundMixer.SoundError;
import jace.core.TimedDevice;
import jace.hardware.mockingboard.PSG;
import jace.hardware.mockingboard.R6522;

/**
 * Mockingboard-C implementation (with partial Phasor support). This uses two
 * 6522 chips to communicate to two respective AY PSG sound chips. This class
 * manages the I/O access as well as the sound playback thread.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("Mockingboard")
public class CardMockingboard extends Card {
    // If true, emulation will cover 4 AY chips.  Otherwise, only 2 AY chips

    @ConfigurableField(name = "Debug", category = "Sound", description = "Enable debug output")
    public static boolean DEBUG = false;

    @ConfigurableField(name = "Volume", shortName = "vol",
            category = "Sound",
            description = "Mockingboard volume, 100=max, 0=silent")
    public int volume = 100;
    static public int MAX_AMPLITUDE = 0x007fff;
    @ConfigurableField(name = "Phasor mode",
            category = "Sound",
            description = "If enabled, card will have 4 sound chips instead of 2")
    public boolean phasorMode = false;
    /**
     * Clock supplied to the AY oscillators.
     *
     * This is the Apple II bus clock the peripheral slots see: the 14.31818 MHz
     * NTSC colorburst crystal divided by 14, which is 1022727 Hz.
     *
     * It is deliberately NOT {@link jace.core.TimedDevice#NTSC_1MHZ} (1020484).
     * That lower figure is the 6502's *average* rate, which only differs because
     * the video logic stretches one cycle per scanline. The stretch describes how
     * long the CPU waits; it does not slow the oscillator down. A card in a slot
     * is clocked by the crystal-derived bus clock, so the AY runs at 1022727 Hz.
     * Using the CPU average here makes every tone about 3.8 cents flat.
     *
     * NTSC_1MHZ remains correct for things that count CPU cycles -- this card's
     * tick pacing and the 6522 timers.
     */
    @ConfigurableField(name = "Clock Rate (hz)",
            category = "Sound",
            defaultValue = "1022727",
            description = "Clock rate of AY oscillators (Apple II slot clock: 14.31818MHz / 14)")
    public int CLOCK_SPEED = 1022727;
    // The array of configured AY chips
    public PSG[] chips;
    // The 6522 controllr chips (always 2)
    public R6522[] controllers;
    @ConfigurableField(name = "Idle sample threshold", description = "Number of samples to wait before suspending sound")
    SoundBuffer buffer;
    double ticksBetweenPlayback = 24.0;
    int MAX_IDLE_TICKS = 1000000;
    boolean activatedAfterReset = false;

    @Override
    public String getDeviceName() {
        return "Mockingboard";
    }

    public CardMockingboard() {
        super(true);
        activatedAfterReset = false;
        controllers = new R6522[2];
        for (int i = 0; i < 2; i++) {
            // has to be final to be used inside of anonymous class below
            final int j = i;
            controllers[i] = new R6522() {
                @Override
                public void sendOutputA(int value) {
                    chips[j].setBus(value);
                    if (phasorMode) {
                        chips[j + 2].setBus(value);
                    }
                }

                @Override
                public void sendOutputB(int value) {
                    if (phasorMode) {
                        if ((chips[j].mask & value) != 0) {
                            chips[j].setControl(value & 0x07);
                        }
                        if ((chips[j + 2].mask & value) != 0) {
                            chips[j + 2].setControl(value & 0x07);
                        }
                    } else {
                        chips[j].setControl(value & 0x07);
                    }
                }

                @Override
                public int receiveOutputA() {
                    return chips[j] == null ? 0 : chips[j].bus;
                }

                @Override
                public int receiveOutputB() {
                    return 0;
                }

                @Override
                public String getShortName() {
                    return "timer" + j;
                }                
            };
            addChildDevice(controllers[i]);
        }
    }

    @Override
    public void reset() {
        activatedAfterReset = false;
        if (chips != null) {
            for (PSG p : chips) {
                p.reset();
            }
        }
        suspend();
    }
    RAMListener mainListener = null;
    
    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        if (chips == null) {
            reconfigure();
        }

        int chip = 0;
        for (PSG psg : chips) {
            if (psg.getBaseReg() == (register & 0x0f0)) {
                break;
            }
            chip++;
        }
        if (chip >= 2) {
            if (DEBUG) {
                System.err.println("Could not determine which PSG to communicate to for access to regsiter + " + Integer.toHexString(register));
            }
            Emulator.withVideo(v->e.setNewValue(v.getFloatingBus()));
            return;
        }
        R6522 controller = controllers[chip & 1];
        if (e.getType().isRead()) {
            int val = controller.readRegister(register & 0x0f);
            e.setNewValue(val);
            // if (DEBUG) System.out.println("Chip " + chip + " Read "+Integer.toHexString(register & 0x0f)+" == "+val);
        } else {
            controller.writeRegister(register & 0x0f, e.getNewValue());
            // if (DEBUG) System.out.println("Chip " + chip + " Write "+Integer.toHexString(register & 0x0f)+" == "+e.getNewValue());
        }
        // Any firmware access will reset the idle counter and wake up the card, this allows the timers to start running again
        // Games such as "Skyfox" use the timer to detect if the card is present.
        idleTicks = 0;
        if (!isRunning() || isPaused()) {
            activatedAfterReset = true;
            // ResumeAll is important so that the 6522's can start their timers
            resumeAll();
        }
}

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        // Oddly, all IO is done at the firmware address bank.  It's a strange card.
        if (DEBUG) {
            System.out.println("MB I/O Access "+type.name()+" "+register+":"+value);
        }
        Emulator.withVideo(v->e.setNewValue(v.getFloatingBus()));
    }
    double ticksSinceLastPlayback = 0;
    long idleTicks = 0;
    @Override
    public void tick() {
        try {
            ticksSinceLastPlayback++;
            if (ticksSinceLastPlayback >= ticksBetweenPlayback) {
                ticksSinceLastPlayback -= ticksBetweenPlayback;
                if (playSound()) {
                    idleTicks = 0;
                } else {
                    idleTicks += ticksBetweenPlayback;
                }
            }
        } catch (InterruptedException | ExecutionException | SoundError | NullPointerException ex) {
            Logger.getLogger(CardMockingboard.class.getName()).log(Level.SEVERE, "Mockingboard playback encountered fatal exception", ex);
            suspend();
            // Do nothing, probably suspending CPU
        }

        if (idleTicks >= MAX_IDLE_TICKS) {
            suspend();
        }
    }

    @Override
    public void reconfigure() {
        if (DEBUG) {
            System.out.println("Reconfiguring Mockingboard");
        }
        // tick() counts this card's ticks, and the card is paced by the motherboard
        // at the CPU rate -- so the sample interval comes from NTSC_1MHZ, not from
        // CLOCK_SPEED (the AY oscillator clock, which is a different number).
        ticksBetweenPlayback = (double) TimedDevice.NTSC_1MHZ / (double) SoundMixer.RATE;
        initPSG();

        super.reconfigure();
        if (DEBUG) {
            System.out.println("Reconfiguring Mockingboard completed");
        }
    }

///////////////////////////////////////////////////////////
    public static int[] VolTable;

    AtomicInteger left  = new AtomicInteger(0);
    AtomicInteger right = new AtomicInteger(0);
    public boolean playSound() throws InterruptedException, ExecutionException, SoundError {
        SoundBuffer b = buffer;
        if (b == null) {
            return false;
        }
        if (phasorMode && chips.length != 4) {
            System.err.println("Wrong number of chips for phasor mode, correcting this");
            initPSG();
        }
        chips[0].update(left, true, left, false, left, false);
        chips[1].update(right, true, right, false, right, false);
        if (phasorMode) {
            chips[2].update(left, false, left, false, left, false);
            chips[3].update(right, false, right, false, right, false);
        }
        b.playSample((short) left.get());
        b.playSample((short) right.get());
        return (left.get() != 0 || right.get() != 0);
    }

    /**
     * Relative amplitude of each of the AY's 16 levels, as a fraction of full
     * scale, derived from measurements of real hardware rather than from a
     * logarithmic formula.
     *
     * <p>A Mockingboard uses the <b>AY-3-8913</b> -- the AY-3-8910's PSG core in a
     * 24-pin package with the parallel I/O ports omitted. MAME models it as
     * {@code ay8910_device(..., PSG_TYPE_AY, 3, 0)} (ay8910.cpp:1630-1631), and
     * because it is {@code PSG_TYPE_AY} the same 16-entry {@code ay8910_param}
     * serves both the tone and the envelope DAC (ay8910.cpp:1578-1579, with
     * {@code m_env_step_mask = 0x0f} at :1575). The YM2149's 32-entry envelope
     * table does not apply here.
     *
     * <p>The numbers come from Matthew Westcott's December 2001 measurements of an
     * AY-3-8910, which MAME cites for its active {@code ay8910_param}
     * (ay8910.cpp:678-722). He set channel C to a constant voltage (register 6
     * bits 2 and 5), swept the low 4 bits of register 10, and measured pin 1
     * against ground:
     *
     * <pre>
     *   level: 0      1      2      3      4      5      6      7
     *   volts: 1.147  1.162  1.169  1.178  1.192  1.213  1.238  1.299
     *   level: 8      9      10     11     12     13     14     15
     *   volts: 1.336  1.457  1.573  1.707  1.882  2.06   2.32   2.58
     * </pre>
     *
     * <p>The values below are those voltages expressed as swing above the level-0
     * floor, so that level 0 is silence and level 15 is full scale:
     * {@code (v[i] - v[0]) / (v[15] - v[0])}.
     *
     * <p><b>Why the readings and not MAME's table verbatim.</b> MAME does not store
     * these voltages; it stores a set of equivalent output <em>resistances</em>
     * which it converts at runtime through {@code build_single_table}, a divider
     * against {@code r_up = 800000}, {@code r_down = 8000000} and a load
     * resistance. Those resistances were fitted in SwitcherCAD to reproduce the
     * readings <em>through the ZX Spectrum's output circuit</em> (see the same
     * comment block: "The ZX spectrum output circuit was modelled in SwitcherCAD
     * and the resistor values below create the voltage levels above"). Jace models
     * no such output network -- it treats level 0 as digital silence and sums
     * channels directly -- so the divider's DC floor and load-dependence are not
     * applicable here. Notably, no load value reproduces the raw readings exactly:
     * the residual bottoms out around 0.0056 of full scale at RL = 1800, and the
     * annotated RL = 2000 leaves 0.011, diverging by about 4 dB at level 1. The
     * measurements are the empirical ground truth; the resistor network is a model
     * of a circuit Jace does not have.
     *
     * <p>Do not replace this with a uniform dB-per-step curve. The real chip's
     * steps range from about 1.74 dB to about 4.46 dB and are not monotonic in
     * size; the regularity of a synthesized curve is not a feature. Relative to a
     * 3 dB/step model every intermediate level sits higher -- by up to +4.8 dB
     * around levels 7-10 -- which compresses the level-1-to-15 span from 42.1 dB
     * to the measured 39.6 dB.
     */
    private static final double[] AY_MEASURED_LEVELS = {
        0.000000, 0.010468, 0.015352, 0.021633,
        0.031403, 0.046057, 0.063503, 0.106071,
        0.131891, 0.216329, 0.297278, 0.390789,
        0.512910, 0.637125, 0.818562, 1.000000
    };

    public void buildMixerTable() {
        VolTable = new int[AY_MEASURED_LEVELS.length];
        int numChips = phasorMode ? 4 : 2;

        double out = (MAX_AMPLITUDE * volume) / 100.0;
        // Reduce max amplitude to reflect post-mixer values so we don't have to scale volume when mixing channels
        out = out * 2.0 / 3.0 / numChips;
        for (int i = 0; i < AY_MEASURED_LEVELS.length; i++) {
            VolTable[i] = (int) (out * AY_MEASURED_LEVELS[i]);
        }
    }

    @Override
    public void resume() {
        if (DEBUG) {
            System.out.println("Resuming Mockingboard");
        }
        if (!activatedAfterReset) {
            if (DEBUG) {
                System.out.println("Resuming Mockingboard: not activated after reset, not resuming");
            }
            // Do not re-activate until firmware access was made
            return;
        }
        initPSG();
        if (buffer == null || !buffer.isAlive()) {
            if (DEBUG) {
                System.out.println("Resuming Mockingboard: creating sound buffer");
            }
            try {
                buffer = SoundMixer.createBuffer(true);
            } catch (InterruptedException | ExecutionException | SoundError e) {
                System.out.println("Error whhen trying to create sound buffer for Mockingboard: " + e.getMessage());
                e.printStackTrace();
                suspend();
            }
        }
        idleTicks = 0;
        super.resume();
        if (DEBUG) {
            System.out.println("Resuming Mockingboard: resume completed");
        }
    }

    @Override
    public boolean suspend() {
        if (DEBUG) {
            System.out.println("Suspending Mockingboard");
            Thread.dumpStack();
        }

        if (buffer != null) {
            try {
                buffer.shutdown();
            } catch (InterruptedException | ExecutionException | SoundError e) {
                System.out.println("Error when trying to shutdown sound buffer for Mockingboard: " + e.getMessage());
                e.printStackTrace();
            } finally {
                buffer = null;
            }
        }
        // Do NOT suspend R6522 controllers — their timers must keep running
        // to generate IRQs regardless of whether audio playback is active.
        // Stopping the timers here breaks headless/reglog mode where sound
        // output is unavailable but the player still needs IRQ-driven timing.
        return super.suspend();
    }
    
    private void initPSG() {
        if (phasorMode && (chips == null || chips.length < 4)) {
            chips = new PSG[4];
            chips[0] = new PSG(0x10, CLOCK_SPEED * 2, SoundMixer.RATE, "AY1", 8);
            chips[1] = new PSG(0x80, CLOCK_SPEED * 2, SoundMixer.RATE, "AY2", 8);
            chips[2] = new PSG(0x10, CLOCK_SPEED * 2, SoundMixer.RATE, "AY3", 16);
            chips[3] = new PSG(0x80, CLOCK_SPEED * 2, SoundMixer.RATE, "AY4", 16);
        } else if (chips == null || chips.length != 2) {
            chips = new PSG[2];
            chips[0] = new PSG(0, CLOCK_SPEED, SoundMixer.RATE, "AY1", 255);
            chips[1] = new PSG(0x80, CLOCK_SPEED, SoundMixer.RATE, "AY2", 255);
        }
        for (PSG psg : chips) {
            psg.setRate(phasorMode ? CLOCK_SPEED * 2 : CLOCK_SPEED, SoundMixer.RATE);
        }
        buildMixerTable();
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // There is no c8 rom access to emulate
    }
}
