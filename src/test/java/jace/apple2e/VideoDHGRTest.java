package jace.apple2e;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import javafx.scene.image.WritableImage;

// This is mostly to provide execution coverage to catch null pointer or index out of range exceptions
public class VideoDHGRTest extends AbstractFXTest {
    WritableImage image = new WritableImage(560, 192);

    private VideoDHGR video;

    @Before
    public void setUp() {        
        video = new VideoDHGR();
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
        // Run through all possible combinations of soft switches to ensure the correct Y offset is returned each time
        SoftSwitches[] switches = {SoftSwitches.HIRES, SoftSwitches.TEXT, SoftSwitches.PAGE2, SoftSwitches._80COL, SoftSwitches.DHIRES, SoftSwitches.MIXED};
        for (int i=0; i < Math.pow(2.0, switches.length); i++) {
            String state = "";
            for (int j=0; j < switches.length; j++) {
                switches[j].getSwitch().setState((i & (1 << j)) != 0);
                state += switches[j].getSwitch().getName() + "=" + (switches[j].getSwitch().getState() ? "1" : "0") + " ";
            }
            video.configureVideoMode();
            int address = video.getCurrentWriter().getYOffset(0);
            int expected = SoftSwitches.TEXT.isOn() || SoftSwitches.HIRES.isOff() ? (SoftSwitches.PAGE2.isOn() ? 0x0800 : 0x0400) 
            : (SoftSwitches.PAGE2.isOn() ? 0x04000 : 0x02000);
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