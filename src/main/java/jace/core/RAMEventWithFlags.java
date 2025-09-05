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

/**
 * An extended RAMEvent that includes information about the memory bank switches
 * in effect when the event occurred. This is useful for listeners that need to
 * be sensitive to specific memory configurations.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class RAMEventWithFlags extends RAMEvent {
    private final MemorySwitch[] memoryFlags;
    private final boolean isAuxMemory;
    private final boolean isLanguageCard;
    private final int lcBank; // 0 = not LC, 1 = bank 1, 2 = bank 2
    
    /**
     * Creates a new instance of RAMEventWithFlags
     * 
     * @param t Event type
     * @param s Event scope
     * @param v Event value
     * @param address Memory address
     * @param oldValue Previous value
     * @param newValue New value
     * @param memoryFlags Memory switches in effect
     */
    public RAMEventWithFlags(TYPE t, SCOPE s, VALUE v, int address, int oldValue, int newValue,
                            MemorySwitch... memoryFlags) {
        super(t, s, v, address, oldValue, newValue);
        this.memoryFlags = memoryFlags;
        
        // Derive the memory configuration from the flags
        this.isAuxMemory = RamAccessor.containsSwitch(memoryFlags, MemorySwitch.AUX);
        this.isLanguageCard = RamAccessor.containsSwitch(memoryFlags, MemorySwitch.LC1) || 
                             RamAccessor.containsSwitch(memoryFlags, MemorySwitch.LC2);
        
        // Determine language card bank
        if (!isLanguageCard) {
            this.lcBank = 0;
        } else if (RamAccessor.containsSwitch(memoryFlags, MemorySwitch.LC2)) {
            this.lcBank = 2;
        } else {
            this.lcBank = 1;
        }
    }
    
    /**
     * Checks if a specific memory switch is active for this event
     * 
     * @param flag The memory switch to check
     * @return true if the switch is active
     */
    public boolean hasFlag(MemorySwitch flag) {
        return RamAccessor.containsSwitch(memoryFlags, flag);
    }
    
    /**
     * @return The memory switches in effect for this event
     */
    public MemorySwitch[] getMemoryFlags() {
        return memoryFlags;
    }
    
    /**
     * @return true if auxiliary memory was accessed
     */
    public boolean isAuxMemory() {
        return isAuxMemory;
    }
    
    /**
     * @return true if language card was accessed
     */
    public boolean isLanguageCard() {
        return isLanguageCard;
    }
    
    /**
     * @return Language card bank number (0 = none, 1 = bank1, 2 = bank2)
     */
    public int getLcBank() {
        return lcBank;
    }
} 