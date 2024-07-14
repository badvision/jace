/*
 * Copyright 2023 org.badvision.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jace.core;
import static jace.TestUtils.initComputer;
import static jace.TestUtils.runAssemblyCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;

/**
 * Test that memory listeners fire appropriately.
 * @author brobert
 */
public class MemoryTest {
    static Computer computer;
    static MOS65C02 cpu;
    static RAM128k ram;

    @BeforeClass
    public static void setupClass() {
        initComputer();
        SoundMixer.MUTE = true;
        computer = Emulator.withComputer(c->c, null);
        cpu = (MOS65C02) computer.getCpu();
        ram = (RAM128k) computer.getMemory();
    }

    @Test
    public void assertMemoryConfiguredCorrectly() {
        assertEquals("Active read bank 3 should be main memory page 3",
                ram.mainMemory.getMemoryPage(3),
                ram.activeRead.getMemoryPage(3));

        assertEquals("Active write bank 3 should be main memory page 3",
                ram.mainMemory.getMemoryPage(3),
                ram.activeWrite.getMemoryPage(3));
    }

    @Test
    public void testListenerRelevance() throws Exception {
        AtomicInteger anyEventCaught = new AtomicInteger();
        RAMListener anyListener = new RAMListener("Execution test", RAMEvent.TYPE.ANY, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                anyEventCaught.incrementAndGet();
            }
        };
                
        AtomicInteger readAnyEventCaught = new AtomicInteger();
        RAMListener readAnyListener = new RAMListener("Execution test 1", RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                readAnyEventCaught.incrementAndGet();
            }
        };
        
        AtomicInteger writeEventCaught = new AtomicInteger();
        RAMListener writeListener = new RAMListener("Execution test 2", RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                writeEventCaught.incrementAndGet();
            }
        };

        AtomicInteger executeEventCaught = new AtomicInteger();
        RAMListener executeListener = new RAMListener("Execution test 3", RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                executeEventCaught.incrementAndGet();
            }
        };

        
        RAMEvent readDataEvent = new RAMEvent(RAMEvent.TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);
        RAMEvent readOperandEvent = new RAMEvent(RAMEvent.TYPE.READ_OPERAND, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);
        RAMEvent executeEvent = new RAMEvent(RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);
        RAMEvent writeEvent = new RAMEvent(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);

        // Any listener        
        assertTrue("Any listener should handle all events", anyListener.isRelevant(readDataEvent));
        assertTrue("Any listener should handle all events", anyListener.isRelevant(readOperandEvent));
        assertTrue("Any listener should handle all events", anyListener.isRelevant(executeEvent));
        assertTrue("Any listener should handle all events", anyListener.isRelevant(writeEvent));
        
        // Read listener
        assertTrue("Read listener should handle all read events", readAnyListener.isRelevant(readDataEvent));
        assertTrue("Read listener should handle all read events", readAnyListener.isRelevant(readOperandEvent));
        assertTrue("Read listener should handle all read events", readAnyListener.isRelevant(executeEvent));
        assertFalse("Read listener should ignore write events", readAnyListener.isRelevant(writeEvent));

        // Write listener
        assertFalse("Write listener should ignore all read events", writeListener.isRelevant(readDataEvent));
        assertFalse("Write listener should ignore all read events", writeListener.isRelevant(readOperandEvent));
        assertFalse("Write listener should ignore all read events", writeListener.isRelevant(executeEvent));
        assertTrue("Write listener should handle write events", writeListener.isRelevant(writeEvent));
        
        // Execution listener
        assertTrue("Execute listener should only catch execution events", executeListener.isRelevant(executeEvent));
        assertFalse("Execute listener should only catch execution events", executeListener.isRelevant(readDataEvent));
        assertFalse("Execute listener should only catch execution events", executeListener.isRelevant(readOperandEvent));
        assertFalse("Execute listener should only catch execution events", executeListener.isRelevant(writeEvent));
        
        ram.addListener(anyListener);
        ram.addListener(executeListener);
        ram.addListener(readAnyListener);
        ram.addListener(writeListener);
        
        runAssemblyCode("NOP", 0x0100, 2);
        
        assertEquals("Should have no writes for 0x0100", 0, writeEventCaught.get());
        assertEquals("Should have read event for 0x0100", 1, readAnyEventCaught.get());
        assertEquals("Should have execute for 0x0100", 1, executeEventCaught.get());
    }    
}
