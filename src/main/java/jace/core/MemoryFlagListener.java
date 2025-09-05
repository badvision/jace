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

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * A specialized RAMListener that filters events based on memory flags.
 * This class simplifies creating listeners that need to respond only to 
 * specific memory configurations (e.g., only events from auxiliary memory,
 * or only from language card bank 2, etc.)
 * 
 * @author Brendan Robert
 */
public abstract class MemoryFlagListener extends RAMListener {
    
    private MemorySwitch[] requiredFlags;
    private MemorySwitch[] excludedFlags;
    private Predicate<RAMEventWithFlags> customPredicate;

    /**
     * Creates a listener that requires specific memory flags to be present.
     * 
     * @param name The name of the listener
     * @param type The type of event to listen for
     * @param scope The scope of addresses to listen for
     * @param value The value criteria to listen for
     * @param requiredFlags Memory flags that must be present in the event
     */
    public MemoryFlagListener(String name, RAMEvent.TYPE type, RAMEvent.SCOPE scope, RAMEvent.VALUE value, 
                             MemorySwitch... requiredFlags) {
        super(name, type, scope, value);
        this.requiredFlags = requiredFlags;
        this.excludedFlags = new MemorySwitch[0];
    }
    
    /**
     * Creates a listener that requires specific memory flags to be present
     * and other memory flags to be absent.
     * 
     * @param name The name of the listener
     * @param type The type of event to listen for
     * @param scope The scope of addresses to listen for
     * @param value The value criteria to listen for
     * @param requiredFlags Memory flags that must be present in the event
     * @param excludedFlags Memory flags that must not be present in the event
     */
    public MemoryFlagListener(String name, RAMEvent.TYPE type, RAMEvent.SCOPE scope, RAMEvent.VALUE value, 
                             MemorySwitch[] requiredFlags, MemorySwitch[] excludedFlags) {
        super(name, type, scope, value);
        this.requiredFlags = requiredFlags;
        this.excludedFlags = excludedFlags;
    }
    
    /**
     * Creates a listener with a custom predicate for advanced filtering.
     * 
     * @param name The name of the listener
     * @param type The type of event to listen for
     * @param scope The scope of addresses to listen for
     * @param value The value criteria to listen for
     * @param customPredicate A predicate that determines if the event should be processed
     */
    public MemoryFlagListener(String name, RAMEvent.TYPE type, RAMEvent.SCOPE scope, RAMEvent.VALUE value, 
                             Predicate<RAMEventWithFlags> customPredicate) {
        super(name, type, scope, value);
        this.requiredFlags = new MemorySwitch[0];
        this.excludedFlags = new MemorySwitch[0];
        this.customPredicate = customPredicate;
    }
    
    @Override
    public boolean isRelevant(RAMEvent e) {
        // First apply standard RAMListener filtering
        if (!super.isRelevant(e)) {
            return false;
        }
        
        // Then check if it's a RAMEventWithFlags
        if (!(e instanceof RAMEventWithFlags)) {
            return false;
        }
        
        RAMEventWithFlags event = (RAMEventWithFlags) e;
        
        // If using a custom predicate, delegate to it
        if (customPredicate != null) {
            return customPredicate.test(event);
        }
        
        // Check for required flags
        if (requiredFlags.length > 0) {
            // If ANY is among required flags, it matches anything
            if (Arrays.stream(requiredFlags).anyMatch(f -> f == MemorySwitch.ANY)) {
                // Skip the required flag check but still do excluded flag check
            } else {
                // All required flags must be present
                for (MemorySwitch flag : requiredFlags) {
                    if (!event.hasFlag(flag)) {
                        return false;
                    }
                }
            }
        }
        
        // Check for excluded flags
        if (excludedFlags.length > 0) {
            // None of the excluded flags can be present
            for (MemorySwitch flag : excludedFlags) {
                if (event.hasFlag(flag)) {
                    return false;
                }
            }
        }
        
        return true;
    }
} 