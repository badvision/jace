package jace.ide;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import jace.JaceApplication;
import jace.ide.EditorTheme;
import jace.ide.Program.DocumentType;
import javafx.animation.PauseTransition;
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
import javafx.scene.control.ToolBar;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/**
 * FXML Controller class
 *
 * @author blurry
 */
public class IdeController {

    @FXML private ResourceBundle resources;
    @FXML private URL location;
    @FXML private MenuItem saveMenuItem;

    private EditorTheme currentTheme = EditorTheme.DARK;
    @FXML private MenuItem saveAllMenuItem;
    @FXML private MenuItem saveAsMenuItem;
    @FXML private MenuItem closeMenuItem;
    @FXML private MenuItem closeAllMenuItem;
    @FXML private Menu editMenu;
    @FXML private Menu runMenu;
    @FXML private TabPane tabPane;
    @FXML private ToolBar statusBar;

    Map<Tab, Program> openDocuments = new HashMap<>();

    // ── File menu ─────────────────────────────────────────────────────────────

    @FXML
    void onCloseAllClicked(ActionEvent event) {
        new java.util.ArrayList<>(openDocuments.keySet()).forEach(t -> {
            if (!event.isConsumed()) closeTab(t, event);
        });
    }

    @FXML void onCloseClicked(ActionEvent event) {
        getCurrentTab().ifPresent(t -> closeTab(t, event));
    }

    @FXML void newApplesoftBasicClicked(ActionEvent event) {
        createTab(DocumentType.applesoft, null, true);
    }

    @FXML void newApplesoftBasicFromMemoryClicked(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Is Applesoft running?");
        alert.setContentText("If Applesoft is not running or there is no active program the emulator might freeze. Press Cancel if unsure.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            createTab(DocumentType.applesoft, null, false);
        }
    }

    @FXML void newAssemblyListingClicked(ActionEvent event) { createTab(DocumentType.assembly, null, false); }
    @FXML void newHexdataClicked(ActionEvent event)         { createTab(DocumentType.hex, null, false); }
    @FXML void newPlainTextClicked(ActionEvent event)       { createTab(DocumentType.plain, null, false); }

    @FXML
    void onOpenClicked(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open document");
        for (DocumentType type : Program.DocumentType.values()) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(type.name(), type.extensions));
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(JaceApplication.getApplication().primaryStage);
        if (file != null && file.isFile() && file.exists()) {
            createTab(DocumentType.fromFile(file), file, true);
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
            getCurrentTab().ifPresent(t -> t.setText(program.getName()));
        });
    }

    @FXML
    void onSaveClicked(ActionEvent event) {
        getCurrentProgram().ifPresent(program -> {
            program.save(program.getFile().orElseGet(() -> chooseFileToSave(program.getType())));
            getCurrentTab().ifPresent(t -> t.setText(program.getName()));
        });
    }

    // ── Edit menu ─────────────────────────────────────────────────────────────

    @FXML void cutClicked(ActionEvent event)         { getCurrentEditor().ifPresent(EditorControl::cut); }
    @FXML void copyClicked(ActionEvent event)        { getCurrentEditor().ifPresent(EditorControl::copy); }
    @FXML void pasteClicked(ActionEvent event)       { getCurrentEditor().ifPresent(EditorControl::paste); }
    @FXML void undoClicked(ActionEvent event)        { getCurrentEditor().ifPresent(EditorControl::undo); }
    @FXML void redoClicked(ActionEvent event)        { getCurrentEditor().ifPresent(EditorControl::redo); }
    @FXML void findReplaceClicked(ActionEvent event) { getCurrentEditor().ifPresent(EditorControl::showFindReplace); }

    @FXML
    void goToLineClicked(ActionEvent event) {
        getCurrentEditor().ifPresent(editor -> {
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
            dialog.setTitle("Go to Line");
            dialog.setHeaderText(null);
            dialog.setContentText("Line number:");
            dialog.showAndWait().ifPresent(s -> {
                try { editor.goToLine(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            });
        });
    }

    // ── Run menu ──────────────────────────────────────────────────────────────

    @FXML
    void buildClicked(ActionEvent event) {
        getCurrentProgram().ifPresent(program -> {
            try {
                program.build();
                updateStatusMessages(program.lastResult);
            } catch (Exception e) {
                showError("Build failed", e.getMessage());
            }
        });
    }

    @FXML
    void executeClicked(ActionEvent event) {
        getCurrentProgram().ifPresent(program -> {
            try {
                program.execute();
                updateStatusMessages(program.lastResult);
            } catch (Exception e) {
                showError("Execute failed", e.getMessage());
            }
        });
    }

    @FXML void viewCompilerOutputClicked(ActionEvent event) {}
    @FXML void viewSymbolTableClicked(ActionEvent event) {}

    // ── View menu ─────────────────────────────────────────────────────────────

    @FXML void themeDarkClicked(ActionEvent event)  { applyTheme(EditorTheme.DARK); }
    @FXML void themeLightClicked(ActionEvent event) { applyTheme(EditorTheme.LIGHT); }

    private void applyTheme(EditorTheme theme) {
        currentTheme = theme;
        openDocuments.forEach((t, p) -> {
            if (p.editorControl instanceof NativeEditorControl nec) {
                nec.setTheme(theme);
            }
        });
    }

    // ── Tab management ────────────────────────────────────────────────────────

    private Program createTab(DocumentType type, File document, boolean isBlank) {
        NativeEditorControl editor = new NativeEditorControl();
        editor.setTheme(currentTheme);
        Program proxy = new Program(type);
        proxy.initEditor(editor, document, isBlank);

        // 1.5s debounce auto-compile
        PauseTransition debounce = new PauseTransition(Duration.millis(1500));
        debounce.setOnFinished(e -> {
            proxy.lastResult = proxy.getHandler().compile(proxy);
            proxy.manageCompileResult(proxy.lastResult);
        });
        editor.textProperty().addListener((obs, oldVal, newVal) -> debounce.playFromStart());

        Tab t = new Tab(proxy.getName(), editor);
        tabPane.getTabs().add(t);
        openDocuments.put(t, proxy);
        t.setOnCloseRequest(this::handleCloseTabRequest);
        tabPane.getSelectionModel().select(t);
        return proxy;
    }

    private void handleCloseTabRequest(Event e) {
        closeTab((Tab) e.getTarget(), e);
    }

    private void closeTab(Tab t, Event e) {
        tabPane.getTabs().remove(t);
        openDocuments.remove(t);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Optional<Tab> getCurrentTab() {
        return Optional.ofNullable(tabPane.getSelectionModel().getSelectedItem());
    }

    public Optional<Program> getCurrentProgram() {
        return getCurrentTab().map(openDocuments::get);
    }

    private Optional<EditorControl> getCurrentEditor() {
        return getCurrentProgram().map(p -> p.editorControl);
    }

    private File chooseFileToSave(DocumentType type) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save " + type.name() + " document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(type.name(), type.extensions),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        return chooser.showSaveDialog(JaceApplication.getApplication().primaryStage);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @SuppressWarnings("all")
    private void updateStatusMessages(CompileResult lastResult) {
        String message = "Compiler " + (lastResult.isSuccessful() ? "successful" : "FAILED")
                + " — " + lastResult.getErrors().size() + " error(s), "
                + lastResult.getWarnings().size() + " warning(s)";
        statusBar.getItems().clear();
        statusBar.getItems().add(new Label(message));
    }

    @FXML
    public void initialize() {
        assert saveMenuItem != null;
        assert saveAllMenuItem != null;
        assert saveAsMenuItem != null;
        assert closeMenuItem != null;
        assert closeAllMenuItem != null;
        assert editMenu != null;
        assert runMenu != null;
        assert tabPane != null;
        assert statusBar != null;

        tabPane.getTabs().addListener((ListChangeListener.Change<? extends Tab> c) -> {
            boolean empty = c.getList().isEmpty();
            saveMenuItem.setDisable(empty);
            saveAsMenuItem.setDisable(empty);
            saveAllMenuItem.setDisable(empty);
            closeMenuItem.setDisable(empty);
            closeAllMenuItem.setDisable(empty);
            editMenu.setDisable(empty);
            runMenu.setDisable(empty);
        });
    }
}
