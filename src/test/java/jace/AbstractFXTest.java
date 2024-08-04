package jace;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import javafx.application.Platform;

public abstract class AbstractFXTest {
    public static boolean fxInitialized = false;
    @BeforeClass
    public static void initJfxRuntime() {
        if (!fxInitialized) {
            fxInitialized = true;
            Platform.startup(() -> {});
        }
    }

    @AfterClass
    public static void shutdown() {
        Emulator.abort();
        Platform.exit();
    }
}
