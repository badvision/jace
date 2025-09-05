package jace.hardware;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;

public class CardSSCRegisterTest {
    
    private CardSSC ssc;
    
    @Before
    public void setUp() {
        ssc = new CardSSC();
    }
    
    @Test
    public void testACIARegisterInitialValues() {
        System.out.println("Testing ACIA register initial values...");
        
        // Test ACIA_Status register (should return 0x10 - transmit register empty, no input)
        RAMEvent statusEvent = new RAMEvent(TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
                                           0xC089, 0, 0);
        ssc.handleIOAccess(CardSSC.ACIA_Status, TYPE.READ_DATA, 0, statusEvent);
        int statusValue = statusEvent.getNewValue();
        System.out.println("ACIA_Status returned: 0x" + Integer.toHexString(statusValue));
        assertEquals("ACIA Status should indicate transmit register empty", 0x10, statusValue);
        
        // Test ACIA_Command register (should return 0x1D)  
        RAMEvent commandEvent = new RAMEvent(TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
                                            0xC08A, 0, 0); 
        ssc.handleIOAccess(CardSSC.ACIA_Command, TYPE.READ_DATA, 0, commandEvent);
        int commandValue = commandEvent.getNewValue();
        System.out.println("ACIA_Command returned: 0x" + Integer.toHexString(commandValue));
        assertEquals("ACIA Command should return correct initialization value", 0x1D, commandValue);
        
        // Test ACIA_Control register (should return 0x0)
        RAMEvent controlEvent = new RAMEvent(TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
                                            0xC08B, 0, 0);
        ssc.handleIOAccess(CardSSC.ACIA_Control, TYPE.READ_DATA, 0, controlEvent);
        int controlValue = controlEvent.getNewValue();
        System.out.println("ACIA_Control returned: 0x" + Integer.toHexString(controlValue));
        assertEquals("ACIA Control should return 0", 0x0, controlValue);
    }
    
    @Test  
    public void testACIARegisterPhantomInputBehavior() {
        System.out.println("Testing ACIA register phantom input behavior...");
        
        // When disconnected, ACIA_Status bit 3 (receive data ready) should be 0
        RAMEvent statusEvent = new RAMEvent(TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
                                           0xC089, 0, 0);
        ssc.handleIOAccess(CardSSC.ACIA_Status, TYPE.READ_DATA, 0, statusEvent);
        int statusValue = statusEvent.getNewValue();
        
        boolean receiveDataReady = (statusValue & 0x08) != 0;
        System.out.println("ACIA_Status = 0x" + Integer.toHexString(statusValue) + 
                          ", receive data ready bit = " + receiveDataReady);
        
        assertFalse("When disconnected, receive data ready bit should be false", receiveDataReady);
    }
}