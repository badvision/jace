package jace.config;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jace.config.Configuration.ConfigNode;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

public class ConfigurationUIController {
    public static final String DELIMITER = "~!~";

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
    void reloadConfig(MouseEvent event) {
        Configuration.loadSettings();
        resetDeviceTree();
    }

    @FXML
    void saveConfig(MouseEvent event) {
        applyConfig(event);
        Configuration.saveSettings();
    }

    @FXML
    void applyConfig(MouseEvent event) {
        Configuration.applySettings(Configuration.BASE);
        resetDeviceTree();
    }

    @FXML
    void cancelConfig(MouseEvent event) {
        Configuration.buildTree();
        resetDeviceTree();
    }

    @FXML
    public void initialize() {
        assert settingsVbox != null : "fx:id=\"settingsVbox\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert splitPane != null : "fx:id=\"splitPane\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert settingsScroll != null : "fx:id=\"settingsScroll\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert deviceTree != null : "fx:id=\"deviceTree\" was not injected: check your FXML file 'Configuration.fxml'.";
        assert treeScroll != null : "fx:id=\"treeScroll\" was not injected: check your FXML file 'Configuration.fxml'.";
        cancelConfig(null);
        deviceTree.getSelectionModel().selectedItemProperty().addListener(this::selectionChanged);
        deviceTree.maxWidthProperty().bind(treeScroll.widthProperty());
    }
    
    @SuppressWarnings("all")
    private void resetDeviceTree() {
        Set<String> expanded = new HashSet<>();
        String current = getCurrentNodePath();
        getExpandedNodes("", deviceTree.getRoot(), expanded);
        deviceTree.setRoot(Configuration.BASE);
        for (ConfigNode node : Configuration.BASE.getChildren()) {
            String prefix = node.name;
            expanded.add(prefix);
            for (ConfigNode child : node.getChildren()) {
                expanded.add(prefix + DELIMITER + child.toString());
                for (ConfigNode grandchild : node.getChildren()) {
                    expanded.add(prefix + DELIMITER + child.toString() + DELIMITER + grandchild.toString());
                }   
            }
        }
        setExpandedNodes("", deviceTree.getRoot(), expanded);
        setCurrentNodePath(current);
    }

    private void getExpandedNodes(String prefix, TreeItem<ConfigNode> root, Set<String> expanded) {
        if (root == null) return;
        root.getChildren().stream().filter(TreeItem::isExpanded).forEach((item) -> {
            String name = prefix+ item;
            expanded.add(name);
            getExpandedNodes(name+DELIMITER, item, expanded);
        });
    }

    private void setExpandedNodes(String prefix, TreeItem<ConfigNode> root, Set<String> expanded) {
        if (root == null) return;
        root.getChildren().forEach((item) -> {
            String name = prefix+item.toString();
            if (expanded.contains(name)) {
                item.setExpanded(true);
            }
            setExpandedNodes(name+DELIMITER, item, expanded);
        });
    }

    private String getCurrentNodePath() {
        TreeItem<ConfigNode> current = deviceTree.getSelectionModel().getSelectedItem();
        if (current == null) return null;
        String out = current.toString();
        while (current.getParent() != null) {
            out = current.getParent().toString()+DELIMITER+current;
            current = current.getParent();
        }
        return out;
    }
    
    @SuppressWarnings("all")
    private void setCurrentNodePath(String value) {
        if (value == null) return;
        String[] parts = value.split(Pattern.quote(DELIMITER));
        TreeItem<ConfigNode> current = deviceTree.getRoot();
        for (String part : parts) {
            for (TreeItem child : current.getChildren()) {
                if (child.toString().equals(part)) {
                    current = child;
                }
            }
        }
        deviceTree.getSelectionModel().select(current);
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
        if (node == null) {
            return;
        }
        node.hotkeys.forEach((name, values) -> buildKeyShortcutRow(node, name, values).ifPresent(settingsVbox.getChildren()::add));
        node.settings.forEach((name, value) -> settingsVbox.getChildren().add(buildSettingRow(node, name, value)));
    }

    private Node buildSettingRow(ConfigNode node, String settingName, Serializable value) {
        ConfigurableField fieldInfo = Configuration.getConfigurableFieldInfo(node.subject, settingName);
        if (fieldInfo == null) {
            return null;
        }
        HBox row = new HBox();
        row.getStyleClass().add("setting-row");
        Label label = new Label(fieldInfo.name());
        label.getStyleClass().add("setting-label");
        label.setMinWidth(150.0);
        Node widget = buildEditField(node, settingName, value);
        label.setLabelFor(widget);
        row.getChildren().add(label);
        row.getChildren().add(widget);
        return row;
    }

    private Optional<Node> buildKeyShortcutRow(ConfigNode node, String actionName, String[] values) {
        InvokableActionRegistry registry = InvokableActionRegistry.getInstance();
        InvokableAction actionInfo = registry.getInstanceMethodInfo(actionName);
        if (actionInfo == null) {
            actionInfo = registry.getStaticMethodInfo(actionName);
        }
        if (actionInfo == null) {
            return Optional.empty();
        }
        HBox row = new HBox();
        row.getStyleClass().add("setting-row");
        Label label = new Label(actionInfo.name());
        label.getStyleClass().add("setting-keyboard-shortcut");
        label.setMinWidth(150.0);
        String value = String.join(" or ", values);
        Text widget = new Text(value);
        widget.setWrappingWidth(180.0);
        widget.getStyleClass().add("setting-keyboard-value");
        // widget.setOnMouseClicked((event) -> editKeyboardShortcut(node, actionName, widget));
        label.setLabelFor(widget);
        row.getChildren().add(label);
        row.getChildren().add(widget);
        return Optional.of(row);
    }

    // private void editKeyboardShortcut(ConfigNode node, String actionName, Text widget) {
    //     throw new UnsupportedOperationException("Not supported yet.");
    // }    
    
    @SuppressWarnings("all")
    private Node buildEditField(ConfigNode node, String settingName, Serializable value) {
        Field field;
        try {
            field = node.subject.getClass().getField(settingName);
        } catch (NoSuchFieldException | SecurityException ex) {
            return null;
        }
        Class type = field.getType();
        if (type == java.lang.String.class) {
            return buildTextField(node, settingName, value, null);
        } else if (type.isPrimitive()) {
            if (type == Integer.TYPE || type == Short.TYPE || type == Byte.TYPE) {
                return buildTextField(node, settingName, value, "-?[0-9]+");
            } else if (type == Float.TYPE || type == Double.TYPE) {
                return buildTextField(node, settingName, value, "-?[0-9]*(\\.[0-9]+)?");
            } else if (type == Boolean.TYPE) {
                return buildBooleanField(node, settingName, value);
            } else {
                return buildTextField(node, settingName, value, null);
            }
        } else if (type.equals(File.class)) {
            return buildFileField(node, settingName, value);
        } else if (ISelection.class.isAssignableFrom(type)) {
            return buildDynamicSelectComponent(node, settingName, value);
        }
        return null;
    }

    // NOTE: This was written but not tested/used currently.  Test before using!
    private Node buildFileField(ConfigNode node, String settingName, Serializable value) {
        // Create a label that shows the name of the file and lets you select a file when the label is clicked.
        HBox hbox = new HBox();
        Label label = new Label(value == null ? "" : ((File) value).getName()); 
        label.setMinWidth(150.0);
        label.getStyleClass().add("setting-file-label");
        label.setOnMouseClicked((e) -> {
            FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showOpenDialog(label.getScene().getWindow());
            if (file != null) {
                node.setFieldValue(settingName, file);
                label.setText(file.getName());
            }
        });
        hbox.getChildren().add(label);
        // Add a button that lets you clear the file selection.
        Label clearButton = new Label("Clear");
        clearButton.getStyleClass().add("setting-file-clear");
        clearButton.setOnMouseClicked((e) -> {
            node.setFieldValue(settingName, null);
            label.setText("");
        });
        return hbox;

    }

    private Node buildTextField(ConfigNode node, String settingName, Serializable value, String validationPattern) {
        TextField widget = new TextField(String.valueOf(value));
        widget.textProperty().addListener((e) -> node.setFieldValue(settingName, widget.getText()));
        return widget;
    }

    private Node buildBooleanField(ConfigNode node, String settingName, Serializable value) {
        CheckBox widget = new CheckBox();
        widget.setSelected(value.equals(Boolean.TRUE));
        widget.selectedProperty().addListener((e) -> node.setFieldValue(settingName, widget.isSelected()));
        return widget;
    }

    @SuppressWarnings("all")
    private Node buildDynamicSelectComponent(ConfigNode node, String settingName, Serializable value) {
        try {
            DynamicSelection sel = (DynamicSelection) node.subject.getClass().getField(settingName).get(node.subject);
            ChoiceBox widget = new ChoiceBox(FXCollections.observableList(new ArrayList(sel.getSelections().keySet())));
            widget.setMinWidth(175.0);
            widget.setConverter(new StringConverter() {
                @Override
                public String toString(Object object) {
                    return (String) sel.getSelections().get(object);
                }
                
                @Override
                public Object fromString(String string) {
                    return sel.findValueByMatch(string);
                }
            });
            Object selected = value == null ? null : widget.getConverter().fromString(String.valueOf(value));
            if (selected == null) {
                widget.getSelectionModel().selectFirst();
            } else {
                widget.setValue(selected);
            }
            widget.valueProperty().addListener((Observable e) ->
                    node.setFieldValue(settingName, widget.getConverter().toString(widget.getValue()))
            );
            return widget;
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            Logger.getLogger(ConfigurationUIController.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
