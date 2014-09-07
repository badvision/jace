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

import jace.config.Configuration.ConfigNode;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextField;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
class StringComponent extends JTextField implements KeyListener {
    ConfigNode node;
    String fieldName;

    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
    }
    
    public void keyReleased(KeyEvent e) {
        node.setFieldValue(fieldName, getText());
    }

    public StringComponent(ConfigNode node, String fieldName) {
        this.node = node;
        this.fieldName = fieldName;
        synchronizeValue();
        addKeyListener(this);
    }

    public void synchronizeValue() {
        try {
            Object value = node.getFieldValue(fieldName);
            if (value == null) {
                setText("");
            } else {
                setText(String.valueOf(value));
            }
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(StringComponent.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
