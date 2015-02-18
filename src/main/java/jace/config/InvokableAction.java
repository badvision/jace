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
package jace.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A invokable action annotation means that an object method can be called by the end-user.
 * This serves as a hook for keybindings as well as semantic navigation potential.
 * <br/>
 * Name should be short, meaningful, and succinct. e.g. "Insert disk"
 * <br/>
 * Category can be used to group actions by overall topic, for example an automated table of contents
 * <br/>
 * Description is descriptive text which provides additional clarity, e.g. 
 * "This will present you with a file selection dialog to pick a floppy disk image.  
 *  Currently, dos-ordered (DSK, DO), Prodos-ordered (PO), and Nibble (NIB) formats are supported.
 * <br/>
 * Alternatives should be delimited by semicolons) can provide more powerful search
 * For "insert disk", alternatives might be "change disk;switch disk" and 
 * reboot might have alternatives as "warm start;cold start;boot;restart".
 * <hr/>
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
    public String name();
    /*
     * Can be used to group actions by overall topic, for example an automated table of contents
     * To be determined...
     */
    public String category() default "General";
    /*
     * More descriptive text which provides additional clarity, e.g. 
     * "This will present you with a file selection dialog to pick a floppy disk image.  
     *  Currently, dos-ordered (DSK, DO), Prodos-ordered (PO), and Nibble (NIB) formats are supported."
     */
    public String description() default "";
    /*
     * Alternatives should be delimited by semicolons) can provide more powerful search
     * For "insert disk", alternatives might be "change disk;switch disk" and 
     * reboot might have alternatives as "warm start;cold start;boot;restart".
     */
    public String alternatives() default "";
    /*
     * If true, the key event will be consumed and not processed by any other event handlers
     * If the corresponding method returns a boolean, that value will be used instead.
     * True = consume (stop processing keystroke), false = pass-through to other handlers
     */
    public boolean consumeKeyEvent() default true;
    /*
     * If false (default) event is only triggered on press, not release.  If true,
     * method is notified on press and on release
     */
    public boolean notifyOnRelease() default false;
    /*
     * Standard keyboard mapping
     */
    public String[] defaultKeyMapping();
}