package jace.config;

import jace.Emulator;
import jace.JaceApplication;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;

public class ConfigurationUIController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private VBox settingsVbox;

    @FXML
    private SplitPane splitPane;

    @FXML
    private ScrollPane settingsScroll;

    @FXML
    private TreeView<?> deviceTree;

    @FXML
    private ScrollPane treeScroll;

    @FXML
    void reloadConfig(ActionEvent event) {

    }

    @FXML
    void saveConfig(ActionEvent event) {

    }

    @FXML
    void applyConfig(ActionEvent event) {

    }

    @FXML
    void cancelConfig(ActionEvent event) {

    }

    @FXML
    public void initialize() {
        assert settingsVbox != null : "fx:id=\"settingsVbox\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert splitPane != null : "fx:id=\"splitPane\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert settingsScroll != null : "fx:id=\"settingsScroll\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert deviceTree != null : "fx:id=\"deviceTree\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert treeScroll != null : "fx:id=\"treeScroll\" was not injected: check your FXML file 'Configuration.fxml'.";
        deviceTree.setRoot(Configuration.BASE);
    }
}
