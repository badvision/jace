/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package jace.cheat;

import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.KeyHandler;
import jace.core.Keyboard;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javax.swing.table.DefaultTableModel;

/**
 * Basic mame-style cheats.  The user interface is in MetaCheatForm.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class MetaCheats extends Cheats {
    static MetaCheats singleton = null;
    // This is used to help the handler exit faster when there is nothing to do
    boolean noCheats = true;
    
    public MetaCheats(Computer computer) {
        super(computer);
        singleton = this;
    }
    
    
    Map<Integer, Integer> holdBytes = new TreeMap<>();
    Map<Integer, Integer> holdWords = new TreeMap<>();
    Set<Integer> disabled = new HashSet<>();
    Map<Integer, Integer> results = new TreeMap<>();
    @Override
    protected String getDeviceName() {
        return "Meta-cheat engine";
    }

    @Override
    public void tick() {
        // Do nothing
    }

    public static int MAX_RESULTS_SHOWN = 256;
    
    public MetaCheatForm form = null;
    public boolean isDrawing = false;
    public void redrawResults() {
        if (isDrawing) {
            return;
        }
        isDrawing = true;
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException ex) {
            Logger.getLogger(MetaCheats.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        RAM128k ram = (RAM128k) computer.getMemory();
        DefaultTableModel model = (DefaultTableModel) form.resultsTable.getModel();
        if (results.size() > MAX_RESULTS_SHOWN) {
            model.setRowCount(0);
            isDrawing = false;
            return;
        }
        boolean useWord = form.searchForWord.isSelected();
        if (model.getRowCount() != results.size()) {
            model.setRowCount(0);
            List<Integer> iter = new ArrayList<>(results.keySet());
            iter.stream().forEach((i) -> {
                int val = results.get(i);
                if (useWord) {
                    int current = ram.readWordRaw(i) & 0x0ffff;
                    model.addRow(new Object[]{hex(i, 4), val + " ("+hex(val,4)+")", current + " ("+hex(current,4)+")"});
                } else {                
                    int current = ram.readRaw(i) & 0x0ff;
                    model.addRow(new Object[]{hex(i, 4), val + " ("+hex(val,2)+")", current + " ("+hex(current,2)+")"});
                }
            });
        } else {
            List<Integer> iter = new ArrayList<>(results.keySet());
            for (int i=0; i < iter.size(); i++) {
                int val = results.get(iter.get(i));
                if (useWord) {
                    int current = ram.readWordRaw(iter.get(i)) & 0x0ffff;
                    model.setValueAt(val + " ("+hex(val,4)+")", i, 1);
                    model.setValueAt(current + " ("+hex(current,4)+")", i, 2);
                } else {                
                    int current = ram.readRaw(iter.get(i)) & 0x0ff;
                    model.setValueAt(val + " ("+hex(val,2)+")", i, 1);
                    model.setValueAt(current + " ("+hex(current,2)+")", i, 2);
                }
            }            
        }
        isDrawing = false;
    }
    
    public static String hex(int val, int size) {
        String out = Integer.toHexString(val);
        while (out.length() < size) {
            out = "0"+out;
        }
        return "$"+out;
    }
    
    public void redrawCheats() {
        noCheats = holdBytes.isEmpty() && holdWords.isEmpty() && disabled.isEmpty();
        DefaultTableModel model = (DefaultTableModel) form.activeCheatsTable.getModel();
        model.setRowCount(0);
        holdBytes.keySet().stream().forEach((i) -> {
            String loc = hex(i, 4);
            if (disabled.contains(i)) loc += " (off)";
            int val = holdBytes.get(i);
            model.addRow(new Object[]{loc, val + " ("+hex(val,2)+")"});
        });
        holdWords.keySet().stream().forEach((i) -> {
            String loc = hex(i, 4);
            if (disabled.contains(i)) loc += " (off)";
            int val = holdWords.get(i);
            model.addRow(new Object[]{loc, val + " ("+hex(val,4)+")"});
        });
    }
    public void showCheatForm() {
        if (form == null) {
            form = new MetaCheatForm();
        }
        form.setVisible(true);
    }

    MemorySpy spy = null;
    public void showMemorySpy() {
        if (spy == null) {
            spy = new MemorySpy();
        }
        spy.setVisible(true);
    }
    
    
    @Override
    public void attach() {
        this.addCheat(new RAMListener(RAMEvent.TYPE.READ, RAMEvent.SCOPE.ANY, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
            }

            @Override
            protected void doEvent(RAMEvent e) {
                if (noCheats) return;
                if (disabled.contains(e.getAddress())) return;
                if (holdBytes.containsKey(e.getAddress())) {
                    e.setNewValue(holdBytes.get(e.getAddress()));
                } else if (holdWords.containsKey(e.getAddress())) {
                    e.setNewValue(holdWords.get(e.getAddress()) & 0x0ff);
                } else if (holdWords.containsKey(e.getAddress()-1)) {
                    if (disabled.contains(e.getAddress()-1)) return;
                    e.setNewValue((holdWords.get(e.getAddress()-1)>>8) & 0x0ff);                
                }
            }
        });
        this.addCheat(new RAMListener(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ANY, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
            }

            @Override
            protected void doEvent(RAMEvent e) {
                if (results.isEmpty()) return;                
                if (results.size() > MAX_RESULTS_SHOWN || isDrawing) return;
                if (results.containsKey(e.getAddress()) || results.containsKey(e.getAddress()-1)) {
                    Thread t = new Thread(() -> {
                        redrawResults();
                    });
                    t.setName("Metacheat results updater");
                    t.start();
                }
            }
        });
        
        Keyboard.registerKeyHandler(new KeyHandler(KeyCode.END) {
            @Override
            public boolean handleKeyUp(KeyEvent e) {
                showCheatForm();
                return false;
            }

            @Override
            public boolean handleKeyDown(KeyEvent e) {
                return false;
            }
        }, this);
        Keyboard.registerKeyHandler(new KeyHandler(KeyCode.HOME) {
            @Override
            public boolean handleKeyUp(KeyEvent e) {
                showMemorySpy();
                return false;
            }

            @Override
            public boolean handleKeyDown(KeyEvent e) {
                return false;
            }
        }, this);
    }

    @Override
    public void detach() {
        super.detach();
        Keyboard.unregisterAllHandlers(this);
    }

    public void addByteCheat(int addr, int val) {
        holdBytes.put(addr, val);        
        redrawCheats();
    }

    public void addWordCheat(int addr, int val) {
        holdWords.put(addr, val);
        redrawCheats();
    }

    void removeCheat(int i) {
        holdBytes.remove(i);
        holdWords.remove(i);
        disabled.remove(i);
        redrawCheats();
    }

    void resetSearch() {
        results.clear();
        redrawResults();
    }

    void performSearch(boolean useDeltaSearch, boolean searchForByteValues, int val) {
        RAM128k ram = (RAM128k) computer.getMemory();
        if (results.isEmpty()) {
            int max = 0x010000;
            if (!searchForByteValues) max--;
            for (int i=0; i < max; i++) {
                if (i >= 0x0c000 && i <= 0x0cfff) continue;
                int v = searchForByteValues ? ram.readRaw(i) & 0x0ff : ram.readWordRaw(i) & 0x0ffff;
                if (useDeltaSearch) {
                    results.put(i, v);
                } else if (v == val) {
                    results.put(i, v);
                }
            }
        } else {
            Set<Integer> remove = new HashSet<>();
            results.keySet().stream().forEach((i) -> {
                int v = searchForByteValues ? ram.readRaw(i) & 0x0ff : ram.readWordRaw(i) & 0x0ffff;
                if (useDeltaSearch) {
                    if (v - results.get(i) != val) {
                        remove.add(i);
                    } else {
                        results.put(i,v);
                    }
                } else {
                    if (v != val) {
                        remove.add(i);
                    } else {
                        results.put(i,v);
                    }
                }
            });
            remove.stream().forEach((i) -> {
                results.remove(i);
            });
        }
        form.resultsStatusLabel.setText("Search found "+results.size()+" result(s).");
        redrawResults();
    }

    void enableCheat(int addr) {
        disabled.remove(addr);
        redrawCheats();
    }

    void disableCheat(int addr) {
        disabled.add(addr);
        redrawCheats();
    }

    void addWatches(int addrStart, int addrEnd) {
        RAM128k ram = (RAM128k) computer.getMemory();
        if (form == null) return;
        boolean searchForByteValues = form.searchForByte.isSelected();
        for (int i = addrStart; i <= addrEnd; i = i + (searchForByteValues ? 1 : 2)) {
            int v = searchForByteValues ? ram.readRaw(i) & 0x0ff : ram.readWordRaw(i) & 0x0ffff;
            results.put(i,v);            
        }
        redrawResults();
    }
}
