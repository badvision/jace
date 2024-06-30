/*
 * Copyright 2018 org.badvision.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jace.hardware;

import jace.Emulator;
import jace.config.ConfigurableField;
import jace.core.Device;
import jace.core.RAMEvent;
import jace.core.RAMListener;

/**
 * Implements a basic hardware accelerator that is able to adjust the speed of the emulator
 */
public class ZipWarpAccelerator extends Device {
    @ConfigurableField(category = "debug", name = "Debug messages")
    public boolean debugMessagesEnabled = false;

    public static final int ENABLE_ADDR = 0x0c05a;
    public static final int MAX_SPEED = 0x0c05b;
    public static final int REGISTERS = 0x0c05c;
    public static final int SET_SPEED = 0x0c05d;
    public static final int UNLOCK_VAL = 0x05A;
    public static final int LOCK_VAL = 0x0A5;
    public static final double UNLOCK_PENALTY_PER_TICK = 0.19;
    public static final double UNLOCK_MIN = 4.0;

    /**
     * Valid values for C074 are:
     * 0: Enable full speed
     * 1: Set speed to 1mhz (temporarily disable)
     * 3: Disable completely (requres cold-start to re-enable -- this isn't implemented)
     */
    public static final int TRANSWARP = 0x0c074;
    public static final int TRANSWARP_ON = 0; // Any other value written disables acceleration

    boolean zipLocked = true;
    double zipUnlockCount = 0;
    int zipRegisters = 0;
    int speedValue = 0;

    RAMListener zipListener;
    RAMListener transwarpListener;

    public ZipWarpAccelerator() {
        super();
        zipListener = getMemory().observe("Zip chip access", RAMEvent.TYPE.ANY, ENABLE_ADDR, SET_SPEED, this::handleZipChipEvent);
        transwarpListener = getMemory().observe("Transwarp access", RAMEvent.TYPE.ANY, TRANSWARP, this::handleTranswarpEvent);
    }

    private void handleZipChipEvent(RAMEvent e) {
        boolean isWrite = e.getType() == RAMEvent.TYPE.WRITE;
        if (ENABLE_ADDR == e.getAddress() && isWrite) {
            if (e.getNewValue() == UNLOCK_VAL) {
                zipUnlockCount = Math.ceil(zipUnlockCount) + 1.0;
                if (debugMessagesEnabled) {
                    System.out.println("Unlock sequence detected, new lock value at " + zipUnlockCount + " of " + UNLOCK_MIN);
                }
                if (zipUnlockCount >= UNLOCK_MIN) {
                    zipLocked = false;
                    if (debugMessagesEnabled) {
                        System.out.println("Zip unlocked!");
                    }
                }
            } else {
                zipLocked = true;
                if (debugMessagesEnabled) {
                    System.out.println("Zip locked!");
                }
                zipUnlockCount = 0;
                if ((e.getNewValue() & 0x0ff) != LOCK_VAL) {
                    if (debugMessagesEnabled) {
                        System.out.println("Warp disabled.");
                    }
                    turnOffAcceleration();
                }
            }
        } else if (!zipLocked && isWrite) {
            switch (e.getAddress()) {
                case MAX_SPEED -> {
                    setSpeed(SPEED.MAX);
                    if (debugMessagesEnabled) {
                        System.out.println("MAXIMUM WARP!");
                    }
                }
                case SET_SPEED -> {
                    SPEED s = lookupSpeedSetting(e.getNewValue());
                    setSpeed(s);
                    if (debugMessagesEnabled) {
                        System.out.println("Set speed to " + s.ratio);
                    }
                }
                case REGISTERS -> zipRegisters = e.getNewValue();
                default -> {
                }
            }
        } else if (!zipLocked && e.getAddress() == REGISTERS) {
            e.setNewValue(zipRegisters);
        }
    }

    private void handleTranswarpEvent(RAMEvent e) {
        if (e.getType().isRead()) {
            e.setNewValue(speedValue);
        } else {
            if (e.getNewValue() == TRANSWARP_ON) {
                setSpeed(SPEED.MAX);
                if (debugMessagesEnabled) {
                    System.out.println("MAXIMUM WARP!");
                }
            } else {
                turnOffAcceleration();
                if (debugMessagesEnabled) {
                    System.out.println("Warp disabled.");
                }
            }
        }
    }

    @Override
    protected String getDeviceName() {
        return "ZipChip Accelerator";
    }

    public enum SPEED {
        MAX(8.0, 0b000000000, 0b011111100),
        _2_667(2.6667, 0b000000100, 0b011111100),
        _3(3.0, 0b000001000, 0b011111000),
        _3_2(3.2, 0b000010000, 0b011110000),
        _3_333(3.333, 0b000100000, 0b011100000),
        _2(2.0, 0b001000000, 0b011111100),
        _1_333(1.333, 0b001000100, 0b011111100),
        _1_5(1.5, 0b001001000, 0b011111000),
        _1_6(1.6, 0b001010000, 0b011110000),
        _1_667(1.6667, 0b001100000, 0b011100000),
        _1b(1.0, 0b010000000, 0b011111100),
        _0_667(0.6667, 0b010000100, 0b011111100),
        _0_75(0.75, 0b010001000, 0b011111000),
        _0_8(0.8, 0b010010000, 0b011110000),
        _0_833(0.833, 0b010100000, 0b011100000),
        _1_33b(1.333, 0b011000000, 0b011111100),
        _0_889(0.8889, 0b011000100, 0b011111100),
        _1(1.0, 0b011001000, 0b011111000),
        _1_067(1.0667, 0b011010000, 0b011110000),
        _1_111(1.111, 0b011100000, 0b011100000);
        double ratio;
        int val;
        int mask;
        boolean max;

        SPEED(double speed, int val, int mask) {
            this.ratio = speed;
            this.val = val;
            this.mask = mask;
            this.max = speed >= 4.0;
        }
    }

    private SPEED lookupSpeedSetting(int v) {
        for (SPEED s : SPEED.values()) {
            if ((v & s.mask) == s.val) {
                return s;
            }
        }
        return SPEED._1;
    }

    private void setSpeed(SPEED speed) {
        speedValue = speed.val;
        Emulator.withComputer(c -> {
            if (speed.max) {
                c.getMotherboard().setMaxSpeed(true);
            } else {
                c.getMotherboard().setMaxSpeed(false);
                c.getMotherboard().setSpeedInPercentage((int) (speed.ratio * 100));
            }
            c.getMotherboard().reconfigure();            
        });
    }

    private void turnOffAcceleration() {
        // The UI Logic retains the user's desired normal speed, reset to that
        Emulator.withComputer(c -> {
            c.getMotherboard().setMaxSpeed(false);
            c.getMotherboard().setSpeedInPercentage(100);
        });
    }
    
    @Override
    public void tick() {
        if (zipUnlockCount > 0.0) {
            zipUnlockCount -= UNLOCK_PENALTY_PER_TICK;
        }
    }

    @Override
    public void attach() {
        getMemory().addListener(zipListener);
        getMemory().addListener(transwarpListener);
    }

    @Override
    public void detach() {
        super.detach();
        getMemory().removeListener(zipListener);
        getMemory().removeListener(transwarpListener);
    }

    @Override
    public String getShortName() {
        return "zip";
    }

    @Override
    public void reconfigure() {
        zipUnlockCount = 0;
        zipLocked = true;
    }

}
