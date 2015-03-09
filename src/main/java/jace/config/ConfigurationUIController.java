package jace.config;

import jace.config.Configuration.ConfigNode;
import java.io.Serializable;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
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
    private TreeView<ConfigNode> deviceTree;

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
        deviceTree.getSelectionModel().selectedItemProperty().addListener(this::selectionChanged);
        deviceTree.maxWidthProperty().bind(treeScroll.widthProperty());
    }

    private void selectionChanged(
            ObservableValue<? extends TreeItem<ConfigNode>> observable, 
            TreeItem<ConfigNode> oldValue, 
            TreeItem<ConfigNode> newValue) {
        clearForm();
        buildForm((ConfigNode) newValue);
    }

    private void clearForm() {
        settingsVbox.getChildren().clear();
    }

    private void buildForm(ConfigNode node) {
        node.hotkeys.forEach((name, values) -> {
            settingsVbox.getChildren().add(buildKeyShortcutRow(node, name, values));
        });
        node.settings.forEach((name, value) -> {
            settingsVbox.getChildren().add(buildSettingRow(node, name, value));
        });
    }
    
    private Node buildSettingRow(ConfigNode node, String settingName, Serializable value) {
        ConfigurableField fieldInfo = Configuration.getConfigurableFieldInfo(node.subject, settingName);
        if (fieldInfo == null) return null;
        HBox row = new HBox();
        Label label = new Label(fieldInfo.name());
        label.getStyleClass().add("setting-label");        
        label.setMinWidth(150.0);
        TextField widget = new TextField(String.valueOf(value));
        label.setLabelFor(widget);
        row.getChildren().add(label);
        row.getChildren().add(widget);
        return row;
    }
    
    private Node buildKeyShortcutRow(ConfigNode node, String actionName, String[] values) {
        InvokableAction actionInfo = Configuration.getInvokableActionInfo(node.subject, actionName);
        if (actionInfo == null) return null;
        HBox row = new HBox();
        Label label = new Label(actionInfo.name());
        label.getStyleClass().add("setting-keyboard-shortcut");
        label.setMinWidth(150.0);
        TextField widget = new TextField(String.valueOf(values));
        label.setLabelFor(widget);
        row.getChildren().add(label);
        row.getChildren().add(widget);
        return row;
    }
}
