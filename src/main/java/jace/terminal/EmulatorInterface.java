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

package jace.terminal;

import java.util.function.Consumer;
import java.util.function.Function;

import jace.apple2e.Apple2e;

/**
 * Interface for emulator operations needed by the Terminal.
 * This provides an abstraction layer for the actual emulator implementation,
 * allowing for better testing and dependency injection.
 */
public interface EmulatorInterface {
    
    /**
     * Execute an action on the emulated computer
     * @param action Consumer to receive the computer instance
     */
    void withComputer(Consumer<Apple2e> action);
    
    /**
     * Execute a function on the emulated computer and return its result
     * @param <T> Return type
     * @param function Function to execute on the computer
     * @param defaultValue Default value to return if computer is unavailable
     * @return Result of the function or defaultValue if unavailable
     */
    <T> T withComputer(Function<Apple2e, T> function, T defaultValue);
    
    /**
     * Suspend the emulator, perform an action, and then resume
     * @param action Consumer to receive the computer instance
     */
    void whileSuspended(Consumer<Apple2e> action);
} 