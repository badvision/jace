/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jace;

import jace.core.Utility;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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
            Emulator.computer.warmStart();
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

}
