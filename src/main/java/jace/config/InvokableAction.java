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

package jace.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A invokable action annotation means that an object method can be called by the end-user.
 * This serves as a hook for keybindings as well as semantic navigation potential.
 * <br>
 * Name should be short, meaningful, and succinct. e.g. "Insert disk"
 * <br>
 * Category can be used to group actions by overall topic, for example an automated table of contents
 * <br>
 * Description is descriptive text which provides additional clarity, e.g. 
 * "This will present you with a file selection dialog to pick a floppy disk image.  
 *  Currently, dos-ordered (DSK, DO), Prodos-ordered (PO), and Nibble (NIB) formats are supported.
 * <br>
 * Alternatives should be delimited by semicolons) can provide more powerful search
 * For "insert disk", alternatives might be "change disk;switch disk" and 
 * reboot might have alternatives as "warm start;cold start;boot;restart".
 * <hr>
 * NOTE: Any method that implements this must be public and take no parameters!
 * If a method signature is not correct, it will result in a runtime exception
 * when the action is triggered.  There is no way to offer a compiler
 * warning to avoid this, unfortunately.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokableAction {
    /*
     * Should be short and meaningful name for action being invoked, e.g. "Insert disk"
     */
    String name();
    /*
     * Can be used to group actions by overall topic, for example an automated table of contents
     * To be determined...
     */
    String category() default "General";
    /*
     * More descriptive text which provides additional clarity, e.g. 
     * "This will present you with a file selection dialog to pick a floppy disk image.  
     *  Currently, dos-ordered (DSK, DO), Prodos-ordered (PO), and Nibble (NIB) formats are supported."
     */
    String description() default "";
    /*
     * Alternatives should be delimited by semicolons) can provide more powerful search
     * For "insert disk", alternatives might be "change disk;switch disk" and 
     * reboot might have alternatives as "warm start;cold start;boot;restart".
     */
    String alternatives() default "";
    /*
     * If true, the key event will be consumed and not processed by any other event handlers
     * If the corresponding method returns a boolean, that value will be used instead.
     * True = consume (stop processing keystroke), false = pass-through to other handlers
     */
    boolean consumeKeyEvent() default true;
    /*
     * If false (default) event is only triggered on press, not release.  If true,
     * method is notified on press and on release
     */
    boolean notifyOnRelease() default false;
    /*
     * Standard keyboard mapping
     */
    String[] defaultKeyMapping();
}