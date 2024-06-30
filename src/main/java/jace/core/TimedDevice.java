/*
 * Copyright (C) 2024 Brendan Robert brendan.robert@gmail.com.
 * *
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
package jace.core;

import jace.config.ConfigurableField;

/**
 * A timed device is a device which executes so many ticks in a given time interval. This is the core of the emulator
 * timing mechanics.
 * 
 * This basic implementation does not run freely and instead will skip cycles if it is running too fast
 * This allows a parent timer to run at a faster rate without causing this device to do the same.
 * Useful for devices which generate sound or video at a specific rate.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class TimedDevice extends Device {
    // From the holy word of Sather 3:5 (Table 3.1) :-)
    // This average speed averages in the "long" cycles
    public static final long NTSC_1MHZ = 1020484L;
    public static final long PAL_1MHZ = 1015625L;
    public static final long SYNC_FREQ_HZ = 60;
    public static final double NANOS_PER_SECOND = 1000000000.0;
    public static final long NANOS_PER_MILLISECOND = 1000000L;
    public static final long SYNC_SLOP = NANOS_PER_MILLISECOND * 10L; // 10ms slop for synchronization
    public static int TEMP_SPEED_MAX_DURATION = 1000000;
    @ConfigurableField(name = "Speed", description = "(Percentage)")
    public int speedRatio = 100;
    @ConfigurableField(name = "Max speed")
    public boolean forceMaxspeed = false;
    public boolean maxspeed = false;
    private long cyclesPerSecond = defaultCyclesPerSecond();
    private int cycleTimer = 0;
    private int tempSpeedDuration = 0;
    private long nanosPerInterval; // How long to wait between pauses
    private long cyclesPerInterval; // How many cycles to wait until a pause interval
    private long nextSync = System.nanoTime(); // When is the next sync interval supposed to finish?
    protected Runnable unthrottledTick = () -> super.doTick();
    Long waitUntil = null;
    protected Runnable throttledTick = () -> {
        if (waitUntil == null || System.nanoTime() >= waitUntil) {
            super.doTick();
            waitUntil = calculateResyncDelay();            
        }
    };
    protected final Runnable tickHandler;

    /**
     * Creates a new instance of TimedDevice, setting default speed
     * Protected as overriding the tick handler should only done
     * for the independent timed device
     */
    protected TimedDevice(boolean throttleUsingTicks) {
        super();
        setSpeedInHz(defaultCyclesPerSecond());
        tickHandler = throttleUsingTicks ? throttledTick : unthrottledTick;
        resetSyncTimer();
    }

    public TimedDevice() {
        this(true);
    }
    
    @Override
    public final void doTick() {
        tickHandler.run();
    }

    public final void resetSyncTimer() {
        nextSync = System.nanoTime() + nanosPerInterval;
        cycleTimer = 0;
    }
    
    @Override
    public boolean suspend() {
        disableTempMaxSpeed();
        return super.suspend();
    }


    @Override
    public void setPaused(boolean paused) {
        if (!isPaused() && paused) {
            pauseStart();
        }
        super.setPaused(paused);
        if (!paused) {
            resetSyncTimer();
        }
    }
    
    protected void pauseStart() {
        // Override if you need to add a pause behavior
    }

    @Override
    public void resume() {
        super.resume();
        setPaused(false);
        resetSyncTimer();
    }

    public final int getSpeedRatio() {
        return speedRatio;
    }

    public final void setMaxSpeed(boolean enabled) {
        maxspeed = enabled;
        if (!enabled) {
            resetSyncTimer();
        }
    }

    public final boolean isMaxSpeedEnabled() {
        return maxspeed;
    }

    public final boolean isMaxSpeed() {
        return forceMaxspeed || maxspeed;
    }

    public final long getSpeedInHz() {
        return cyclesPerSecond;
    }

    public final void setSpeedInHz(long newSpeed) {
        // System.out.println("Raw set speed for " + getName() + " to " + cyclesPerSecond + "hz");
        // Thread.dumpStack();
        cyclesPerSecond = newSpeed;
        speedRatio = (int) Math.round(cyclesPerSecond * 100.0 / defaultCyclesPerSecond());
        cyclesPerInterval = cyclesPerSecond / SYNC_FREQ_HZ;
        nanosPerInterval = (long) (cyclesPerInterval * NANOS_PER_SECOND / cyclesPerSecond);
            //    System.out.println("Will pause " + nanosPerInterval + " nanos every " + cyclesPerInterval + " cycles");
        resetSyncTimer();
    }

    public final void setSpeedInPercentage(int ratio) {
        cyclesPerSecond = defaultCyclesPerSecond() * ratio / 100;
        if (cyclesPerSecond == 0) {
            cyclesPerSecond = defaultCyclesPerSecond();
        }
        setSpeedInHz(cyclesPerSecond);
    }

    public void enableTempMaxSpeed() {
        tempSpeedDuration = TEMP_SPEED_MAX_DURATION;
    }

    public void disableTempMaxSpeed() {
        tempSpeedDuration = 0;
        resetSyncTimer();
    }

    @Override
    public void reconfigure() {
        resetSyncTimer();
    }

    public long defaultCyclesPerSecond() {
        return NTSC_1MHZ;
    }

    private boolean useParentTiming() {
        if (getParent() != null && getParent() instanceof TimedDevice) {
            TimedDevice pd = (TimedDevice) getParent();
            if (pd.useParentTiming() || (!pd.isMaxSpeed() && pd.getSpeedInHz() <= getSpeedInHz())) {
                return true;
            }
        }
        return false;
    }

    protected Long calculateResyncDelay() {        
        if (++cycleTimer < cyclesPerInterval) {
            return null;
        }
        cycleTimer = 0;
        long retVal = nextSync;
        nextSync = Math.max(nextSync, System.nanoTime()) + nanosPerInterval; 
        if (isMaxSpeed() || useParentTiming()) {
            if (tempSpeedDuration > 0) {
                tempSpeedDuration -= cyclesPerInterval;
                if (tempSpeedDuration <= 0) {
                    disableTempMaxSpeed();
                }
            }
            return null;
        }
        return retVal;
    }
}