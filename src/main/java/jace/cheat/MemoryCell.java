/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jace.cheat;

import java.util.ArrayList;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 * @author blurry
 */
public class MemoryCell implements Comparable<MemoryCell> {
    public static ChangeListener<MemoryCell> listener;
    public int address;
    public IntegerProperty value = new SimpleIntegerProperty();
    public IntegerProperty readCount = new SimpleIntegerProperty();
    public IntegerProperty execCount = new SimpleIntegerProperty();
    public IntegerProperty writeCount = new SimpleIntegerProperty();
    public BooleanBinding hasCount = readCount.add(execCount).add(writeCount).greaterThan(0);
    public ObservableList<Integer> readInstructions = FXCollections.observableList(new ArrayList<>());
    public ObservableList<String> readInstructionsDisassembly = FXCollections.observableArrayList(new ArrayList<>());
    public ObservableList<Integer> writeInstructions = FXCollections.observableList(new ArrayList<>());
    public ObservableList<String> writeInstructionsDisassembly = FXCollections.observableArrayList(new ArrayList<>());
    public ObservableList<String> execInstructionsDisassembly = FXCollections.observableArrayList(new ArrayList<>());
    private int x;
    private int y;
    private int width;
    private int height;

    public static void setListener(ChangeListener<MemoryCell> l) {
        listener = l;
    }

    public MemoryCell() {
        ChangeListener<Number> changeListener = (ObservableValue<? extends Number> val, Number oldVal, Number newVal) -> {
            if (listener != null) {
                listener.changed(null, this, this);
            }
        };
        value.addListener(changeListener);
    }

    public void setRect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public int compareTo(MemoryCell o) {
        return address - o.address;
    }
    
    public boolean hasCounts() {
        return hasCount.get();
    }    
}
