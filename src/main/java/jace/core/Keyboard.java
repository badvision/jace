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

import jace.apple2e.SoftSwitches;
import jace.config.InvokableAction;
import jace.config.Reconfigurable;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

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
    private static Map<KeyCode, Set<KeyHandler>> keyHandlersByKey = new HashMap<>();
    private static Map<Object, Set<KeyHandler>> keyHandlersByOwner = new HashMap<>();

    public static void registerInvokableAction(InvokableAction action, Object owner, Method method, String code) {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        registerKeyHandler(new KeyHandler(code) {
            @Override
            public boolean handleKeyUp(KeyEvent e) {
                if (!action.notifyOnRelease()) {
                    return false;
                }
                System.out.println("Key up: "+method.toString());
                Object returnValue = null;
                try {
                    if (method.getParameterCount() > 0) {
                        returnValue = method.invoke(isStatic ? null : owner, true);
                    } else {
                        returnValue = method.invoke(isStatic ? null : owner);
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    Logger.getLogger(Keyboard.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (returnValue != null) {
                    return (Boolean) returnValue;
                }
                return action.consumeKeyEvent();
            }

            @Override
            public boolean handleKeyDown(KeyEvent e) {
                System.out.println("Key down: "+method.toString());
                Object returnValue = null;
                try {
                    if (method.getParameterCount() > 0) {
                        returnValue = method.invoke(isStatic ? null : owner, true);
                    } else {
                        returnValue = method.invoke(isStatic ? null : owner);
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    Logger.getLogger(Keyboard.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (returnValue != null) {
                    return (Boolean) returnValue;
                }
                return action.consumeKeyEvent();
            }
        }, owner);
    }

    public static void registerKeyHandler(KeyHandler l, Object owner) {
        if (!keyHandlersByKey.containsKey(l.key)) {
            keyHandlersByKey.put(l.key, new HashSet<>());
        }
        keyHandlersByKey.get(l.key).add(l);
        if (!keyHandlersByOwner.containsKey(owner)) {
            keyHandlersByOwner.put(owner, new HashSet<>());
        }
        keyHandlersByOwner.get(owner).add(l);
        System.out.println("Registered handler for "+l.getComboName()+"; code is "+l.getKeyName());
    }

    public static void unregisterAllHandlers(Object owner) {
        if (!keyHandlersByOwner.containsKey(owner)) {
            return;
        }
        keyHandlersByOwner.get(owner).stream().filter((handler) -> !(!keyHandlersByKey.containsKey(handler.key))).forEach((handler) -> {
            keyHandlersByKey.get(handler.key).remove(handler);
        });
        keyHandlersByOwner.remove(owner);
    }

    public static void processKeyDownEvents(KeyEvent e) {
        if (keyHandlersByKey.containsKey(e.getCode())) {
            for (KeyHandler h : keyHandlersByKey.get(e.getCode())) {
                if (!h.match(e)) {
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
        if (keyHandlersByKey.containsKey(e.getCode())) {
            for (KeyHandler h : keyHandlersByKey.get(e.getCode())) {
                if (!h.match(e)) {
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

    public EventHandler<KeyEvent> getListener() {
        return (KeyEvent event) -> {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                keyPressed(event);
            } else if (event.getEventType() == KeyEvent.KEY_RELEASED) {
                keyReleased(event);
            }
        };
    }

    public void keyPressed(KeyEvent e) {
        processKeyDownEvents(e);
        if (e.isConsumed()) {
            return;
        }

        char c = 255;
        if (e.getText().length() > 0) {
            c = e.getText().charAt(0);
        }

        switch (e.getCode()) {
            case LEFT:
            case KP_LEFT:
                c = 8;
                break;
            case RIGHT:
            case KP_RIGHT:
                c = 21;
                break;
            case UP:
            case KP_UP:
                c = 11;
                break;
            case DOWN:
            case KP_DOWN:
                c = 10;
                break;
            case ESCAPE:
                c = 27;
                break;
            case TAB:
                c = 9;
                break;
            case ENTER:
                c = 13;
                break;
            case BACK_SPACE:
                c = 127;
                break;
            default:
        }

        if (c < 128) {
            pressKey((byte) c);
        }
    }

    public void keyReleased(KeyEvent e) {
        KeyCode code = e.getCode();
        processKeyUpEvents(e);
        if (code == null || e.isConsumed()) {
            return;
        }
        
        e.consume();
    }

    @InvokableAction(name = "Open Apple Key", alternatives = "OA", category = "Keyboard", notifyOnRelease = true, defaultKeyMapping = "Alt", consumeKeyEvent = false)
    public void openApple(boolean pressed) {
        computer.pause();
        SoftSwitches.PB0.getSwitch().setState(pressed);
        computer.resume();
    }

    @InvokableAction(name = "Closed Apple Key", alternatives = "CA", category = "Keyboard", notifyOnRelease = true, defaultKeyMapping = "Meta", consumeKeyEvent = false)
    public void solidApple(boolean pressed) {
        computer.pause();
        SoftSwitches.PB1.getSwitch().setState(pressed);
        computer.resume();
    }

    public static void pasteFromString(String text) {
        pasteBuffer = new StringReader(text);
    }

    @InvokableAction(name = "Paste clipboard", alternatives = "paste", category = "Keyboard", notifyOnRelease = false, defaultKeyMapping = {"Ctrl+Shift+V","Shift+Insert"}, consumeKeyEvent = true)
    public static void pasteFromClipboard() {
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
