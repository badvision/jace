package jace.hardware.mockingboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.SoundMixer;
import jace.core.SoundMixer.SoundBuffer;
import jace.core.SoundMixer.SoundError;
import jace.core.Utility;

public class VotraxTest extends AbstractFXTest {
    
    // Flag to track if we can run sound tests
    private boolean soundAvailable = false;
    
    @Before
    public void setUp() {
        System.out.println("Init sound for Votrax test");
        // Mute sound during tests to avoid unwanted audio output
        SoundMixer.MUTE = true;
        
        // We attempt to initialize sound with a timeout
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> {
            try {
                SoundMixer.initSound();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        
        try {
            // Wait up to 2 seconds for sound initialization
            future.get(2, TimeUnit.SECONDS);
            
            // Regardless of initialization success, we need to enable playback for tests
            SoundMixer.PLAYBACK_ENABLED = true;
            
            // Create a test sound buffer to verify successful initialization
            try {
                SoundBuffer testBuffer = SoundMixer.createBuffer(true);
                if (testBuffer != null) {
                    testBuffer.shutdown();
                    soundAvailable = true;  // Sound is available if we can create a buffer
                } else {
                    soundAvailable = false;
                }
            } catch (Exception e) {
                System.out.println("Could not create sound buffer for Votrax test: " + e.getMessage());
                soundAvailable = false;
            }
        } catch (Exception e) {
            System.out.println("Sound initialization failed for Votrax test: " + e.getMessage());
            SoundMixer.PLAYBACK_ENABLED = true;  // Still enable playback for tests
            soundAvailable = false;
        } finally {
            executor.shutdownNow();
        }
        
        System.out.println("Sound available for Votrax test: " + soundAvailable);
    }
    
    @After
    public void tearDown() {
        // Always restore headless mode for other tests
        Utility.setHeadlessMode(true);
        SoundMixer.MUTE = false;
        
        // Clean up sound if it was initialized
        if (soundAvailable) {
            try {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<?> future = executor.submit(() -> {
                    try {
                        SoundMixer.performSoundOperation(() -> {
                            SoundMixer.PLAYBACK_ENABLED = false;
                        }, "Disable sound after Votrax testing", true);
                    } catch (Exception e) {
                        System.out.println("Sound cleanup error in Votrax test: " + e.getMessage());
                    }
                });
                
                // Only wait 2 seconds for cleanup
                future.get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Error during Votrax sound cleanup: " + e.getMessage());
            }
        }
    }

    @Test
    public void testVoicedSource() {
        // This test is empty - implementation placeholder
    }

    @Test
    public void testFricativeSource() {
        // This test is empty - implementation placeholder
    }

    @Test
    public void testMixer() throws Exception {
        // Use assumeTrue to properly mark test as skipped if sound isn't available
        assumeTrue("Sound system must be available for this test", soundAvailable);

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
