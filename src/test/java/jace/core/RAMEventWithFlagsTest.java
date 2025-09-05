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

/**
 * Tests for the RAMEventWithFlags class, focusing on its ability to
 * discriminate based on memory flags.
 */
public class RAMEventWithFlagsTest {

    @Test
    public void testFlagDetection() {
        // Create event with specific flags
        RAMEventWithFlags event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ,
            RAMEvent.SCOPE.ADDRESS,
            RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.AUX, MemorySwitch.LC1
        );
        
        // Test has flag method
        assertTrue(event.hasFlag(MemorySwitch.AUX));
        assertTrue(event.hasFlag(MemorySwitch.LC1));
        assertFalse(event.hasFlag(MemorySwitch.AUX_ZP));
        assertFalse(event.hasFlag(MemorySwitch.LC2));
        
        // Test property getters
        assertTrue(event.isAuxMemory());
        assertTrue(event.isLanguageCard());
        assertEquals(1, event.getLcBank());
    }
    
    @Test
    public void testFlagDependentListener() {
        // Create atomic flags to track which handlers were called
        AtomicBoolean auxHandlerCalled = new AtomicBoolean(false);
        AtomicBoolean mainHandlerCalled = new AtomicBoolean(false);
        AtomicBoolean lc1HandlerCalled = new AtomicBoolean(false);
        AtomicBoolean lc2HandlerCalled = new AtomicBoolean(false);
        
        // Create handlers for different memory configurations
        RAMEvent.RAMEventHandler auxHandler = e -> {
            if (e instanceof RAMEventWithFlags) {
                RAMEventWithFlags fe = (RAMEventWithFlags) e;
                if (fe.hasFlag(MemorySwitch.AUX)) {
                    auxHandlerCalled.set(true);
                }
            }
        };
        
        RAMEvent.RAMEventHandler mainHandler = e -> {
            if (e instanceof RAMEventWithFlags) {
                RAMEventWithFlags fe = (RAMEventWithFlags) e;
                if (!fe.hasFlag(MemorySwitch.AUX)) {
                    mainHandlerCalled.set(true);
                }
            }
        };
        
        RAMEvent.RAMEventHandler lc1Handler = e -> {
            if (e instanceof RAMEventWithFlags) {
                RAMEventWithFlags fe = (RAMEventWithFlags) e;
                if (fe.hasFlag(MemorySwitch.LC1) && !fe.hasFlag(MemorySwitch.LC2)) {
                    lc1HandlerCalled.set(true);
                }
            }
        };
        
        RAMEvent.RAMEventHandler lc2Handler = e -> {
            if (e instanceof RAMEventWithFlags) {
                RAMEventWithFlags fe = (RAMEventWithFlags) e;
                if (fe.hasFlag(MemorySwitch.LC2)) {
                    lc2HandlerCalled.set(true);
                }
            }
        };
        
        // Create events with different configurations
        RAMEventWithFlags auxEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX
        );
        
        RAMEventWithFlags mainEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42
        );
        
        RAMEventWithFlags lc1Event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.LC1
        );
        
        RAMEventWithFlags lc2Event = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0xD042, 0, 0x42,
            MemorySwitch.LC2
        );
        
        // Test handlers with each event
        
        // Aux event should trigger aux handler but not main handler
        auxHandler.handleEvent(auxEvent);
        mainHandler.handleEvent(auxEvent);
        assertTrue(auxHandlerCalled.get());
        assertFalse(mainHandlerCalled.get());
        
        // Reset flags
        auxHandlerCalled.set(false);
        mainHandlerCalled.set(false);
        
        // Main event should trigger main handler but not aux handler
        auxHandler.handleEvent(mainEvent);
        mainHandler.handleEvent(mainEvent);
        assertFalse(auxHandlerCalled.get());
        assertTrue(mainHandlerCalled.get());
        
        // LC1 event should trigger LC1 handler but not LC2 handler
        lc1Handler.handleEvent(lc1Event);
        lc2Handler.handleEvent(lc1Event);
        assertTrue(lc1HandlerCalled.get());
        assertFalse(lc2HandlerCalled.get());
        
        // Reset flags
        lc1HandlerCalled.set(false);
        lc2HandlerCalled.set(false);
        
        // LC2 event should trigger LC2 handler but not LC1 handler
        lc1Handler.handleEvent(lc2Event);
        lc2Handler.handleEvent(lc2Event);
        assertFalse(lc1HandlerCalled.get());
        assertTrue(lc2HandlerCalled.get());
    }
    
    @Test
    public void testRAMListener() {
        // Create a custom RAM listener that is sensitive to AUX flag
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        
        RAMListener auxListener = new RAMListener("Aux Memory Listener", RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x42);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                if (e instanceof RAMEventWithFlags) {
                    RAMEventWithFlags fe = (RAMEventWithFlags) e;
                    if (fe.hasFlag(MemorySwitch.AUX)) {
                        listenerCalled.set(true);
                    }
                }
            }
        };
        
        // Create events for different memory configurations
        RAMEventWithFlags auxEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX
        );
        
        RAMEventWithFlags mainEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42);
        
        // Check if the listener correctly filters events based on flags
        auxListener.handleEvent(auxEvent);
        assertTrue(listenerCalled.get());
        
        // Reset and test with main memory event
        listenerCalled.set(false);
        auxListener.handleEvent(mainEvent);
        assertFalse(listenerCalled.get());
    }
    
    @Test
    public void testEventRejection() {
        // Create a flag to track if the handler was called
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        
        // Create a handler that only processes events in auxiliary memory
        RAMEvent.RAMEventHandler auxOnlyHandler = e -> {
            if (e instanceof RAMEventWithFlags) {
                RAMEventWithFlags fe = (RAMEventWithFlags) e;
                if (fe.hasFlag(MemorySwitch.AUX)) {
                    handlerCalled.set(true);
                }
            }
        };
        
        // Create a main memory event (should be rejected)
        RAMEventWithFlags mainEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42
        );
        
        // Test that the handler rejects the main memory event
        auxOnlyHandler.handleEvent(mainEvent);
        assertFalse("Handler should not be called for main memory event", handlerCalled.get());
        
        // Create an aux memory event (should be accepted)
        RAMEventWithFlags auxEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX
        );
        
        // Test that the handler accepts the aux memory event
        auxOnlyHandler.handleEvent(auxEvent);
        assertTrue("Handler should be called for aux memory event", handlerCalled.get());
    }
    
    @Test
    public void testAnyFlagListener() {
        // Create a flag to track if the handler was called
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        
        // Create a handler that processes any memory configuration
        RAMEvent.RAMEventHandler anyMemoryHandler = e -> {
            if (e instanceof RAMEventWithFlags) {
                RAMEventWithFlags fe = (RAMEventWithFlags) e;
                // A handler that wants to receive all events can check for the ANY flag
                // or simply not check any flags at all
                if (fe.hasFlag(MemorySwitch.ANY)) {
                    handlerCalled.set(true);
                }
            }
        };
        
        // Create events with different memory configurations
        RAMEventWithFlags mainEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42
        );
        
        RAMEventWithFlags auxEvent = new RAMEventWithFlags(
            RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
            0x42, 0, 0x42,
            MemorySwitch.AUX
        );
        
        // Test that the handler accepts both events
        anyMemoryHandler.handleEvent(mainEvent);
        assertTrue("Handler should accept main memory event", handlerCalled.get());
        
        handlerCalled.set(false);
        anyMemoryHandler.handleEvent(auxEvent);
        assertTrue("Handler should accept aux memory event", handlerCalled.get());
    }
    
    @Test
    public void testMultipleConditionsFiltering() {
        // Create a flag to track if the handler was called
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        
        // Create a handler with complex conditions
        // It should only accept events that:
        // 1. Are in auxiliary memory AND
        // 2. Are in language card bank 2
        RAMEvent.RAMEventHandler complexHandler = e -> {
            if (e instanceof RAMEventWithFlags) {
                RAMEventWithFlags fe = (RAMEventWithFlags) e;
                if (fe.hasFlag(MemorySwitch.AUX) && fe.hasFlag(MemorySwitch.LC2)) {
                    handlerCalled.set(true);
                }
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
        complexHandler.handleEvent(mainLC2Event);
        assertFalse("Handler should reject main memory LC2 event", handlerCalled.get());
        
        complexHandler.handleEvent(auxLC1Event);
        assertFalse("Handler should reject aux memory LC1 event", handlerCalled.get());
        
        complexHandler.handleEvent(auxLC2Event);
        assertTrue("Handler should accept aux memory LC2 event", handlerCalled.get());
    }
} 