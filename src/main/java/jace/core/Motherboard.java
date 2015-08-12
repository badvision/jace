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

import jace.apple2e.SoftSwitches;
import jace.apple2e.Speaker;
import jace.config.ConfigurableField;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Motherboard is the heart of the computer. It can have a list of cards
 * inserted (the behavior and number of cards is determined by the Memory class)
 * as well as a speaker and any other miscellaneous devices (e.g. joysticks).
 * This class provides the real main loop of the emulator, and is responsible
 * for all timing as well as the pause/resume features used to prevent resource
 * collisions between threads. Created on May 1, 2007, 11:22 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Motherboard extends TimedDevice {

    final public Set<Device> miscDevices = new LinkedHashSet<>();
    @ConfigurableField(name = "Enable Speaker", shortName = "speaker", defaultValue = "true")
    public static boolean enableSpeaker = true;
    public Speaker speaker;

    void vblankEnd() {
        SoftSwitches.VBL.getSwitch().setState(true);
        computer.notifyVBLStateChanged(true);
    }

    void vblankStart() {
        SoftSwitches.VBL.getSwitch().setState(false);
        computer.notifyVBLStateChanged(false);
    }

    /**
     * Creates a new instance of Motherboard
     * @param computer
     */
    public Motherboard(Computer computer) {
        super(computer);
    }

    @Override
    protected String getDeviceName() {
        return "Motherboard";
    }

    @Override
    public String getShortName() {
        return "mb";
    }
    @ConfigurableField(category = "advanced", name = "CPU per clock", defaultValue = "1", description = "Number of CPU cycles per clock cycle (normal = 1)")
    public static int cpuPerClock = 1;
    public int clockCounter = 1;

    @Override
    public void tick() {
        Optional<Card>[] cards = computer.getMemory().getAllCards();
        try {
            clockCounter--;
            computer.getCpu().doTick();
            if (clockCounter > 0) {
                return;
            }
            clockCounter = cpuPerClock;
            computer.getVideo().doTick();
            for (Optional<Card> card : cards) {
                card.ifPresent(c -> c.doTick());
            }
            miscDevices.stream().forEach((m) -> {
                m.doTick();
            });
        } catch (Throwable t) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, t);
        }
    }
    // From the holy word of Sather 3:5 (Table 3.1) :-)
    // This average speed averages in the "long" cycles
    public static long SPEED = 1020484L; // (NTSC)
    //public static long SPEED = 1015625L; // (PAL)

    @Override
    public long defaultCyclesPerSecond() {
        return SPEED;
    }

    @Override
    public synchronized void reconfigure() {
        boolean startAgain = pause();
        accelorationRequestors.clear();
        super.reconfigure();
        // Now create devices as needed, e.g. sound

        if (enableSpeaker) {
            try {
                if (speaker == null) {
                    speaker = new Speaker(computer);
                    if (computer.mixer.lineAvailable) {
                        speaker.attach();
                        miscDevices.add(speaker);
                    } else {
                        System.out.print("No lines available!  Speaker not running.");
                    }
                }
                speaker.reconfigure();
            } catch (Throwable t) {
                System.out.println("Unable to initalize sound -- deactivating speaker out");
                speaker.detach();
                miscDevices.remove(speaker);
            }
        } else {
            System.out.println("Speaker not enabled, leaving it off.");
            if (speaker != null) {
                speaker.detach();
                miscDevices.remove(speaker);
            }
        }
        if (startAgain && computer.getMemory() != null) {
            resume();
        }
    }
    static HashSet<Object> accelorationRequestors = new HashSet<>();

    public void requestSpeed(Object requester) {
        accelorationRequestors.add(requester);
        enableTempMaxSpeed();
    }

    public void cancelSpeedRequest(Object requester) {
        accelorationRequestors.remove(requester);
        if (accelorationRequestors.isEmpty()) {
            disableTempMaxSpeed();
        }
    }

    @Override
    public void attach() {
    }
    final Set<Card> resume = new HashSet<>();

    @Override
    public boolean suspend() {
        synchronized (resume) {
            resume.clear();
            for (Optional<Card> c : computer.getMemory().getAllCards()) {
                if (!c.isPresent()) {
                    continue;
                }
                if (!c.get().suspendWithCPU() || !c.get().isRunning()) {
                    continue;
                }
                if (c.get().suspend()) {
                    resume.add(c.get());
                }
            }
        }
        return super.suspend();
    }

    @Override
    public void resume() {
        super.resume();
        synchronized (resume) {
            resume.stream().forEach((c) -> {
                c.resume();
            });
        }
    }

    @Override
    public void detach() {
        System.out.println("Detaching motherboard");
        miscDevices.stream().forEach((d) -> {
            d.suspend();
            d.detach();
        });
        miscDevices.clear();
//        halt();
        super.detach();
    }
}
