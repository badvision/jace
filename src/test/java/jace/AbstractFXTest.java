package jace;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import jace.core.Utility;
import javafx.application.Platform;

/**
 * Abstract base class for Jace tests that require JavaFX support.
 * Extends AbstractJaceTest with JavaFX initialization and cleanup.
 */
public abstract class AbstractFXTest extends AbstractJaceTest {
    
    // Flag to track if JavaFX runtime has been initialized
    protected static boolean fxInitialized = false;
    
    /**
     * Initialize the JavaFX runtime before any tests in the class run.
     * This is only done once, even if multiple test classes extend this class.
     * In test mode, JavaFX initialization is skipped.
     */
    @BeforeClass
    public static void initJavaFX() {
        // Call the parent setup first
        commonSetupClass();
        
        // Skip JavaFX initialization in test mode
        if (Utility.isTestMode()) {
            System.out.println("Skipping JavaFX initialization in test mode");
            return;
        }
        
        // Then initialize JavaFX if needed
        if (!fxInitialized) {
            try {
                fxInitialized = true;
                Platform.startup(() -> {});
                System.out.println("JavaFX initialized successfully");
            } catch (Exception e) {
                System.err.println("Failed to initialize JavaFX: " + e.getMessage());
                // Continue without JavaFX in test mode
                Utility.setTestMode(true);
            }
        }
    }
    
    /**
     * Ensure proper setup before each test
     */
    @Before
    @Override
    public void commonSetup() {
        super.commonSetup();
        
        // Skip JavaFX initialization in test mode
        if (Utility.isTestMode()) {
            return;
        }
        
        // Ensure JavaFX is initialized
        if (!fxInitialized) {
            try {
                fxInitialized = true;
                Platform.startup(() -> {});
            } catch (Exception e) {
                System.err.println("Failed to initialize JavaFX: " + e.getMessage());
                // Continue without JavaFX in test mode
                Utility.setTestMode(true);
            }
        }
    }
    
    /**
     * Clean up the JavaFX runtime after all tests in the class have run.
     */
    @AfterClass
    public static void shutdownJavaFX() {
        // First call parent teardown
        commonTeardownClass();
        
        // Then ensure emulator is aborted
        Emulator.abort();
        
        // Only exit Platform if it was initialized
        if (fxInitialized && !Utility.isTestMode()) {
            try {
                Platform.exit();
            } catch (Exception e) {
                System.err.println("Error during JavaFX shutdown: " + e.getMessage());
            }
        }
    }
}
