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
package jace.apple2e.softswitch;

import jace.core.RAMEvent;
import jace.core.SoftSwitch;

/**
 * A memory softswitch is a softswitch which triggers a memory reconfiguration
 * after its value is changed.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class MemorySoftSwitch extends SoftSwitch {

    public MemorySoftSwitch(String name, int offAddress, int onAddress, int queryAddress, RAMEvent.TYPE changeType, Boolean initalState) {
        super(name, offAddress, onAddress, queryAddress, changeType, initalState);
    }

    public MemorySoftSwitch(String name, int[] offAddrs, int[] onAddrs, int[] queryAddrs, RAMEvent.TYPE changeType, Boolean initalState) {
        super(name, offAddrs, onAddrs, queryAddrs, changeType, initalState);
    }

    @Override
    public void stateChanged() {
//        System.out.println(getName()+ " was switched to "+getState());
        if (computer.getMemory() != null) {
            computer.getMemory().configureActiveMemory();
        }
    }

    // Todo: Implement floating bus, maybe?
    @Override
    protected byte readSwitch() {
        if (computer.getVideo() == null) {
            return 0x00;
        } else {
            byte value = computer.getVideo().getFloatingBus();
            if (getState()) {
                return (byte) (value | 0x080);
            } else {
                return (byte) (value & 0x07f);
            }
        }
    }
}
