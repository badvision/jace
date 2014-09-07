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

/**
 * A timed device is a device which executes so many ticks in a given time
 * interval. This is the core of the emulator timing mechanics.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class TimedDevice extends Device {

    /**
     * Creates a new instance of TimedDevice
     */
    public TimedDevice() {
        setSpeed(cyclesPerSecond);
    }
    @ConfigurableField(name = "Speed", description = "(in hertz)")
    public long cyclesPerSecond = defaultCyclesPerSecond();
    @ConfigurableField(name = "Max speed")
    public boolean maxspeed = false;

    @Override
    public abstract void tick();
    private static final double NANOS_PER_SECOND = 1000000000.0;
    // current cycle within the period
    private int cycleTimer = 0;
    // The actual worker that the device runs as
    public Thread worker;
    public static int TEMP_SPEED_MAX_DURATION = 1000000;
    private int tempSpeedDuration = 0;
    public boolean hasStopped = true;

    @Override
    public boolean suspend() {
        disableTempMaxSpeed();
        boolean result = super.suspend();
        if (worker != null && worker.isAlive()) {
            try {
                worker.interrupt();
                worker.join(1000);
            } catch (InterruptedException ex) {
            }
        }
        worker = null;
        return result;
    }
    Thread timerThread;

    public boolean pause() {
        if (!isRunning()) {
            return false;
        }
        isPaused = true;
        try {
            // KLUDGE: Sleeping to wait for worker thread to hit paused state.  We might be inside the worker (?)
            Thread.sleep(10);
        } catch (InterruptedException ex) {
        }
        return true;
    }

    @Override
    public void resume() {
        super.resume();
        isPaused = false;
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(() -> {
            while (isRunning()) {
                hasStopped = false;
                doTick();
                while (isPaused) {
                    hasStopped = true;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
                resync();
            }
            hasStopped = true;
        });
        worker.setDaemon(false);
        worker.setPriority(Thread.MAX_PRIORITY);
        worker.start();
        worker.setName("Timed device " + getDeviceName() + " worker");
    }
    long nanosPerInterval; // How long to wait between pauses
    long cyclesPerInterval; // How many cycles to wait until a pause interval
    long nextSync; // When was the last pause?

    public final void setSpeed(long cyclesPerSecond) {
        cyclesPerInterval = cyclesPerSecond / 100L;
        nanosPerInterval = (long) ((double) cyclesPerInterval * NANOS_PER_SECOND / (double) cyclesPerSecond);
//        System.out.println("Will pause " + nanosPerInterval + " nanos every " + cyclesPerInterval + " cycles");
        cycleTimer = 0;
        resetSyncTimer();
    }
    long skip = 0;
    long wait = 0;

    public final void resetSyncTimer() {
        nextSync = System.nanoTime() + nanosPerInterval;
        cycleTimer = 0;
    }

    public void enableTempMaxSpeed() {
        tempSpeedDuration = TEMP_SPEED_MAX_DURATION;
    }

    public void disableTempMaxSpeed() {
        tempSpeedDuration = 0;
        resetSyncTimer();
    }

    protected void resync() {
        if (++cycleTimer >= cyclesPerInterval) {
            if (maxspeed || tempSpeedDuration > 0) {
                if (tempSpeedDuration > 0) {
                    tempSpeedDuration -= cyclesPerInterval;
                }
                resetSyncTimer();
                return;
            }
            long now = System.nanoTime();
            if (now < nextSync) {
                cycleTimer = 0;
                long currentSyncDiff = nextSync - now;
                // Don't bother resynchronizing unless we're off by 10ms
                if (currentSyncDiff > 10000000L) {
                    try {
//                        System.out.println("Sleeping for " + currentSyncDiff / 1000000 + " milliseconds");
                        Thread.sleep(currentSyncDiff / 1000000L, (int) (currentSyncDiff % 1000000L));
                    } catch (InterruptedException ex) {
                        System.err.println(getDeviceName() + " was trying to sleep for " + (currentSyncDiff / 1000000) + " millis but was woken up");
//                        Logger.getLogger(TimedDevice.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
//                    System.out.println("Sleeping for " + currentSyncDiff + " nanoseconds");
//                    LockSupport.parkNanos(currentSyncDiff);
                }
            }
            nextSync += nanosPerInterval;
        }
    }

    @Override
    public void reconfigure() {
        if (cyclesPerSecond == 0) {
            cyclesPerSecond = defaultCyclesPerSecond();
        }
        setSpeed(cyclesPerSecond);
    }

    public abstract long defaultCyclesPerSecond();
}
