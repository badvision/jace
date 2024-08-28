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
    @ConfigurableField(name = "Clock Rate (hz)",
            category = "Sound",
            defaultValue = "1020484",
            description = "Clock rate of AY oscillators")
    public int CLOCK_SPEED = 1020484;
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
        ticksBetweenPlayback = (double) CLOCK_SPEED / (double) SoundMixer.RATE;
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
        SoundBuffer b = buffer;
        if (b == null) {
            return false;
        }
        b.playSample((short) left.get());
        b.playSample((short) right.get());
        return (left.get() != 0 || right.get() != 0);
    }

    public void buildMixerTable() {
        VolTable = new int[16];
        int numChips = phasorMode ? 4 : 2;

        /* calculate the volume->voltage conversion table */
        /* The AY-3-8910 has 16 levels, in a logarithmic scale (3dB per step) */
        /* The YM2149 still has 16 levels for the tone generators, but 32 for */
        /* the envelope generator (1.5dB per step). */
        double out = (MAX_AMPLITUDE * volume) / 100.0;
        // Reduce max amplitude to reflect post-mixer values so we don't have to scale volume when mixing channels
        out = out * 2.0 / 3.0 / numChips;
        // double delta = 1.15;
        for (int i = 15; i > 0; i--) {
            VolTable[i] = (int) (out / Math.pow(Math.sqrt(2),(15-i)));
//            out /= 1.188502227;	/* = 10 ^ (1.5/20) = 1.5dB */
//            out /= 1.15;	/* = 10 ^ (3/20) = 3dB */
//            delta += 0.0225;
//            out /= delta;   // As per applewin's source, the levels don't scale as documented.
        }
        
        VolTable[0] = 0;
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
        for (R6522 c : controllers) {
            c.suspend();
        }
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
