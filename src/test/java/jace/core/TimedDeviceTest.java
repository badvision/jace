package jace.core;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;

import static org.junit.Assert.*;

public class TimedDeviceTest extends AbstractFXTest {

    private TimedDevice timedDevice;
    public int countedTicks = 0;

    @Before
    public void setUp() {
        countedTicks = 0;
        timedDevice = new TimedDevice(true) {

            @Override
            public String getShortName() {
                return "Test";
            }

            @Override
            protected String getDeviceName() {
                return "Test";
            }

            @Override
            public void tick() {
                countedTicks++;
            }
        };
    }

    @Test
    public void testSetSpeedInHz() {
        long newSpeed = 2000000;
        timedDevice.setSpeedInHz(newSpeed);
        assertEquals(newSpeed, timedDevice.getSpeedInHz());
    }

    @Test
    public void testSetSpeedInPercentage() {
        int ratio = 50;
        timedDevice.setSpeedInPercentage(ratio);
        assertEquals(ratio, timedDevice.getSpeedRatio());
    }

    @Test
    public void testMaxSpeed() {
        // Use temp max speed
        timedDevice.setMaxSpeed(false);
        timedDevice.enableTempMaxSpeed();
        assertTrue("Max speed enabled", timedDevice.isMaxSpeed());
        timedDevice.disableTempMaxSpeed();
        assertFalse("Max speed disabled", timedDevice.isMaxSpeed());
        // Run 250 cycles and make sure none were skipped
        timedDevice.setSpeedInHz(1000);
        timedDevice.resume();
        for (int i=0 ; i<250 ; i++) {
            timedDevice.enableTempMaxSpeed();
            timedDevice.doTick();
        }
        assertEquals("250 ticks were counted", 250, countedTicks);
        // Disable temp max speed
        timedDevice.disableTempMaxSpeed();
        countedTicks = 0;
        for (int i=0 ; i<250 ; i++) {
            timedDevice.doTick();
        }
        assertTrue("Should have counted fewer than 250 ticks", countedTicks < 250);
        // Now use max speed
        timedDevice.setMaxSpeed(true);
        countedTicks = 0;
        for (int i=0 ; i<250 ; i++) {
            timedDevice.doTick();
        }
        assertEquals("250 ticks were counted", 250, countedTicks);
    }

    @Test
    public void testReconfigure() {
        timedDevice.reconfigure();
        // Add assertions here
    }

    @Test
    public void testTicks() {
        timedDevice.setSpeedInHz(1000);
        timedDevice.resume();
        long now = System.nanoTime();
        for (countedTicks=0 ; countedTicks<250 ; ) {
            timedDevice.doTick();
        }
        assertEquals("250 ticks were counted", 250, countedTicks);
        long ellapsed = System.nanoTime() - now;
        assertTrue("About 250ms elapsed", ellapsed / 1000000 >= 240);
    }

}