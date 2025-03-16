package jace;

import java.util.concurrent.CountDownLatch;

import jace.apple2e.Apple2e;
import jace.core.Utility;

/**
 * A special emulator implementation for testing that avoids JavaFX dependencies.
 * This class provides static methods to initialize the emulator for testing.
 */
public class TestEmulator {
    
    /**
     * Initializes a testing-only emulator.
     * Sets system properties to avoid JavaFX initialization.
     */
    static {
        // Set system properties to disable JavaFX
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("jace.test", "true");
    }
    
    /**
     * Reset the current emulator or create a new one for testing.
     */
    public static void resetForTesting() {
        try {
            // Abort any running emulator
            Emulator.abort();
            
            // Reset the static instance
            Emulator.resetForTesting();
            
            // Configure for test mode
            Utility.setTestMode(true);
            Utility.setHeadlessMode(true);
            Utility.setVideoEnabled(false);
            
            // Configure the emulator for testing
            CountDownLatch latch = new CountDownLatch(1);
            
            try {
                // Create a basic Apple2e computer for testing
                Apple2e computer = new Apple2e();
                
                // Set the computer in the emulator using withComputer
                // Since there's no direct setter, we need to initialize the computer in the emulator instance
                Emulator.getInstance();
                
                // Configure the computer for test mode
                computer.getMotherboard().suspend();
                
                // Indicate setup is complete
                latch.countDown();
            } catch (Exception e) {
                System.err.println("Error setting up test emulator: " + e.getMessage());
                e.printStackTrace();
                latch.countDown();
            }
            
            try {
                // Wait for setup to complete
                latch.await();
            } catch (InterruptedException e) {
                // Ignore
            }
            
            System.out.println("Test emulator setup complete");
        } catch (Exception e) {
            System.err.println("Exception during test emulator reset: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 