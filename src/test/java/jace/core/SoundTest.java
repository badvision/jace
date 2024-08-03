package jace.core;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.SoundMixer.SoundBuffer;
import jace.core.SoundMixer.SoundError;

public class SoundTest extends AbstractFXTest {
    @Before
    public void setUp() {
        System.out.println("Init sound");
        Utility.setHeadlessMode(false);
        SoundMixer.initSound();
    }

    @After
    public void tearDown() {
        Utility.setHeadlessMode(true);
    }

    @Test
    //(Only use this to ensure the sound engine produces audible output, it's otherwise annoying to hear all the time)
    public void soundGenerationTest() throws SoundError {
        try {
        System.out.println("Performing sound test...");
        SoundMixer mixer = new SoundMixer();
        System.out.println("Attach mixer");
        mixer.attach();
        System.out.println("Allocate buffer");
        SoundBuffer buffer = SoundMixer.createBuffer(false);
        System.out.println("Generate sound");
        // for (int i = 0; i < 100000; i++) {
        for (int i = 0; i < 100; i++) {
            // Gerate a sin wave with a frequency sweep so we can tell if the buffer is being fully processed
            double x = Math.sin(i*i * 0.0001);
            buffer.playSample((short) (Short.MAX_VALUE * x));
        }
        System.out.println("Closing buffer");
        buffer.shutdown();
        System.out.println("Deactivating sound");
        mixer.detach();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

     @Test
    // Commented out because it's annoying to hear all the time, but it worked without issues
    public void mixerTortureTest() throws SoundError, InterruptedException, ExecutionException {
        System.out.println("Performing speaker tick test...");
        SoundMixer.initSound();
        System.out.println("Create mixer");
        SoundMixer mixer = new SoundMixer();
        System.out.println("Attach mixer");
        mixer.attach();
        // We want to create and destroy lots of buffers to make sure we don't have any memory leaks
        // for (int i = 0; i < 10000; i++) {
        for (int i = 0; i < 1000; i++) {
                // Print status every 1000 iterations
            if (i % 1000 == 0) {
                System.out.println("Iteration %d".formatted(i));
            }
            SoundBuffer buffer = SoundMixer.createBuffer(false);
            for (int j = 0; j < SoundMixer.BUFFER_SIZE*2; j++) {
                // Gerate a sin wave with a frequency sweep so we can tell if the buffer is being fully processed
                double x = Math.sin(j*j * 0.0001);
                buffer.playSample((short) (Short.MAX_VALUE * x));
            }
            buffer.flush();
            buffer.shutdown();
        }
        // Assert buffers are empty
        assertEquals("All buffers should be empty", 0, mixer.getActiveBuffers());
        System.out.println("Deactivating sound");
        mixer.detach();
    }
}
