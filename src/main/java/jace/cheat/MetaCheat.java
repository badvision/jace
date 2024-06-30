package jace.cheat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.JaceApplication;
import jace.core.CPU;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.state.State;
import jace.ui.MetacheatUI;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MetaCheat extends Cheats {
    public enum SearchType {
        VALUE, TEXT, CHANGE
    }

    public enum SearchChangeType {
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

        public int getAddress() {
            return address;
        }
    }

    MetacheatUI ui;

    public int fadeRate = 1;
    public int lightRate = 30;
    public int historyLength = 10;

    private int startAddress = 0;
    private int endAddress = 0x0BFFF;
    private final StringProperty startAddressProperty = new SimpleStringProperty(Integer.toHexString(startAddress));
    private final StringProperty endAddressProperty = new SimpleStringProperty(Integer.toHexString(endAddress));
    private boolean byteSized = true;
    private SearchType searchType = SearchType.VALUE;
    private SearchChangeType searchChangeType = SearchChangeType.NO_CHANGE;
    private final BooleanProperty signedProperty = new SimpleBooleanProperty(false);
    private final StringProperty searchValueProperty = new SimpleStringProperty("0");
    private final StringProperty changeByProperty = new SimpleStringProperty("0");
    private final ObservableList<DynamicCheat> cheatList = FXCollections.observableArrayList();
    private final ObservableList<SearchResult> resultList = FXCollections.observableArrayList();
    private final ObservableList<State> snapshotList = FXCollections.observableArrayList();

    public MetaCheat() {
        addNumericValidator(startAddressProperty);
        addNumericValidator(endAddressProperty);
        addNumericValidator(searchValueProperty);
        addNumericValidator(changeByProperty);
        startAddressProperty.addListener((prop, oldVal, newVal) -> {
            startAddress = Math.max(0, Math.min(65535, parseInt(newVal)));
        });
        endAddressProperty.addListener((prop, oldVal, newVal) -> {
            endAddress = Math.max(0, Math.min(65535, parseInt(newVal)));
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
        if (s == null || s.isEmpty()) {
            return 0;
        }
        if (s.matches("(\\+|-)?[0-9]+")) {
            return Integer.parseInt(s);
        } else {
            String upper = s.toUpperCase();
            boolean positive = !upper.startsWith("-");
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
    public void registerListeners() {
    }

    public void addCheat(DynamicCheat cheat) {
        cheatList.add(cheat);
        getMemory().addListener(cheat);
        cheat.addressProperty().addListener((prop, oldVal, newVal) -> {
            getMemory().removeListener(cheat);
            cheat.doConfig();
            getMemory().addListener(cheat);
        });
    }

    public void removeCheat(DynamicCheat cheat) {
        cheat.active.set(false);
        getMemory().removeListener(cheat);
        cheatList.remove(cheat);
    }

    @Override
    protected void unregisterListeners() {
        super.unregisterListeners();
        cheatList.forEach(getMemory()::removeListener);
    }

    @Override
    protected String getDeviceName() {
        return "MetaCheat";
    }

    @Override
    public void detach() {
        super.detach();
        JaceApplication.getApplication().closeMetacheat();
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

    public ObservableList<DynamicCheat> getCheats() {
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
        Emulator.withMemory(memory -> {
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
        });
    }

    public void performSearch() {
        Emulator.withMemory(memory -> {
            boolean signed = signedProperty.get();
            resultList.removeIf((SearchResult result) -> {
                int val = byteSized
                        ? signed ? memory.readRaw(result.address) : memory.readRaw(result.address) & 0x0ff
                        : signed ? memory.readWordRaw(result.address) : memory.readWordRaw(result.address) & 0x0ffff;
                int last = result.lastObservedValue;
                result.lastObservedValue = val;
                switch (searchType) {
                    case VALUE -> {
                        int compare = parseInt(searchValueProperty.get());
                        return compare != val;
                    }
                    case CHANGE -> {
                        switch (searchChangeType) {
                            case AMOUNT -> {
                                int amount = parseInt(searchChangeByProperty().getValue());
                                return (val - last) != amount;
                            }
                            case GREATER -> {
                                return val <= last;
                            }
                            case ANY_CHANGE -> {
                                return val == last;
                            }
                            case LESS -> {
                                return val >= last;
                            }
                            case NO_CHANGE -> {
                                return val != last;
                            }
                        }
                    }

                    case TEXT -> {
                    }
                }
                return false;
            });
        });
    }

    RAMListener memoryViewListener = null;
    private final Map<Integer, MemoryCell> memoryCells = new ConcurrentHashMap<>();

    public MemoryCell getMemoryCell(int address) {
        return memoryCells.get(address);
    }

    public void initMemoryView() {
        Emulator.withMemory(memory -> {
            for (int addr = getStartAddress(); addr <= getEndAddress(); addr++) {
                if (getMemoryCell(addr) == null) {
                    MemoryCell cell = new MemoryCell();
                    cell.address = addr;
                    cell.value.set(memory.readRaw(addr));
                    memoryCells.put(addr, cell);
                }
            }
            if (memoryViewListener == null) {
                memoryViewListener = memory.observe("Metacheat memory viewer", RAMEvent.TYPE.ANY, startAddress, endAddress, this::processMemoryEvent);
                listeners.add(memoryViewListener);
            }
        });
    }

    int fadeCounter = 0;
    int FADE_TIMER_VALUE = Emulator.withComputer(c-> (int) (c.getMotherboard().getSpeedInHz() / 60), 100);

    @Override
    public void tick() {
        Emulator.withComputer(c-> c.getCpu().performSingleTrace());
        if (fadeCounter-- <= 0) {
            fadeCounter = FADE_TIMER_VALUE;
            memoryCells.values().stream()
                    .filter(MemoryCell::hasCounts)
                    .forEach((cell) -> {
                        cell.execCount.set(Math.max(0, cell.execCount.get() - fadeRate));
                        cell.readCount.set(Math.max(0, cell.readCount.get() - fadeRate));
                        cell.writeCount.set(Math.max(0, cell.writeCount.get() - fadeRate));
                        if (MemoryCell.listener != null) {
                            MemoryCell.listener.changed(null, cell, cell);
                        }
                    });
        }
    }

    AtomicInteger pendingInspectorUpdates = new AtomicInteger(0);
    public void onInspectorChanged() {
        pendingInspectorUpdates.set(0);
    }

    private void processMemoryEvent(RAMEvent e) {
        MemoryCell cell = getMemoryCell(e.getAddress());
        if (cell != null) {
            Emulator.withComputer(c -> {
                CPU cpu = c.getCpu();
                int pc = cpu.getProgramCounter();
                String trace = cpu.getLastTrace();
                switch (e.getType()) {
                    case EXECUTE:
                        cell.execInstructionsDisassembly.add(trace);
                        if (cell.execInstructionsDisassembly.size() > historyLength) {
                            cell.execInstructionsDisassembly.remove(0);
                        }
                    case READ_OPERAND:
                        cell.execCount.set(Math.min(255, cell.execCount.get() + lightRate));
                        break;
                    case WRITE:
                        cell.writeCount.set(Math.min(255, cell.writeCount.get() + lightRate));
                        if (ui.isInspecting(cell.address)) {
                            if (pendingInspectorUpdates.incrementAndGet() < 5) {
                                Platform.runLater(() -> {
                                    pendingInspectorUpdates.decrementAndGet();
                                    cell.writeInstructions.add(pc);
                                    cell.writeInstructionsDisassembly.add(trace);
                                    if (cell.writeInstructions.size() > historyLength) {
                                        cell.writeInstructions.remove(0);
                                        cell.writeInstructionsDisassembly.remove(0);
                                    }
                                });
                            }
                        } else {
                            cell.writeInstructions.add(cpu.getProgramCounter());
                            cell.writeInstructionsDisassembly.add(cpu.getLastTrace());
                            if (cell.writeInstructions.size() > historyLength) {
                                cell.writeInstructions.remove(0);
                                cell.writeInstructionsDisassembly.remove(0);
                            }
                        }
                        break;
                    default:
                        cell.readCount.set(Math.min(255, cell.readCount.get() + lightRate));
                        if (ui.isInspecting(cell.address)) {
                            if (pendingInspectorUpdates.incrementAndGet() < 5) {
                                Platform.runLater(() -> {
                                    pendingInspectorUpdates.decrementAndGet();
                                    cell.readInstructions.add(pc);
                                    cell.readInstructionsDisassembly.add(trace);
                                    if (cell.readInstructions.size() > historyLength) {
                                        cell.readInstructions.remove(0);
                                        cell.readInstructionsDisassembly.remove(0);
                                    }
                                });
                            }
                        } else {
                            cell.readInstructions.add(cpu.getProgramCounter());
                            cell.readInstructionsDisassembly.add(cpu.getLastTrace());
                            if (cell.readInstructions.size() > historyLength) {
                                cell.readInstructions.remove(0);
                                cell.readInstructionsDisassembly.remove(0);
                            }
                        }
                }
                cell.value.set(e.getNewValue());
            });
        }
    }

    public void saveCheats(File saveFile) {
        try (FileWriter writer = new FileWriter(saveFile)) {
            for (DynamicCheat cheat : cheatList) {
                writer.write(cheat.serialize());
                writer.write("\n");
            }
        } catch (IOException ex) {
            Logger.getLogger(MetaCheat.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void loadCheats(File saveFile) {
        try (BufferedReader in = new BufferedReader(new FileReader(saveFile))) {
            String line;
            while ((line = in.readLine()) != null) {
                DynamicCheat cheat = DynamicCheat.deserialize(line);
                addCheat(cheat);
            }
        } catch (IOException ex) {
            Logger.getLogger(MetaCheat.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
