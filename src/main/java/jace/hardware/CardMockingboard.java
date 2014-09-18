/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package jace.hardware;

import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.Computer;
import jace.core.Motherboard;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.RAMListener;
import jace.core.SoundMixer;
import static jace.core.Utility.*;
import jace.hardware.mockingboard.PSG;
import jace.hardware.mockingboard.R6522;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Mockingboard-C implementation (with partial Phasor support). This uses two
 * 6522 chips to communicate to two respective AY PSG sound chips. This class
 * manages the I/O access as well as the sound playback thread.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Name("Mockingboard")
public class CardMockingboard extends Card implements Runnable {
    // If true, emulation will cover 4 AY chips.  Otherwise, only 2 AY chips

    static final int[] AY_ADDRESSES = new int[]{0, 0x080, 0x010, 0x090};
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
    public int SAMPLE_RATE = 48000;
    @ConfigurableField(name = "Buffer size",
    category = "Sound",
    description = "Number of samples to generate on each pass")
    public int BUFFER_LENGTH = 64;
    // The array of configured AY chips
    public PSG[] chips;
    // The 6522 controllr chips (always 2)
    public R6522[] controllers;
    private int ticksBeteenPlayback = 5000;
    Lock timerSync = new ReentrantLock();
    Condition cpuCountReached = timerSync.newCondition();
    Condition playbackFinished = timerSync.newCondition();
    @ConfigurableField(name = "Idle sample threshold", description = "Number of samples to wait before suspending sound")
    private int MAX_IDLE_SAMPLES = SAMPLE_RATE;

    @Override
    public String getDeviceName() {
        return "Mockingboard";
    }

    public CardMockingboard(Computer computer) {
        super(computer);
        controllers = new R6522[2];
        for (int i = 0; i < 2; i++) {
            //don't ask...
            final int j = i;
            controllers[i] = new R6522(computer) {
                @Override
                public void sendOutputA(int value) {
                    if (activeChip != null) {
                        activeChip.setBus(value);
                    } else {
                        System.out.println("No active AY chip!");
                    }
                }

                @Override
                public void sendOutputB(int value) {
                    if (activeChip != null) {
                        activeChip.setControl(value & 0x07);
                    } else {
                        System.out.println("No active AY chip!");
                    }
                }

                @Override
                public int receiveOutputA() {
                    return activeChip == null ? 0 : activeChip.bus;
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
        }
    }

    @Override
    public void reset() {
        // Reset PSG registers
        suspend();
        if (chips != null) {
            for (PSG psg : chips) {
                psg.reset();
            }
        }
    }
    RAMListener mainListener = null;
    PSG activeChip = null;

    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
//        System.out.println(e.getType().toString() + " event to mockingboard register "+Integer.toHexString(register)+", value "+e.getNewValue());
        activeChip = null;
        resume();
        int chip = 0;
        for (PSG psg : chips) {
            if (psg.getBaseReg() == (register & 0x0f0)) {
                activeChip = psg;
                break;
            }
            chip++;
        }
        if (activeChip == null) {
            System.err.println("Could not determine which PSG to communicate to");
            e.setNewValue(computer.getVideo().getFloatingBus());
            return;
        }
        R6522 controller = controllers[chip & 1];
        if (e.getType().isRead()) {
            int val = controller.readRegister(register & 0x0f);
//            System.out.println("Register returns "+Integer.toHexString(val));
            e.setNewValue(val);
        } else {
            controller.writeRegister(register & 0x0f, e.getNewValue());
        }
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        // Oddly, all IO is done at the firmware address bank.  It's a strange card.
        e.setNewValue(computer.getVideo().getFloatingBus());
    }
    long ticksSinceLastPlayback = 0;

    @Override
    public void tick() {
        for (R6522 c : controllers) {
            if (c == null || !c.isRunning()) {
                continue;
            }
            c.tick();
        }

        if (isRunning() && !pause) {
            timerSync.lock();
            try {
                ticksSinceLastPlayback++;
                if (ticksSinceLastPlayback >= ticksBeteenPlayback) {
                    cpuCountReached.signalAll();
                    while (isRunning() && ticksSinceLastPlayback >= ticksBeteenPlayback) {
                        if (!playbackFinished.await(1, TimeUnit.SECONDS)) {
                            gripe("The mockingboard playback thread has stalled.  Disabling mockingboard.");
                            suspend();
                        }
                    }
                }
            } catch (InterruptedException ex) {
                suspend();
                // Do nothing, probably suspending CPU
            } finally {
                timerSync.unlock();
            }
        }
    }

    @Override
    public void reconfigure() {
        boolean restart = suspend();
        initPSG();
        for (PSG chip : chips) {
            chip.setRate(CLOCK_SPEED, SAMPLE_RATE);
            chip.reset();
        }
        super.reconfigure();
        if (restart) {
            resume();
        }
    }
///////////////////////////////////////////////////////////
    public static int[] VolTable;
    int[][] buffers;
    int bufferLength = -1;

    public void playSound(int[] left, int[] right) {
        chips[0].update(left, true, left, false, left, false, BUFFER_LENGTH);
        chips[1].update(right, true, right, false, right, false, BUFFER_LENGTH);
        if (phasorMode) {
            chips[2].update(left, false, left, false, left, false, BUFFER_LENGTH);
            chips[3].update(right, false, right, false, right, false, BUFFER_LENGTH);
        }
    }

    public void buildMixerTable() {
        VolTable = new int[16];
        int numChips = phasorMode ? 4 : 2;

        /* calculate the volume->voltage conversion table */
        /* The AY-3-8910 has 16 levels, in a logarithmic scale (3dB per step) */
        /* The YM2149 still has 16 levels for the tone generators, but 32 for */
        /* the envelope generator (1.5dB per step). */
        double out = ((double) MAX_AMPLITUDE * (double) volume) / 100.0;
        // Reduce max amplitude to reflect post-mixer values so we don't have to scale volume when mixing channels
        out = out * 2.0 / 3.0 / numChips;
        double delta = 1.15;
        for (int i = 15; i > 0; i--) {
            VolTable[i] = (int) Math.round(out);	/* round to nearest */	// [TC: unsigned int cast]
//            out /= 1.188502227;	/* = 10 ^ (1.5/20) = 1.5dB */
//            out /= 1.15;	/* = 10 ^ (3/20) = 3dB */
            delta += 0.0225;
            out /= delta;   // As per applewin's source, the levels don't scale as documented.
        }
        VolTable[0] = 0;
    }
    Thread playbackThread = null;
    boolean pause = false;

    @Override
    public void resume() {
        pause = false;
        if (!isRunning()) {
            if (chips == null) {
                initPSG();
            }
            for (R6522 controller : controllers) {
                controller.attach();
                controller.resume();
            }
        }
        super.resume();
        if (playbackThread == null || !playbackThread.isAlive()) {
            playbackThread = new Thread(this, "Mockingboard sound playback");
            playbackThread.start();
        }
    }

    @Override
    public boolean suspend() {
        super.suspend();
        for (R6522 controller : controllers) {
            controller.suspend();
            controller.detach();
        }
        if (playbackThread == null || !playbackThread.isAlive()) {
            return false;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            try {
                // Wait for thread to die
                playbackThread.join();
            } catch (InterruptedException ex) {
            }
        }
        playbackThread = null;
        return true;
    }

    @Override
    /**
     * This is the audio playback thread
     */
    public void run() {
        try {
            SourceDataLine out = computer.getMotherboard().mixer.getLine(this);
            int[] leftBuffer = new int[BUFFER_LENGTH];
            int[] rightBuffer = new int[BUFFER_LENGTH];
            int frameSize = out.getFormat().getFrameSize();
            byte[] buffer = new byte[BUFFER_LENGTH * frameSize];
            System.out.println("Mockingboard playback started");
            int bytesPerSample = frameSize / 2;
            buildMixerTable();
            ticksBeteenPlayback = (int) ((Motherboard.SPEED * BUFFER_LENGTH) / SAMPLE_RATE);
            ticksSinceLastPlayback = 0;
            int zeroSamples = 0;
            while (isRunning()) {
                computer.getMotherboard().requestSpeed(this);
                playSound(leftBuffer, rightBuffer);
                int p = 0;
                for (int idx = 0; idx < BUFFER_LENGTH; idx++) {
                    int sampleL = leftBuffer[idx];
                    int sampleR = rightBuffer[idx];
                    // Convert left + right samples into buffer format
                    if (sampleL == 0 && sampleR == 0) {
                        zeroSamples++;
                    } else {
                        zeroSamples = 0;
                    }
                    for (int shift = SoundMixer.BITS - 8, index = 0; shift >= 0; shift -= 8, index++) {
                        buffer[p + index] = (byte) (sampleR >> shift);
                        buffer[p + index + bytesPerSample] = (byte) (sampleL >> shift);
                    }
                    p += frameSize;
                }
                try {
                    timerSync.lock();
                    ticksSinceLastPlayback -= ticksBeteenPlayback;
                } finally {
                    timerSync.unlock();
                }
                out.write(buffer, 0, buffer.length);
                if (zeroSamples >= MAX_IDLE_SAMPLES) {
                    zeroSamples = 0;
                    pause = true;
                    computer.getMotherboard().cancelSpeedRequest(this);
                    while (pause && isRunning()) {
                        try {
                            Thread.sleep(50);
                            timerSync.lock();
                            playbackFinished.signalAll();
                        } catch (InterruptedException ex) {
                            return;
                        } catch (IllegalMonitorStateException ex) {
                            // Do nothing
                        } finally {
                            try {
                                timerSync.unlock();
                            } catch (IllegalMonitorStateException ex) {
                                // Do nothing -- this is probably caused by a suspension event
                            }
                        }
                    }
                }
                try {
                    timerSync.lock();
                    playbackFinished.signalAll();
                    while (isRunning() && ticksSinceLastPlayback < ticksBeteenPlayback) {
                        cpuCountReached.await();
                    }
                } catch (InterruptedException ex) {
                    // Do nothing, probably killing playback thread on purpose
                } finally {
                    timerSync.unlock();
                }
            }
        } catch (LineUnavailableException ex) {
            Logger.getLogger(CardMockingboard.class
                    .getName()).log(Level.SEVERE, null, ex);
        } finally {
            computer.getMotherboard().cancelSpeedRequest(this);
            System.out.println("Mockingboard playback stopped");
            computer.getMotherboard().mixer.returnLine(this);
        }
    }

    private void initPSG() {
        int max = phasorMode ? 4 : 2;
        chips = new PSG[max];
        for (int i = 0; i < max; i++) {
            chips[i] = new PSG(AY_ADDRESSES[i], CLOCK_SPEED, SAMPLE_RATE, "AY" + i);
        }
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // There is no c8 rom access to emulate
    }

    // This fixes freezes when resizing the window, etc.
    @Override
    public boolean suspendWithCPU() {
        return true;
    }
}