package jace.apple2e;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.TestUtils;
import jace.core.VideoWriter;
import javafx.scene.image.WritableImage;

// This is mostly to provide execution coverage to catch null pointer or index out of range exceptions
public class VideoDHGRTest extends AbstractFXTest {
    WritableImage image = new WritableImage(560, 192);

    private VideoDHGR video;

    @Before
    public void setUp() {
        // These tests set video softswitches by hand and then assert on the resulting
        // Y offset, so no emulated code may be executing concurrently -- a live
        // motherboard worker thread runs the //e ROM, which flips TEXT/PAGE2/HIRES
        // underneath the assertion.
        TestUtils.quiesceEmulator();

        // Ensure we have a properly configured mock DHGR video
        TestUtils.setupMockVideoDHGR();
        
        // Get the current video instance (which is now our MockVideoDHGR)
        video = (VideoDHGR) jace.Emulator.withComputer(c -> c.getVideo(), null);
    }

    @Test
    public void testInitHgrDhgrTables() {
        // Test the initialization of HGR_TO_DHGR and HGR_TO_DHGR_BW tables
        assertNotNull(video.HGR_TO_DHGR);
        assertNotNull(video.HGR_TO_DHGR_BW);
        // Add more assertions here
    }

    @Test
    public void testInitCharMap() {
        // Test the initialization of CHAR_MAP1, CHAR_MAP2, and CHAR_MAP3 arrays
        assertNotNull(video.CHAR_MAP1);
        assertNotNull(video.CHAR_MAP2);
        assertNotNull(video.CHAR_MAP3);
        // Add more assertions here
    }

    private void writeToScreen() {
        video.getCurrentWriter().displayByte(image, 0, 0, 0, 0);
        video.getCurrentWriter().displayByte(image, 0, 4, 0, 0);
        video.getCurrentWriter().displayByte(image, 0, 190, 0, 0);
        video.getCurrentWriter().displayByte(image, -1, 0, 0, 0);
        video.getCurrentWriter().actualWriter().displayByte(image, 0, 0, 0, 0);
    }

    @Test
    public void testGetYOffset() {
        // Make sure _80STORE is OFF so PAGE2 works correctly
        SoftSwitches._80STORE.getSwitch().setState(false);
        
        SoftSwitches[] switches = {SoftSwitches.HIRES, SoftSwitches.TEXT, SoftSwitches.PAGE2, SoftSwitches._80COL, SoftSwitches.DHIRES, SoftSwitches.MIXED};
        for (int i=0; i < Math.pow(2.0, switches.length); i++) {
            String state = "";
            for (int j=0; j < switches.length; j++) {
                switches[j].getSwitch().setState((i & (1 << j)) != 0);
                state += switches[j].getSwitch().getName() + "=" + (switches[j].getSwitch().getState() ? "1" : "0") + " ";
            }
            video.configureVideoMode();
            int address = video.getCurrentWriter().getYOffset(0);
            
            // Calculate expected address based on actual video mode logic
            boolean page2 = SoftSwitches.PAGE2.isOn() && SoftSwitches._80STORE.isOff();
            
            int expected;
            if (SoftSwitches.TEXT.isOn()) {
                // Text mode (including 80-column text)
                expected = page2 ? 0x0800 : 0x0400;
            } else if (SoftSwitches.HIRES.isOff()) {
                // Lores mode (including double-lores when 80COL is ON)
                expected = page2 ? 0x0800 : 0x0400;
            } else {
                // Hires mode (including double-hires when 80COL and DHIRES are ON)
                expected = page2 ? 0x04000 : 0x02000;
            }
            
            // To help debug the specific failure cases
            if (expected != address) {
                System.out.println("Failed case: " + state);
                System.out.println("Expected: " + expected + ", Actual: " + address);
                System.out.println("Current Writer: " + video.getCurrentWriter().getClass().getName());
            }
            
            assertEquals("Address for mode not correct: " + state, expected, address);
        }
    }

    @Test
    public void testDisplayByte() {
        // Run through all possible combinations of soft switches to ensure the video writer executes without error
        SoftSwitches[] switches = {SoftSwitches.HIRES, SoftSwitches.TEXT, SoftSwitches.PAGE2, SoftSwitches._80COL, SoftSwitches.DHIRES, SoftSwitches.MIXED};
        for (int i=0; i < Math.pow(2.0, switches.length); i++) {
            for (int j=0; j < switches.length; j++) {
                switches[j].getSwitch().setState((i & (1 << j)) != 0);
            }
            video.configureVideoMode();
            writeToScreen();
        }
    }

    // Add more test cases for other methods in the VideoDHGR class

}