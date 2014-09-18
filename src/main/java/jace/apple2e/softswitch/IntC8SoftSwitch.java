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

import jace.apple2e.SoftSwitches;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.SoftSwitch;

/**
 * Very funky softswitch which controls Slot 3 / C8 ROM behavior (and is the
 * reason why some cards don't like Slot 3)
 * Created on February 1, 2007, 9:40 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class IntC8SoftSwitch extends SoftSwitch {

    /**
     * Creates a new instance of IntC8SoftSwitch
     */
    public IntC8SoftSwitch() {
        super("InternalC8Rom", false);
        // INTC8Rom should activate whenever C3xx memory is accessed and SLOTC3ROM is off
        addListener(
                new RAMListener(RAMEvent.TYPE.ANY, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0C300);
                setScopeEnd(0x0C3FF);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                if (SoftSwitches.SLOTC3ROM.isOff()) {
                    setState(true);
                }
            }
        });

        // INTCXRom shoud deactivate whenever CFFF is accessed
        addListener(
                new RAMListener(RAMEvent.TYPE.ANY, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            protected void doConfig() {
                setScopeStart(0x0CFFF);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                setState(false);
            }
        });
    }

    @Override
    protected byte readSwitch() {
        return 0;
    }

    @Override
    public void stateChanged() {
        if (computer.getMemory() != null) {
            computer.getMemory().configureActiveMemory();
        }
    }
}