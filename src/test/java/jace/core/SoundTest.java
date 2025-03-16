package jace.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.SoundMixer.SoundBuffer;
import jace.core.SoundMixer.SoundError;

public class SoundTest extends AbstractFXTest {
    
    // Flag to track if we can run sound tests
    private boolean soundAvailable = false;
    
    @Before
    public void setUp() {
        System.out.println("Init sound");
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
                System.out.println("Could not create sound buffer: " + e.getMessage());
                soundAvailable = false;
            }
        } catch (Exception e) {
            System.out.println("Sound initialization failed: " + e.getMessage());
            SoundMixer.PLAYBACK_ENABLED = true;  // Still enable playback for tests
            soundAvailable = false;
        } finally {
            executor.shutdownNow();
        }
        
        System.out.println("Sound available: " + soundAvailable);
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
                        }, "Disable sound after testing", true);
                    } catch (Exception e) {
                        System.out.println("Sound cleanup error: " + e.getMessage());
                    }
                });
                
                // Only wait 2 seconds for cleanup
                future.get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Error during sound cleanup: " + e.getMessage());
            }
        }
    }

    @Test
    public void soundGenerationTest() throws SoundError {
        // Use assumeTrue to properly mark test as skipped if sound isn't available
        assumeTrue("Sound system must be available for this test", soundAvailable);
        
        try {
            System.out.println("Performing sound test...");
            SoundMixer mixer = new SoundMixer();
            System.out.println("Attach mixer");
            mixer.attach();
            System.out.println("Allocate buffer");
            SoundBuffer buffer = SoundMixer.createBuffer(false);
            System.out.println("Generate sound");
            for (int i = 0; i < 100; i++) {
                // Generate a sin wave with a frequency sweep
                double x = Math.sin(i*i * 0.0001);
                buffer.playSample((short) (Short.MAX_VALUE * x));
            }
            System.out.println("Closing buffer");
            buffer.shutdown();
            System.out.println("Deactivating sound");
            mixer.detach();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Error during sound test: " + e.getMessage());
            throw new SoundError("Sound test failed: " + e.getMessage());
        }
    }

    @Test
    public void mixerTortureTest() throws SoundError, InterruptedException, ExecutionException {
        // Use assumeTrue to properly mark test as skipped if sound isn't available
        assumeTrue("Sound system must be available for this test", soundAvailable);
        
        System.out.println("Performing speaker tick test...");
        System.out.println("Create mixer");
        SoundMixer mixer = new SoundMixer();
        System.out.println("Attach mixer");
        mixer.attach();
        // Use fewer iterations for testing
        for (int i = 0; i < 5; i++) {
            System.out.println("Iteration " + i);
            SoundBuffer buffer = SoundMixer.createBuffer(false);
            for (int j = 0; j < 100; j++) {
                // Generate a sin wave with a frequency sweep
                double x = Math.sin(j*j * 0.0001);
                buffer.playSample((short) (Short.MAX_VALUE * x));
            }
            buffer.flush();
            buffer.shutdown();
            
            // Wait a short time to ensure the buffer is properly cleaned up
            Thread.sleep(50);
        }
        
        // Wait for any remaining buffers to be cleaned up
        for (int i = 0; i < 10 && mixer.getActiveBuffers() > 0; i++) {
            System.out.println("Waiting for buffers to be cleaned up: " + mixer.getActiveBuffers() + " remaining");
            Thread.sleep(200);
        }
        
        // Assert buffers are empty
        assertEquals("All buffers should be empty", 0, mixer.getActiveBuffers());
        System.out.println("Deactivating sound");
        mixer.detach();
    }
}
