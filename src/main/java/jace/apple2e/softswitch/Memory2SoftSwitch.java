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