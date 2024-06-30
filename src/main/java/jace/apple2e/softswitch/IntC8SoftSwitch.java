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

package jace.apple2e.softswitch;

import jace.Emulator;
import jace.apple2e.SoftSwitches;
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
                new RAMListener("Softswitch " + getName() + " on", RAMEvent.TYPE.ANY, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
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
                new RAMListener("Softswitch " + getName() + " off", RAMEvent.TYPE.ANY, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
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
        Emulator.withMemory(m->m.configureActiveMemory());
    }
}