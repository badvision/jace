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

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

/**
 * Listen for a specific key or set of keys If there is a match, the handleKeyUp
 * or handleKeyDown methods will be called. This is meant to save a lot of extra
 * conditional logic elsewhere.
 *
 * The handler methods should return true if they have consumed the key event
 * and do not want any other processing to continue for that keypress.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class KeyHandler {
    public KeyCombination keyCodeCombination;

    public KeyCode key;
    public KeyHandler(String comboText) {
        KeyCode testCode = KeyCode.getKeyCode(comboText);
        if (testCode != null) {
            key = testCode;
        } else {
            init((KeyCodeCombination) KeyCodeCombination.valueOf(comboText));
        }
    }
    
    public KeyHandler(KeyCodeCombination keyCodeCombo) {
        init(keyCodeCombo);
    }
    
    private void init(KeyCombination keyCodeCombo) {
        keyCodeCombination = keyCodeCombo;
        if (keyCodeCombo instanceof KeyCodeCombination) {
            key = ((KeyCodeCombination) keyCodeCombo).getCode();
        }
    }
    
    public boolean match(KeyEvent e) {
        if (keyCodeCombination != null) {
            return keyCodeCombination.match(e);
        } else {
            return e.getCode().equals(key);
        }
    }

    public abstract boolean handleKeyUp(KeyEvent e);

    public abstract boolean handleKeyDown(KeyEvent e);

    public String getComboName() {
        if (keyCodeCombination != null) {
            return keyCodeCombination.getName();
        } else {
            return key.getName();
        }
    }

    public String getKeyName() {
        if (key != null) {
            return key.getName();
        } else {
            return null;
        }
    }
}
