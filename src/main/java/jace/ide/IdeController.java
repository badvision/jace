/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jace.ide;

import java.io.File;
import java.net.URL;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import jace.JaceApplication;
import jace.ide.Program.DocumentType;
import jace.ide.Program.Option;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

/**
 * FXML Controller class
 *
 * @author blurry
 */
public class IdeController {

    @FXML // ResourceBundle that was given to the FXMLLoader
    private ResourceBundle resources;

    @FXML // URL location of the FXML file that was given to the FXMLLoader
    private URL location;

    @FXML // fx:id="saveMenuItem"
    private MenuItem saveMenuItem; // Value injected by FXMLLoader

    @FXML // fx:id="saveAllMenuItem"
    private MenuItem saveAllMenuItem; // Value injected by FXMLLoader

    @FXML // fx:id="saveAsMenuItem"
    private MenuItem saveAsMenuItem; // Value injected by FXMLLoader

    @FXML // fx:id="closeMenuItem"
    private MenuItem closeMenuItem; // Value injected by FXMLLoader

    @FXML // fx:id="closeAllMenuItem"
    private MenuItem closeAllMenuItem; // Value injected by FXMLLoader

    @FXML // fx:id="editMenu"
    private Menu editMenu; // Value injected by FXMLLoader

    @FXML // fx:id="runMenu"
    private Menu runMenu; // Value injected by FXMLLoader

    @FXML // fx:id="autocompile"
    private ToggleGroup autocompile; // Value injected by FXMLLoader

    @FXML // fx:id="tabPane"
    private TabPane tabPane; // Value injected by FXMLLoader

    @FXML // fx:id="statusBar"
    private ToolBar statusBar; // Value injected by FXMLLoader

    @FXML
    void onCloseAllClicked(ActionEvent event) {
        openDocuments.forEach((Tab t, Program proxy) -> {
            if (!event.isConsumed()) {
                closeTab(t, event);
            }
        });
    }

    @FXML
    void onCloseClicked(ActionEvent event) {
        getCurrentTab().ifPresent((t) -> closeTab(t, event));
    }

    public Optional<Tab> getCurrentTab() {
        return Optional.ofNullable(tabPane.getSelectionModel().getSelectedItem());
    }

    public Optional<Program> getCurrentProgram() {
        return getCurrentTab().map(t -> openDocuments.get(t));
    }

    @FXML
    void newApplesoftBasicClicked(ActionEvent event) {
        // Program tab = 
        createTab(DocumentType.applesoft, null, true);
    }

    @FXML
    void newApplesoftBasicFromMemoryClicked(ActionEvent event) {
        Alert warningAlert = new Alert(Alert.AlertType.CONFIRMATION);
        warningAlert.setTitle("Is Applesoft running?");
        warningAlert.setContentText("If you proceed and applesoft is not running or there is no active program then the emulator might freeze.  Press Cancel if you are unsure.");
        Optional<ButtonType> result = warningAlert.showAndWait();
        if (result.get() == ButtonType.OK) {
            // Program tab = 
            createTab(DocumentType.applesoft, null, false);
        }
    }

    @FXML
    void newAssemblyListingClicked(ActionEvent event) {
        createTab(DocumentType.assembly, null, false);
    }

    @FXML
    void newHexdataClicked(ActionEvent event) {
        createTab(DocumentType.hex, null, false);
    }

    @FXML
    void newPlainTextClicked(ActionEvent event) {
        createTab(DocumentType.plain, null, false);
    }

    Map<Tab, Program> openDocuments = new HashMap<>();
    Map<Option, Object> globalOptions = new EnumMap<>(Option.class);

    private Program createTab(DocumentType type, File document, boolean isBlank) {
        WebView editor = new WebView();
        Program proxy = new Program(type, globalOptions);
        proxy.initEditor(editor, document, isBlank);
        Tab t = new Tab(proxy.getName(), editor);
        tabPane.getTabs().add(t);
        openDocuments.put(t, proxy);
        t.setOnCloseRequest(this::handleCloseTabRequest);
        return proxy;
    }

    private void handleCloseTabRequest(Event e) {
        Tab t = (Tab) e.getTarget();
        closeTab(t, e);
    }

    private void closeTab(Tab t, Event e) {
        tabPane.getTabs().remove(t);
        openDocuments.remove(t);
    }

    @FXML
    void onOpenClicked(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open document");
        for (DocumentType type : Program.DocumentType.values()) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(type.name(), type.extensions)
            );
        }
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(JaceApplication.getApplication().primaryStage);
        if (file != null && file.isFile() && file.exists()) {
            DocumentType type = DocumentType.fromFile(file);
            createTab(type, file, true);
        }
    }

    @FXML
    void onSaveAllClicked(ActionEvent event) {
        openDocuments.forEach((Tab t, Program p) -> {
            if (p.isChanged()) {
                if (p.getFile().isPresent()) {
                    p.save(p.getFile().get());
                    t.setText(p.getName());
                } else {
                    tabPane.getSelectionModel().select(t);
                    onSaveAsClicked(event);
                }
            }
        });
    }

    @FXML
    void onSaveAsClicked(ActionEvent event) {
        getCurrentProgram().ifPresent(program -> {
            program.save(chooseFileToSave(program.getType()));
            getCurrentTab().get().setText(program.getName());
        });
    }

    @FXML
    void onSaveClicked(ActionEvent event) {
        getCurrentProgram().ifPresent(program -> {
            program.save(
                    program.getFile()
                    .orElseGet(() -> chooseFileToSave(program.getType())));
            getCurrentTab().get().setText(program.getName());
        });
    }

    private File chooseFileToSave(DocumentType type) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save " + type.name() + " document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(type.name(), type.extensions),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return chooser.showSaveDialog(JaceApplication.getApplication().primaryStage);
    }

    @FXML
    void executeClicked(ActionEvent event) {
        getCurrentProgram().ifPresent(program -> {
            try {
                program.execute();
                updateStatusMessages(program.lastResult);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @FXML
    void testCompileClicked(ActionEvent event) {
        getCurrentProgram().ifPresent(program -> {
            program.test();
            updateStatusMessages(program.lastResult);
        });
    }

    @FXML
    void viewCompilerOutputClicked(ActionEvent event) {

    }

    @FXML
    void viewSymbolTableClicked(ActionEvent event) {

    }

    @FXML // This method is called by the FXMLLoader when initialization is complete
    public void initialize() {
        assert saveMenuItem != null : "fx:id=\"saveMenuItem\" was not injected: check your FXML file 'editor.fxml'.";
        assert saveAllMenuItem != null : "fx:id=\"saveAllMenuItem\" was not injected: check your FXML file 'editor.fxml'.";
        assert saveAsMenuItem != null : "fx:id=\"saveAsMenuItem\" was not injected: check your FXML file 'editor.fxml'.";
        assert closeMenuItem != null : "fx:id=\"closeMenuItem\" was not injected: check your FXML file 'editor.fxml'.";
        assert closeAllMenuItem != null : "fx:id=\"closeAllMenuItem\" was not injected: check your FXML file 'editor.fxml'.";
        assert editMenu != null : "fx:id=\"editMenu\" was not injected: check your FXML file 'editor.fxml'.";
        assert runMenu != null : "fx:id=\"runMenu\" was not injected: check your FXML file 'editor.fxml'.";
        assert autocompile != null : "fx:id=\"autocompile\" was not injected: check your FXML file 'editor.fxml'.";
        assert tabPane != null : "fx:id=\"tabPane\" was not injected: check your FXML file 'editor.fxml'.";
        assert statusBar != null : "fx:id=\"statusBar\" was not injected: check your FXML file 'editor.fxml'.";

        tabPane.getTabs().addListener((ListChangeListener.Change<? extends Tab> c) -> {
            boolean hasNoItems = c.getList().isEmpty();
            saveMenuItem.setDisable(hasNoItems);
            saveAsMenuItem.setDisable(hasNoItems);
            saveAllMenuItem.setDisable(hasNoItems);
            closeMenuItem.setDisable(hasNoItems);
            closeAllMenuItem.setDisable(hasNoItems);
            editMenu.setDisable(hasNoItems);
            runMenu.setDisable(hasNoItems);
        });
    }

    @SuppressWarnings("all")
    private void updateStatusMessages(CompileResult lastResult) {
        String message = "Compiler was " + (lastResult.isSuccessful() ? " successful" : " NOT SUCCESSFUL");
        message += " -- ";
        message += lastResult.getErrors().size() + " error(s) and "+lastResult.getWarnings().size()+" warning(s) reported.";
        statusBar.getItems().clear();
        statusBar.getItems().add(new Label(message));
    }
}
