/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jace;

import jace.core.Computer;
import jace.core.Video;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;

/**
 *
 * @author blurry
 */
public class JaceUIController {
    @FXML
    private URL location;

    @FXML
    private AnchorPane rootPane;

    @FXML
    private Region notificationRegion;

    @FXML
    private ImageView appleScreen;

    @FXML
    void initialize() {
        assert rootPane != null : "fx:id=\"rootPane\" was not injected: check your FXML file 'JaceUI.fxml'.";
        assert notificationRegion != null : "fx:id=\"notificationRegion\" was not injected: check your FXML file 'JaceUI.fxml'.";
        assert appleScreen != null : "fx:id=\"appleScreen\" was not injected: check your FXML file 'JaceUI.fxml'.";
        appleScreen.fitWidthProperty().bind(rootPane.widthProperty());
        appleScreen.fitHeightProperty().bind(rootPane.heightProperty());
    }
    
    public void connectComputer(Computer computer) {
        appleScreen.setImage(computer.getVideo().getFrameBuffer());
        EventHandler<KeyEvent> keyboardHandler = computer.getKeyboard().getListener();
        rootPane.setFocusTraversable(true);
        rootPane.setOnKeyPressed(keyboardHandler);
        rootPane.setOnKeyReleased(keyboardHandler);
//        rootPane.onKeyTypedProperty().setValue(keyboardHandler);
        rootPane.requestFocus();
    }
}
