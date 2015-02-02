/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jace;

import javafx.scene.canvas.Canvas;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.Region;

/**
 *
 * @author blurry
 */
public class JaceUIController {
    @FXML
    private ResourceBundle resources;

    @FXML
    private Canvas displayCanvas;

    @FXML
    private Region notificationRegion;

    
    @FXML
    public void initialize() {
        assert displayCanvas != null : "fx:id=\"displayCanvas\" was not injected: check your FXML file 'JaceUI.fxml'.";
        assert notificationRegion != null : "fx:id=\"notificationRegion\" was not injected: check your FXML file 'JaceUI.fxml'.";
    }

    
}
