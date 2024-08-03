package jace.hardware;

import static jace.hardware.CardSSC.ACIA_Command;
import static jace.hardware.CardSSC.ACIA_Control;
import static jace.hardware.CardSSC.ACIA_Data;
import static jace.hardware.CardSSC.ACIA_Status;
import static jace.hardware.CardSSC.SW1;
import static jace.hardware.CardSSC.SW2_CTS;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.RAMEvent;
import jace.core.RAMEvent.SCOPE;
import jace.core.RAMEvent.TYPE;
import jace.core.RAMEvent.VALUE;

public class CardSSCTest extends AbstractFXTest {

    private CardSSC cardSSC;

    @Before
    public void setUp() {
        cardSSC = new CardSSC();
    }

    @Test
    public void testGetDeviceName() {
        assertEquals("Super Serial Card", cardSSC.getDeviceName());
    }

    @Test
    public void testSetSlot() {
        cardSSC.setSlot(1);
        // assertEquals("Slot 1", cardSSC.activityIndicator.getText());
    }

    @Test
    public void testReset() {
        cardSSC.reset();
    }

    @Test
    public void testIOAccess() {
        RAMEvent event = new RAMEvent(TYPE.READ_DATA, SCOPE.ANY, VALUE.ANY, 0, 0, 0);
        int[] registers = {SW1, SW2_CTS, ACIA_Data, ACIA_Control, ACIA_Status, ACIA_Command};
        for (int register : registers) {
            cardSSC.handleIOAccess(register, TYPE.READ_DATA, 0, event);
            cardSSC.handleIOAccess(register, TYPE.WRITE, 0, event);
        }
    }
}