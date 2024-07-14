package jace.hardware;

import static jace.hardware.PassportMidiInterface.ACIA_RECV;
import static jace.hardware.PassportMidiInterface.ACIA_STATUS;
import static jace.hardware.PassportMidiInterface.TIMER1_LSB;
import static jace.hardware.PassportMidiInterface.TIMER1_MSB;
import static jace.hardware.PassportMidiInterface.TIMER2_LSB;
import static jace.hardware.PassportMidiInterface.TIMER2_MSB;
import static jace.hardware.PassportMidiInterface.TIMER3_LSB;
import static jace.hardware.PassportMidiInterface.TIMER3_MSB;
import static jace.hardware.PassportMidiInterface.TIMER_CONTROL_1;
import static jace.hardware.PassportMidiInterface.TIMER_CONTROL_2;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.RAMEvent;
import jace.core.RAMEvent.SCOPE;
import jace.core.RAMEvent.TYPE;
import jace.core.RAMEvent.VALUE;


public class PassportMidiInterfaceTest extends AbstractFXTest {
    PassportMidiInterface midi = new PassportMidiInterface();

    @Test
    public void testDeviceSelection() {
        assertNotNull(PassportMidiInterface.preferredMidiDevice.getSelections());
        assertNotEquals(0, PassportMidiInterface.preferredMidiDevice.getSelections().size());
    }

    @Test
    public void testIOAccess() {
        RAMEvent event = new RAMEvent(TYPE.READ_DATA, SCOPE.ANY, VALUE.ANY, 0, 0, 0);
        int[] registers = {ACIA_STATUS, ACIA_RECV, TIMER_CONTROL_1, TIMER_CONTROL_2, TIMER1_LSB, TIMER1_MSB, TIMER2_LSB, TIMER2_MSB, TIMER3_LSB, TIMER3_MSB};
        for (int register : registers) {
            midi.handleIOAccess(register, TYPE.READ_DATA, 0, event);
            midi.handleIOAccess(register, TYPE.WRITE, 0, event);
        }
    }

}
