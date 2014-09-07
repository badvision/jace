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
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class IntegerComponent extends StringComponent {

    @Override
    public void keyReleased(KeyEvent e) {
        String t = getText();
        if (t == null || t.equals("")) {
            try {
                ConfigurableField f = node.subject.getClass().getField(fieldName).getAnnotation(ConfigurableField.class);
                t = f.defaultValue();
                if (t == null || t.equals("")) {
                    t = "0";
                }
//                setText(t);
            } catch (NoSuchFieldException ex) {
                Logger.getLogger(IntegerComponent.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(IntegerComponent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        try {
            int i = Integer.parseInt(t);
            node.setFieldValue(fieldName, i);
            setBackground(Color.white);
        } catch (NumberFormatException ex) {
            setBackground(Color.red);
        }
    }


    public IntegerComponent(ConfigNode node, String fieldName) {
        super(node, fieldName);
        setColumns(10);
    }
}
