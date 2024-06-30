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

package jace.core;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.apple2e.SoftSwitches;
import jace.config.InvokableAction;
import jace.config.Reconfigurable;
import javafx.event.EventHandler;
import javafx.scene.input.Clipboard;
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

    public void resetState() {
        clearStrobe();
        openApple(false);
        solidApple(false);
    }

    @Override
    public String getShortName() {
        return "kbd";
    }
    static byte currentKey = 0;
    public boolean shiftPressed = false;

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
    private static final Map<KeyCode, Set<KeyHandler>> keyHandlersByKey = new HashMap<>();
    private static final Map<Object, Set<KeyHandler>> keyHandlersByOwner = new HashMap<>();

    /**
     *
     * @param action
     * @param owner
     * @param method
     * @param code
     */
    public static void registerInvokableAction(InvokableAction action, Object owner, Function<Boolean, Boolean> method, String code) {
        registerKeyHandler(new KeyHandler(code) {
            @Override
            public boolean handleKeyUp(KeyEvent e) {
                Emulator.withComputer(c -> c.getKeyboard().shiftPressed = e.isShiftDown());
                if (action == null || !action.notifyOnRelease()) {
                    return false;
                }
                return method.apply(false) && action.consumeKeyEvent();
            }

            @Override
            public boolean handleKeyDown(KeyEvent e) {
//                System.out.println("Key down: "+method.toString());
                Emulator.withComputer(c -> c.getKeyboard().shiftPressed = e.isShiftDown());
                if (action == null) {
                    return false;
                }
                return method.apply(true) && action.consumeKeyEvent();
            }
        }, owner);
    }

    public static void registerInvokableAction(InvokableAction action, Object owner, BiFunction<Object, Boolean, Boolean> method, String code) {
        registerKeyHandler(new KeyHandler(code) {
            @Override
            public boolean handleKeyUp(KeyEvent e) {
                Emulator.withComputer(c -> c.getKeyboard().shiftPressed = e.isShiftDown());
                if (action == null || !action.notifyOnRelease()) {
                    return false;
                }
                return method.apply(owner, false) && action.consumeKeyEvent();
            }

            @Override
            public boolean handleKeyDown(KeyEvent e) {
//                System.out.println("Key down: "+method.toString());
                Emulator.withComputer(c -> c.getKeyboard().shiftPressed = e.isShiftDown());
                if (action == null) {
                    return false;
                }
                return method.apply(owner, true) && action.consumeKeyEvent();
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
//        System.out.println("Registered handler for "+l.getComboName()+"; code is "+l.getKeyName());
    }

    public static void unregisterAllHandlers(Object owner) {
        if (!keyHandlersByOwner.containsKey(owner)) {
            return;
        }
        keyHandlersByOwner.get(owner).stream().filter((handler) -> keyHandlersByKey.containsKey(handler.key)).forEach(
                (handler) -> keyHandlersByKey.get(handler.key).remove(handler));
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

        Emulator.withComputer(computer -> computer.getKeyboard().shiftPressed = e.isShiftDown());
        if (e.isShiftDown()) {
            c = fixShiftedChar(c);
        }

        if (e.isControlDown()) {
            if (c == 255) {
                return;
            }
            c = (char) (c & 0x01f);
        }
        
        if (c < 128) {
            pressKey((byte) c);
        }
    }

    private char fixShiftedChar(char c) {
        if (c >= 'a' && c <= 'z') {
            return (char) (c - 32);
        } else {
            switch (c) {
                case '0': return ')';
                case '1': return '!';
                case '2': return '@';
                case '3': return '#';
                case '4': return '$';
                case '5': return '%';
                case '6': return '^';
                case '7': return '&';
                case '8': return '*';
                case '9': return '(';
                case '-': return '_';
                case '=': return '+';
                case '[': return '{';
                case ']': return '}';
                case '\\': return '|';
                case ';': return ':';
                case '\'': return '"';
                case ',': return '<';
                case '.': return '>';
                case '/': return '?';
                case '`': return '~';                    
            }
        }
        return c;
    }
    
    public void keyReleased(KeyEvent e) {
        KeyCode code = e.getCode();
        processKeyUpEvents(e);
        if (code == null || e.isConsumed()) {
            return;
        }
        
        e.consume();
    }

    public static boolean isOpenApplePressed = false;
    @InvokableAction(name = "Open Apple Key", alternatives = "OA", category = "Keyboard", notifyOnRelease = true, defaultKeyMapping = "Alt", consumeKeyEvent = false)
    public void openApple(boolean pressed) {
        isOpenApplePressed = pressed;
        SoftSwitches.PB0.getSwitch().setState(pressed);
    }

    public static boolean isClosedApplePressed = false;
    @InvokableAction(name = "Closed Apple Key", alternatives = "CA", category = "Keyboard", notifyOnRelease = true, defaultKeyMapping = {"Shortcut","Meta","Command"}, consumeKeyEvent = false)
    public void solidApple(boolean pressed) {
        isClosedApplePressed = pressed;
        SoftSwitches.PB1.getSwitch().setState(pressed);
    }

    public static void pasteFromString(String text) {
        text = text.replaceAll("\\r?\\n|\\r", (char) 0x0d + "");
        pasteBuffer = new StringReader(text);
    }

    @InvokableAction(name = "Paste clipboard", alternatives = "paste", category = "Keyboard", notifyOnRelease = false, defaultKeyMapping = {"Ctrl+Shift+V","Shift+Insert"}, consumeKeyEvent = true)
    public static void pasteFromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            pasteFromString(clipboard.getString());
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
