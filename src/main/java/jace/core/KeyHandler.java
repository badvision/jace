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
            init(KeyCodeCombination.valueOf(comboText));
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
