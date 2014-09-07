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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import static jace.core.Utility.*;
import java.io.FileNotFoundException;

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
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.showSaveDialog(null);
            File f = fileChooser.getSelectedFile();
            if (f == null) {
                return;
            }
            if (f.exists()) {
                int i = JOptionPane.showConfirmDialog(null, "Overwrite existing file?");
                if (i != JOptionPane.OK_OPTION && i != JOptionPane.YES_OPTION) {
                    return;
                }
            }
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
    static int BUFFER_SIZE = (int) (((float) SoundMixer.RATE) * 0.4);
    // Number of samples available in output stream before playback happens (avoid extra blocking)
//    static int MIN_PLAYBACK_BUFFER = BUFFER_SIZE / 2;
    static int MIN_PLAYBACK_BUFFER = 64;
    // Number of samples in buffer to wait until playback (avoid underrun)
    private int MIN_SAMPLE_PLAYBACK = 64;
    /**
     * Playback volume (should be < 1423)
     */
    @ConfigurableField(name = "Speaker Volume", shortName = "vol", description = "Should be under 1400")
    public static int VOLUME = 600;
    /**
     * Number of idle cycles until speaker playback is deactivated
     */
    @ConfigurableField(name = "Idle cycles before sleep", shortName = "idle")
    public static int MAX_IDLE_CYCLES = 100000;
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
    byte[] soundBuffer1;
    byte[] soundBuffer2;
    int currentBuffer = 1;
    int bufferPos = 0;
    private double TICKS_PER_SAMPLE = ((double) Motherboard.SPEED) / ((double) SoundMixer.RATE);
    private double TICKS_PER_SAMPLE_FLOOR = Math.floor(TICKS_PER_SAMPLE);
    Thread playbackThread;
    private final RAMListener listener
            = new RAMListener(RAMEvent.TYPE.ANY, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {

                @Override
                public boolean isRelevant(RAMEvent e) {
                    return true;
                }

                @Override
                protected void doConfig() {
                    setScopeStart(0x0C030);
                    setScopeEnd(0x0C03F);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    if (e.getType() == RAMEvent.TYPE.WRITE) {
                        level += 2;
                    } else {
                        speakerBit = !speakerBit;
                    }
                    resetIdle();
                }
            };

    /**
     * Creates a new instance of Speaker
     */
    public Speaker() {
        configureListener();
        reconfigure();
    }

    /**
     * Suspend playback of sound
     * @return 
     */
    @Override
    public boolean suspend() {
        boolean result = super.suspend();
        speakerBit = false;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread = null;
        }
        return result;
    }

    /**
     * Start or resume playback of sound
     */
    @Override
    public void resume() {
        sdl = null;
        try {
            sdl = Motherboard.mixer.getLine(this);
        } catch (LineUnavailableException ex) {
            System.out.println("ERROR: Could not output sound: " + ex.getMessage());
        }
        if (sdl != null) {
            setRun(true);
            counter = 0;
            idleCycles = 0;
            level = 0;
            bufferPos = 0;
            if (playbackThread == null || !playbackThread.isAlive()) {
                playbackThread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        int len;
                        while (isRunning()) {
//                            Motherboard.requestSpeed(this);                            
                            len = bufferPos;
                            if (len >= MIN_SAMPLE_PLAYBACK) {
                                byte[] buffer;
                                synchronized (bufferLock) {
                                    len = bufferPos;
                                    buffer = (currentBuffer == 1) ? soundBuffer1 : soundBuffer2;
                                    currentBuffer = (currentBuffer == 1) ? 2 : 1;
                                    bufferPos = 0;
                                }
                                sdl.write(buffer, 0, len);
                                if (fileOutputActive && out != null) {
                                    try {
                                        out.write(buffer, 0, len);
                                    } catch (IOException ex) {
                                        Logger.getLogger(Speaker.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            } else {
                                try {
                                    // Wait 12.5 ms, which is 1/8 the total duration of the buffer
                                    Thread.sleep(10);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Speaker.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                        Motherboard.cancelSpeedRequest(this);
                        Motherboard.mixer.returnLine(this);

                    }
                });
                playbackThread.setName("Speaker playback");
                playbackThread.start();
            }
        }
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
        if (!isRunning() || playbackThread == null) {
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

            // Force emulator to wait until sound buffer has been processed
            int wait = 0;
            while (bufferPos >= BUFFER_SIZE) {
                if (wait++ > 1000) {
                    Computer.pause();
                    detach();
                    Computer.resume();
                    Motherboard.enableSpeaker = false;
                    gripe("Sound playback is not working properly.  Check your configuration and sound system to ensure they are set up properly.");
                    return;
                }
                try {
                    // Yield to other threads (e.g. sound) so that the buffer can drain
                    Thread.sleep(5);
                } catch (InterruptedException ex) {

                }
            }

            byte[] buf;
            synchronized (bufferLock) {
                if (currentBuffer == 1) {
                    buf = soundBuffer1;
                } else {
                    buf = soundBuffer2;
                }
                int index = bufferPos;
                for (int i = 0; i < SoundMixer.BITS; i += 8, index++) {
                    shift -= 8;
                    buf[index] = buf[index + bytes] = (byte) ((sample >> shift) & 0x0ff);
                }

                bufferPos += bytes * 2;
            }

            // Set level back to 0
            level = 0;
            // Set counter to 0
            counter -= TICKS_PER_SAMPLE_FLOOR;
        }
    }

    /**
     * Add a memory event listener for C03x for capturing speaker events
     */
    private void configureListener() {
        Computer.getComputer().getMemory().addListener(listener);
    }

    private void removeListener() {
        Computer.getComputer().getMemory().removeListener(listener);
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
        if (soundBuffer1 != null && soundBuffer2 != null) {
            return;
        }
        BUFFER_SIZE = 10000 * (SoundMixer.BITS >> 3);
        MIN_SAMPLE_PLAYBACK = SoundMixer.BITS * 8;
        soundBuffer1 = new byte[BUFFER_SIZE];
        soundBuffer2 = new byte[BUFFER_SIZE];
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
    }
}
