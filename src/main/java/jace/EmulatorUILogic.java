/**
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
package jace;

import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.config.ConfigurationUIController;
import jace.config.InvokableAction;
import jace.config.Reconfigurable;
import jace.core.CPU;
import jace.core.Computer;
import jace.core.Debugger;
import jace.core.RAM;
import jace.core.RAMListener;
import static jace.core.Utility.*;
import jace.ide.IdeController;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
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
                MOS65C02 cpu = (MOS65C02) Emulator.computer.getCpu();
                updateCPURegisters(cpu);
            }
        };
    }

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
        Emulator.computer.getCpu().setTraceEnabled(b);
    }

    public static void stepForward() {
        debugger.step = true;
    }

    static void registerDebugger() {
        Emulator.computer.getCpu().setDebug(debugger);
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
//                Emulator.computer.getMemory().removeListener(oldWatch);
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
//            Emulator.computer.getMemory().addListener(newListener);
//            watches.add(newListener);
//            // Print out the current value right away
//            byte b = Emulator.computer.getMemory().readRaw(address);
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
        Emulator.computer.pause();
        FileChooser select = new FileChooser();
        File binary = select.showOpenDialog(JaceApplication.getApplication().primaryStage);
        if (binary == null) {
            Emulator.computer.resume();
            return;
        }
        runFileNamed(binary);
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
        Emulator.computer.getCpu().resume();
    }

    public static void brun(File binary, int address) throws FileNotFoundException, IOException {
        // If it was halted already, then it was initiated outside of an opcode execution
        // If it was not yet halted, then it is the case that the CPU is processing another opcode
        // So if that is the case, the program counter will need to be decremented here to compensate
        // TODO: Find a better mousetrap for this one -- it's an ugly hack
        Emulator.computer.pause();
        FileInputStream in = new FileInputStream(binary);
        byte[] data = new byte[in.available()];
        in.read(data);
        RAM ram = Emulator.computer.getMemory();
        for (int i = 0; i < data.length; i++) {
            ram.write(address + i, data[i], false, true);
        }
        CPU cpu = Emulator.computer.getCpu();
        Emulator.computer.getCpu().setProgramCounter(address);
        Emulator.computer.resume();
    }

    @InvokableAction(
            name = "Toggle Debug",
            category = "debug",
            description = "Show/hide the debug panel",
            alternatives = "Show Debug;Hide Debug",
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
            alternatives = "fullscreen,maximize",
            defaultKeyMapping = "ctrl+shift+f")
    public static void toggleFullscreen() {
        Platform.runLater(() -> {
            Stage stage = JaceApplication.getApplication().primaryStage;
            stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
            stage.setFullScreen(!stage.isFullScreen());
        });
    }

    @InvokableAction(
            name = "Save Raw Screenshot",
            category = "general",
            description = "Save raw (RAM) format of visible screen",
            alternatives = "screendump, raw screenshot",
            defaultKeyMapping = "ctrl+shift+z")
    public static void saveScreenshotRaw() throws FileNotFoundException, IOException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
        String timestamp = df.format(new Date());
        String type;
        int start = Emulator.computer.getVideo().getCurrentWriter().actualWriter().getYOffset(0);
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
            RAM128k ram = (RAM128k) Emulator.computer.memory;
            Emulator.computer.pause();
            if (dres) {
                for (int i = 0; i < len; i++) {
                    out.write(ram.getAuxVideoMemory().readByte(start + i));
                }
            }
            for (int i = 0; i < len; i++) {
                out.write(ram.getMainMemory().readByte(start + i));
            }
        }
        System.out.println("Wrote screenshot to " + outFile.getAbsolutePath());
    }

    @InvokableAction(
            name = "Save Screenshot",
            category = "general",
            description = "Save image of visible screen",
            alternatives = "Save image,save framebuffer,screenshot",
            defaultKeyMapping = "ctrl+shift+s")
    public static void saveScreenshot() throws IOException {
        FileChooser select = new FileChooser();
        Emulator.computer.pause();
        Image i = Emulator.computer.getVideo().getFrameBuffer();
//        BufferedImage bufImageARGB = SwingFXUtils.fromFXImage(i, null);
        File targetFile = select.showSaveDialog(JaceApplication.getApplication().primaryStage);
        if (targetFile == null) {
            return;
        }
        String filename = targetFile.getName();
        System.out.println("Writing screenshot to " + filename);
        String extension = filename.substring(filename.lastIndexOf(".") + 1);
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
            alternatives = "Reconfigure,Preferences,Settings",
            defaultKeyMapping = {"f4", "ctrl+shift+c"})
    public static void showConfig() {
        FXMLLoader fxmlLoader = new FXMLLoader(EmulatorUILogic.class.getResource("/fxml/Configuration.fxml"));
        fxmlLoader.setResources(null);
        try {
            Stage configWindow = new Stage();
            AnchorPane node = (AnchorPane) fxmlLoader.load();
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
            alternatives = "dev,development,acme,assembler,editor",
            defaultKeyMapping = {"ctrl+shift+i"})
    public static void showIDE() {
        FXMLLoader fxmlLoader = new FXMLLoader(EmulatorUILogic.class.getResource("/fxml/editor.fxml"));
        fxmlLoader.setResources(null);
        try {
            Stage editorWindow = new Stage();
            AnchorPane node = (AnchorPane) fxmlLoader.load();
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
            description = "Resize the screen to 1x/1.5x/2x video size",
            alternatives = "Adjust screen;Adjust window size;Adjust aspect ratio;Fix screen;Fix window size;Fix aspect ratio;Correct aspect ratio;",
            defaultKeyMapping = {"ctrl+shift+a"})
    public static void scaleIntegerRatio() {
        Platform.runLater(() -> {
            JaceApplication.getApplication().primaryStage.setFullScreen(false);
            size++;
            if (size > 2) {
                size = 0;
            }
            switch (size) {
                case 0:
                    JaceApplication.getApplication().primaryStage.setWidth(560);
                    JaceApplication.getApplication().primaryStage.setHeight(384);
                    break;
                case 1:
                    JaceApplication.getApplication().primaryStage.setWidth(840);
                    JaceApplication.getApplication().primaryStage.setHeight(576);
                    break;
                default:
                    JaceApplication.getApplication().primaryStage.setWidth(1020);
                    JaceApplication.getApplication().primaryStage.setHeight(768);
            }
        });
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
        Computer computer = JaceApplication.singleton.controller.computer;
        computer.keyboard.openApple(true);
        computer.warmStart();
        Platform.runLater(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Logger.getLogger(EmulatorUILogic.class.getName()).log(Level.SEVERE, null, ex);
            }
            computer.keyboard.openApple(false);
        });
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
