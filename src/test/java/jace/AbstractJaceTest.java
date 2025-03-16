package jace;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.SoundMixer;
import jace.core.Utility;

/**
 * Abstract base class for Jace test cases.
 * Provides common setup, teardown, and utility methods for tests.
 */
public abstract class AbstractJaceTest {
    
    // Common test resources
    protected static Computer computer;
    protected static MOS65C02 cpu;
    protected static RAM128k ram;
    
    // Flag to track if setup has been done
    protected static boolean setupComplete = false;
    
    /**
     * Common setup for all test classes.
     * Sets up the emulator in headless mode with the needed components.
     */
    @BeforeClass
    public static void commonSetupClass() {
        try {
            // Configure the test environment
            configureTestEnvironment();
            
            // Abort any running emulator
            Emulator.abort();
            
            // Reset the emulator
            Emulator.resetForTesting();
            
            // Use the helper method for consistent emulator setup
            JaceApplication.setupForTesting(true);
            
            // Get reference to computer and components
            computer = Emulator.withComputer(c->c, null);
            cpu = (MOS65C02) computer.getCpu();
            ram = (RAM128k) computer.getMemory();
            setupComplete = true;
            
            System.out.println("Setup complete for test class: " + 
                    Thread.currentThread().getStackTrace()[2].getClassName());
        } catch (Exception e) {
            System.err.println("Error in test class setup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Configure the test environment to ensure headless mode
     * and prevent JavaFX initialization.
     */
    private static void configureTestEnvironment() {
        // Set system properties to disable JavaFX
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        
        // Set test mode flag
        System.setProperty("jace.test", "true");
        Utility.setTestMode(true);
        Utility.setHeadlessMode(true);
        Utility.setVideoEnabled(false);
        
        // Disable sound
        SoundMixer.MUTE = true;
        
        System.out.println("Test environment configured for headless mode");
    }
    
    /**
     * Common teardown for all test classes.
     * Ensures the emulator is properly cleaned up after all tests in the class.
     */
    @AfterClass
    public static void commonTeardownClass() {
        try {
            // Shut down emulator gracefully
            Emulator.abort();
            
            // Reset static state
            computer = null;
            cpu = null;
            ram = null;
            setupComplete = false;
            
            // Force garbage collection
            System.gc();
            
            System.out.println("Teardown complete for test class: " + 
                    Thread.currentThread().getStackTrace()[2].getClassName());
        } catch (Exception e) {
            System.err.println("Error in test class teardown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reset the emulator state before each test.
     * This ensures a clean environment for each test.
     */
    @Before
    public void commonSetup() {
        try {
            // Make sure we have a properly configured class-level setup
            if (!setupComplete) {
                commonSetupClass();
            }
            
            // Suspend computer operations during setup
            Emulator.withComputer(c -> c.getMotherboard().suspend());
            
            // Reset the computer to a clean state
            computer.warmStart();
            
            // Ensure we have valid references
            cpu = (MOS65C02) computer.getCpu();
            ram = (RAM128k) computer.getMemory();
            
            // Set up mock video to prevent NPEs when accessing the floating bus
            TestUtils.setupMockVideo();
            
            // Reset CPU and memory to known state
            cpu.clearState();
            cpu.reset();
            cpu.resume();
            ram.resetState();
            
            // Zero out memory for consistent test state
            if (ram instanceof TestUtils.FakeRAM) {
                TestUtils.clearFakeRam(ram);
            } else {
                // If not using fake RAM, at least zero out important areas
                for (int i = 0; i < 0x10000; i++) {
                    ram.write(i, (byte) 0, false, false);
                }
            }
            
            // Make sure emulator is in a valid but suspended state
            Emulator.withComputer(c -> c.getMotherboard().suspend());
        } catch (Exception e) {
            System.err.println("Error in test setup: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Test setup failed", e);
        }
    }
    
    /**
     * Cleanup after each test.
     * This ensures each test leaves the emulator in a clean state.
     */
    @After
    public void commonTeardown() {
        try {
            // Ensure the computer is suspended
            Emulator.withComputer(c -> {
                if (c.getMotherboard().isRunning()) {
                    c.getMotherboard().suspend();
                }
            });
            
            // Reset CPU state
            resetCPU();
            
            // Clear all RAM
            clearRAM();
        } catch (Exception e) {
            System.err.println("Error in test teardown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Resets CPU to a known state.
     */
    protected void resetCPU() {
        cpu.clearState();
        cpu.reset();
        cpu.resume();
    }
    
    /**
     * Clear the RAM and reset its state.
     */
    protected void clearRAM() {
        if (ram instanceof TestUtils.FakeRAM) {
            TestUtils.clearFakeRam((RAM) ram);
        }
        ram.resetState();
    }
    
    /**
     * Sets up a fake RAM for testing purposes.
     * This can be used when you need to isolate tests from real memory.
     */
    protected void setupFakeRAM() {
        // Create a new FakeRAM instance
        RAM fakeRam = TestUtils.initFakeRam();
        ram = (RAM128k) fakeRam;
        
        // Update references to use the fake RAM
        computer.setMemory(ram);
        cpu.setMemory(ram);
        
        // Zero out memory
        TestUtils.clearFakeRam(ram);
        
        // Perform a warm start with the new RAM
        cpu.reset();
        computer.reconfigure();
    }
} 