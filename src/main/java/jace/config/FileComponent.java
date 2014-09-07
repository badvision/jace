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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

/**
 * This component provides a text field for the manual input of a file, as well
 * as an associated 'Browse' button, allowing the user to browse for a file and
 * have its location show up automatically in the text field.
 *
 * --borrowed and modified by Brendan Robert
 *
 * @author Eelke Spaak
 * @see javax.swing.JFileChooser
 */
class FileComponent extends javax.swing.JPanel implements ActionListener, KeyListener {
    ConfigNode node;
    String fieldName;

    public void actionPerformed(ActionEvent e) {
        textField.setBackground(Color.WHITE);
        String value = textField.getText();
        if (value == null || value.equals("")) {
            node.setFieldValue(fieldName, null);
        } else {
            File f = new File(value);
            if (f.exists()) {
                node.setFieldValue(fieldName, f);
            } else {
                textField.setBackground(Color.RED);
            }
        }
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

    private int TEXT_FIELD_WIDTH = 150;

    /** Creates new form JFileField */
    public FileComponent(ConfigNode node, String fieldName) {
        this.node = node;
        this.fieldName = fieldName;
//        if (".".equals(type.value())) {
//            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY;
//        } else {
//            setFileTypeName(type.value());
//            setExtensionFilter(type.value());
//        }
        initComponents();
        textField.addActionListener(this);
        synchronizeValue();
    }
    private String extensionFilter;
    private String fileTypeName;
    private int fileSelectionMode = JFileChooser.FILES_ONLY;

    /** This method is called from within the constructor to
     * initialize the form.
     */
    private void initComponents() {
        textField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();
        textField.setPreferredSize(new Dimension(150,20));
        textField.addKeyListener(this);
        browseButton.setText("...");
        browseButton.setPreferredSize(new Dimension(25,20));
        browseButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });
        this.add(textField);
        this.add(browseButton);

        FlowLayout layout = new FlowLayout();
        this.setLayout(layout);
        this.validate();
    }

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {
        File currentDirectory = new File(".");
        JFileChooser chooser = new JFileChooser();

        chooser.setFileSelectionMode(fileSelectionMode);

        // Quick-n-dirty implementation of file extension filter since it's new in JDK 1.6
        if (extensionFilter != null && fileTypeName != null) {
            FileFilter filter = new FileFilter() {
                String[] extensions = extensionFilter.toLowerCase().split(",");
                @Override
                public boolean accept(File f) {
                    for (int i=0; i < extensions.length; i++) {
                        if (f.getPath().toLowerCase().endsWith(extensions[i]))
                             return true;
                    }
                    return false;
                }

                @Override
                public String getDescription() {
                    return fileTypeName;
                }
            };
            chooser.setFileFilter(filter);
        }

        try {
            File f = new File(textField.getText());
            if (f.exists()) {
                if (f.isDirectory()) {
                    chooser.setCurrentDirectory(f);
                } else {
                    chooser.setCurrentDirectory(f.getParentFile());
                    chooser.setSelectedFile(f);
                }
            } else {
                chooser.setCurrentDirectory(currentDirectory);
            }
        } catch (Exception ignore) {
        }

        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = chooser.getSelectedFile();
                if (selectedFile.getCanonicalPath().startsWith(currentDirectory.getCanonicalPath())) {
                    String use = selectedFile.getCanonicalPath().substring(currentDirectory.getCanonicalPath().length() + 1);
                    textField.setText(use);
                } else {
                    textField.setText(selectedFile.getPath());
                }
                node.setFieldValue(fieldName, selectedFile);

            } catch (IOException ex) {
                Logger.getLogger(FileComponent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Returns the value of the text field.
     *
     * @return the value of the text field
     */
    public String getText() {
        return textField.getText();
    }

    /**
     * Sets the value of the text field.
     *
     * @param the value to put in the text field
     */
    public void setText(String text) {
        textField.setText(text);
    }

    /**
     * Returns the extension filter (a comma-separated string of extensions)
     * that the JFileChooser should use when browsing for a file.
     *
     * @return the extension filter
     */
    public String getExtensionFilter() {
        return extensionFilter;
    }

    /**
     * Sets the extension filter (a comma-separated string of extensions)
     * that the JFileChooser should use when browsing for a file.
     *
     * @param extensionFilter the extension filter
     */
    public void setExtensionFilter(String extensionFilter) {
        this.extensionFilter = extensionFilter;
    }

    /**
     * Returns the description of the file types the JFileChooser should be
     * browsing for.
     *
     * @return the file type description
     */
    public String getFileTypeName() {
        return fileTypeName;
    }

    /**
     * Sets the description of the file types the JFileChooser should be
     * browsing for.
     *
     * @param fileTypeName the file type description
     */
    public void setFileTypeName(String fileTypeName) {
        this.fileTypeName = fileTypeName;
    }

    /**
     * Returns the file selection mode to be used by the JFileChooser.
     *
     * @return the type of files to be displayed
     * @see javax.swing.JFileChooser#getFileSelectionMode()
     */
    public int getFileSelectionMode() {
        return fileSelectionMode;
    }

    /**
     * Sets the file selection mode to be used by the JFileChooser.
     *
     * @param fileSelectionMode the type of files to be displayed
     * @see javax.swing.JFileChooser#setFileSelectionMode(int)
     */
    public void setFileSelectionMode(int fileSelectionMode) {
        this.fileSelectionMode = fileSelectionMode;
    }

    /**
     * Implemented to make layout managers align the JFileField on the baseline
     * of the included text field, rather than on the absolute bottom of the
     * JPanel.
     *
     * @param w
     * @param h
     * @return
     */
//    @Override
//    public int getBaseline(int w, int h) {
//        return textField.getBaseline(w, h);
//    }
    // Variables declaration - do not modify
    private javax.swing.JButton browseButton;
    private javax.swing.JTextField textField;

    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
        actionPerformed(null);
    }
    // End of variables declaration
}
