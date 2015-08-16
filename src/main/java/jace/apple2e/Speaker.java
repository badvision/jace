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
package jace.apple2e;

import jace.config.ConfigurableField;
import jace.core.Computer;
import jace.core.Device;
import jace.core.Motherboard;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.SoundMixer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.stage.FileChooser;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Apple // Speaker Emulation Created on May 9, 2007, 9:55 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Speaker extends Device {

    static boolean fileOutputActive = false;
    static OutputStream out;

    public static void toggleFileOutput() {
        if (fileOutputActive) {
            try {
                out.close();
            } catch (IOException ex) {
                Logger.getLogger(Speaker.class.getName()).log(Level.SEVERE, null, ex);
            }
            out = null;
            fileOutputActive = false;
        } else {
            FileChooser fileChooser = new FileChooser();
            File f = fileChooser.showSaveDialog(null);
            if (f == null) {
                return;
            }
//            if (f.exists()) {
//                int i = JOptionPane.showConfirmDialog(null, "Overwrite existing file?");
//                if (i != JOptionPane.OK_OPTION && i != JOptionPane.YES_OPTION) {
//                    return;
//                }
//            }
            try {
                out = new FileOutputStream(f);
                fileOutputActive = true;
            } catch (FileNotFoundException ex) {
                Logger.getLogger(Speaker.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    /**
     * Counter tracks the number of cycles between sampling
     */
    private double counter = 0;
    /**
     * Level is the number of cycles the speaker has been on
     */
    private int level = 0;
    /**
     * Idle cycles counts the number of cycles the speaker has not been changed
     * (used to deactivate sound when not in use)
     */
    private int idleCycles = 0;
    /**
     * Number of samples in buffer
     */
    static int BUFFER_SIZE = (int) (SoundMixer.RATE * 0.4);
    // Number of samples available in output stream before playback happens (avoid extra blocking)
//    static int MIN_PLAYBACK_BUFFER = BUFFER_SIZE / 2;
    static int MIN_PLAYBACK_BUFFER = 64;
    /**
     * Playback volume (should be < 1423)
     */
    @ConfigurableField(name = "Speaker Volume", shortName = "vol", description = "Should be under 1400")
    public static int VOLUME = 600;
    /**
     * Number of idle cycles until speaker playback is deactivated
     */
    @ConfigurableField(name = "Idle cycles before sleep", shortName = "idle")
    public static int MAX_IDLE_CYCLES = 2000000;
    /**
     * Java sound output
     */
    private SourceDataLine sdl;
    /**
     * Manifestation of the apple speaker softswitch
     */
    private boolean speakerBit = false;
    //
    /**
     * Locking semaphore to prevent race conditions when working with buffer or
     * related variables
     */
    private final Object bufferLock = new Object();
    /**
     * Double-buffer used for playing processed sound -- as one is played the
     * other fills up.
     */
    byte[] primaryBuffer;
    byte[] secondaryBuffer;
    int bufferPos = 0;
    Timer playbackTimer;
    private final double TICKS_PER_SAMPLE = ((double) Motherboard.SPEED) / SoundMixer.RATE;
    private final double TICKS_PER_SAMPLE_FLOOR = Math.floor(TICKS_PER_SAMPLE);
    private RAMListener listener = null;

    /**
     * Creates a new instance of Speaker
     *
     * @param computer
     */
    public Speaker(Computer computer) {
        super(computer);
    }

    /**
     * Suspend playback of sound
     *
     * @return
     */
    @Override
    public boolean suspend() {
        boolean result = super.suspend();
        playbackTimer.cancel();
        speakerBit = false;
        sdl = null;
        computer.getMotherboard().cancelSpeedRequest(this);
        computer.mixer.returnLine(this);

        return result;
    }

    /**
     * Start or resume playback of sound
     */
    @Override
    public void resume() {
        if (sdl != null && isRunning()) {
            return;
        }
        try {
            if (sdl == null || !sdl.isOpen()) {
                sdl = computer.mixer.getLine(this);
            }
            sdl.start();
            setRun(true);
            counter = 0;
            idleCycles = 0;
            level = 0;
            bufferPos = 0;
            playbackTimer = new Timer();
            playbackTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    playCurrentBuffer();
                }
            }, 10, 30);
        } catch (LineUnavailableException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "ERROR: Could not output sound", ex);
        }
    }

    public void playCurrentBuffer() {
        byte[] buffer;
        int len;
        synchronized (bufferLock) {
            len = bufferPos;
            buffer = primaryBuffer;
            primaryBuffer = secondaryBuffer;
            bufferPos = 0;
        }
        secondaryBuffer = buffer;
        sdl.write(buffer, 0, len);
    }

    /**
     * Reset idle counter whenever sound playback occurs
     */
    public void resetIdle() {
        idleCycles = 0;
        if (!isRunning()) {
            resume();
        }
    }

    /**
     * Motherboard cycle tick Every 23 ticks a sample will be added to the
     * buffer If the buffer is full, this will block until there is room in the
     * buffer, thus keeping the emulation in sync with the sound
     */
    @Override
    public void tick() {
        if (!isRunning() || sdl == null) {
            return;
        }
        if (idleCycles++ >= MAX_IDLE_CYCLES) {
            suspend();
        }
        if (speakerBit) {
            level++;
        }
        counter += 1.0d;
        if (counter >= TICKS_PER_SAMPLE) {
            int sample = level * VOLUME;
            int bytes = SoundMixer.BITS >> 3;
            int shift = SoundMixer.BITS;

            while (bufferPos >= primaryBuffer.length) {
                Thread.yield();
            }
            synchronized (bufferLock) {
                int index = bufferPos;
                for (int i = 0; i < SoundMixer.BITS; i += 8, index++) {
                    shift -= 8;
                    primaryBuffer[index] = primaryBuffer[index + bytes] = (byte) ((sample >> shift) & 0x0ff);
                }

                bufferPos += bytes * 2;
            }

            // Set level back to 0
            level = 0;
            // Set counter to 0
            counter -= TICKS_PER_SAMPLE_FLOOR;
        }
    }

    private void toggleSpeaker(RAMEvent e) {
        if (e.getType() == RAMEvent.TYPE.WRITE) {
            level += 2;
        } else {
            speakerBit = !speakerBit;
        }
        resetIdle();
    }

    /**
     * Add a memory event listener for C03x for capturing speaker events
     */
    private void configureListener() {
        listener = computer.getMemory().observe(RAMEvent.TYPE.ANY, 0x0c030, 0x0c03f, this::toggleSpeaker);
    }

    private void removeListener() {
        computer.getMemory().removeListener(listener);
    }

    /**
     * Returns "Speaker"
     *
     * @return "Speaker"
     */
    @Override
    protected String getDeviceName() {
        return "Speaker";
    }

    @Override
    public String getShortName() {
        return "spk";
    }

    @Override
    public final void reconfigure() {
        if (primaryBuffer != null && secondaryBuffer != null) {
            return;
        }
        BUFFER_SIZE = 20000 * (SoundMixer.BITS >> 3);
        primaryBuffer = new byte[BUFFER_SIZE];
        secondaryBuffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void attach() {
        configureListener();
        resume();
    }

    @Override
    public void detach() {
        removeListener();
        suspend();
        super.detach();
    }
}
