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

import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import java.util.Set;

/**
 * Utility class for accessing RAM with explicit memory bank switching control.
 * This allows for direct access to specific memory banks regardless of the current
 * soft switch settings.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class RamAccessor {
    private final RAM128k ram;
    
    /**
     * Creates a new RamAccessor for the specified RAM128k instance.
     * 
     * @param ram The RAM128k instance to access
     */
    public RamAccessor(RAM128k ram) {
        this.ram = ram;
    }
    
    /**
     * Reads a byte from RAM with explicit control over which memory bank to use
     * 
     * @param address The memory address to read from
     * @param triggerEvent Whether to trigger RAM read events
     * @param switches Zero or more memory switches to control bank selection
     * @return The byte value read from the specified memory location
     */
    public byte bankRead(int address, boolean triggerEvent, MemorySwitch... switches) {
        // Determine which memory configuration to use
        boolean useAux = shouldUseAux(address, switches);
        boolean useAuxLC = containsSwitch(switches, MemorySwitch.AUX_LC);
        
        // Language card handling (D000-FFFF)
        if (address >= 0xD000) {
            // Determine which LC bank to use
            boolean useLC2 = containsSwitch(switches, MemorySwitch.LC2) && address < 0xE000;
            boolean useLC1 = containsSwitch(switches, MemorySwitch.LC1); 
            
            if (useLC1 || useLC2) {
                // Read from language card RAM (selected bank)
                return readLanguageCardRAM(address, useLC2 ? 2 : 1, useAuxLC, triggerEvent);
            } else {
                // Read from ROM
                return readROM(address, triggerEvent);
            }
        }
        
        // Regular memory access
        return readRAM(address, useAux, triggerEvent);
    }
    
    /**
     * Writes a byte to RAM with explicit control over which memory bank to use
     * 
     * @param address The memory address to write to
     * @param value The byte value to write
     * @param triggerEvent Whether to trigger RAM write events
     * @param switches Zero or more memory switches to control bank selection
     */
    public void bankWrite(int address, byte value, boolean triggerEvent, MemorySwitch... switches) {
        // Determine which memory configuration to use
        boolean useAux = shouldUseAux(address, switches);
        boolean useAuxLC = containsSwitch(switches, MemorySwitch.AUX_LC);
        
        // Language card handling (D000-FFFF)
        if (address >= 0xD000) {
            // Determine which LC bank to use
            boolean useLC2 = containsSwitch(switches, MemorySwitch.LC2) && address < 0xE000;
            boolean useLC1 = containsSwitch(switches, MemorySwitch.LC1);
            
            if (useLC1 || useLC2) {
                // Write to language card RAM (selected bank)
                writeLanguageCardRAM(address, value, useLC2 ? 2 : 1, useAuxLC, triggerEvent);
            }
            // Else: Can't write to ROM, silently ignore
            return;
        }
        
        // Regular memory access
        writeRAM(address, value, useAux, triggerEvent);
    }
    
    /**
     * Determines if auxiliary memory should be used for a given address
     * 
     * @param address The memory address to check
     * @param switches The memory switches to consider
     * @return true if auxiliary memory should be used
     */
    public static boolean shouldUseAux(int address, MemorySwitch[] switches) {
        // Zero page and stack (0000-01FF)
        if (address < 0x0200) {
            return containsSwitch(switches, MemorySwitch.AUX_ZP);
        }
        
        // Everything else
        return containsSwitch(switches, MemorySwitch.AUX);
    }
    
    /**
     * Checks if a specific memory switch is present in the array
     * 
     * @param switches Array of memory switches
     * @param target The target switch to look for
     * @return true if the target switch is present
     */
    public static boolean containsSwitch(MemorySwitch[] switches, MemorySwitch target) {
        // If target is ANY, then it always matches regardless of what's in the switches
        if (target == MemorySwitch.ANY) {
            return true;
        }

        // If switches is null or empty, no match (unless target was ANY, handled above)
        if (switches == null || switches.length == 0) {
            return false;
        }
        
        // Check if ANY is in the switches or if the target switch is present
        for (MemorySwitch s : switches) {
            if (s == MemorySwitch.ANY || s == target) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Converts the current memory configuration from soft switches to MemorySwitch array
     * 
     * @return Array of MemorySwitch values representing current configuration
     */
    public MemorySwitch[] getCurrentMemorySwitches() {
        java.util.List<MemorySwitch> switches = new java.util.ArrayList<>();
        
        // Add appropriate switches based on current state
        if (SoftSwitches.AUXZP.getState()) {
            switches.add(MemorySwitch.AUX_ZP);
        }
        
        if (SoftSwitches.RAMRD.getState() || SoftSwitches.RAMWRT.getState()) {
            switches.add(MemorySwitch.AUX);
        }
        
        if (SoftSwitches.AUXZP.getState() && (SoftSwitches.LCRAM.isOn() || SoftSwitches.LCWRITE.isOn())) {
            switches.add(MemorySwitch.AUX_LC);
        }
        
        if (SoftSwitches.LCRAM.isOn() || SoftSwitches.LCWRITE.isOn()) {
            switches.add(MemorySwitch.LC1);
            
            if (SoftSwitches.LCBANK1.isOff()) {
                switches.add(MemorySwitch.LC2);
            }
        }
        
        return switches.toArray(new MemorySwitch[0]);
    }
    
    // Implementation methods that do the actual memory access
    
    private byte readRAM(int address, boolean useAux, boolean triggerEvent) {
        PagedMemory memory = useAux ? ram.getAuxMemory() : ram.getMainMemory();
        byte[] memoryBank = memory.get(address >> 8);
        byte value = memoryBank[address & 0xFF];
        
        if (triggerEvent) {
            fireReadEvent(address, value, useAux, false, 0);
        }
        
        return value;
    }
    
    private void writeRAM(int address, byte value, boolean useAux, boolean triggerEvent) {
        PagedMemory memory = useAux ? ram.getAuxMemory() : ram.getMainMemory();
        byte[] memoryBank = memory.get(address >> 8);
        memoryBank[address & 0xFF] = value;
        
        if (triggerEvent) {
            fireWriteEvent(address, value, useAux, false, 0);
        }
    }
    
    private byte readLanguageCardRAM(int address, int bankNumber, boolean useAuxLC, boolean triggerEvent) {
        PagedMemory lcMemory;
        if (useAuxLC) {
            lcMemory = bankNumber == 2 ? ram.getAuxLanguageCard2() : ram.getAuxLanguageCard();
        } else {
            lcMemory = bankNumber == 2 ? ram.getLanguageCard2() : ram.getLanguageCard();
        }
        
        // Map D000-FFFF to the language card memory
        int lcAddress = address - 0xD000;
        byte[] memoryBank = lcMemory.get(lcAddress >> 8);
        byte value = memoryBank[lcAddress & 0xFF];
        
        if (triggerEvent) {
            fireReadEvent(address, value, useAuxLC, true, bankNumber);
        }
        
        return value;
    }
    
    private void writeLanguageCardRAM(int address, byte value, int bankNumber, boolean useAuxLC, boolean triggerEvent) {
        PagedMemory lcMemory;
        if (useAuxLC) {
            lcMemory = bankNumber == 2 ? ram.getAuxLanguageCard2() : ram.getAuxLanguageCard();
        } else {
            lcMemory = bankNumber == 2 ? ram.getLanguageCard2() : ram.getLanguageCard();
        }
        
        // Map D000-FFFF to the language card memory
        int lcAddress = address - 0xD000;
        byte[] memoryBank = lcMemory.get(lcAddress >> 8);
        memoryBank[lcAddress & 0xFF] = value;
        
        if (triggerEvent) {
            fireWriteEvent(address, value, useAuxLC, true, bankNumber);
        }
    }
    
    private byte readROM(int address, boolean triggerEvent) {
        // ROM is in D000-FFFF
        int romAddress = address - 0xD000;
        byte[] romBank = ram.getRom().get(romAddress >> 8);
        byte value = romBank[romAddress & 0xFF];
        
        if (triggerEvent) {
            fireReadEvent(address, value, false, false, 0);
        }
        
        return value;
    }
    
    // Event handling methods
    
    private void fireReadEvent(int address, byte value, boolean isAux, boolean isLC, int lcBank) {
        // Create an event with memory switch information
        MemorySwitch[] flags = createFlagsForEvent(isAux, isLC, lcBank);
        
        RAMEventWithFlags event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ,
            RAMEvent.SCOPE.ADDRESS,
            RAMEvent.VALUE.ANY,
            address, 
            value, 
            value,
            flags
        );
        
        // We need to manually handle firing the event since the RAM class doesn't
        // accept RAMEventWithFlags objects directly
        fireEventDirectly(event);
    }
    
    private void fireWriteEvent(int address, byte value, boolean isAux, boolean isLC, int lcBank) {
        // Create an event with memory switch information
        MemorySwitch[] flags = createFlagsForEvent(isAux, isLC, lcBank);
        byte oldValue = bankRead(address, false, flags);
        
        RAMEventWithFlags event = new RAMEventWithFlags(
            RAMEvent.TYPE.WRITE,
            RAMEvent.SCOPE.ADDRESS,
            RAMEvent.VALUE.ANY,
            address, 
            oldValue, 
            value,
            flags
        );
        
        // We need to manually handle firing the event since the RAM class doesn't
        // accept RAMEventWithFlags objects directly
        fireEventDirectly(event);
    }
    
    /**
     * Directly fires an event to relevant listeners, bypassing the standard RAM.callListener
     * method to preserve the memory flag information.
     * 
     * @param event The event to fire
     * @return The potentially modified value from the event
     */
    private byte fireEventDirectly(RAMEvent event) {
        int address = event.getAddress();
        Set<RAMListener> activeListeners = null;
        
        // Safely access listener maps
        Set<RAMListener>[] listenerMap = ram.getListenerMap();
        Set<RAMListener>[] ioListenerMap = ram.getIOListenerMap();
        
        // Determine which listener map to use based on address (I/O or regular)
        if (listenerMap != null && ioListenerMap != null) {
            if ((address & 0x0FF00) == 0x0C000) {
                // I/O space (C000-CFFF)
                int index = address & 0x0FF;
                if (index < ioListenerMap.length) {
                    activeListeners = ioListenerMap[index];
                }
            } else {
                // Regular memory
                int index = (address >> 8) & 0x0ff;
                if (index < listenerMap.length) {
                    activeListeners = listenerMap[index];
                }
            }
            
            // If we have active listeners, notify them
            if (activeListeners != null && !activeListeners.isEmpty()) {
                for (RAMListener listener : activeListeners) {
                    listener.handleEvent(event);
                }
            }
        }
        
        // Also call the RAM's standard callListener to maintain compatibility
        // with code that doesn't use RAMEventWithFlags
        ram.callListener(event.getType(), address, event.getOldValue(), event.getNewValue(), false);
        
        return (byte) event.getNewValue();
    }
    
    /**
     * Creates the appropriate memory switch flags based on memory bank info
     * 
     * @param isAux Whether auxiliary memory is being used
     * @param isLC Whether language card is being accessed
     * @param lcBank Language card bank number (0 = none, 1 = bank1, 2 = bank2)
     * @return Array of MemorySwitch values
     */
    private MemorySwitch[] createFlagsForEvent(boolean isAux, boolean isLC, int lcBank) {
        java.util.List<MemorySwitch> flags = new java.util.ArrayList<>();
        
        // Always include the ANY flag to support wildcard matching
        flags.add(MemorySwitch.ANY);
        
        // Add appropriate memory switches
        if (isAux) {
            flags.add(MemorySwitch.AUX);
            
            // Check if we're in zero page (0000-01FF)
            if (isAux && SoftSwitches.AUXZP.getState()) {
                flags.add(MemorySwitch.AUX_ZP);
            }
        }
        
        // Add language card switches if applicable
        if (isLC) {
            if (isAux) {
                flags.add(MemorySwitch.AUX_LC);
            }
            
            if (lcBank == 1) {
                flags.add(MemorySwitch.LC1);
            } else if (lcBank == 2) {
                flags.add(MemorySwitch.LC2);
            }
        }
        
        return flags.toArray(new MemorySwitch[0]);
    }
} 