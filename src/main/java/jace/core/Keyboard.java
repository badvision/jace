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
package jace.core;

import jace.EmulatorUILogic;
import jace.apple2e.SoftSwitches;
import jace.apple2e.Speaker;
import jace.apple2e.softswitch.KeyboardSoftSwitch;
import jace.config.Reconfigurable;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keyboard manages all keyboard-related activities. For now, all hotkeys are
 * hard-coded. The eventual direction for this class is to only manage key
 * handlers for all keys and provide remapping -- but it's not there yet.
 * Created on March 29, 2007, 11:32 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Keyboard implements Reconfigurable {
    private Computer computer;
    public Keyboard(Computer computer) {
        this.computer = computer;
    }
    @Override
    public String getShortName() {
        return "kbd";
    }
    static byte currentKey = 0;

    public static void clearStrobe() {
        currentKey = (byte) (currentKey & 0x07f);
    }

    public static void pressKey(byte key) {
        currentKey = (byte) (0x0ff & (0x080 | key));
    }

    public static byte readState() {
        // If strobe was cleared...
        if ((currentKey & 0x080) == 0) {
            // Call clipboard buffer paste routine
            int newKey = Keyboard.getClipboardKeystroke();
            if (newKey >= 0) {
                pressKey((byte) newKey);
            }
        }
        return currentKey;
    }

    /**
     * Creates a new instance of Keyboard
     */
    public Keyboard() {
    }
    private static Map<Integer, Set<KeyHandler>> keyHandlersByKey = new HashMap<>();
    private static Map<Object, Set<KeyHandler>> keyHandlersByOwner = new HashMap<>();

    public static void registerKeyHandler(KeyHandler l, Object owner) {
        if (!keyHandlersByKey.containsKey(l.key)) {
            keyHandlersByKey.put(l.key, new HashSet<>());
        }
        keyHandlersByKey.get(l.key).add(l);
        if (!keyHandlersByOwner.containsKey(owner)) {
            keyHandlersByOwner.put(owner, new HashSet<>());
        }
        keyHandlersByOwner.get(owner).add(l);
    }

    public static void unregisterAllHandlers(Object owner) {
        if (!keyHandlersByOwner.containsKey(owner)) {
            return;
        }
        keyHandlersByOwner.get(owner).stream().filter((handler) -> !(!keyHandlersByKey.containsKey(handler.key))).forEach((handler) -> {
            keyHandlersByKey.get(handler.key).remove(handler);
        });
    }

    public static void processKeyDownEvents(KeyEvent e) {
        if (keyHandlersByKey.containsKey(e.getKeyCode())) {
            for (KeyHandler h : keyHandlersByKey.get(e.getKeyCode())) {
                if (h.modifiers != e.getModifiers() && h.modifiers != -1) {
                    continue;
                }
                boolean isHandled = h.handleKeyDown(e);
                if (isHandled) {
                    e.consume();
                    return;
                }
            }
        }
    }

    public static void processKeyUpEvents(KeyEvent e) {
        if (keyHandlersByKey.containsKey(e.getKeyCode())) {
            for (KeyHandler h : keyHandlersByKey.get(e.getKeyCode())) {
                if (h.modifiers != e.getModifiers() && h.modifiers != -1) {
                    continue;
                }
                boolean isHandled = h.handleKeyUp(e);
                if (isHandled) {
                    e.consume();
                    return;
                }
            }
        }
    }

    public KeyListener getListener() {
        return new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                processKeyDownEvents(e);
                if (e.getKeyCode() == 0 || e.isConsumed()) {
                    return;
                }

                KeyboardSoftSwitch key =
                        (KeyboardSoftSwitch) SoftSwitches.KEYBOARD.getSwitch();
                char c = e.getKeyChar();
                if ((e.getModifiers() & (KeyEvent.ALT_MASK|KeyEvent.META_MASK|KeyEvent.META_DOWN_MASK)) > 0) {
                    // explicit left and right here because other locations
                    // can be sent as well, e.g. KEY_LOCATION_STANDARD
                    if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT) {
                        pressOpenApple();
                    } else if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) {
                        pressSolidApple();
                    }
                }

                int code = e.getKeyCode();

                switch (code) {
                    case KeyEvent.VK_LEFT:
                    case KeyEvent.VK_KP_LEFT:
                        c = 8;
                        break;
                    case KeyEvent.VK_RIGHT:
                    case KeyEvent.VK_KP_RIGHT:
                        c = 21;
                        break;
                    case KeyEvent.VK_UP:
                    case KeyEvent.VK_KP_UP:
                        c = 11;
                        break;
                    case KeyEvent.VK_DOWN:
                    case KeyEvent.VK_KP_DOWN:
                        c = 10;
                        break;
                    case KeyEvent.VK_TAB:
                        c = 9;
                        break;
                    case KeyEvent.VK_ENTER:
                        c = 13;
                        break;
                    case KeyEvent.VK_BACK_SPACE:
                        c = 127;
                        break;
                    default:
                        if ((e.getModifiers() & KeyEvent.CTRL_DOWN_MASK) > 0) {
                            c = (char) (code - 'A' + 1);
                        }
                }

                if (c < 128) {
                    pressKey((byte) c);
                }

//                e.consume();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                processKeyUpEvents(e);
                if (code == 0 || e.isConsumed()) {
                    return;
                }
                if (code == KeyEvent.VK_INSERT && e.isShiftDown()) {
                    doPaste();
                }
                if (code == KeyEvent.VK_F10) {
                    EmulatorUILogic.toggleDebugPanel();
                }
                if ((code == KeyEvent.VK_F12 || code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_PAUSE) && ((e.getModifiers() & KeyEvent.CTRL_MASK) > 0)) {
                    computer.warmStart();
                }
                if (code == KeyEvent.VK_F1) {
                    EmulatorUILogic.showMediaManager();
                }
                if (code == KeyEvent.VK_F4) {
                    EmulatorUILogic.showConfig();
                }
                if (code == KeyEvent.VK_F7) {
                    Speaker.toggleFileOutput();
                }
                if (code == KeyEvent.VK_F8) {
                    EmulatorUILogic.scaleIntegerRatio();
                }
                if (code == KeyEvent.VK_F9) {
                    EmulatorUILogic.toggleFullscreen();
                }
                if (code == KeyEvent.VK_PRINTSCREEN || code == KeyEvent.VK_SCROLL_LOCK) {
                    try {
                        if (e.isShiftDown()) {
                            EmulatorUILogic.saveScreenshotRaw();
                        } else {
                            EmulatorUILogic.saveScreenshot();
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(Keyboard.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    computer.resume();
                }
                if ((e.getModifiers() & (KeyEvent.ALT_MASK|KeyEvent.META_MASK|KeyEvent.META_DOWN_MASK)) > 0) {
                    // explicit left and right here because other locations
                    // can be sent as well, e.g. KEY_LOCATION_STANDARD
                    if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT) {
                        releaseOpenApple();
                    } else if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) {
                        releaseSolidApple();
                    }
                }

                e.consume();
//                e.setKeyChar((char) 0);
//                e.setKeyCode(0);
            }

            private void pressOpenApple() {
                computer.pause();
                SoftSwitches.PB0.getSwitch().setState(true);
                computer.resume();
            }

            private void pressSolidApple() {
                computer.pause();
                SoftSwitches.PB1.getSwitch().setState(true);
                computer.resume();
            }

            private void releaseOpenApple() {
                computer.pause();
                SoftSwitches.PB0.getSwitch().setState(false);
                computer.resume();
            }

            private void releaseSolidApple() {
                computer.pause();
                SoftSwitches.PB1.getSwitch().setState(false);
                computer.resume();
            }
        };
    }

    public static void doPaste(String text) {
        pasteBuffer = new StringReader(text);
    }

    private static void doPaste() {
        try {
            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            String contents = (String) clip.getData(DataFlavor.stringFlavor);
            if (contents != null && !"".equals(contents)) {
                contents = contents.replaceAll("\\n(\\r)?", (char) 0x0d + "");
                pasteBuffer = new StringReader(contents);
            }
        } catch (UnsupportedFlavorException | IOException ex) {
            Logger.getLogger(Keyboard.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

    }
    static StringReader pasteBuffer = null;

    public static int getClipboardKeystroke() {
        if (pasteBuffer == null) {
            return -1;
        }

        try {
            int keypress = pasteBuffer.read();
            // Handle end of paste buffer
            if (keypress == -1) {
                pasteBuffer.close();
                pasteBuffer = null;
                return -1;
            }

            KeyboardSoftSwitch key =
                    (KeyboardSoftSwitch) SoftSwitches.KEYBOARD.getSwitch();
            return (keypress & 0x0ff);


        } catch (IOException ex) {
            Logger.getLogger(Keyboard.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }

    @Override
    public String getName() {
        return "Keyboard";
    }

    @Override
    public void reconfigure() {
    }
}
