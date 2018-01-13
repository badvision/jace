/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jace;

import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.Utility;
import jace.ui.MetacheatUI;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 *
 * @author blurry
 */
public class JaceApplication extends Application {

    static JaceApplication singleton;

    public Stage primaryStage;
    JaceUIController controller;

    static boolean romStarted = false;

    @Override
    public void start(Stage stage) throws Exception {
        singleton = this;
        primaryStage = stage;
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/JaceUI.fxml"));
        fxmlLoader.setResources(null);
        try {
            AnchorPane node = (AnchorPane) fxmlLoader.load();
            controller = fxmlLoader.getController();
            controller.initialize();
            Scene s = new Scene(node);
            primaryStage.setScene(s);
            primaryStage.setTitle("Jace");
            EmulatorUILogic.scaleIntegerRatio();
            Utility.loadIcon("woz_figure.gif").ifPresent(primaryStage.getIcons()::add);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        primaryStage.show();
        new Thread(() -> {
            new Emulator(getParameters().getRaw());
            reconnectUIHooks();
            EmulatorUILogic.scaleIntegerRatio();
            while (Emulator.computer.getVideo() == null || Emulator.computer.getVideo().getFrameBuffer() == null) {
                Thread.yield();
            }
            bootWatchdog();
        }).start();
        primaryStage.setOnCloseRequest(event -> {
            Emulator.computer.deactivate();
            Platform.exit();
            System.exit(0);
        });
    }

    public void reconnectUIHooks() {
        controller.connectComputer(Emulator.computer, primaryStage);        
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
        romStarted = false;
        RAMListener startListener = Emulator.computer.getMemory().
                observe(RAMEvent.TYPE.EXECUTE, 0x0FA62, (e) -> {
                    romStarted = true;
                });
        Emulator.computer.coldStart();
        try {
            Thread.sleep(250);
            if (!romStarted) {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "Boot not detected, performing a cold start");
                Emulator.computer.coldStart();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JaceApplication.class.getName()).log(Level.SEVERE, null, ex);
        }
        Emulator.computer.getMemory().removeListener(startListener);
    }

}
