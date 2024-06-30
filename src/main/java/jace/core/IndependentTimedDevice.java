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

/**
 * This is the core of a device that runs with its own independent clock in its
 * own thread.  Device timing is controlled by pausing the thread at regular
 * intervals as necessary.
 * 
 * This is primarily only used for the system clock, but it is possible to
 * use for other devices that need to operate independently -- but it is best
 * to do so only with caution as extra threads can lead to weird glitches if they
 * need to have guarantees of synchronization, etc.
 * 
 * @author brobert
 */
public abstract class IndependentTimedDevice extends TimedDevice {

    public IndependentTimedDevice() {
        super(false);
    }

    // The actual worker that the device runs as
    public Thread worker;
    public boolean hasStopped = true;

    @Override
    /* We really don't want to suspect the worker thread if we're running in it.
     * The goal for suspending the thread is to prevent any concurrent activity
     * affecting the emulator state.  However, if we're already in the worker
     * thread, then we're already blocking the execution of the emulator, so
     * we don't need to suspend it.
     */
    public void whileSuspended(Runnable r) {
        if (isDeviceThread()) {
            r.run();            
        } else {
            super.whileSuspended(r);
        }
    }

    @Override
    public <T> T whileSuspended(java.util.function.Supplier<T> r, T defaultValue) {
        if (isDeviceThread()) {
            return r.get();
        } else {
            return super.whileSuspended(r, defaultValue);
        }
    }


    public boolean isDeviceThread() {
        return Thread.currentThread() == worker;
    }

    /**
     * This is used in unit tests where we want the device
     * to act like it is resumed, but not actual free-running.
     * This allows tests to step manually to check behaviors, etc.
     */
    public void resumeInThread() {
        super.resume();
        setPaused(false);
    }
    
    @Override
    public boolean suspend() {
        boolean result = super.suspend();
        Thread w = worker;
        worker = null;
        if (w != null && w.isAlive()) {
            try {
                w.interrupt();
                w.join(100);
            } catch (InterruptedException ex) {
            }
        }
        return result;
    }
    
    @Override
    protected void pauseStart() {
        // KLUDGE: Sleeping to wait for worker thread to hit paused state.  We might be inside the worker (?)
        if (!isDeviceThread()) {
            Thread.onSpinWait();
        }
    }
    
    public static int SLEEP_PRECISION_LIMIT = 100;
    public void sleepUntil(Long time) {
        if (time != null) {
            while (System.nanoTime() < time) {
                int waitTime = (int) ((time - System.nanoTime()) / 1000000);
                if (waitTime >= SLEEP_PRECISION_LIMIT) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ex) {
                        return;
                    }
                } else {
                    Thread.onSpinWait();
                }
            }
        }
    }
    
    @Override
    public synchronized void resume() {
        super.resume();
        if (worker != null && worker.isAlive()) {
            return;
        }
        Thread newWorker = new Thread(() -> {
            // System.out.println("Worker thread for " + getDeviceName() + " starting");
            while (isRunning()) {
                if (isPaused()) {
                    hasStopped = true;
                    while (isPaused() && isRunning()) {
                        Thread.onSpinWait();
                    }
                    hasStopped = false;
                } else {
                    doTick();
                    sleepUntil(calculateResyncDelay());
                }
            }
            hasStopped = true;
            // System.out.println("Worker thread for " + getDeviceName() + " stopped");
        });
        this.worker = newWorker;
        newWorker.setDaemon(false);
        newWorker.setPriority(Thread.MAX_PRIORITY);
        newWorker.setName("Timed device " + getDeviceName() + " worker");
        newWorker.start();
    }
}
