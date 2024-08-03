package jace.hardware.mockingboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.SoundMixer;
import jace.core.Utility;

public class VotraxTest extends AbstractFXTest {
    @Before
    public void setUp() {
        System.out.println("Init sound");
        Utility.setHeadlessMode(false);
        SoundMixer.PLAYBACK_ENABLED = true;
        SoundMixer.initSound();
    }

    @Test
    public void testVoicedSource() {

    }

    @Test
    public void testFricativeSource() {

    }

    @Test
    public void testMixer() throws Exception {

        Votrax vo = new Votrax();
        vo.resume();
        System.out.println("Sound: ON for 2sec");
        Thread.sleep(2000);
        boolean stillRunning = vo.isRunning();
        vo.suspend();
        System.out.println("Sound: OFF");
        boolean overrun = vo.isRunning();

        assertTrue("Playback was interrupted early", stillRunning);
        assertFalse("Playback didn't stop when suspended", overrun);
    }

    
}
