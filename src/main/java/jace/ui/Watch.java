/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jace.ui;

import jace.Emulator;
import jace.cheat.MemoryCell;
import jace.core.RAMListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 *
 * @author blurry
 */
class Watch extends VBox {

    private static final int GRAPH_WIDTH = 50;
    private static final double GRAPH_HEIGHT = 50;
    int address;
    ScheduledFuture redraw;
    Canvas graph;
    List<Integer> samples = Collections.synchronizedList(new ArrayList<>());
    int value = 0;
    BooleanProperty holding = null;
    private final MetacheatUI outer;
    MemoryCell cell;

    public Watch(int address, final MetacheatUI outer) {
        super();
        this.outer = outer;
        this.address = address;
        cell = outer.cheatEngine.getMemoryCell(address);
        redraw = outer.animationTimer.scheduleAtFixedRate(this::redraw, MetacheatUI.FRAME_RATE, MetacheatUI.FRAME_RATE, TimeUnit.MILLISECONDS);
        setBackground(new Background(new BackgroundFill(Color.NAVY, CornerRadii.EMPTY, Insets.EMPTY)));
        Label addrLabel = new Label("$" + Integer.toHexString(address));
        addrLabel.setOnMouseClicked((evt)-> outer.inspectAddress(address));
        addrLabel.setTextAlignment(TextAlignment.CENTER);
        addrLabel.setMinWidth(GRAPH_WIDTH);
        addrLabel.setFont(new Font(Font.getDefault().getFamily(), 14));
        addrLabel.setTextFill(Color.WHITE);
        graph = new Canvas(GRAPH_WIDTH, GRAPH_HEIGHT);
        getChildren().add(addrLabel);
        getChildren().add(graph);
        CheckBox hold = new CheckBox("Hold");
        holding = hold.selectedProperty();
        holding.addListener((prop, oldVal, newVal) -> this.updateHold());
        getChildren().add(hold);
        hold.setTextFill(Color.WHITE);
    }

    public int getValue() {
        return value;
    }

    public void redraw() {
        if (!Emulator.computer.getRunningProperty().get()) {
            return;
        }
        int val = cell.value.get() & 0x0ff;
        if (!holding.get()) {
            value = val;
        }
        if (samples.size() >= GRAPH_WIDTH) {
            samples.remove(0);
        }
        samples.add(val);
        Platform.runLater(() -> {
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
        });
    }
    RAMListener holdListener;

    public BooleanProperty holdingProperty() {
        return holding;
    }

    private void updateHold() {
        if (!holding.get()) {
            outer.cheatEngine.removeListener(holdListener);
            holdListener = null;
        } else {
            value = Emulator.computer.memory.readRaw(address) & 0x0ff;
            holdListener = outer.cheatEngine.forceValue(value, address);
        }
    }

    public void disconnect() {
        holding.set(false);
        redraw.cancel(false);
    }

}
