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

import jace.core.RAMEvent.TYPE;

/**
 * A softswitch that requires two consecutive accesses to flip
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Memory2SoftSwitch extends MemorySoftSwitch {
    public Memory2SoftSwitch(String name, int offAddress, int onAddress, int queryAddress, TYPE changeType, Boolean initalState) {
        super(name, offAddress, onAddress, queryAddress, changeType, initalState);
    }

    public Memory2SoftSwitch(String name, int[] offAddrs, int[] onAddrs, int[] queryAddrs, TYPE changeType, Boolean initalState) {
        super(name, offAddrs, onAddrs, queryAddrs, changeType, initalState);
    }
    
    // The switch must be set true two times in a row before it will actually be set.
    int count = 0;
    @Override
    public void setState(boolean newState) {
        if (!newState) {
            count = 0;
            super.setState(newState);
        } else {
            count++;
            if (count >= 2) {
                super.setState(newState);
                count = 0;
            }
        }
    }

    @Override
    public String toString() {
        return getName()+(getState()?":1":":0")+"~~"+count;
    }
}