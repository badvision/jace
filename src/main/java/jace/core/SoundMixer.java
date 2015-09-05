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
package jace.core;

import jace.config.ConfigurableField;
import jace.config.DynamicSelection;
import jace.config.Reconfigurable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Mixer.Info;
import javax.sound.sampled.SourceDataLine;

/**
 * Manages sound resources used by various audio devices (such as speaker and
 * mockingboard cards.) The plumbing is managed in this class so that the
 * consumers do not have to do a lot of work to manage mixer lines or deal with
 * how to reuse active lines if needed. It is possible that this class might be
 * used to manage volume in the future, but that remains to be seen.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class SoundMixer extends Device {

    private final Set<SourceDataLine> availableLines = Collections.synchronizedSet(new HashSet<SourceDataLine>());
    private final Map<Object, SourceDataLine> activeLines = Collections.synchronizedMap(new HashMap<Object, SourceDataLine>());
    /**
     * Bits per sample
     */
    @ConfigurableField(name = "Bits per sample", shortName = "bits")
    public static int BITS = 16;
    /**
     * Sample playback rate
     */
    @ConfigurableField(name = "Playback Rate", shortName = "freq")
    public static int RATE = 48000;
    /**
     * Sound format used for playback
     */
    private AudioFormat af;
    /**
     * Is sound line available for playback at all?
     */
    public boolean lineAvailable;
    @ConfigurableField(name = "Audio device", description = "Audio output device")
    public static DynamicSelection<String> preferredMixer = new DynamicSelection<String>(null) {
        @Override
        public boolean allowNull() {
            return false;
        }

        @Override
        public LinkedHashMap<? extends String, String> getSelections() {
            Info[] mixerInfo = AudioSystem.getMixerInfo();
            LinkedHashMap<String, String> out = new LinkedHashMap<>();
            for (Info i : mixerInfo) {
                out.put(i.getName(), i.getName());
            }
            return out;
        }
    };
    private Mixer theMixer;

    public SoundMixer(Computer computer) {
        super(computer);
    }

    @Override
    public String getDeviceName() {
        return "Sound Output";
    }

    @Override
    public String getShortName() {
        return "mixer";
    }

    @Override
    public synchronized void reconfigure() {
        if (isConfigDifferent()) {
            detach();
            try {
                initMixer();
                if (lineAvailable) {
                    initAudio();
                } else {
                    System.out.println("Sound not stared: Line not available");
                }
            } catch (LineUnavailableException ex) {
                System.out.println("Unable to start sound");
                Logger.getLogger(SoundMixer.class.getName()).log(Level.SEVERE, null, ex);
            }
            attach();
        }
    }

    private AudioFormat getAudioFormat() {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, RATE, BITS, 2, BITS / 4, RATE, true);
    }

    /**
     * Obtain sound playback line if available
     *
     * @throws javax.sound.sampled.LineUnavailableException If there is no line
     * available
     */
    private void initAudio() throws LineUnavailableException {
        af = getAudioFormat();
        DataLine.Info dli = new DataLine.Info(SourceDataLine.class, af);
        lineAvailable = AudioSystem.isLineSupported(dli);
    }

    public synchronized SourceDataLine getLine(Object requester) throws LineUnavailableException {
        if (activeLines.containsKey(requester)) {
            return activeLines.get(requester);
        }
        SourceDataLine sdl;
        if (availableLines.isEmpty()) {
            sdl = getNewLine();
        } else {
            sdl = availableLines.iterator().next();
            availableLines.remove(sdl);
        }
        activeLines.put(requester, sdl);
        sdl.start();
        return sdl;
    }

    public void returnLine(Object requester) {
        if (activeLines.containsKey(requester)) {
            SourceDataLine sdl = activeLines.remove(requester);
// Calling drain on pulse driver can cause it to freeze up (?)
//            sdl.drain();
            if (sdl.isRunning()) {
                sdl.flush();
                sdl.stop();
            }
            availableLines.add(sdl);
        }
    }

    private SourceDataLine getNewLine() throws LineUnavailableException {
        SourceDataLine l = null;
//        Line.Info[] info = theMixer.getSourceLineInfo();
        DataLine.Info dli = new DataLine.Info(SourceDataLine.class, af);
        System.out.println("Maximum output lines: " + theMixer.getMaxLines(dli));
        System.out.println("Allocated output lines: " + theMixer.getSourceLines().length);
        System.out.println("Getting source line from " + theMixer.getMixerInfo().toString() + ": " + af.toString());
        try {
            l = (SourceDataLine) theMixer.getLine(dli);
        } catch (IllegalArgumentException e) {
            lineAvailable = false;
            throw new LineUnavailableException(e.getMessage());
        } catch (LineUnavailableException e) {
            lineAvailable = false;
            throw e;
        }
        if (!(l instanceof SourceDataLine)) {
            lineAvailable = false;
            throw new LineUnavailableException("Line is not an output line!");
        }
        final SourceDataLine sdl = l;
        sdl.open();
        return sdl;
    }

    public byte randomByte() {
        return (byte) (Math.random() * 256);
    }

    @Override
    public void tick() {
    }

    @Override
    public void attach() {
//        if (Motherboard.enableSpeaker)
//            Motherboard.speaker.attach();
    }

    @Override
    public void detach() {
        availableLines.stream().forEach((line) -> {
            line.close();
        });
        Set requesters = new HashSet(activeLines.keySet());
        requesters.stream().map((o) -> {
            if (o instanceof Device) {
                ((Device) o).detach();
            }
            return o;
        }).filter((o) -> (o instanceof Card)).forEach((o) -> {
            ((Reconfigurable) o).reconfigure();
        });
        if (theMixer != null) {
            for (Line l : theMixer.getSourceLines()) {
//                if (l.isOpen()) {
//                    l.close();
//                }
            }
        }
        availableLines.clear();
        activeLines.clear();
        super.detach();
    }

    private void initMixer() {
        Info selected;
        Info[] mixerInfo = AudioSystem.getMixerInfo();

        if (mixerInfo == null || mixerInfo.length == 0) {
            theMixer = null;
            lineAvailable = false;
            System.out.println("No sound mixer is available!");
            return;
        }

        String mixer = preferredMixer.getValue();
        selected = mixerInfo[0];
        for (Info i : mixerInfo) {
            if (i.getName().equalsIgnoreCase(mixer)) {
                selected = i;
                break;
            }
        }
        theMixer = AudioSystem.getMixer(selected);
//        for (Line l : theMixer.getSourceLines()) {
//            l.close();
//        }
        lineAvailable = true;
    }

    String oldPreferredMixer = null;

    private boolean isConfigDifferent() {
        boolean changed = false;
        AudioFormat newAf = getAudioFormat();
        changed |= (af == null || !newAf.matches(af));
        if (oldPreferredMixer == null) {
            changed |= preferredMixer.getValue() != null;
        } else {
            changed |= !oldPreferredMixer.matches(preferredMixer.getValue());
        }
        oldPreferredMixer = preferredMixer.getValue();
        return changed;
    }
}
