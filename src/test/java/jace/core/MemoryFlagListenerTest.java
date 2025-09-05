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

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for the MemoryFlagListener class, demonstrating how it simplifies
 * event handling with memory flags.
 */
public class MemoryFlagListenerTest {

    @Test
    public void testBasicFlagFilter() {
        // Create a counter to track how many times the event handler is called
        AtomicInteger handlerCalled = new AtomicInteger(0);
        
        // Create a listener that only processes events in auxiliary memory
        MemoryFlagListener auxListener = new MemoryFlagListener("Aux Memory Listener", 
                RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
                MemorySwitch.AUX) {
            @Override
            protected void doConfig() {
                setScopeStart(0x42);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                // The event has already been verified to be in auxiliary memory,
                // so we can focus on our core logic without flag checking
                handlerCalled.incrementAndGet();
            }
        };
        
        // Create events with different memory configurations
        RAMEventWithFlags auxEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX
        );
        
        RAMEventWithFlags mainEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42
        );
        
        // Test that the listener correctly filters events based on flags
        auxListener.handleEvent(auxEvent);
        assertEquals("Handler should be called for aux memory event", 1, handlerCalled.get());
        
        auxListener.handleEvent(mainEvent);
        assertEquals("Handler should not be called for main memory event", 1, handlerCalled.get());
    }
    
    @Test
    public void testCompoundFlagFiltering() {
        // Create a counter to track how many times the event handler is called
        AtomicInteger handlerCalled = new AtomicInteger(0);
        
        // Create a listener that processes events that are:
        // 1. In auxiliary memory AND
        // 2. In language card bank 2
        MemoryFlagListener complexListener = new MemoryFlagListener("Complex Listener", 
                RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
                new MemorySwitch[] { MemorySwitch.AUX, MemorySwitch.LC2 }) {
            @Override
            protected void doConfig() {
                setScopeStart(0xD042);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                // The event has already been verified to meet our complex criteria,
                // so we can focus on our core logic without flag checking
                handlerCalled.incrementAndGet();
            }
        };
        
        // Create events with different combinations
        
        // Case 1: Main memory, LC2 (should be rejected)
        RAMEventWithFlags mainLC2Event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.LC2
        );
        
        // Case 2: Aux memory, LC1 (should be rejected)
        RAMEventWithFlags auxLC1Event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.AUX, MemorySwitch.LC1
        );
        
        // Case 3: Aux memory, LC2 (should be accepted)
        RAMEventWithFlags auxLC2Event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.AUX, MemorySwitch.LC2
        );
        
        // Test each case
        complexListener.handleEvent(mainLC2Event);
        assertEquals("Handler should reject main memory LC2 event", 0, handlerCalled.get());
        
        complexListener.handleEvent(auxLC1Event);
        assertEquals("Handler should reject aux memory LC1 event", 0, handlerCalled.get());
        
        complexListener.handleEvent(auxLC2Event);
        assertEquals("Handler should accept aux memory LC2 event", 1, handlerCalled.get());
    }
    
    @Test
    public void testExclusionFiltering() {
        // Create a counter to track how many times the event handler is called
        AtomicInteger handlerCalled = new AtomicInteger(0);
        
        // Create a listener that processes events that are:
        // 1. In auxiliary memory AND
        // 2. NOT in zero page
        MemoryFlagListener exclusionListener = new MemoryFlagListener("Exclusion Listener", 
                RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
                new MemorySwitch[] { MemorySwitch.AUX },
                new MemorySwitch[] { MemorySwitch.AUX_ZP }) {
            @Override
            protected void doConfig() {
                setScopeStart(0x42);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                handlerCalled.incrementAndGet();
            }
        };
        
        // Aux memory, not zero page (should be accepted)
        RAMEventWithFlags auxStandardEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX
        );
        
        // Aux memory, zero page (should be rejected)
        RAMEventWithFlags auxZPEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX, MemorySwitch.AUX_ZP
        );
        
        // Test each case
        exclusionListener.handleEvent(auxStandardEvent);
        assertEquals("Handler should accept aux memory standard event", 1, handlerCalled.get());
        
        exclusionListener.handleEvent(auxZPEvent);
        assertEquals("Handler should reject aux memory zero page event", 1, handlerCalled.get());
    }
    
    @Test
    public void testCustomPredicateFiltering() {
        // Create a counter to track how many times the event handler is called
        AtomicInteger handlerCalled = new AtomicInteger(0);
        
        // Create a listener with a custom predicate that processes events
        // only in language card with bank number > 1
        MemoryFlagListener customListener = new MemoryFlagListener("Custom Predicate Listener", 
                RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
                e -> e.isLanguageCard() && e.getLcBank() > 1) {
            @Override
            protected void doConfig() {
                setScopeStart(0xD042);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                handlerCalled.incrementAndGet();
            }
        };
        
        // Language card bank 1 (should be rejected)
        RAMEventWithFlags lc1Event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.LC1
        );
        
        // Language card bank 2 (should be accepted)
        RAMEventWithFlags lc2Event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.LC2
        );
        
        // Test each case
        customListener.handleEvent(lc1Event);
        assertEquals("Handler should reject LC bank 1 event", 0, handlerCalled.get());
        
        customListener.handleEvent(lc2Event);
        assertEquals("Handler should accept LC bank 2 event", 1, handlerCalled.get());
    }
    
    @Test 
    public void testAnyFlagListener() {
        // Create a counter to track how many times the event handler is called
        AtomicInteger handlerCalled = new AtomicInteger(0);
        
        // Create a listener that accepts any memory configuration using ANY flag
        MemoryFlagListener anyListener = new MemoryFlagListener("Any Flag Listener", 
                RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
                MemorySwitch.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x42);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                handlerCalled.incrementAndGet();
            }
        };
        
        // Main memory event
        RAMEventWithFlags mainEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42
        );
        
        // Aux memory event
        RAMEventWithFlags auxEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX
        );
        
        // Test each case
        anyListener.handleEvent(mainEvent);
        assertEquals("Handler should accept main memory event", 1, handlerCalled.get());
        
        anyListener.handleEvent(auxEvent);
        assertEquals("Handler should accept aux memory event", 2, handlerCalled.get());
    }
} 