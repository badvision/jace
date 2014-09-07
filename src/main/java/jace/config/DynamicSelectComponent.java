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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.event.ListDataListener;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
class DynamicSelectComponent extends JComboBox implements ActionListener {

    ConfigNode node;
    String fieldName;
    Serializable currentValue;

    @Override
    public void actionPerformed(ActionEvent e) {
        node.setFieldValue(fieldName, currentValue);
    }

    public void synchronizeValue() {
        try {
            Object value = node.getFieldValue(fieldName);
            if (value == null) {
                getModel().setSelectedItem(null);
                setSelectedItem(getModel().getSelectedItem());
            } else {
                getModel().setSelectedItem(value);
                setSelectedItem(getModel().getSelectedItem());
            }
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(StringComponent.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public DynamicSelectComponent(ConfigNode node, String fieldName) {
        try {
            this.node = node;
            this.fieldName = fieldName;
            DynamicSelection sel;
            try {
                sel = (DynamicSelection) node.subject.getClass().getField(fieldName).get(node.subject);
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(DynamicSelectComponent.class.getName()).log(Level.SEVERE, null, ex);
                System.err.print("Couldn't get selections for field " + fieldName);
                return;
            } catch (IllegalAccessException ex) {
                Logger.getLogger(DynamicSelectComponent.class.getName()).log(Level.SEVERE, null, ex);
                System.err.print("Couldn't get selections for field " + fieldName);
                return;
            } catch (NoSuchFieldException ex) {
                Logger.getLogger(DynamicSelectComponent.class.getName()).log(Level.SEVERE, null, ex);
                System.err.print("Couldn't get selections for field " + fieldName);
                return;
            } catch (SecurityException ex) {
                Logger.getLogger(DynamicSelectComponent.class.getName()).log(Level.SEVERE, null, ex);
                System.err.print("Couldn't get selections for field " + fieldName);
                return;
            }
            currentValue = node.getFieldValue(fieldName);
            final LinkedHashMap selections = sel.getSelections();

            addActionListener(this);
            ComboBoxModel m;

            setModel(new ComboBoxModel() {

                Entry value;

                public void setSelectedItem(Object anItem) {
                    if (anItem != null && anItem instanceof Map.Entry) {
                        value = (Entry) anItem;
                        currentValue = (Serializable) ((Entry) anItem).getKey();
                    } else {
                        for (Map.Entry entry : (Set<Map.Entry>) selections.entrySet()) {
                            if (entry.getValue().equals(anItem)) {
                                value = entry;
                                currentValue = (Serializable) entry.getKey();
                            }
                            if (entry.getKey() == null && anItem == null) {
                                value = entry;
                                currentValue = (Serializable) entry.getKey();
                            }
                            if (entry.getKey() != null && entry.equals(anItem)) {
                                value = entry;
                                currentValue = (Serializable) entry.getKey();
                            }
                        }
                    }

                }

                public Object getSelectedItem() {
                    return selections.get(currentValue);
                }

                public int getSize() {
                    return selections.size();
                }

                public Object getElementAt(int index) {
                    for (Map.Entry entry : (Set<Map.Entry>) selections.entrySet()) {
                        if (index == 0) {
                            return entry.getValue();
                        }
                        index--;
                    }
                    return null;
                }

                public void addListDataListener(ListDataListener l) {
                }

                public void removeListDataListener(ListDataListener l) {
                }
            });
            synchronizeValue();
        } catch (SecurityException ex) {
            Logger.getLogger(DynamicSelectComponent.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
