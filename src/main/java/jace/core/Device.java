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

import jace.state.Stateful;
import jace.config.Reconfigurable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Device is a very simple abstraction of any emulation component. A device
 * performs some sort of action on every clock tick, unless it is waiting for a
 * number of cycles to elapse (waitCycles > 0). A device might also be paused or
 * suspended.
 *
 * Depending on the type of device, some special work might be required to
 * attach or detach it to the active emulation (such as what should happen when
 * a card is inserted or removed from a slot?)
 * Created on May 10, 2007, 5:46 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
public abstract class Device implements Reconfigurable {
    protected Computer computer;
    private List<Device> children;
    
    private Device() {
        children = Collections.synchronizedList(new ArrayList<>());
    }
    
    public Device(Computer computer) {
        this();
        this.computer = computer;
    }
    
    // Number of cycles to do nothing (for cpu/video cycle accuracy)
    @Stateful
    private int waitCycles = 0;
    @Stateful
    private final BooleanProperty run = new SimpleBooleanProperty(true);
    @Stateful
    public boolean isPaused = false;
    @Stateful
    public boolean isAttached = false;
    
    public void addChildDevice(Device d) {
        children.add(d);
        if (isAttached) {
            d.attach();
        }
    }
    
    public void removeChildDevice(Device d) {
        children.remove(d);
        d.suspend();
        if (isAttached) {
            d.detach();
        }
    }
    
    public void addAllDevices(Collection<Device> devices) {
        devices.forEach(this::addChildDevice);
    }
    
    public List<Device> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public BooleanProperty getRunningProperty() {
        return run;
    }
    
    public void addWaitCycles(int wait) {
        waitCycles += wait;
    }

    public void setWaitCycles(int wait) {
        waitCycles = wait;
    }

    public void doTick() {
        /*
         if (waitCycles <= 0)
         tick();
         else
         waitCycles--;
         */

        if (!run.get()) {
//            System.out.println("Device stopped: " + getName());
            isPaused = true;
            return;
        }
        // The following might be as much as 7% faster than the above
        // My guess is that the above results in a GOTO
        // whereas the following pre-emptive return avoids that
        if (waitCycles > 0) {
            waitCycles--;
            return;
        }
        // Implicit else...
        children.forEach(Device::doTick);
        tick();
    }

    public boolean isRunning() {
        return run.get();
    }

    public synchronized void setRun(boolean run) {
//        System.out.println(Thread.currentThread().getName() + (run ? " resuming " : " suspending ")+ getDeviceName());
        isPaused = false;
        this.run.set(run);
    }

    protected abstract String getDeviceName();

    @Override
    public String getName() {
        return getDeviceName();
    }

    public abstract void tick();

    public boolean suspend() {
        if (isRunning()) {
            setRun(false);
            return true;
        }
        children.forEach(Device::suspend);
        return false;
    }

    public void resume() {
        setRun(true);
        waitCycles = 0;
        children.forEach(Device::resume);
    }

    public void attach() {
        isAttached = true;
        children.forEach(Device::attach);
    }

    public void detach() {
        Keyboard.unregisterAllHandlers(this);
        children.forEach(Device::suspend);
        children.forEach(Device::detach);
    }
}
