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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.xml.transform.Source;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class UserInterface {

    static Font EDITOR_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    public static enum Theme {

        background(0x000000),
        foreground(0xffffff),
        backgroundEdit(0x000080),
        foregroundEdit(0xffff80);
        Color color;

        Theme(int col) {
            color = new Color(col & 0x0ffffff);
        }
    }
    public static int BASE_OCTAVE = 3;

    public static enum Note {

        C0("C", -1),
        CS0("CS", -1),
        D0("D", -1),
        DS0("DS", -1),
        E0("E", -1),
        F0("F", -1),
        FS0("FS", -1),
        G0("G", -1),
        GS0("GS", -1),
        A0("A", -1),
        AS0("AS", -1),
        B0("B", -1),
        C1("C", 0),
        CS1("CS", 0),
        D1("D", 0),
        DS1("DS", 0),
        E1("E", 0),
        F1("F", 0),
        FS1("FS", 0),
        G1("G", 0),
        GS1("GS", 0),
        A1("A", 0),
        AS1("AS", 0),
        B1("B", 0),
        C2("C", 1),
        CS2("CS", 1),
        D2("D", 1),
        DS2("DS", 1),
        E2("E", 1);
        public String note;
        public int octaveOffset;

        Note(String n, int offset) {
            note = n;
            octaveOffset = offset;
        }
    };
    public static final Map<Integer, Note> KEYBOARD_MAP = new HashMap<Integer, Note>();

    static {
        KEYBOARD_MAP.put(KeyEvent.VK_Z, Note.C0);
        KEYBOARD_MAP.put(KeyEvent.VK_S, Note.CS0);
        KEYBOARD_MAP.put(KeyEvent.VK_X, Note.D0);
        KEYBOARD_MAP.put(KeyEvent.VK_D, Note.DS0);
        KEYBOARD_MAP.put(KeyEvent.VK_C, Note.E0);
        KEYBOARD_MAP.put(KeyEvent.VK_V, Note.F0);
        KEYBOARD_MAP.put(KeyEvent.VK_G, Note.FS0);
        KEYBOARD_MAP.put(KeyEvent.VK_B, Note.G0);
        KEYBOARD_MAP.put(KeyEvent.VK_H, Note.GS0);
        KEYBOARD_MAP.put(KeyEvent.VK_N, Note.A0);
        KEYBOARD_MAP.put(KeyEvent.VK_J, Note.AS0);
        KEYBOARD_MAP.put(KeyEvent.VK_M, Note.B0);
        KEYBOARD_MAP.put(KeyEvent.VK_Q, Note.C1);
        KEYBOARD_MAP.put(KeyEvent.VK_2, Note.CS1);
        KEYBOARD_MAP.put(KeyEvent.VK_W, Note.D1);
        KEYBOARD_MAP.put(KeyEvent.VK_3, Note.DS1);
        KEYBOARD_MAP.put(KeyEvent.VK_E, Note.E1);
        KEYBOARD_MAP.put(KeyEvent.VK_R, Note.F1);
        KEYBOARD_MAP.put(KeyEvent.VK_5, Note.FS1);
        KEYBOARD_MAP.put(KeyEvent.VK_T, Note.G1);
        KEYBOARD_MAP.put(KeyEvent.VK_6, Note.GS1);
        KEYBOARD_MAP.put(KeyEvent.VK_Y, Note.A1);
        KEYBOARD_MAP.put(KeyEvent.VK_7, Note.AS1);
        KEYBOARD_MAP.put(KeyEvent.VK_U, Note.B1);
        KEYBOARD_MAP.put(KeyEvent.VK_I, Note.C2);
        KEYBOARD_MAP.put(KeyEvent.VK_9, Note.CS2);
        KEYBOARD_MAP.put(KeyEvent.VK_O, Note.D2);
        KEYBOARD_MAP.put(KeyEvent.VK_0, Note.DS2);
        KEYBOARD_MAP.put(KeyEvent.VK_P, Note.E2);
    }

    public static void main(String... args) {
        Row r = new Row();
        JFrame testWindow = new JFrame();
        testWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        testWindow.setSize(900, 600);
        Container content = testWindow.getContentPane();
        content.setLayout(new BoxLayout(testWindow.getContentPane(), BoxLayout.Y_AXIS));
        content.setBackground(Theme.background.color);
        content.setForeground(Theme.foreground.color);
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
        content.add(createRowEditor(r));
//        testWindow.doLayout();
        testWindow.setVisible(true);
    }

    public static KeyAdapter toneListner = new KeyAdapter() {

        @Override
        public void keyReleased(KeyEvent e) {
            e.consume();
        }

        @Override
        public void keyTyped(KeyEvent e) {
            e.consume();
        }


        @Override
        public void keyPressed(KeyEvent e) {
            JTextField field = (JTextField) e.getSource();
            if (KEYBOARD_MAP.containsKey(e.getKeyCode())) {
                Note n = KEYBOARD_MAP.get(e.getKeyCode());
                String noteval = n.note;
                int octave = BASE_OCTAVE + n.octaveOffset;
                noteval += octave;
                try {
                    // Test the waters, is ths value ok?
                    Row.Note.valueOf(noteval);
                    // Looks like it worked -- use the value
                    field.setText(noteval);
                } catch (Throwable t) {
                    // out of bounds or bad value
                }
                
            }
            e.consume();
            field.setFocusable(false);
            field.setFocusable(true);
        }
    };
    
    public static Component createRowEditor(Row r) {
        JPanel rowEditor = new JPanel();
        rowEditor.setSize(800, 24);
        rowEditor.setLayout(new BoxLayout(rowEditor, BoxLayout.X_AXIS));
        rowEditor.setBackground(Theme.background.color);
        rowEditor.setOpaque(true);
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.A1.tone", 3, toneListner));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.A1.volume", 1));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.A1.commands", 3));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.B1.tone", 3, toneListner));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.B1.volume", 1));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.B1.commands", 3));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.C1.tone", 3, toneListner));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.C1.volume", 1));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.C1.commands", 3));
        rowEditor.add(EditableLabel.generateTextLabel(r, "ay1noisePeriod", 4));
        rowEditor.add(EditableLabel.generateTextLabel(r, "ay1envelopePeriod", 4));
        rowEditor.add(generateEnvelopeEditor(r.ay1envelopeShape, r, "ay2envelopeShape"));

        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.A2.tone", 3, toneListner));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.A2.volume", 1));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.A2.commands", 3));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.B2.tone", 3, toneListner));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.B2.volume", 1));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.B2.commands", 3));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.C2.tone", 3, toneListner));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.C2.volume", 1));
        rowEditor.add(EditableLabel.generateTextLabel(r, "channels.C2.commands", 3));
        rowEditor.add(EditableLabel.generateTextLabel(r, "ay2noisePeriod", 4));
        rowEditor.add(EditableLabel.generateTextLabel(r, "ay2envelopePeriod", 4));
        rowEditor.add(generateEnvelopeEditor(r.ay2envelopeShape, r, "ay2envelopeShape"));

        rowEditor.add(EditableLabel.generateTextLabel(r, "globalCommands", 6));
        rowEditor.doLayout();
        return rowEditor;
    }

    public static Component generateEnvelopeEditor(Row.EnvelopeShape envelope, Row row, String property) {
        if (envelope == null) {
            envelope = Row.EnvelopeShape.unspecified;
        }
        JLabel label = new JLabel(envelope.icon) {
            @Override
            public void setText(String text) {
                Row.EnvelopeShape e;
                try {
                    e = Row.EnvelopeShape.valueOf(text);
                } catch (Throwable ex) {
                    e = Row.EnvelopeShape.unspecified;
                }
                setIcon(e.getIcon());
            }
        };
        label.setText(envelope.toString());
        JComboBox editor = new JComboBox(Row.ENVELOPE_ICONS);
        EditableLabel result = new EditableLabel(label, editor, 64, row, property);
        return result;
    }
}
