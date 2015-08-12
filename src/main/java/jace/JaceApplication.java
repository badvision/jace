/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jace;

import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.Utility;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

/**
 *
 * @author blurry
 */
public class JaceApplication extends Application {

    static JaceApplication singleton;
    Stage primaryStage;
    JaceUIController controller;

    static boolean romStarted = false;
    static RAMListener startListener = new RAMListener(RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
        @Override
        protected void doConfig() {
            setScopeStart(0x0FA62);
        }

        @Override
        protected void doEvent(RAMEvent e) {
            romStarted = true;
        }

        @Override
        public boolean isRelevant(RAMEvent e) {
            return super.isRelevant(e);
        }
    };
    
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
            primaryStage.getIcons().add(Utility.loadIcon("woz_figure.gif"));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        primaryStage.show();
        Emulator emulator = new Emulator(getParameters().getRaw());
        javafx.application.Platform.runLater(() -> {
            while (Emulator.computer.getVideo() == null || Emulator.computer.getVideo().getFrameBuffer() == null) {
                Thread.yield();
            }
            controller.connectComputer(Emulator.computer);
            bootWatchdog();
        });
        primaryStage.setOnCloseRequest(event -> {
            Emulator.computer.deactivate();
            Platform.exit();
            System.exit(0);
        });
    }

    public static JaceApplication getApplication() {
        return singleton;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Start the computer and make sure it runs through the expected rom routine for cold boot
     */
    private void bootWatchdog() {
        Emulator.computer.getMemory().addListener(startListener);
        Emulator.computer.coldStart();
        try {
            Thread.sleep(250);
            if (!romStarted) {
                System.out.println("Boot not detected, performing a cold start");
                Emulator.computer.coldStart();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JaceApplication.class.getName()).log(Level.SEVERE, null, ex);
        }
        Emulator.computer.getMemory().removeListener(startListener);
    }

}
