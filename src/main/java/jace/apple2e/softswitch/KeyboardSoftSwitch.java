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

import jace.core.Keyboard;
import jace.core.RAMEvent;
import jace.core.SoftSwitch;

/**
 * Keyboard keypress strobe -- on = key pressed
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class KeyboardSoftSwitch extends SoftSwitch {
    public KeyboardSoftSwitch(String name, int offAddress, int onAddress, int queryAddress, RAMEvent.TYPE changeType, Boolean initalState) {
        super(name,offAddress,onAddress,queryAddress,changeType,initalState);
    }

    public KeyboardSoftSwitch(String name, int[] offAddrs, int[] onAddrs, int[] queryAddrs, RAMEvent.TYPE changeType, Boolean initalState) {
        super(name,offAddrs,onAddrs,queryAddrs,changeType,initalState);
    }
    
    @Override
    public void stateChanged() {
        Keyboard.clearStrobe();
    }

    /**
     * return current keypress (if high bit set, strobe is not cleared)
     * @return
     */
    @Override
    public byte readSwitch() {
        return Keyboard.readState();
    }    
}