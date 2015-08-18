/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package jace;

import com.sun.glass.ui.Application;
import jace.cheat.MetaCheat;
import jace.core.Card;
import jace.core.Computer;
import jace.library.MediaCache;
import jace.library.MediaConsumer;
import jace.library.MediaConsumerParent;
import jace.library.MediaEntry;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 *
 * @author blurry
 */
public class JaceUIController {

    @FXML
    private URL location;

    @FXML
    private AnchorPane rootPane;
    
    @FXML
    private StackPane stackPane;

    @FXML
    private HBox notificationBox;

    @FXML
    private ImageView appleScreen;

    Computer computer;
    
    @FXML
    void initialize() {
        assert rootPane != null : "fx:id=\"rootPane\" was not injected: check your FXML file 'JaceUI.fxml'.";
        assert stackPane != null : "fx:id=\"stackPane\" was not injected: check your FXML file 'JaceUI.fxml'.";
        assert notificationBox != null : "fx:id=\"notificationBox\" was not injected: check your FXML file 'JaceUI.fxml'.";
        assert appleScreen != null : "fx:id=\"appleScreen\" was not injected: check your FXML file 'JaceUI.fxml'.";
        appleScreen.fitWidthProperty().bind(rootPane.widthProperty());
        appleScreen.fitHeightProperty().bind(rootPane.heightProperty());
        rootPane.setOnDragEntered(this::processDragEnteredEvent);
        rootPane.setOnDragExited(this::processDragExitedEvent);
    }
    
    public void connectComputer(Computer computer) {
        this.computer = computer;
        appleScreen.setImage(computer.getVideo().getFrameBuffer());
        EventHandler<KeyEvent> keyboardHandler = computer.getKeyboard().getListener();
        rootPane.setFocusTraversable(true);
        rootPane.setOnKeyPressed(keyboardHandler);
        rootPane.setOnKeyReleased(keyboardHandler);
        rootPane.requestFocus();
    }
    
    private void processDragEnteredEvent(DragEvent evt) {
        MediaEntry media = null;
        if (evt.getDragboard().hasFiles()) {
            media = MediaCache.getMediaFromFile(getDraggedFile(evt.getDragboard().getFiles()));
        } else if (evt.getDragboard().hasUrl()) {
            media = MediaCache.getMediaFromUrl(evt.getDragboard().getUrl());
        } else if (evt.getDragboard().hasString()) {
            String path = evt.getDragboard().getString();
            try {
                URI.create(path);
                media = MediaCache.getMediaFromUrl(path);
            } catch (IllegalArgumentException ex) {
                File f = new File(path);
                if (f.exists()) {
                    media = MediaCache.getMediaFromFile(f);
                }
            }
        }
        if (media != null) {
            evt.acceptTransferModes(TransferMode.LINK, TransferMode.COPY);
            startDragEvent(media);
        }
    }
    
    private void processDragExitedEvent(DragEvent evt) {
        endDragEvent();
    }

    
    private File getDraggedFile(List<File> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        for (File f : files) {
            if (f.isFile()) return f;
        }
        return null;
    }

    HBox drivePanel;
    private void startDragEvent(MediaEntry media) {
        List<MediaConsumer> consumers = getMediaConsumers();
        drivePanel = new HBox();
        consumers.stream()
            .filter((consumer) -> (consumer.isAccepted(media, media.files.get(0))))
            .forEach((consumer) -> {
                Label icon = consumer.getIcon();
                icon.setTextFill(Color.WHITE);
                icon.setPadding(new Insets(2.0));
                drivePanel.getChildren().add(icon);
                icon.setOnDragOver(event -> {
                    event.acceptTransferModes(TransferMode.ANY);
                    event.consume();
                });
                icon.setOnDragDropped(event -> {
                    System.out.println("Dropping media on "+icon.getText());
                    try {
                        computer.pause();
                        consumer.insertMedia(media, media.files.get(0));
                        computer.resume();
                        event.setDropCompleted(true);
                        event.consume();
                    } catch (IOException ex) {
                        Logger.getLogger(JaceUIController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    endDragEvent();
                });
            });
        stackPane.getChildren().add(drivePanel);        
        drivePanel.setLayoutX(10);
        drivePanel.setLayoutY(10);
    }
    
    private void endDragEvent() {
        stackPane.getChildren().remove(drivePanel);
        drivePanel.getChildren().stream().forEach((n) -> {
            n.setOnDragDropped(null);
        });
    }
    
    private List<MediaConsumer> getMediaConsumers() {
        List<MediaConsumer> consumers = new ArrayList<>();
        for (Optional<Card> card : computer.memory.getAllCards()) {
            card.filter(c -> c instanceof MediaConsumerParent).ifPresent(parent -> {
                consumers.addAll(Arrays.asList(((MediaConsumerParent) parent).getConsumers()));
            });
        }
        return consumers;
    }
    
    Map<Label, Long> iconTTL = new ConcurrentHashMap<>();
    void addIndicator(Label icon) {
        addIndicator(icon, 250);
    }
    void addIndicator(Label icon, long TTL) {
        if (!iconTTL.containsKey(icon)) {
            Application.invokeLater(()->{
                if (!notificationBox.getChildren().contains(icon)) {
                    notificationBox.getChildren().add(icon);
                }
            });
        }
        trackTTL(icon, TTL);
    }

    void removeIndicator(Label icon) {
        Application.invokeLater(()->{
            notificationBox.getChildren().remove(icon);
            iconTTL.remove(icon);
        });
    }    

    ScheduledExecutorService notificationExecutor = Executors.newSingleThreadScheduledExecutor();
    ScheduledFuture ttlCleanupTask = null;
    private void trackTTL(Label icon, long TTL) {
        iconTTL.put(icon, System.currentTimeMillis()+TTL);
        
        if (ttlCleanupTask == null || ttlCleanupTask.isCancelled()) {        
            ttlCleanupTask = notificationExecutor.scheduleWithFixedDelay(this::processTTL, 1, 100, TimeUnit.MILLISECONDS);
        }
    }
    
    private void processTTL() {
        Long now = System.currentTimeMillis();
        iconTTL.keySet().stream()
            .filter((icon) -> (iconTTL.get(icon) <= now))
            .forEach((icon) -> {
                removeIndicator(icon);
            });
        if (iconTTL.isEmpty()) {
            ttlCleanupTask.cancel(true);
            ttlCleanupTask = null;
        }
    }
    
    public void addMouseListener(EventHandler<MouseEvent> handler) {
        appleScreen.addEventHandler(MouseEvent.ANY, handler);
    }
    
    public void removeMouseListener(EventHandler<MouseEvent> handler) {
        appleScreen.removeEventHandler(MouseEvent.ANY, handler);
    }
}
