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

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

import jace.Emulator;
import jace.config.Reconfigurable;
import jace.state.Stateful;

/**
 * Device is a very simple abstraction of any emulation component. A device
 * performs some sort of action on every clock tick, unless it is waiting for a
 * number of cycles to elapse (waitCycles > 0). A device might also be paused or
 * suspended.
 *
 * Depending on the type of device, some special work might be required to
 * attach or detach it to the active emulation (such as what should happen when
 * a card is inserted or removed from a slot?) Created on May 10, 2007, 5:46 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
public abstract class Device implements Reconfigurable {

    private final Set<Device> children = new CopyOnWriteArraySet<>();
    private Device[] childrenArray = new Device[0];
    private Runnable tickHandler = this::__doTickNotRunning;

    // Number of cycles to do nothing (for cpu/video cycle accuracy)
    @Stateful
    private int waitCycles = 0;
    @Stateful
    private boolean run = false;
    @Stateful
    // Pausing a device overrides its run state, and is not reset by resuming directly
    // Therefore a caller pausing a device must unpause it directly!
    private boolean paused = false;
    @Stateful
    public boolean isAttached = false;

    private RAM _ram = null;
    protected RAM getMemory() {
        if (_ram == null) {
            _ram = Emulator.withMemory(m->m, null);
            _ram.onDetach(()->_ram = null);
        }
        return _ram;
    }

    Device parentDevice = null;
    public Device getParent() {
        return parentDevice;
    }

    public void addChildDevice(Device d) {
        if (d == null || children.contains(d) || d.equals(this)) {
            return;
        }
        d.parentDevice = this;
        children.add(d);
        d.attach();
        childrenArray = children.toArray(Device[]::new);
        updateTickHandler();
    }

    public void removeChildDevice(Device d) {
        if (d == null) {
            return;
        }
        children.remove(d);
        d.suspend();
        d.detach();
        childrenArray = children.toArray(Device[]::new);
        updateTickHandler();
    }

    public void addAllDevices(Iterable<Device> devices) {
        devices.forEach(this::addChildDevice);
    }

    public Iterable<Device> getChildren() {
        return children;
    }
    
    public void setAllDevices(Collection<Device> newDevices) {
        children.stream().filter(d-> !newDevices.contains(d)).forEach(this::removeChildDevice);
        newDevices.stream().filter(d-> !children.contains(d)).forEach(this::addChildDevice);
    }

    public void addWaitCycles(int wait) {
        waitCycles += wait;
    }

    public void setWaitCycles(int wait) {
        waitCycles = wait;
    }

    private void updateTickHandler() {
        if (!isRunning() || isPaused()) {
            tickHandler = this::__doTickNotRunning;
        } else if (childrenArray.length == 0) {
            tickHandler = this::__doTickNoDevices;           
        } else {
            tickHandler = this::__doTickIsRunning;
        }
    }
    
    private void __doTickNotRunning() {
        // Do nothing
    }
    
    private void __doTickIsRunning() {
        for (Device d : childrenArray) {
            if (d.isRunning() && !d.isPaused()) {
                d.doTick();
            }
        }
        if (waitCycles <= 0) {
            tick();
            return;
        }
        waitCycles--;
    }
    
    private void __doTickNoDevices() {
        if (waitCycles <= 0) {
            tick();
            return;
        }
        waitCycles--;
    }
    
    /**
     * This is called every tick, but it is critical that tick() should be overridden
     * not this method!  This is only overridable so timed device can implement timing
     * semantics around this without interfering with the tick() method implementations.
     */
    public void doTick() {
        tickHandler.run();
    }

    public boolean isRunning() {
        return run;
    }
    
    public final boolean isPaused() {
        return paused;
    }

    public final synchronized void setRun(boolean run) {
        // if (this.run != run) {
        //     System.out.println(getDeviceName() + " " + (run ? "RUN" : "STOP"));
        //     Thread.dumpStack();
        // }
        this.run = run;
        updateTickHandler();
    }

    public synchronized void setPaused(boolean paused) {
        // if (this.paused != paused) {
        //     System.out.println(getDeviceName() + " " + (paused ? "PAUSED" : "UNPAUSED"));
        //     Thread.dumpStack();
        // }
        this.paused = paused;
        updateTickHandler();
    }
    
    protected abstract String getDeviceName();

    @Override
    public String getName() {
        return getDeviceName();
    }

    public abstract void tick();
    
    public void whileSuspended(Runnable r) {
        whileSuspended(()->{
            r.run();
            return null;
        }, null);
    }

    public void whilePaused(Runnable r) {
        whilePaused(()->{
            r.run();
            return null;
        }, null);
    }

    public <T> T whileSuspended(Supplier<T> r, T defaultValue) {
        T result;
        if (isRunning()) {
            suspend();
            result = r.get();
            resume();
        } else {
            result = r.get();
        }
        return result != null ? result : defaultValue;
    }

    public <T> T whilePaused(Supplier<T> r, T defaultValue) {
        T result;
        if (!isPaused() && isRunning()) {
            setPaused(true);
            result = r.get();
            setPaused(false);
        } else {
            result = r.get();
        }
        return result != null ? result : defaultValue;
    }

    
    public boolean suspend() {
        // Suspending the parent device means the children are not going to run
        // children.forEach(Device::suspend);
        if (isRunning()) {
            setRun(false);
            return true;
        }
        return false;
    }

    public void resumeAll() {
        resume();
        children.forEach(Device::resumeAll);
    }

    public void resume() {
        // Resuming children pre-emptively might lead to unexpected behavior
        // Don't do that unless we really mean to (such as cold-starting the computer)
        // children.forEach(Device::resume);
        if (!isRunning()) {
            setRun(true);
            waitCycles = 0;
        }
    }

    public void attach() {
        isAttached = true;
        children.forEach(Device::attach);
    }

    public void detach() {
        children.forEach(Device::suspend);
        children.forEach(Device::detach);
        Keyboard.unregisterAllHandlers(this);
        if (this.isRunning()) {
            this.suspend();
        }
        isAttached = false;
        _ram = null;
    }
}
