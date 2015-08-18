package jace.ui;

import com.sun.glass.ui.Application;
import jace.Emulator;
import jace.cheat.MetaCheat;
import jace.cheat.MetaCheat.SearchChangeType;
import jace.cheat.MetaCheat.SearchType;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.state.State;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.TilePane;

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
            System.out.println("Tab selected: " + newVal.getText());
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

        engine.addCheat(RAMEvent.TYPE.ANY, this::processMemoryEvent, 0, 0x0ffff);
    }

    private void changeZoom(double amount) {
        double zoom = memoryViewPane.getScaleX();
        zoom += amount;
        memoryViewPane.setScaleX(zoom);
        memoryViewPane.setScaleY(zoom);
    }

    private void processMemoryEvent(RAMEvent e) {
        if (e.getAddress() < cheatEngine.getStartAddress() || e.getAddress() > cheatEngine.getEndAddress()) {
            return;
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
        cheatEngine = null;
    }

    private void updateSearchStats() {
        int size = cheatEngine.getSearchResults().size();
        searchStatusLabel.setText(size + (size == 1 ? " result" : " results") + " found.");
    }
}
