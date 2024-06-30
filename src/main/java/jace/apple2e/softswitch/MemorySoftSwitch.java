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
        Emulator.withMemory(m->m.configureActiveMemory());
    }

    // Todo: Implement floating bus, maybe?
    @Override
    protected byte readSwitch() {
        byte value = Emulator.withComputer(c->c.getVideo().getFloatingBus(), (byte) 0);
        if (getState()) {
            return (byte) (value | 0x080);
        } else {
            return (byte) (value & 0x07f);
        }
    }
}