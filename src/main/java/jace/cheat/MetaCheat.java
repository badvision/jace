package jace.cheat;

import jace.Emulator;
import jace.JaceApplication;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.RAMListener;
import jace.state.State;
import jace.ui.MetacheatUI;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MetaCheat extends Cheats {

    public static enum SearchType {
        VALUE, TEXT, CHANGE
    }

    public static enum SearchChangeType {
        NO_CHANGE, ANY_CHANGE, LESS, GREATER, AMOUNT
    }

    public static class SearchResult {

        int address;
        int lastObservedValue = 0;

        private SearchResult(int address, int val) {
            this.address = address;
            lastObservedValue = val;
        }

        @Override
        public String toString() {
            return Integer.toHexString(address) + ": " + lastObservedValue + " (" + Integer.toHexString(lastObservedValue) + ")";
        }
    }

    MetacheatUI ui;
    private int startAddress = 0;
    private int endAddress = 0x0ffff;
    private final StringProperty startAddressProperty = new SimpleStringProperty("0");
    private final StringProperty endAddressProperty = new SimpleStringProperty("FFFF");
    private boolean byteSized = false;
    private SearchType searchType = SearchType.VALUE;
    private SearchChangeType searchChangeType = SearchChangeType.NO_CHANGE;
    private final BooleanProperty signedProperty = new SimpleBooleanProperty(false);
    private final StringProperty searchValueProperty = new SimpleStringProperty("0");
    private final StringProperty changeByProperty = new SimpleStringProperty("0");
    private final ObservableList<RAMListener> cheatList = FXCollections.observableArrayList();
    private final ObservableList<SearchResult> resultList = FXCollections.observableArrayList();
    private final ObservableList<State> snapshotList = FXCollections.observableArrayList();

    public MetaCheat(Computer computer) {
        super(computer);
        addNumericValidator(startAddressProperty);
        addNumericValidator(endAddressProperty);
        addNumericValidator(searchValueProperty);
        addNumericValidator(changeByProperty);
        startAddressProperty.addListener((prop, oldVal, newVal) -> {
            startAddress = parseInt(newVal);
        });
        endAddressProperty.addListener((prop, oldVal, newVal) -> {
            endAddress = parseInt(newVal);
        });
    }

    private void addNumericValidator(StringProperty stringProperty) {
        stringProperty.addListener((ObservableValue<? extends String> prop, String oldVal, String newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                return;
            }
            if (!newVal.matches("(\\+|-)?(x|$)?[0-9a-fA-F]*")) {
                stringProperty.set("");
            }
        });
    }

    public int parseInt(String s) throws NumberFormatException {
        if (s.matches("(\\+|-)?[0-9]+")) {
            return Integer.parseInt(s);
        } else if (s.matches("(\\+|-)?[0-9a-fA-F]+")) {
            return Integer.parseInt(s.toUpperCase(), 16);
        } else if (s.matches("(\\+|-)?(x|$)[0-9a-fA-F]+")) {
            String upper = s.toUpperCase();
            boolean positive = true;
            if (upper.startsWith("-")) {
                positive = false;
            }
            for (int i = 0; i < upper.length(); i++) {
                char c = upper.charAt(i);
                if ((c >= '0' && c <= '9') || (c >= 'A' & c <= 'F')) {
                    int value = Integer.parseInt(s.substring(i), 16);
                    if (!positive) {
                        value *= -1;
                    }
                    return value;
                }
            }
        }
        throw new NumberFormatException("Could not interpret int value " + s);
    }

    @Override
    void registerListeners() {
    }

    @Override
    protected String getDeviceName() {
        return "MetaCheat";
    }

    @Override
    public void tick() {
    }

    @Override
    public void detach() {
        super.detach();
        ui.detach();
    }

    @Override
    public void attach() {
        ui = JaceApplication.getApplication().showMetacheat();
        ui.registerMetacheatEngine(this);
        super.attach();
    }

    public int getStartAddress() {
        return startAddress;
    }

    public int getEndAddress() {
        return endAddress;
    }

    public void setByteSized(boolean b) {
        byteSized = b;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

    public void setSearchChangeType(SearchChangeType searchChangeType) {
        this.searchChangeType = searchChangeType;
    }

    public Property<Boolean> signedProperty() {
        return signedProperty;
    }

    public Property<String> searchValueProperty() {
        return searchValueProperty;
    }

    public Property<String> searchChangeByProperty() {
        return changeByProperty;
    }

    public ObservableList<RAMListener> getCheats() {
        return cheatList;
    }

    public ObservableList<SearchResult> getSearchResults() {
        return resultList;
    }

    public ObservableList<State> getSnapshots() {
        return snapshotList;
    }

    public Property<String> startAddressProperty() {
        return startAddressProperty;
    }

    public Property<String> endAddressProperty() {
        return endAddressProperty;
    }

    public void newSearch() {
        RAM memory = Emulator.computer.getMemory();
        resultList.clear();
        int compare = parseInt(searchValueProperty.get());
        for (int i = 0; i < 0x10000; i++) {
            boolean signed = signedProperty.get();
            int val
                    = byteSized
                            ? signed ? memory.readRaw(i) : memory.readRaw(i) & 0x0ff
                            : signed ? memory.readWordRaw(i) : memory.readWordRaw(i) & 0x0ffff;
            if (!searchType.equals(SearchType.VALUE) || val == compare) {
                SearchResult result = new SearchResult(i, val);
                resultList.add(result);
            }
        }
    }

    public void performSearch() {
        RAM memory = Emulator.computer.getMemory();
        boolean signed = signedProperty.get();
        resultList.removeIf((SearchResult result) -> {
            int val = byteSized
                    ? signed ? memory.readRaw(result.address) : memory.readRaw(result.address) & 0x0ff
                    : signed ? memory.readWordRaw(result.address) : memory.readWordRaw(result.address) & 0x0ffff;
            int last = result.lastObservedValue;
            result.lastObservedValue = val; 
            switch (searchType) {
                case VALUE:
                    int compare = parseInt(searchValueProperty.get());
                    return compare != val;
                case CHANGE:
                    switch (searchChangeType) {
                        case AMOUNT:
                            int amount = parseInt(searchChangeByProperty().getValue());
                            return (val - last) != amount;
                        case GREATER:
                            return val <= last;
                        case ANY_CHANGE:
                            return val == last;
                        case LESS:
                            return val >= last;
                        case NO_CHANGE:
                            return val != last;
                    }
                    break;
                case TEXT:
                    break;
            }
            return false;
        });
    }

}
