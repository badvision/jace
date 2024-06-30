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

package jace.core;

import java.util.HashSet;

import jace.Emulator;
import jace.apple2e.SoftSwitches;
import jace.apple2e.Speaker;
import jace.config.ConfigurableField;

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
public class Motherboard extends IndependentTimedDevice {

    @ConfigurableField(name = "Enable Speaker", shortName = "speaker", defaultValue = "true")
    public static boolean enableSpeaker = true;
    public Speaker speaker;

    void vblankEnd() {
        SoftSwitches.VBL.getSwitch().setState(true);
        Emulator.withComputer(c->c.notifyVBLStateChanged(true));
    }

    void vblankStart() {
        SoftSwitches.VBL.getSwitch().setState(false);
        Emulator.withComputer(c->c.notifyVBLStateChanged(false));
    }

    /**
     * Creates a new instance of Motherboard
     * @param computer
     * @param oldMotherboard
     */
    public Motherboard(Motherboard oldMotherboard) {
        super();
        if (oldMotherboard != null) {
            addAllDevices(oldMotherboard.getChildren());
            speaker = oldMotherboard.speaker;
            accelorationRequestors.addAll(oldMotherboard.accelorationRequestors);
            setSpeedInHz(oldMotherboard.getSpeedInHz());
            setMaxSpeed(oldMotherboard.isMaxSpeed());
        }
    }

    @Override
    protected String getDeviceName() {
        return "Motherboard";
    }

    @Override
    public String getShortName() {
        return "mb";
    }

    private CPU _cpu = null;
    public CPU getCpu() {
        if (_cpu == null) {
            _cpu = Emulator.withComputer(Computer::getCpu, null);
        }
        return _cpu;
    }

    @Override
    public void tick() {
    }

    @Override
    public synchronized void reconfigure() {
        _cpu = null;
        accelorationRequestors.clear();
        disableTempMaxSpeed();
        super.reconfigure();

        // Now create devices as needed, e.g. sound

        if (enableSpeaker) {
            try {
                if (speaker == null) {
                    speaker = new Speaker();
                    speaker.attach();
                }
                speaker.reconfigure();
                addChildDevice(speaker);
            } catch (Throwable t) {
                System.out.println("Unable to initalize sound -- deactivating speaker out");
                t.printStackTrace();
            }
        } else {
            System.out.println("Speaker not enabled, leaving it off.");
        }
    }
    HashSet<Object> accelorationRequestors = new HashSet<>();

    public void requestSpeed(Object requester) {
        accelorationRequestors.add(requester);
        enableTempMaxSpeed();
    }

    public void cancelSpeedRequest(Object requester) {
        if (accelorationRequestors.remove(requester) && accelorationRequestors.isEmpty()) {
            disableTempMaxSpeed();
        }
    }
}
