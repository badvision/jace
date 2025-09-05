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
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for the RamAccessor utility class
 */
public class RamAccessorTest {

    private RAM128k mockRam;
    private RamAccessor accessor;
    private PagedMemory mockMainMemory;
    private PagedMemory mockAuxMemory;
    private PagedMemory mockLanguageCard;
    private PagedMemory mockLanguageCard2;
    private PagedMemory mockAuxLanguageCard;
    private PagedMemory mockAuxLanguageCard2;
    private PagedMemory mockRom;
    
    @Before
    public void setup() {
        // Create mocks
        mockRam = Mockito.mock(RAM128k.class);
        mockMainMemory = Mockito.mock(PagedMemory.class);
        mockAuxMemory = Mockito.mock(PagedMemory.class);
        mockLanguageCard = Mockito.mock(PagedMemory.class);
        mockLanguageCard2 = Mockito.mock(PagedMemory.class);
        mockAuxLanguageCard = Mockito.mock(PagedMemory.class);
        mockAuxLanguageCard2 = Mockito.mock(PagedMemory.class);
        mockRom = Mockito.mock(PagedMemory.class);
        
        // Set up mock behavior
        when(mockRam.getMainMemory()).thenReturn(mockMainMemory);
        when(mockRam.getAuxMemory()).thenReturn(mockAuxMemory);
        when(mockRam.getLanguageCard()).thenReturn(mockLanguageCard);
        when(mockRam.getLanguageCard2()).thenReturn(mockLanguageCard2);
        when(mockRam.getAuxLanguageCard()).thenReturn(mockAuxLanguageCard);
        when(mockRam.getAuxLanguageCard2()).thenReturn(mockAuxLanguageCard2);
        when(mockRam.getRom()).thenReturn(mockRom);
        
        // Set up memory bank arrays
        byte[] mainBank = new byte[256];
        byte[] auxBank = new byte[256];
        byte[] lcBank = new byte[256];
        byte[] lc2Bank = new byte[256];
        byte[] auxLcBank = new byte[256];
        byte[] auxLc2Bank = new byte[256];
        byte[] romBank = new byte[256];
        
        // Fill test values
        mainBank[0x42] = (byte) 0x11;
        auxBank[0x42] = (byte) 0x22;
        lcBank[0x42] = (byte) 0x33;
        lc2Bank[0x42] = (byte) 0x44;
        auxLcBank[0x42] = (byte) 0x55;
        auxLc2Bank[0x42] = (byte) 0x66;
        romBank[0x42] = (byte) 0x77;
        
        // Set up mock behavior for memory access
        when(mockMainMemory.get(anyInt())).thenReturn(mainBank);
        when(mockAuxMemory.get(anyInt())).thenReturn(auxBank);
        when(mockLanguageCard.get(anyInt())).thenReturn(lcBank);
        when(mockLanguageCard2.get(anyInt())).thenReturn(lc2Bank);
        when(mockAuxLanguageCard.get(anyInt())).thenReturn(auxLcBank);
        when(mockAuxLanguageCard2.get(anyInt())).thenReturn(auxLc2Bank);
        when(mockRom.get(anyInt())).thenReturn(romBank);
        
        // Create accessor
        accessor = new RamAccessor(mockRam);
    }
    
    @Test
    public void testMainMemoryRead() {
        // Test reading from main memory
        byte result = accessor.bankRead(0x42, false);
        assertEquals((byte) 0x11, result);
    }
    
    @Test
    public void testAuxMemoryRead() {
        // Test reading from aux memory
        byte result = accessor.bankRead(0x42, false, MemorySwitch.AUX);
        assertEquals((byte) 0x11, result);
    }
    
    @Test
    public void testLanguageCardRead() {
        // Test reading from language card bank 1
        byte result = accessor.bankRead(0xD042, false, MemorySwitch.LC1);
        assertEquals((byte) 0x33, result);
    }
    
    @Test
    public void testLanguageCard2Read() {
        // Test reading from language card bank 2
        byte result = accessor.bankRead(0xD042, false, MemorySwitch.LC2);
        assertEquals((byte) 0x44, result);
    }
    
    @Test
    public void testAuxLanguageCardRead() {
        // Test reading from aux language card bank 1
        byte result = accessor.bankRead(0xD042, false, MemorySwitch.AUX_LC, MemorySwitch.LC1);
        assertEquals((byte) 0x55, result);
    }
    
    @Test
    public void testAuxLanguageCard2Read() {
        // Test reading from aux language card bank 2
        byte result = accessor.bankRead(0xD042, false, MemorySwitch.AUX_LC, MemorySwitch.LC2);
        assertEquals((byte) 0x66, result);
    }
    
    @Test
    public void testRomRead() {
        // Test reading from ROM
        byte result = accessor.bankRead(0xD042, false);
        assertEquals((byte) 0x77, result);
    }
    
    @Test
    public void testZeroPageAux() {
        // Test address in zero page with AUX_ZP switch
        boolean result = RamAccessor.shouldUseAux(0x0042, new MemorySwitch[] { MemorySwitch.AUX_ZP });
        assertTrue(result);
        
        // Test address in zero page without AUX_ZP switch
        result = RamAccessor.shouldUseAux(0x0042, new MemorySwitch[] { MemorySwitch.AUX });
        assertFalse(result);
    }
    
    @Test
    public void testRAMEventHandling() {
        // Create atomic values to capture event details
        AtomicBoolean eventCalled = new AtomicBoolean(false);
        AtomicReference<RAMEvent> capturedEvent = new AtomicReference<>();
        
        // Set up RAM to call our listener
        doAnswer(invocation -> {
            RAMEvent.TYPE type = invocation.getArgument(0);
            int address = invocation.getArgument(1);
            int oldValue = invocation.getArgument(2);
            int newValue = invocation.getArgument(3);
            
            // Create a RAMEvent just like RAM would do
            RAMEvent event = new RAMEvent(type, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, address, oldValue, newValue);
            
            // Save the event
            capturedEvent.set(event);
            eventCalled.set(true);
            
            return (byte) newValue;
        }).when(mockRam).callListener(any(RAMEvent.TYPE.class), anyInt(), anyInt(), anyInt(), anyBoolean());
        
        // Trigger a read with event firing
        accessor.bankRead(0x42, true, MemorySwitch.AUX);
        
        // Verify the event was fired
        assertTrue(eventCalled.get());
        assertNotNull(capturedEvent.get());
        assertEquals(RAMEvent.TYPE.READ, capturedEvent.get().getType());
        assertEquals(0x42, capturedEvent.get().getAddress());
    }
    
    @Test
    public void testWithFlaggedEvent() {
        // Create a mocked RAM128k for the test
        RAM128k flaggedRam = Mockito.mock(RAM128k.class);
        
        // Set up the mock behavior
        when(flaggedRam.getAuxMemory()).thenReturn(mockAuxMemory);
        when(flaggedRam.getAuxLanguageCard()).thenReturn(mockAuxLanguageCard);
        when(flaggedRam.getAuxLanguageCard2()).thenReturn(mockAuxLanguageCard2);
        when(flaggedRam.getMainMemory()).thenReturn(mockMainMemory);
        when(flaggedRam.getLanguageCard()).thenReturn(mockLanguageCard);
        when(flaggedRam.getLanguageCard2()).thenReturn(mockLanguageCard2);
        when(flaggedRam.getRom()).thenReturn(mockRom);
        
        // Set up the callListener behavior for our test
        when(flaggedRam.callListener(any(RAMEvent.TYPE.class), eq(0xD042), anyInt(), anyInt(), anyBoolean()))
            .thenAnswer(invocation -> {
                RAMEvent.TYPE t = invocation.getArgument(0);
                int address = invocation.getArgument(1);
                int oldValue = invocation.getArgument(2);
                int newValue = invocation.getArgument(3);
                
                // Create and test our extended event
                RAMEventWithFlags event = new RAMEventWithFlags(
                    t, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
                    address, oldValue, newValue,
                    new MemorySwitch[] { MemorySwitch.AUX, MemorySwitch.AUX_LC, MemorySwitch.LC2 }
                );
                
                // Test that the event has the correct flag information
                assertTrue(event.hasFlag(MemorySwitch.AUX_LC));
                assertTrue(event.hasFlag(MemorySwitch.LC2));
                assertFalse(event.hasFlag(MemorySwitch.AUX_ZP));
                assertEquals(2, event.getLcBank());
                // These values are now derived from the flags, so the assertions should match
                // what the flags imply
                assertTrue(event.isAuxMemory());
                assertTrue(event.isLanguageCard());
                
                return (byte) newValue;
            });
        
        // Create accessor with our mocked RAM implementation
        RamAccessor flaggedAccessor = new RamAccessor(flaggedRam);
        
        // Trigger the test case
        flaggedAccessor.bankRead(0xD042, true, MemorySwitch.AUX_LC, MemorySwitch.LC2);
        
        // Verify that callListener was called with the right parameters
        verify(flaggedRam).callListener(eq(RAMEvent.TYPE.READ), eq(0xD042), anyInt(), anyInt(), eq(false));
    }
    
    @Test
    public void testAnyFlag() {
        // Test that ANY flag in the switches list matches any target
        assertTrue(RamAccessor.containsSwitch(new MemorySwitch[] { MemorySwitch.ANY }, MemorySwitch.AUX));
        assertTrue(RamAccessor.containsSwitch(new MemorySwitch[] { MemorySwitch.ANY }, MemorySwitch.LC1));
        
        // Test that ANY as the target matches any switches list
        assertTrue(RamAccessor.containsSwitch(new MemorySwitch[] { MemorySwitch.AUX }, MemorySwitch.ANY));
        assertTrue(RamAccessor.containsSwitch(new MemorySwitch[] { }, MemorySwitch.ANY));
        assertTrue(RamAccessor.containsSwitch(null, MemorySwitch.ANY));
    }
} 