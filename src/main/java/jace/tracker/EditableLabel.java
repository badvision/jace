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
package jace.tracker;

import jace.core.Utility;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class EditableLabel extends JPanel implements MouseListener, FocusListener {

    public static EditableLabel FOCUS;
    public static int DEFAULT_GAP = 4;
    Component editComponent;
    JLabel labelComponent;
    CardLayout layout;
    boolean isEditing = false;
    int width = 4;
    Object ownerObject;
    String objectProperty;

    private void showBlankValue() {
        String s = "...................".substring(0, width);
        labelComponent.setText(s);
    }

    public static enum cards {

        label, edit
    };

    public EditableLabel(JLabel label, Component edit, int width, Object owner, String property) {
        this(label, edit, width, DEFAULT_GAP, DEFAULT_GAP, owner, property);
    }

    public EditableLabel(JLabel label, Component edit, int width, int horizontalPadding, int verticalPadding, Object owner, String property) {
        ownerObject = owner;
        this.width = width;
        objectProperty = property;
        addMouseListener(this);
        edit.addFocusListener(this);
        addFocusListener(this);
        layout = new CardLayout(horizontalPadding, verticalPadding);
        setLayout(layout);
        add(label, cards.label.toString());
        add(edit, cards.edit.toString());
        labelComponent = label;
        editComponent = edit;
        deactivateEdit();
        setBackground(UserInterface.Theme.background.color);
        label.setForeground(UserInterface.Theme.foreground.color);
        label.setOpaque(false);
        edit.setBackground(UserInterface.Theme.backgroundEdit.color);
        edit.setForeground(UserInterface.Theme.foregroundEdit.color);
        edit.setFocusTraversalKeysEnabled(false);
        edit.addKeyListener(NAV_LISTENER);
        label.addKeyListener(NAV_LISTENER);
        this.addKeyListener(NAV_LISTENER);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        mousePressed(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (isEditing) {
            return;
        }
        activateEdit();
        // This next bit will generate a second mouse event and pass it on to the edit component so that 
        // the edit cursor appears under the mouse pointer, not a the start of the component.
        final MouseEvent e2 = new MouseEvent(editComponent, e.getID(), e.getWhen(), e.getModifiers(), e.getX(), e.getY(), e.getClickCount(), e.isPopupTrigger(), e.getButton());
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                editComponent.dispatchEvent(e2);
            }
        });
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (e.getComponent() == this || e.getComponent() == labelComponent || e.getComponent() == editComponent) {
            activateEdit();
        } else {
            deactivateEdit();
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        deactivateEdit();
    }

    private void activateEdit() {
        FOCUS = this;
        isEditing = true;
        layout.show(this, cards.edit.toString());
        editComponent.requestFocusInWindow();
    }

    private void deactivateEdit() {
        isEditing = false;
        if (editComponent instanceof JTextField) {
            String value = ((JTextComponent) editComponent).getText();
            if (value != null) {
                value = value.trim();
                if (value.length() > width) {
                    value = value.substring(0, width);
                }
            }
            Object result = Utility.setProperty(ownerObject, objectProperty, value, true);

            value = (result == null) ? null : (result instanceof Integer ? Integer.toString((Integer) result, 16) : result.toString());
            if (value != null && value.length() < width) {
                value = value.concat("          ".substring(0, width - value.length()));
            }
            if (value == null || value.equals("")) {
                showBlankValue();
                ((JTextComponent) editComponent).setText(null);
            } else {
                labelComponent.setText(value);
                ((JTextComponent) editComponent).setText(value.trim());
            }
        } else if (editComponent instanceof JComboBox) {
            ImageIcon selection = (ImageIcon) ((JComboBox) editComponent).getSelectedItem();
            labelComponent.setText(selection.getDescription());
        }
        layout.show(this, cards.label.toString());
    }

    public static EditableLabel generateTextLabel(Object owner, String property, int width, KeyListener listener) {
        EditableLabel label = generateTextLabel(owner, property, width);
        label.editComponent.addKeyListener(listener);
        return label;
    }

    public static EditableLabel generateTextLabel(Object owner, String property, int width) {
        Object value = Utility.getProperty(owner, property);
        JLabel label = new JLabel(value != null ? value.toString() : null);
        JTextField editor = new JTextField("");
        editor.setCaretColor(UserInterface.Theme.foregroundEdit.color);
        editor.setBorder(new EmptyBorder(0, 0, 0, 0));
        label.setFont(UserInterface.EDITOR_FONT);
        editor.setFont(UserInterface.EDITOR_FONT);

        EditableLabel output = new EditableLabel(label, editor, width, owner, property);
        if (value == null || value.equals("")) {
            output.showBlankValue();
        }
        return output;
    }
    static KeyListener NAV_LISTENER = new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_TAB:
                    if (e.isShiftDown()) {
                        moveLeft();
                    } else {
                        moveRight();
                    }
                    e.consume();
                    break;
                case KeyEvent.VK_UP:
                    moveUp();
                    e.consume();
                    break;
                case KeyEvent.VK_DOWN:
                    moveDown();
                    e.consume();
                    break;
                default:
                    break;
            }
        }

        public int getIndex(Component c) {
            System.out.println("Looking for " + c.getClass().getName() + " in parent " + c.getParent().getClass().getName());
            for (int i = 0; i < c.getParent().getComponentCount(); i++) {
                if (c == c.getParent().getComponent(i)) {
                    return i;
                }
            }
            return -1;
        }

        public void focus(final Component c) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    if (c instanceof EditableLabel) {
                        ((EditableLabel) c).activateEdit();
                    } else {
                        c.requestFocusInWindow();
                    }
                }
            };
            EventQueue.invokeLater(r);
        }

        public void moveDown() {
            Component c = FOCUS;
            int col = getIndex(c);
            int row = getIndex(c.getParent()) + 1;
            if (row < c.getParent().getParent().getComponentCount()) {
                System.out.println("Trying to focus on col " + col + " row " + row);
                focus(((Container) c.getParent().getParent().getComponent(row)).getComponent(col));
            }
        }

        public void moveUp() {
            Component c = FOCUS;
            int col = getIndex(c);
            int row = getIndex(c.getParent()) - 1;
            if (row >= 0) {
                System.out.println("Trying to focus on col " + col + " row " + row);
                focus(((Container) c.getParent().getParent().getComponent(row)).getComponent(col));
            }
        }

        public void moveLeft() {
            Component c = FOCUS;
            int col = getIndex(c) - 1;
            System.out.println("Trying to focus on col " + col);
            if (col >= 0) {
                focus(c.getParent().getComponent(col));
            }
        }

        public void moveRight() {
            Component c = FOCUS;
            int col = getIndex(c) + 1;
            System.out.println("Trying to focus on col " + col);
            if (col < c.getParent().getComponentCount()) {
                focus(c.getParent().getComponent(col));
            }
        }
    };
}
