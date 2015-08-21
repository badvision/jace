package jace.ui;

import com.sun.glass.ui.Application;
import jace.Emulator;
import jace.cheat.MetaCheat;
import jace.cheat.MetaCheat.SearchChangeType;
import jace.cheat.MetaCheat.SearchType;
import jace.core.RAMListener;
import jace.state.State;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class MetacheatUI {

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
    private TableView<RAMListener> cheatsTableView;

    @FXML
    void addCheat(ActionEvent event) {

    }

    @FXML
    void createSnapshot(ActionEvent event) {

    }

    @FXML
    void deleteCheat(ActionEvent event) {

    }

    @FXML
    void deleteSnapshot(ActionEvent event) {

    }

    @FXML
    void diffSnapshots(ActionEvent event) {

    }

    @FXML
    void loadCheats(ActionEvent event) {

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
    void saveCheats(ActionEvent event) {

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
        assert cheatsTableView != null : "fx:id=\"cheatsTableView\" was not injected: check your FXML file 'Metacheat.fxml'.";

        Emulator.computer.getRunningProperty().addListener((val, oldVal, newVal) -> pauseButton.setText(newVal ? "Pause" : "Resume"));

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

        memoryViewCanvas.setMouseTransparent(false);
        memoryViewCanvas.addEventFilter(MouseEvent.MOUSE_CLICKED, this::memoryViewClicked);
        showValuesCheckbox.selectedProperty().addListener((prop, oldVal, newVal) -> {
            if (newVal) {
                redrawMemoryView();
            }
        });
        memoryViewPane.boundsInParentProperty().addListener((prop, oldVal, newVal) -> redrawMemoryView());
        memoryViewCanvas.widthProperty().bind(memoryViewPane.widthProperty());
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

        searchStartAddressField.textProperty().addListener(addressRangeListener);
        searchEndAddressField.textProperty().addListener(addressRangeListener);

        Application.invokeLater(this::redrawMemoryView);
    }

    ChangeListener<String> addressRangeListener = (prop, oldVal, newVal) -> Application.invokeLater(this::redrawMemoryView);

    public static final int MEMORY_BOX_SIZE = 4;
    public static final int MEMORY_BOX_GAP = 2;
    public static final int MEMORY_BOX_TOTAL_SIZE = (MEMORY_BOX_SIZE + MEMORY_BOX_GAP);
    public int memoryViewColumns;
    public int memoryViewRows;

    public static Set<MetaCheat.MemoryCell> redrawNodes = new ConcurrentSkipListSet<>();
    ScheduledExecutorService animationTimer = null;
    ScheduledFuture animationFuture = null;
    StackPane pane = new StackPane();

    Tooltip memoryWatchTooltip = new Tooltip();

    private void memoryViewClicked(MouseEvent e) {
        if (cheatEngine != null) {
            Watch currentWatch = (Watch) memoryWatchTooltip.getGraphic();
            if (currentWatch != null) {
                currentWatch.disconnect();
            }

            double x = e.getX();
            double y = e.getY();
            int col = (int) (x / MEMORY_BOX_TOTAL_SIZE);
            int row = (int) (y / MEMORY_BOX_TOTAL_SIZE);
            int addr = cheatEngine.getStartAddress() + row * memoryViewColumns + col;
            Watch watch = new Watch(addr);
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
        Application.invokeLater(() -> {
            GraphicsContext context = memoryViewCanvas.getGraphicsContext2D();
            Set<MetaCheat.MemoryCell> draw = new HashSet<>(redrawNodes);
            redrawNodes.clear();
            draw.stream().forEach((cell) -> {
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
            animationTimer = new ScheduledThreadPoolExecutor(10);
        }

        if (animationFuture != null) {
            animationFuture.cancel(false);
        }

        animationFuture = animationTimer.scheduleAtFixedRate(this::processMemoryViewUpdates, FRAME_RATE, 1000 / 60, TimeUnit.MILLISECONDS);

        cheatEngine.initMemoryView();
        int pixelsPerBlock = 16 * MEMORY_BOX_TOTAL_SIZE;
        memoryViewColumns = ((int) memoryViewPane.getWidth()) / pixelsPerBlock * 16;
        memoryViewRows = ((cheatEngine.getEndAddress() - cheatEngine.getStartAddress()) / memoryViewColumns) + 1;
        int canvasHeight = memoryViewRows * MEMORY_BOX_TOTAL_SIZE;
        memoryViewContents.setPrefHeight(canvasHeight);
        memoryViewCanvas.setHeight(canvasHeight);
        GraphicsContext context = memoryViewCanvas.getGraphicsContext2D();
        context.setFill(Color.rgb(40, 40, 40));
        context.fillRect(0, 0, memoryViewCanvas.getWidth(), memoryViewCanvas.getHeight());
        for (int addr = cheatEngine.getStartAddress(); addr <= cheatEngine.getEndAddress(); addr++) {
            int col = (addr - cheatEngine.getStartAddress()) % memoryViewColumns;
            int row = (addr - cheatEngine.getStartAddress()) / memoryViewColumns;
            MetaCheat.MemoryCell cell = cheatEngine.getMemoryCell(addr);
            cell.setRect(col * MEMORY_BOX_TOTAL_SIZE, row * MEMORY_BOX_TOTAL_SIZE, MEMORY_BOX_SIZE, MEMORY_BOX_SIZE);
            redrawNodes.add(cell);
        }
        MetaCheat.MemoryCell.setListener((prop, oldCell, newCell) -> {
            redrawNodes.add(newCell);
        });

        if (resume) {
            Emulator.computer.resume();
        }
    }

    private void changeZoom(double amount) {
        if (memoryViewCanvas != null) {
            double zoom = memoryViewCanvas.getScaleX();
            zoom += amount;
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

    private static int GRAPH_WIDTH = 50;
    private static double GRAPH_HEIGHT = 50;

    private class Watch extends VBox {

        int address;
        ScheduledFuture redraw;
        Canvas graph;
        List<Integer> samples = Collections.synchronizedList(new ArrayList<>());

        public Watch(int address) {
            super();
            this.address = address;
            redraw = animationTimer.scheduleAtFixedRate(this::redraw, FRAME_RATE, FRAME_RATE, TimeUnit.MILLISECONDS);

            setBackground(new Background(new BackgroundFill(Color.NAVY, CornerRadii.EMPTY, Insets.EMPTY)));
            Label addrLabel = new Label("$" + Integer.toHexString(address));
            addrLabel.setTextAlignment(TextAlignment.CENTER);
            addrLabel.setMinWidth(GRAPH_WIDTH);
            addrLabel.setFont(new Font(Font.getDefault().getFamily(), 14));
            addrLabel.setTextFill(Color.WHITE);
            graph = new Canvas(GRAPH_WIDTH, GRAPH_HEIGHT);
            getChildren().add(addrLabel);
            getChildren().add(graph);

            CheckBox hold = new CheckBox("Hold");
            hold.selectedProperty().addListener((prop, oldVal, newVal) -> this.hold(newVal));
            getChildren().add(hold);
        }

        public void redraw() {
            int val = cheatEngine.getMemoryCell(address).value.get() & 0x0ff;
            if (samples.size() >= GRAPH_WIDTH) {
                samples.remove(0);
            }
            samples.add(val);

            GraphicsContext g = graph.getGraphicsContext2D();
            g.setFill(Color.BLACK);
            g.fillRect(0, 0, GRAPH_WIDTH, GRAPH_HEIGHT);

            if (samples.size() > 1) {
                g.setLineWidth(1);
                g.setStroke(Color.LAWNGREEN);
                int y = (int) (GRAPH_HEIGHT - ((samples.get(0) / 255.0) * GRAPH_HEIGHT));
                g.beginPath();
                g.moveTo(0, y);
                for (int i = 1; i < samples.size(); i++) {
                    y = (int) (GRAPH_HEIGHT - ((samples.get(i) / 255.0) * GRAPH_HEIGHT));
                    g.lineTo(i, y);
                }
                g.stroke();
            }
            g.beginPath();
            g.setStroke(Color.WHITE);
            g.strokeText(String.valueOf(val), GRAPH_WIDTH - 25, GRAPH_HEIGHT - 5);
        }

        RAMListener holdListener;

        private void hold(boolean state) {
            if (!state) {
                cheatEngine.removeListener(holdListener);
                holdListener = null;
            } else {
                int val = cheatEngine.getMemoryCell(address).value.get() & 0x0ff;
                holdListener = cheatEngine.forceValue(val, address);
            }
        }

        public void disconnect() {
            if (holdListener != null) {
                cheatEngine.removeListener(holdListener);
            }
            redraw.cancel(false);
        }
    }
}
