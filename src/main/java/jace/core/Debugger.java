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

package jace.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A debugger has the ability to track a list of breakpoints and step a CPU one
 * instruction at a time. This is a very generic abstraction used to describe
 * the basic contract of what a debugger should do. EmulatorUILogic creates an
 * anonymous subclass that hooks into the actual emulator.
 * Created on April 16, 2007, 10:37 PM
 *
 * @author Administrator
 */
public abstract class Debugger {

    public abstract void updateStatus();
    private boolean active = false;
    public boolean step = false;

    public void setActive(boolean state) {
        active = state;
    }

    public boolean isActive() {
        return active;
    }
    private final List<Integer> breakpoints = new ArrayList<>();

    public List<Integer> getBreakpoints() {
        return breakpoints;
    }

    boolean hasBreakpoints() {
        return !breakpoints.isEmpty() && breakpoints.stream().anyMatch(Objects::nonNull);
    }

    boolean takeStep() {
        if (step) {
            step = false;
            return true;
        }
        return false;
    }
}
