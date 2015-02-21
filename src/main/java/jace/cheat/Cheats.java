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

import jace.core.Computer;
import jace.core.Device;
import jace.core.RAMListener;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents some combination of hacks that can be enabled or disabled
 * through the configuration interface.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class Cheats extends Device {
    Set<RAMListener> listeners = new HashSet<>();

    public Cheats(Computer computer) {
        super(computer);
    }
    
    public void addCheat(RAMListener l) {
        listeners.add(l);
        computer.getMemory().addListener(l);
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