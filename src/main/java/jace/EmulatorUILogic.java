/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace;

import static jace.core.Utility.gripe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.config.ConfigurableField;
import jace.config.ConfigurationUIController;
import jace.config.InvokableAction;
import jace.config.Reconfigurable;
import jace.core.Debugger;
import jace.core.RAM;
import jace.core.RAMListener;
import jace.ide.IdeController;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * This class contains miscellaneous user-invoked actions such as debugger
 * operations and running arbitrary files in the emulator. It is possible for
 * these methods to be later refactored into more sensible locations. Created on
 * April 16, 2007, 10:30 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class EmulatorUILogic implements Reconfigurable {

    static Debugger debugger;

    static {
        debugger = new Debugger() {
            @Override
            public void updateStatus() {
                enableDebug(true);
                MOS65C02 cpu = (MOS65C02) Emulator.withComputer(c->c.getCpu(), null);
                updateCPURegisters(cpu);
            }
        };
    }
    
    @ConfigurableField(
            category = "General",
            name = "Show Drives"
    )
    public boolean showDrives = true;

    public static void updateCPURegisters(MOS65C02 cpu) {
//        DebuggerPanel debuggerPanel = Emulator.getFrame().getDebuggerPanel();
//        debuggerPanel.valueA.setText(Integer.toHexString(cpu.A));
//        debuggerPanel.valueX.setText(Integer.toHexString(cpu.X));
//        debuggerPanel.valueY.setText(Integer.toHexString(cpu.Y));
//        debuggerPanel.valuePC.setText(Integer.toHexString(cpu.getProgramCounter()));
//        debuggerPanel.valueSP.setText(Integer.toHexString(cpu.getSTACK()));
//        debuggerPanel.valuePC2.setText(cpu.getFlags());
//        debuggerPanel.valueINST.setText(cpu.disassemble());
    }

    public static void enableDebug(boolean b) {
//        DebuggerPanel debuggerPanel = Emulator.getFrame().getDebuggerPanel();
//        debugger.setActive(b);
//        debuggerPanel.enableDebug.setSelected(b);
//        debuggerPanel.setBackground(
//                b ? Color.RED : new Color(0, 0, 0x040));
    }

    public static void enableTrace(boolean b) {
        Emulator.withComputer(c->c.getCpu().setTraceEnabled(b));
    }

    public static void stepForward() {
        debugger.step = true;
    }

    static void registerDebugger() {
        Emulator.withComputer(c->c.getCpu().setDebug(debugger));
    }

    public static Integer getValidAddress(String s) {
        try {
            int addr = Integer.parseInt(s.toUpperCase(), 16);
            if (addr >= 0 && addr < 0x10000) {
                return addr;
            }
            return null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    public static List<RAMListener> watches = new ArrayList<>();

//    public static void updateWatchList(final DebuggerPanel panel) {
//        java.awt.EventQueue.invokeLater(() -> {
//            watches.stream().forEach((oldWatch) -> {
//                Emulator.getComputer().getMemory().removeListener(oldWatch);
//            });
//            if (panel == null) {
//                return;
//            }
//            addWatch(panel.textW1, panel.valueW1);
//            addWatch(panel.textW2, panel.valueW2);
//            addWatch(panel.textW3, panel.valueW3);
//            addWatch(panel.textW4, panel.valueW4);
//        });
//    }
//
//    private static void addWatch(JTextField watch, final JLabel watchValue) {
//        final Integer address = getValidAddress(watch.getText());
//        if (address != null) {
//            //System.out.println("Adding watch for "+Integer.toString(address, 16));
//            RAMListener newListener = new RAMListener(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
//                @Override
//                protected void doConfig() {
//                    setScopeStart(address);
//                }
//
//                @Override
//                protected void doEvent(RAMEvent e) {
//                    watchValue.setText(Integer.toHexString(e.getNewValue() & 0x0FF));
//                }
//            };
//            Emulator.getComputer().getMemory().addListener(newListener);
//            watches.add(newListener);
//            // Print out the current value right away
//            byte b = Emulator.getComputer().getMemory().readRaw(address);
//            watchValue.setText(Integer.toString(b & 0x0ff, 16));
//        } else {
//            watchValue.setText("00");
//        }
//    }
//    public static void updateBreakpointList(final DebuggerPanel panel) {
//        java.awt.EventQueue.invokeLater(() -> {
//            Integer address;
//            debugger.getBreakpoints().clear();
//            if (panel == null) {
//                return;
//            }
//            address = getValidAddress(panel.textBP1.getText());
//            if (address != null) {
//                debugger.getBreakpoints().add(address);
//            }
//            address = getValidAddress(panel.textBP2.getText());
//            if (address != null) {
//                debugger.getBreakpoints().add(address);
//            }
//            address = getValidAddress(panel.textBP3.getText());
//            if (address != null) {
//                debugger.getBreakpoints().add(address);
//            }
//            address = getValidAddress(panel.textBP4.getText());
//            if (address != null) {
//                debugger.getBreakpoints().add(address);
//            }
//            debugger.updateBreakpoints();
//        });
//    }
//
    @InvokableAction(
            name = "BRUN file",
            category = "file",
            description = "Loads a binary file in memory and executes it. File should end with #06xxxx, where xxxx is the start address in hex",
            alternatives = "Execute program;Load binary;Load program;Load rom;Play single-load game",
            defaultKeyMapping = "ctrl+shift+b")
    public static void runFile() {
        Emulator.whileSuspended(c-> {
            FileChooser select = new FileChooser();
            File binary = select.showOpenDialog(JaceApplication.getApplication().primaryStage);
            if (binary != null) {
                runFileNamed(binary);
            }
        });
    }

    public static void runFileNamed(File binary) {
        String fileName = binary.getName().toLowerCase();
        try {
            if (fileName.contains("#06")) {
                String addressStr = fileName.substring(fileName.length() - 4);
                int address = Integer.parseInt(addressStr, 16);
                brun(binary, address);
            } else if (fileName.contains("#fc")) {
                gripe("BASIC not supported yet");
            }
        } catch (NumberFormatException | IOException ex) {
        }
    }

    public static void brun(File binary, int address) throws IOException {
        byte[] data;
        try (FileInputStream in = new FileInputStream(binary)) {
            data = new byte[in.available()];
            in.read(data);
        }

        Emulator.whileSuspended(c-> {
            RAM ram = c.getMemory();
            for (int i = 0; i < data.length; i++) {
                ram.write(address + i, data[i], false, true);
            }
            c.getCpu().setProgramCounter(address);
        });
    }

    @InvokableAction(
            name = "Toggle Debug",
            category = "debug",
            description = "Show/hide the debug panel",
            alternatives = "Show Debug;Hide Debug;Inspect",
            defaultKeyMapping = "ctrl+shift+d")
    public static void toggleDebugPanel() {
//        AbstractEmulatorFrame frame = Emulator.getFrame();
//        if (frame == null) {
//            return;
//        }
//        frame.setShowDebug(!frame.isShowDebug());
//        frame.reconfigure();
//        Emulator.resizeVideo();
    }

    @InvokableAction(
            name = "Toggle fullscreen",
            category = "general",
            description = "Activate/deactivate fullscreen mode",
            alternatives = "fullscreen;maximize",
            defaultKeyMapping = "ctrl+shift+f")
    public static void toggleFullscreen() {
        Platform.runLater(() -> {
            Stage stage = JaceApplication.getApplication().primaryStage;
            stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
            stage.setFullScreen(!stage.isFullScreen());
            JaceApplication.getApplication().controller.setAspectRatioEnabled(stage.isFullScreen());
        });
    }

    @InvokableAction(
            name = "Save Raw Screenshot",
            category = "general",
            description = "Save raw (RAM) format of visible screen",
            alternatives = "screendump;raw screenshot",
            defaultKeyMapping = "ctrl+shift+z")
    public static void saveScreenshotRaw() throws IOException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
        String timestamp = df.format(new Date());
        String type;
        int start = Emulator.withComputer(c->c.getVideo().getCurrentWriter().actualWriter().getYOffset(0), 0);
        int len;
        if (start < 0x02000) {
            // Lo-res or double-lores
            len = 0x0400;
            type = "gr";
        } else {
            // Hi-res or double-hires
            len = 0x02000;
            type = "hgr";
        }
        boolean dres = SoftSwitches._80COL.getState() && (SoftSwitches.DHIRES.getState() || start < 0x02000);
        if (dres) {
            type = "d" + type;
        }
        File outFile = new File("screen_" + type + "_a" + Integer.toHexString(start) + "_" + timestamp);
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            Emulator.whileSuspended(c -> {
                RAM128k ram = (RAM128k) c.getMemory();
                try {
                    if (dres) {
                        for (int i = 0; i < len; i++) {
                            out.write(ram.getAuxVideoMemory().readByte(start + i));
                        }
                    }
                    for (int i = 0; i < len; i++) {
                        out.write(ram.getMainMemory().readByte(start + i));
                    }
                } catch (IOException e) {
                    Logger.getLogger(EmulatorUILogic.class.getName()).log(Level.SEVERE, "Error writing screenshot", e);
                }
            });
        }
        System.out.println("Wrote screenshot to " + outFile.getAbsolutePath());
    }

    @InvokableAction(
            name = "Save Screenshot",
            category = "general",
            description = "Save image of visible screen",
            alternatives = "Save image;save framebuffer;screenshot",
            defaultKeyMapping = "ctrl+shift+s")
    public static void saveScreenshot() throws IOException {
        FileChooser select = new FileChooser();
        // Image i = Emulator.getComputer().getVideo().getFrameBuffer();
//        BufferedImage bufImageARGB = SwingFXUtils.fromFXImage(i, null);
        File targetFile = select.showSaveDialog(JaceApplication.getApplication().primaryStage);
        if (targetFile == null) {
            return;
        }
        String filename = targetFile.getName();
        System.out.println("Writing screenshot to " + filename);
        // String extension = filename.substring(filename.lastIndexOf(".") + 1);
//        BufferedImage bufImageRGB = new BufferedImage(bufImageARGB.getWidth(), bufImageARGB.getHeight(), BufferedImage.OPAQUE);
//
//        Graphics2D graphics = bufImageRGB.createGraphics();
//        graphics.drawImage(bufImageARGB, 0, 0, null);
//
//        ImageIO.write(bufImageRGB, extension, targetFile);
//        graphics.dispose();
    }

    public static final String CONFIGURATION_DIALOG_NAME = "Configuration";

    @InvokableAction(
            name = "Configuration",
            category = "general",
            description = "Edit emulator configuraion",
            alternatives = "Reconfigure;Preferences;Settings;Config",
            defaultKeyMapping = {"f4", "ctrl+shift+c"})
    public static void showConfig() {
        FXMLLoader fxmlLoader = new FXMLLoader(EmulatorUILogic.class.getResource("/fxml/Configuration.fxml"));
        fxmlLoader.setResources(null);
        try {
            Stage configWindow = new Stage();
            AnchorPane node = fxmlLoader.load();
            ConfigurationUIController controller = fxmlLoader.getController();
            controller.initialize();
            Scene s = new Scene(node);
            configWindow.setScene(s);
            configWindow.show();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @InvokableAction(
            name = "Open IDE",
            category = "development",
            description = "Open new IDE window for Basic/Assembly/Plasma coding",
            alternatives = "IDE;dev;development;acme;assembler;editor",
            defaultKeyMapping = {"ctrl+shift+i"})
    public static void showIDE() {
        FXMLLoader fxmlLoader = new FXMLLoader(EmulatorUILogic.class.getResource("/fxml/editor.fxml"));
        fxmlLoader.setResources(null);
        try {
            Stage editorWindow = new Stage();
            AnchorPane node = fxmlLoader.load();
            IdeController controller = fxmlLoader.getController();
            controller.initialize();
            Scene s = new Scene(node);
            editorWindow.setScene(s);
            editorWindow.show();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    static int size = -1;

    @InvokableAction(
            name = "Resize window",
            category = "general",
            description = "Resize the screen to 1x/1.5x/2x/3x video size",
            alternatives = "Aspect;Adjust screen;Adjust window size;Adjust aspect ratio;Fix screen;Fix window size;Fix aspect ratio;Correct aspect ratio;",
            defaultKeyMapping = {"ctrl+shift+a"})
    public static void scaleIntegerRatio() {
        Platform.runLater(() -> {
            if (JaceApplication.getApplication() == null
                    || JaceApplication.getApplication().primaryStage == null) {
                return;
            }
            Stage stage = JaceApplication.getApplication().primaryStage;
            size++;
            if (size > 3) {
                size = 0;
            }
            if (stage.isFullScreen()) {
                JaceApplication.getApplication().controller.toggleAspectRatio();
            } else {
                int width, height;
                switch (size) {
                    case 0 -> {
                        // 1x
                        width = 560;
                        height = 384;
                    }
                    case 1 -> {
                        // 1.5x
                        width = 840;
                        height = 576;
                    }
                    case 2 -> {
                        // 2x
                        width = 560 * 2;
                        height = 384 * 2;
                    }
                    case 3 -> {
                        // 3x (retina) 2880x1800
                        width = 560 * 3;
                        height = 384 * 3;
                    }
                    default -> {
                        // 2x
                        width = 560 * 2;
                        height = 384 * 2;
                    }
                }
                double vgap = stage.getScene().getY();
                double hgap = stage.getScene().getX();
                stage.setWidth(hgap * 2 + width);
                stage.setHeight(vgap + height);
            }
        });
    }
    
    @InvokableAction(
            name = "About",
            category = "general",
            description = "Display about window",
            alternatives = "info;credits",
            defaultKeyMapping = {"ctrl+shift+."})
    public static void showAboutWindow() {
        //TODO: Implement
    }    

    public static boolean confirm(String message) {
//        return JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(Emulator.getFrame(), message);
        return false;
    }

    static final Map<Object, Set<Label>> INDICATORS = new HashMap<>();

    static public void addIndicator(Object owner, Label icon) {
        addIndicator(owner, icon, 250);
    }

    static public void addIndicator(Object owner, Label icon, long TTL) {
        if (JaceApplication.getApplication() == null) {
            return;
        }
        synchronized (INDICATORS) {
            Set<Label> ind = INDICATORS.get(owner);
            if (ind == null) {
                ind = new HashSet<>();
                INDICATORS.put(owner, ind);
            }
            ind.add(icon);
            JaceApplication.getApplication().controller.addIndicator(icon);
        }
    }

    static public void removeIndicator(Object owner, Label icon) {
        if (JaceApplication.singleton == null) {
            return;
        }
        synchronized (INDICATORS) {
            Set<Label> ind = INDICATORS.get(owner);
            if (ind != null) {
                ind.remove(icon);
            }
            JaceApplication.singleton.controller.removeIndicator(icon);
        }
    }

    static public void removeIndicators(Object owner) {
        if (JaceApplication.singleton == null) {
            return;
        }
        synchronized (INDICATORS) {
            Set<Label> ind = INDICATORS.get(owner);
            if (ind == null) {
                return;
            }
            ind.stream().forEach((icon) -> {
                JaceApplication.singleton.controller.removeIndicator(icon);
            });
            INDICATORS.remove(owner);
        }
    }

    static public void addMouseListener(EventHandler<MouseEvent> handler) {
        if (JaceApplication.singleton != null) {
            JaceApplication.singleton.controller.addMouseListener(handler);
        }
    }

    static public void removeMouseListener(EventHandler<MouseEvent> handler) {
        if (JaceApplication.singleton != null) {
            JaceApplication.singleton.controller.removeMouseListener(handler);
        }
    }

    public static void simulateCtrlAppleReset() {
        Emulator.withComputer(c -> {
            c.getKeyboard().openApple(true);
            c.warmStart();
            Platform.runLater(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Logger.getLogger(EmulatorUILogic.class.getName()).log(Level.SEVERE, null, ex);
                }
                c.getKeyboard().openApple(false);
            });
        });
    }

    public static void notify(String message) {
        if (JaceApplication.singleton != null) {
            JaceApplication.singleton.controller.displayNotification(message);
        }
    }

    @Override
    public String getName() {
        return "Jace User Interface";
    }

    @Override
    public String getShortName() {
        return "UI";
    }

    @Override
    public void reconfigure() {
    }
}
