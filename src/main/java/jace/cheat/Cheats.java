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
package jace.cheat;

import jace.apple2e.MOS65C02;
import jace.core.Computer;
import jace.core.Device;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents some combination of hacks that can be enabled or disabled through
 * the configuration interface.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class Cheats extends Device {

    Set<RAMListener> listeners = new HashSet<>();

    public Cheats(Computer computer) {
        super(computer);
    }

    public RAMListener bypassCode(int address, int addressEnd) {
        int noOperation = MOS65C02.COMMAND.NOP.ordinal();
        return addCheat(RAMEvent.TYPE.READ, (e) -> e.setNewValue(noOperation), address, addressEnd);
    }

    public RAMListener forceValue(int value, int... address) {
        return addCheat(RAMEvent.TYPE.ANY, (e) -> e.setNewValue(value), address);
    }

    public RAMListener forceValue(int value, boolean auxFlag, int... address) {
        return addCheat(RAMEvent.TYPE.ANY, auxFlag, (e) -> e.setNewValue(value), address);
    }

    public RAMListener addCheat(RAMEvent.TYPE type, RAMEvent.RAMEventHandler handler, int... address) {
        RAMListener listener;
        if (address.length == 1) {
            listener = computer.getMemory().observe(type, address[0], handler);
        } else {
            listener = computer.getMemory().observe(type, address[0], address[1], handler);
        }
        listeners.add(listener);
        return listener;
    }

    public RAMListener addCheat(RAMEvent.TYPE type, boolean auxFlag, RAMEvent.RAMEventHandler handler, int... address) {
        RAMListener listener;
        if (address.length == 1) {
            listener = computer.getMemory().observe(type, address[0], auxFlag, handler);
        } else {
            listener = computer.getMemory().observe(type, address[0], address[1], auxFlag, handler);
        }
        listeners.add(listener);
        return listener;
    }

    @Override
    public void attach() {
        registerListeners();
    }

    @Override
    public void detach() {
        unregisterListeners();
        super.detach();
    }

    abstract void registerListeners();

    protected void unregisterListeners() {
        listeners.stream().forEach((l) -> {
            computer.getMemory().removeListener(l);
        });
        listeners.clear();
    }

    public void removeListener(RAMListener l) {
        computer.getMemory().removeListener(l);
        listeners.remove(l);
    }

    @Override
    public void reconfigure() {
        unregisterListeners();
        registerListeners();
    }

    @Override
    public String getShortName() {
        return "cheat";
    }
}
