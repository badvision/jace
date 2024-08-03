package jace.hardware.mockingboard;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

public class PSGTest {

    private PSG psg;

    @Before
    public void setUp() {
        psg = new PSG(0, 100, 44100, "name", 255);
    }

    @Test
    public void setControl_InactiveCommand_NoAction() {
        psg.setControl(0); // Set control to inactive
        // Assert that no action is taken
    }

    @Test
    public void setControl_LatchCommand_SelectedRegUpdated() {
        psg.setControl(1); // Set control to latch
        // Assert that selectedReg is updated correctly
        // Add your assertions here
    }

    @Test
    public void setControl_ReadCommand_BusUpdated() {
        psg.setControl(2); // Set control to read
        // Assert that bus is updated correctly
        // Add your assertions here
    }

    @Test
    public void setControl_WriteCommand_RegUpdated() {
        psg.setControl(3); // Set control to write
        // Assert that the corresponding register is updated correctly
        // Add your assertions here
    }

    @Test
    public void updateTest() {
        AtomicInteger out = new AtomicInteger();
        psg.update(out, false, out, false, out, false);
        psg.update(out, true, out, true, out, true);
    }
}