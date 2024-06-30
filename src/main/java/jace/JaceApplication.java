package jace;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.apple2e.MOS65C02;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.Utility;
import jace.ui.MetacheatUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 *  
 * @author blurry
 */
public class JaceApplication extends Application {

    static JaceApplication singleton;

    public Stage primaryStage;
    public JaceUIController controller;

    static AtomicBoolean romStarted = new AtomicBoolean(false);
    int watchdogDelay = 500;

    @Override
    public void start(Stage stage) throws Exception {
        singleton = this;
        primaryStage = stage;
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/JaceUI.fxml"));
        fxmlLoader.setResources(null);
        try {
            AnchorPane node = fxmlLoader.load();
            controller = fxmlLoader.getController();
            controller.initialize();
            Scene s = new Scene(node);
            s.setFill(Color.BLACK);
            primaryStage.setScene(s);
            primaryStage.titleProperty().set("jace");
            Utility.loadIcon("jace_icon.png").ifPresent(icon -> {
                primaryStage.getIcons().add(icon);
            });
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        primaryStage.show();
        new Thread(() -> {
            Emulator.getInstance(getParameters().getRaw());
            reconnectUIHooks();
            EmulatorUILogic.scaleIntegerRatio();
            AtomicBoolean waitingForVideo = new AtomicBoolean(true);
            while (waitingForVideo.get()) {
                Emulator.withVideo(v -> {
                    if (v.getFrameBuffer() != null) {
                        waitingForVideo.set(false);
                    }
                });
                Thread.onSpinWait();
            }
            bootWatchdog();
        }).start();
        primaryStage.setOnCloseRequest(event -> {
            Emulator.withComputer(Computer::deactivate);
            Platform.exit();
            System.exit(0);
        });
    }

    public void reconnectUIHooks() {
        controller.connectComputer(primaryStage);
    }

    public static JaceApplication getApplication() {
        return singleton;
    }

    Stage cheatStage;
    private MetacheatUI cheatController;

    public MetacheatUI showMetacheat() {
        if (cheatController == null) {
            cheatStage = new Stage(StageStyle.DECORATED);
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Metacheat.fxml"));
            fxmlLoader.setResources(null);
            try {
                VBox node = fxmlLoader.load();
                cheatController = fxmlLoader.getController();
                Scene s = new Scene(node);
                cheatStage.setScene(s);
                cheatStage.setTitle("Jace: MetaCheat");
                Utility.loadIcon("woz_figure.gif").ifPresent(cheatStage.getIcons()::add);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }

        }
        cheatStage.show();
        return cheatController;
    }

    public void closeMetacheat() {
        if (cheatStage != null) {
            cheatStage.close();
        }
        if (cheatController != null) {
            cheatController.detach();
            cheatController = null;
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Start the computer and make sure it runs through the expected rom routine
     * for cold boot
     */
    private void bootWatchdog() {
        Emulator.withComputer(c -> {
            // We know the game started properly when it runs the decompressor the first time
            int watchAddress = 0x0ff3a;
            new Thread(()->{
                // Logger.getLogger(getClass().getName()).log(Level.WARNING, "Booting with watchdog");
                final RAMListener startListener = c.getMemory().observeOnce("Boot watchdog", RAMEvent.TYPE.EXECUTE, watchAddress, (e) -> {
                    // Logger.getLogger(getClass().getName()).log(Level.WARNING, "Boot was detected, watchdog terminated.");
                    romStarted.set(true);
                });
                romStarted.set(false);
                c.coldStart();
                try {
                    // Logger.getLogger(getClass().getName()).log(Level.WARNING, "Watchdog: waiting " + watchdogDelay + "ms for boot to start.");
                    Thread.sleep(watchdogDelay);
                    watchdogDelay = 500;
                    if (!romStarted.get() || !c.isRunning() || c.getCpu().getProgramCounter() == MOS65C02.FASTBOOT || c.getCpu().getProgramCounter() == 0) {
                        Logger.getLogger(getClass().getName()).log(Level.WARNING, "Boot not detected, performing a cold start");
                        Logger.getLogger(getClass().getName()).log(Level.WARNING, "Old PC: {0}", Integer.toHexString(c.getCpu().getProgramCounter()));
                        resetEmulator();
                        bootWatchdog();
                    } else {
                        startListener.unregister();
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(JaceApplication.class.getName()).log(Level.SEVERE, null, ex);
                }
            }).start();
        });
    }

    public void resetEmulator() {
        // Reset the emulator memory and restart
        Emulator.withComputer(c -> {
            c.getMemory().resetState();
            c.warmStart();
        });
    }
}
