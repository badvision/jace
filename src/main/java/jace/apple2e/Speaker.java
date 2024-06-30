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

package jace.apple2e;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.JaceApplication;
import jace.config.ConfigurableField;
import jace.config.InvokableAction;
import jace.core.Device;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.SoundMixer;
import jace.core.SoundMixer.SoundBuffer;
import jace.core.SoundMixer.SoundError;
import jace.core.TimedDevice;
import jace.core.Utility;
import javafx.stage.FileChooser;

/**
 * Apple // Speaker Emulation Created on May 9, 2007, 9:55 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Speaker extends Device {

    static boolean fileOutputActive = false;
    static OutputStream out;
    
    @ConfigurableField(category = "sound", name = "1mhz timing", description = "Force speaker output to 1mhz?")
    public static boolean force1mhz = true;

    @ConfigurableField(category = "sound", name = "Show sound", description = "Use black color value to show sound output")
    public static boolean showSound = false;

    @InvokableAction(category = "sound", name = "Record sound", description="Toggles recording (saving) sound output to a file", defaultKeyMapping = "ctrl+shift+w")
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
            File f = fileChooser.showSaveDialog(JaceApplication.getApplication().primaryStage);
            if (f == null) {
                return;
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
     * Playback volume (should be < 1423)
     */
    @ConfigurableField(name = "Speaker Volume", shortName = "vol", description = "Should be under 1400")
    public static int VOLUME = 400;
    private int currentVolume = 0;
    private int fadeOffAmount = 1;
    /**
     * Manifestation of the apple speaker softswitch
     */
    private boolean speakerBit = false;
    private static double TICKS_PER_SAMPLE = ((double) TimedDevice.NTSC_1MHZ) / SoundMixer.RATE;
    private RAMListener listener = null;
    private SoundBuffer buffer = null;

    /**
     * Number of idle cycles until speaker playback is deactivated
     */
    @ConfigurableField(name = "Idle cycles before sleep", shortName = "idle")
    // public static int MAX_IDLE_CYCLES = (int) (SoundMixer.BUFFER_SIZE * TICKS_PER_SAMPLE * 2);
    public static int MAX_IDLE_CYCLES = (int) TimedDevice.NTSC_1MHZ / 4;

    /**
     * Suspend playback of sound
     *
     * @return
     */
    @Override
    public boolean suspend() {
        boolean result = super.suspend();
        speakerBit = false;
        if (buffer != null) {
            try {
                buffer.shutdown();
            } catch (InterruptedException | ExecutionException | SoundError e) {
                // Ignore
            } finally {
                buffer = null;
            }
        }
        Emulator.withComputer(c->c.getMotherboard().cancelSpeedRequest(this));

        return result;
    }

    /**
     * Start or resume playback of sound
     */
    @Override
    public void resume() {
        if (Utility.isHeadlessMode()) {
            return;
        }
        if (buffer == null || !buffer.isAlive()) {
            try {
                buffer = SoundMixer.createBuffer(false);
            } catch (InterruptedException | ExecutionException | SoundError e) {
                e.printStackTrace();
                detach();
                return;
            }
        }
        if (buffer != null) {
            counter = 0;
            idleCycles = 0;
            level = 0;
        } else {
            Logger.getLogger(getClass().getName()).severe("Unable to get audio buffer for speaker!");
            detach();
            return;
        }

        if (force1mhz) {
            TICKS_PER_SAMPLE = ((double) TimedDevice.NTSC_1MHZ) / SoundMixer.RATE;
        } else {
            TICKS_PER_SAMPLE = Emulator.withComputer(c-> ((double) c.getMotherboard().getSpeedInHz()) / SoundMixer.RATE, 0.0);
        }
        super.resume();
    }

    /**
     * Reset idle counter whenever sound playback occurs
     */
    public void resetIdle() {
        currentVolume = VOLUME;
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
        if (speakerBit) {
            level++;
            if (showSound) {
                VideoNTSC.CHANGE_BLACK_COLOR(40, 20, 20);
            }
        } else if (showSound) {
            VideoNTSC.CHANGE_BLACK_COLOR(20,20,40);
        }
        if (idleCycles++ >= MAX_IDLE_CYCLES && (currentVolume <= 0 || !speakerBit)) {
            suspend();
            if (showSound) {
                VideoNTSC.CHANGE_BLACK_COLOR(0,0,0);
            }
        }
        counter += 1.0d;
        if (counter >= TICKS_PER_SAMPLE) {
            if (idleCycles >= MAX_IDLE_CYCLES) {
                currentVolume -= fadeOffAmount;
            }
            playSample(level * currentVolume);
            // Emulator.withComputer(c->c.getMotherboard().requestSpeed(this));

            // Set level back to 0
            level = 0;
            // Set counter to 0
            counter -= TICKS_PER_SAMPLE;
        }
    }

    private void toggleSpeaker(RAMEvent e) {
        // if (e.getType() == RAMEvent.TYPE.WRITE) {
        //     level += 2;
        // }
        speakerBit = !speakerBit;
        resetIdle();
    }
    
    private void playSample(int sample) {
        try {
            if (buffer == null || !buffer.isAlive()) {
                // Logger.getLogger(getClass().getName()).severe("Audio buffer not initalized properly!");
                buffer = SoundMixer.createBuffer(false);
                if (buffer == null) {
                    System.err.println("Unable to create emergency audio buffer, detaching speaker");
                    detach();
                    return;
                }
            }
            buffer.playSample((short) sample);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } catch (SoundError e) {
            System.err.println("Sound error, detaching speaker: " + e.getMessage());
            e.printStackTrace();
            detach();
            buffer = null;
        }

        if (fileOutputActive) {
            byte[] bytes = new byte[2];
            bytes[0] = (byte) (sample & 0x0ff);
            bytes[1] = (byte) ((sample >> 8) & 0x0ff);

            try {
                out.write(bytes, 0, 2);
            } catch (IOException ex) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error recording sound", ex);
                toggleFileOutput();
            }
        }
        
    }

    /**
     * Add a memory event listener for C03x for capturing speaker events
     */
    private void configureListener() {
        listener = Emulator.withMemory(m->m.observe("Speaker", RAMEvent.TYPE.ANY, 0x0c030, 0x0c03f, this::toggleSpeaker), null);
    }

    private void removeListener() {
        Emulator.withMemory(m->m.removeListener(listener));
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
    }

    @Override
    public void attach() {
        configureListener();
    }

    @Override
    public void detach() {
        removeListener();
        super.detach();
    }
}
