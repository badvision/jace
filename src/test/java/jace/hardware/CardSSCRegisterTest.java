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
        
        // A 6551 hardware reset clears the command register to $00, and the
        // register is a read/write latch, so an un-programmed card reads back $00.
        // (0x1D was never reachable: it would require software to have written it.)
        RAMEvent commandEvent = new RAMEvent(TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
                                            0xC08A, 0, 0);
        ssc.handleIOAccess(CardSSC.ACIA_Command, TYPE.READ_DATA, 0, commandEvent);
        int commandValue = commandEvent.getNewValue();
        System.out.println("ACIA_Command returned: 0x" + Integer.toHexString(commandValue));
        assertEquals("ACIA Command should read back the post-reset value", 0x00, commandValue);

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

    /**
     * The 6551 command and control registers are read/write latches: a read
     * returns the last value written. The SSC firmware relies on this. SETUPCMD
     * at $CDC1 performs a read-modify-write:
     *     LDA $C08A,Y / ORA #$0C / STA $C08A,Y
     * and BINIT at $C239 reads the command register back and branches on
     * AND #$1F. If reads return a constant instead of the latched value, both
     * of those go wrong, so this behaviour is pinned by test.
     */
    @Test
    public void testCommandAndControlRegistersLatchWrites() {
        assertEquals("Command register should latch a written value",
                     0x0B, writeThenRead(CardSSC.ACIA_Command, 0x0B));
        assertEquals("Command register should latch a second, different value",
                     0x09, writeThenRead(CardSSC.ACIA_Command, 0x09));
        assertEquals("Control register should latch a written value",
                     0x16, writeThenRead(CardSSC.ACIA_Control, 0x16));

        // Reproduce the firmware's read-modify-write and confirm bits survive it.
        int base = writeThenRead(CardSSC.ACIA_Command, 0x01);
        int modified = writeThenRead(CardSSC.ACIA_Command, base | 0x0C);
        assertEquals("ORA #$0C read-modify-write should preserve the original bits",
                     0x0D, modified);
    }

    private int writeThenRead(int register, int value) {
        RAMEvent write = new RAMEvent(TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
                                      0xC080 + register, 0, value);
        ssc.handleIOAccess(register, TYPE.WRITE, value, write);
        RAMEvent read = new RAMEvent(TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY,
                                     0xC080 + register, 0, 0);
        ssc.handleIOAccess(register, TYPE.READ_DATA, 0, read);
        return read.getNewValue();
    }
}