package jace.ui;

import com.sun.glass.ui.Application;
import jace.Emulator;
import jace.JaceApplication;
import jace.cheat.DynamicCheat;
import jace.cheat.MemoryCell;
import jace.cheat.MetaCheat;
import jace.cheat.MetaCheat.SearchChangeType;
import jace.cheat.MetaCheat.SearchResult;
import jace.cheat.MetaCheat.SearchType;
import jace.state.State;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.IntegerStringConverter;

public class MetacheatUI {
    boolean isRetina;
    double drawScale;
    
    @FXML
    private Button pauseButton;

    @FXML
    private TextField searchStartAddressField;

    @FXML
    private TextField searchEndAddressField;

    @FXML
    private ScrollPane memoryViewPane;

    @FXML
    private StackPane memoryViewContents;

    @FXML
    private Canvas memoryViewCanvas;

    @FXML
    private TabPane searchTypesTabPane;

    @FXML
    private TextField searchValueField;

    @FXML
    private RadioButton searchTypeByte;

    @FXML
    private ToggleGroup searchSize;

    @FXML
    private RadioButton searchTypeWord;

    @FXML
    private CheckBox searchTypeSigned;

    @FXML
    private RadioButton searchChangeNoneOption;

    @FXML
    private ToggleGroup changeSearchType;

    @FXML
    private RadioButton searchChangeAnyOption;

    @FXML
    private RadioButton searchChangeLessOption;

    @FXML
    private RadioButton searchChangeGreaterOption;

    @FXML
    private RadioButton searchChangeByOption;

    @FXML
    private TextField searchChangeByField;

    @FXML
    private Label searchStatusLabel;

    @FXML
    private ListView<MetaCheat.SearchResult> searchResultsListView;

    @FXML
    private CheckBox showValuesCheckbox;

    @FXML
    private TilePane watchesPane;

    @FXML
    private ListView<State> snapshotsListView;

    @FXML
    private TableView<DynamicCheat> cheatsTableView;

    @FXML
    private TextField codeInspectorAddress;

    @FXML
    private ListView<String> codeInspectorWriteList;

    @FXML
    private ListView<String> codeInspectorReadList;

    @FXML
    void createSnapshot(ActionEvent event) {

    }

    @FXML
    void deleteSnapshot(ActionEvent event) {

    }

    @FXML
    void diffSnapshots(ActionEvent event) {

    }

    @FXML
    void addCheat(ActionEvent event) {
        cheatEngine.addCheat(new DynamicCheat(0, "?"));
    }

    @FXML
    void deleteCheat(ActionEvent event) {
        cheatsTableView.getSelectionModel().getSelectedItems().forEach(cheatEngine::removeCheat);
    }

    @FXML
    void loadCheats(ActionEvent event) {
        boolean resume = Emulator.computer.pause();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load cheats");
        chooser.setInitialFileName("cheat.txt");
        File saveFile = chooser.showOpenDialog(JaceApplication.getApplication().primaryStage);
        if (saveFile != null) {
            cheatEngine.loadCheats(saveFile);
        }
        if (resume) {
            Emulator.computer.resume();
        }
    }

    @FXML
    void saveCheats(ActionEvent event) {
        boolean resume = Emulator.computer.pause();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save current cheats");
        chooser.setInitialFileName("cheat.txt");
        File saveFile = chooser.showSaveDialog(JaceApplication.getApplication().primaryStage);
        if (saveFile != null) {
            cheatEngine.saveCheats(saveFile);
        }
        if (resume) {
            Emulator.computer.resume();
        }
    }

    @FXML
    void newSearch(ActionEvent event) {
        Platform.runLater(() -> {
            cheatEngine.newSearch();
            updateSearchStats();
        });
    }

    @FXML
    void pauseClicked(ActionEvent event) {
        Application.invokeLater(() -> {
            if (Emulator.computer.isRunning()) {
                Emulator.computer.pause();
            } else {
                Emulator.computer.resume();
            }
        });
    }

    @FXML
    void search(ActionEvent event) {
        Platform.runLater(() -> {
            cheatEngine.performSearch();
            updateSearchStats();
        });
    }

    @FXML
    void zoomIn(ActionEvent event) {
        changeZoom(0.1);
    }

    @FXML
    void zoomOut(ActionEvent event) {
        changeZoom(-0.1);
    }

    @FXML
    void initialize() {
        assert pauseButton != null : "fx:id=\"pauseButton\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchStartAddressField != null : "fx:id=\"searchStartAddressField\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchEndAddressField != null : "fx:id=\"searchEndAddressField\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert memoryViewPane != null : "fx:id=\"memoryViewPane\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchTypesTabPane != null : "fx:id=\"searchTypesTabPane\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchValueField != null : "fx:id=\"searchValueField\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchTypeByte != null : "fx:id=\"searchTypeByte\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchSize != null : "fx:id=\"searchSize\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchTypeWord != null : "fx:id=\"searchTypeWord\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchTypeSigned != null : "fx:id=\"searchTypeSigned\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchChangeNoneOption != null : "fx:id=\"searchChangeNoneOption\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert changeSearchType != null : "fx:id=\"changeSearchType\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchChangeAnyOption != null : "fx:id=\"searchChangeAnyOption\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchChangeLessOption != null : "fx:id=\"searchChangeLessOption\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchChangeGreaterOption != null : "fx:id=\"searchChangeGreaterOption\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchChangeByOption != null : "fx:id=\"searchChangeByOption\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchChangeByField != null : "fx:id=\"searchChangeByField\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchStatusLabel != null : "fx:id=\"searchStatusLabel\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert searchResultsListView != null : "fx:id=\"searchResultsListView\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert watchesPane != null : "fx:id=\"watchesPane\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert snapshotsListView != null : "fx:id=\"snapshotsListView\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert codeInspectorAddress != null : "fx:id=\"codeInspectorAddress\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert codeInspectorWriteList != null : "fx:id=\"codeInspectorWriteList\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert codeInspectorReadList != null : "fx:id=\"codeInspectorReadList\" was not injected: check your FXML file 'Metacheat.fxml'.";
        assert cheatsTableView != null : "fx:id=\"cheatsTableView\" was not injected: check your FXML file 'Metacheat.fxml'.";

        isRetina = Screen.getPrimary().getDpi() >= 110;
        
        Emulator.computer.getRunningProperty().addListener((val, oldVal, newVal) -> {
            Platform.runLater(() -> pauseButton.setText(newVal ? "Pause" : "Resume"));
        });

        searchTypesTabPane.getTabs().get(0).setUserData(SearchType.VALUE);
        searchTypesTabPane.getTabs().get(1).setUserData(SearchType.CHANGE);
        searchTypesTabPane.getTabs().get(2).setUserData(SearchType.TEXT);
        searchTypesTabPane.getSelectionModel().selectedItemProperty().addListener((prop, oldVal, newVal) -> {
            if (cheatEngine != null) {
                cheatEngine.setSearchType((SearchType) newVal.getUserData());
            }
        });

        searchChangeAnyOption.setUserData(SearchChangeType.ANY_CHANGE);
        searchChangeByOption.setUserData(SearchChangeType.AMOUNT);
        searchChangeGreaterOption.setUserData(SearchChangeType.GREATER);
        searchChangeLessOption.setUserData(SearchChangeType.LESS);
        searchChangeNoneOption.setUserData(SearchChangeType.NO_CHANGE);
        changeSearchType.selectedToggleProperty().addListener((ObservableValue<? extends Toggle> val, Toggle oldVal, Toggle newVal) -> {
            if (cheatEngine != null) {
                cheatEngine.setSearchChangeType((SearchChangeType) newVal.getUserData());
            }
        });

        searchTypeByte.setUserData(true);
        searchTypeWord.setUserData(false);
        searchSize.selectedToggleProperty().addListener((ObservableValue<? extends Toggle> val, Toggle oldVal, Toggle newVal) -> {
            if (cheatEngine != null) {
                cheatEngine.setByteSized((boolean) newVal.getUserData());
            }
        });

        searchResultsListView.setEditable(true);
        searchResultsListView.setOnEditStart((editEvent) -> {
            editEvent.consume();
            SearchResult result = cheatEngine.getSearchResults().get(editEvent.getIndex());
            addWatch(result.getAddress());
        });

        memoryViewCanvas.setMouseTransparent(false);
        memoryViewCanvas.addEventFilter(MouseEvent.MOUSE_CLICKED, this::memoryViewClicked);
        showValuesCheckbox.selectedProperty().addListener((prop, oldVal, newVal) -> {
            if (newVal) {
                redrawMemoryView();
            }
        });
        memoryViewPane.boundsInParentProperty().addListener((prop, oldVal, newVal) -> redrawMemoryView());
        drawScale = isRetina ? 0.5 : 1.0;
        memoryViewCanvas.widthProperty().bind(memoryViewPane.widthProperty().multiply(drawScale));

        watchesPane.setHgap(5);
        watchesPane.setVgap(5);

        searchStartAddressField.textProperty().addListener(addressRangeListener);
        searchEndAddressField.textProperty().addListener(addressRangeListener);

        TableColumn<DynamicCheat, Boolean> activeColumn = (TableColumn<DynamicCheat, Boolean>) cheatsTableView.getColumns().get(0);
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("active"));
        activeColumn.setCellFactory((TableColumn<DynamicCheat, Boolean> param) -> new CheckBoxTableCell<>());

        TableColumn<DynamicCheat, String> nameColumn = (TableColumn<DynamicCheat, String>) cheatsTableView.getColumns().get(1);
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setCellFactory((TableColumn<DynamicCheat, String> param) -> new TextFieldTableCell<>(new DefaultStringConverter()));

        TableColumn<DynamicCheat, Integer> addrColumn = (TableColumn<DynamicCheat, Integer>) cheatsTableView.getColumns().get(2);
        addrColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        addrColumn.setCellFactory((TableColumn<DynamicCheat, Integer> param) -> {
            return new TextFieldTableCell<>(new IntegerStringConverter() {
                @Override
                public String toString(Integer value) {
                    return "$" + Integer.toHexString(value);
                }

                @Override
                public Integer fromString(String value) {
                    return cheatEngine.parseInt(value);
                }
            });
        });

        TableColumn<DynamicCheat, String> exprColumn = (TableColumn<DynamicCheat, String>) cheatsTableView.getColumns().get(3);
        exprColumn.setCellValueFactory(new PropertyValueFactory<>("expression"));
        exprColumn.setCellFactory((TableColumn<DynamicCheat, String> param) -> new TextFieldTableCell<>(new DefaultStringConverter()));

        codeInspectorAddress.textProperty().addListener((prop, oldValue, newValue) -> {
            try {
                int address = cheatEngine.parseInt(newValue);
                MemoryCell cell = cheatEngine.getMemoryCell(address);
                currentlyInspecting = address;
                cheatEngine.onInspectorChanged();
                codeInspectorReadList.setItems(cell.readInstructionsDisassembly);
                codeInspectorWriteList.setItems(cell.writeInstructionsDisassembly);
            } catch (NumberFormatException ex) {
            }
        });
    }
    MetaCheat cheatEngine = null;

    public void registerMetacheatEngine(MetaCheat engine) {
        cheatEngine = engine;

        cheatsTableView.setItems(cheatEngine.getCheats());
        searchResultsListView.setItems(cheatEngine.getSearchResults());
        snapshotsListView.setItems(cheatEngine.getSnapshots());
        searchTypeSigned.selectedProperty().bindBidirectional(cheatEngine.signedProperty());
        searchStartAddressField.textProperty().bindBidirectional(cheatEngine.startAddressProperty());
        searchEndAddressField.textProperty().bindBidirectional(cheatEngine.endAddressProperty());
        searchValueField.textProperty().bindBidirectional(cheatEngine.searchValueProperty());
        searchChangeByField.textProperty().bindBidirectional(cheatEngine.searchChangeByProperty());

        Application.invokeLater(this::redrawMemoryView);
    }

    ChangeListener<String> addressRangeListener = (prop, oldVal, newVal) -> Application.invokeLater(this::redrawMemoryView);

    public static final int MEMORY_BOX_SIZE = 4;
    public static final int MEMORY_BOX_GAP = 2;
    public static final int MEMORY_BOX_TOTAL_SIZE = (MEMORY_BOX_SIZE + MEMORY_BOX_GAP);
    public int memoryViewColumns;
    public int memoryViewRows;

    public static Set<MemoryCell> redrawNodes = new ConcurrentSkipListSet<>();
    ScheduledExecutorService animationTimer = null;
    ScheduledFuture animationFuture = null;
    Tooltip memoryWatchTooltip = new Tooltip();

    private void memoryViewClicked(MouseEvent e) {
        if (cheatEngine != null) {
            Watch currentWatch = (Watch) memoryWatchTooltip.getGraphic();
            if (currentWatch != null) {
                currentWatch.disconnect();
            }

            double x = e.getX() / drawScale;
            double y = e.getY() / drawScale;
            int col = (int) (x / MEMORY_BOX_TOTAL_SIZE);
            int row = (int) (y / MEMORY_BOX_TOTAL_SIZE);
            int addr = cheatEngine.getStartAddress() + row * memoryViewColumns + col;
            Watch watch = new Watch(addr, this);

            Label addWatch = new Label("Watch >>");
            addWatch.setOnMouseClicked((mouseEvent) -> {
                Watch newWatch = addWatch(addr);
                if (watch.holdingProperty().get()) {
                    newWatch.holdingProperty().set(true);
                }
                memoryWatchTooltip.hide();
            });
            watch.getChildren().add(addWatch);

            Label addCheat = new Label("Cheat >>");
            addCheat.setOnMouseClicked((mouseEvent) -> {
                Platform.runLater(() -> addCheat(addr, watch.getValue()));
            });
            watch.getChildren().add(addCheat);

            memoryWatchTooltip.setStyle("-fx-background-color:NAVY");
            memoryWatchTooltip.onHidingProperty().addListener((prop, oldVal, newVal) -> {
                watch.disconnect();
                memoryWatchTooltip.setGraphic(null);
            });
            memoryWatchTooltip.setGraphic(watch);
            memoryWatchTooltip.show(memoryViewContents, e.getScreenX() + 5, e.getScreenY() - 15);
        }
    }

    private void processMemoryViewUpdates() {
        if (!Emulator.computer.getRunningProperty().get()) {
            return;
        }
        GraphicsContext context = memoryViewCanvas.getGraphicsContext2D();
        Set<MemoryCell> draw = new HashSet<>(redrawNodes);
        redrawNodes.clear();
        Application.invokeLater(() -> {
            draw.stream().forEach((jace.cheat.MemoryCell cell) -> {
                if (showValuesCheckbox.isSelected()) {
                    int val = cell.value.get() & 0x0ff;
                    context.setFill(Color.rgb(val, val, val));
                } else {
                    context.setFill(Color.rgb(
                            cell.writeCount.get(),
                            cell.readCount.get(),
                            cell.execCount.get()));
                }
                context.fillRect(cell.getX(), cell.getY(), cell.getWidth(), cell.getHeight());
            });
        });
    }

    public static int FRAME_RATE = 1000 / 60;

    public void redrawMemoryView() {
        if (cheatEngine == null) {
            return;
        }
        boolean resume = Emulator.computer.pause();

        if (animationTimer == null) {
            animationTimer = new ScheduledThreadPoolExecutor(1);
        }

        if (animationFuture != null) {
            animationFuture.cancel(false);
        }

        animationFuture = animationTimer.scheduleAtFixedRate(this::processMemoryViewUpdates, FRAME_RATE, FRAME_RATE, TimeUnit.MILLISECONDS);

        cheatEngine.initMemoryView();
        int pixelsPerBlock = 16 * MEMORY_BOX_TOTAL_SIZE;
        memoryViewColumns = (int) (memoryViewPane.getWidth() / pixelsPerBlock * 16);
        memoryViewRows = ((cheatEngine.getEndAddress() - cheatEngine.getStartAddress()) / memoryViewColumns) + 1;
        double canvasHeight = memoryViewRows * MEMORY_BOX_TOTAL_SIZE * drawScale;

        // Guard against asking for a big texture because bad things will happen!
        while (canvasHeight >= 16384) {
            memoryViewColumns += 16;
            memoryViewRows = ((cheatEngine.getEndAddress() - cheatEngine.getStartAddress()) / memoryViewColumns) + 1;
            canvasHeight = memoryViewRows * MEMORY_BOX_TOTAL_SIZE * drawScale;
        }
        double canvasWidth = memoryViewColumns * MEMORY_BOX_TOTAL_SIZE * drawScale;

        memoryViewContents.setPrefHeight(canvasHeight);
        memoryViewCanvas.setHeight(canvasHeight);
        GraphicsContext context = memoryViewCanvas.getGraphicsContext2D();
        context.setFill(Color.rgb(40, 40, 40));
        context.fillRect(0, 0, memoryViewCanvas.getWidth(), memoryViewCanvas.getHeight());
        for (int addr = cheatEngine.getStartAddress(); addr <= cheatEngine.getEndAddress(); addr++) {
            int col = (addr - cheatEngine.getStartAddress()) % memoryViewColumns;
            int row = (addr - cheatEngine.getStartAddress()) / memoryViewColumns;
            MemoryCell cell = cheatEngine.getMemoryCell(addr);
            cell.setRect(
                    (int) (col * MEMORY_BOX_TOTAL_SIZE * drawScale), 
                    (int) (row * MEMORY_BOX_TOTAL_SIZE * drawScale), 
                    (int) (MEMORY_BOX_SIZE * drawScale), 
                    (int) (MEMORY_BOX_SIZE * drawScale));
            redrawNodes.add(cell);
        }
        MemoryCell.setListener((javafx.beans.value.ObservableValue<? extends jace.cheat.MemoryCell> prop, jace.cheat.MemoryCell oldCell, jace.cheat.MemoryCell newCell) -> {
            redrawNodes.add(newCell);
        });
        
        setZoom((1/drawScale) - 0.1);

        if (resume) {
            Emulator.computer.resume();
        }
    }

    private void changeZoom(double amount) {
        if (memoryViewCanvas != null) {
            double zoom = memoryViewCanvas.getScaleX();
            zoom += amount;
            setZoom(zoom);
        }
    }
    
    private void setZoom(double zoom) {
        if (memoryViewCanvas != null) {
            memoryViewCanvas.setScaleX(zoom);
            memoryViewCanvas.setScaleY(zoom);
            StackPane scrollArea = (StackPane) memoryViewCanvas.getParent();
            scrollArea.setPrefSize(memoryViewCanvas.getWidth() * zoom, memoryViewCanvas.getHeight() * zoom);
        }
    }

    public void detach() {
        cheatsTableView.setItems(FXCollections.emptyObservableList());
        searchResultsListView.setItems(FXCollections.emptyObservableList());
        searchTypeSigned.selectedProperty().unbind();
        searchStartAddressField.textProperty().unbind();
        searchStartAddressField.textProperty().unbind();
        searchEndAddressField.textProperty().unbind();
        searchValueField.textProperty().unbind();
        searchChangeByField.textProperty().unbind();
        memoryWatchTooltip.hide();
        animationTimer.shutdown();
        animationTimer = null;
        cheatEngine = null;
    }

    private void updateSearchStats() {
        int size = cheatEngine.getSearchResults().size();
        searchStatusLabel.setText(size + (size == 1 ? " result" : " results") + " found.");
    }

    private Watch addWatch(int addr) {
        Watch watch = new Watch(addr, this);
        watch.setPadding(new Insets(5));
        watch.setOpaqueInsets(new Insets(10));

        Label addCheat = new Label("Cheat >>");
        addCheat.setOnMouseClicked((mouseEvent) -> {
            addCheat(addr, watch.getValue());
        });
        addCheat.setTextFill(Color.WHITE);
        watch.getChildren().add(addCheat);

        Label close = new Label("Close  X");
        close.setOnMouseClicked((mouseEvent) -> {
            watch.disconnect();
            watchesPane.getChildren().remove(watch);
        });
        close.setTextFill(Color.WHITE);
        watch.getChildren().add(close);

        watchesPane.getChildren().add(watch);
        return watch;
    }

    private void addCheat(int addr, int val) {
        cheatEngine.addCheat(new DynamicCheat(addr, String.valueOf(val)));
    }

    int currentlyInspecting = 0;

    public void inspectAddress(int address) {
        codeInspectorAddress.setText("$" + Integer.toHexString(address));
    }

    public boolean isInspecting(int address) {
        return currentlyInspecting == address;
    }
}
