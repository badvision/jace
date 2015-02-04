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

import jace.Emulator;
import jace.core.Computer;
import jace.core.Utility;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Manages the configuration state of the emulator components.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Configuration implements Reconfigurable {

    public String getName() {
        return "Configuration";
    }

    @Override
    public String getShortName() {
        return "cfg";
    }

    public void reconfigure() {
    }

    public static class ConfigTreeModel implements TreeModel {

        public Object getRoot() {
            return BASE;
        }

        public Object getChild(Object parent, int index) {
            if (parent instanceof ConfigNode) {
                ConfigNode n = (ConfigNode) parent;
                return n.children.values().toArray()[index];
            } else {
                return null;
            }
        }

        public int getChildCount(Object parent) {
            if (parent instanceof ConfigNode) {
                ConfigNode n = (ConfigNode) parent;
                return n.children.size();
            } else {
                return 0;
            }
        }

        public boolean isLeaf(Object node) {
            return getChildCount(node) == 0;
        }

        public void valueForPathChanged(TreePath path, Object newValue) {
            // Do nothing...
        }

        public int getIndexOfChild(Object parent, Object child) {
            if (parent instanceof ConfigNode) {
                ConfigNode n = (ConfigNode) parent;
                ConfigNode[] c = (ConfigNode[]) n.children.values().toArray(new ConfigNode[0]);
                for (int i = 0; i < c.length; i++) {
                    if (c[i].equals(child)) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public void addTreeModelListener(TreeModelListener l) {
            // Do nothing...
        }

        public void removeTreeModelListener(TreeModelListener l) {
            // Do nothing...
        }
    }

    /**
     * Represents a serializable configuration node as part of a tree. The root
     * node should be a single instance (e.g. Computer) The child nodes should
     * be all object instances that stem from each object The overall goal of
     * this class is two-fold: 1) Provide a navigable manner to inspect
     * configuration 2) Provide a simple persistence mechanism to load/store
     * configuration
     */
    public static class ConfigNode implements Serializable {

        public transient ConfigNode root;
        public transient ConfigNode parent;
        public transient Reconfigurable subject;
        private Map<String, Serializable> settings;
        protected Map<String, ConfigNode> children;
        private boolean changed = false;

        @Override
        public String toString() {
            if (subject == null) {
                return "???";
            }
            return (changed ? "<html><i>" : "") + subject.getName();
        }

        public ConfigNode(Reconfigurable subject) {
            this(null, subject);
            this.root = null;
        }

        public ConfigNode(ConfigNode parent, Reconfigurable subject) {
            this.subject = subject;
            this.settings = new TreeMap<>();
            this.children = new TreeMap<>();
            this.parent = parent;
            if (this.parent != null) {
                this.root = this.parent.root != null ? this.parent.root : this.parent;
            }
        }

        public void setFieldValue(String field, Serializable value) {
            changed = true;
            if (value != null) {
                if (value.equals(getFieldValue(field))) {
                    return;
                }
            } else {
                if (getFieldValue(field) == null) {
                    return;
                }
            }
            setRawFieldValue(field, value);
        }

        public void setRawFieldValue(String field, Serializable value) {
            settings.put(field, value);
        }

        public Serializable getFieldValue(String field) {
            return settings.get(field);
        }

        public Set<String> getAllSettingNames() {
            return settings.keySet();
        }
    }
    public final static ConfigNode BASE;
    public static Computer emulator = Emulator.computer;
    @ConfigurableField(name = "Autosave Changes", description = "If unchecked, changes are only saved when the Save button is pressed.")
    public static boolean saveAutomatically = false;

    static {
        BASE = new ConfigNode(new Configuration());
        buildTree();
    }

    public static void buildTree() {
        buildTree(BASE, new HashSet());
    }

    private static void buildTree(ConfigNode node, Set visited) {
        if (node.subject == null) {
            return;
        }
        for (Field f : node.subject.getClass().getFields()) {
//            System.out.println("Evaluating field " + f.getName());
            try {
                Object o = f.get(node.subject);
                if (/*o == null ||*/visited.contains(o)) {
                    continue;
                }
//                System.out.println(o.getClass().getName());
                // If the object in question is not reconfigurable,
                // skip over it and investigate its fields instead
//                if (o.getClass().isAssignableFrom(Reconfigurable.class)) {
//                if (Reconfigurable.class.isAssignableFrom(o.getClass())) {
                if (f.isAnnotationPresent(ConfigurableField.class)) {
                    if (o != null && ISelection.class.isAssignableFrom(o.getClass())) {
                        ISelection selection = (ISelection) o;
                        node.setRawFieldValue(f.getName(), (Serializable) selection.getSelections().get(selection.getValue()));
                    } else {
                        node.setRawFieldValue(f.getName(), (Serializable) o);
                    }
                    continue;
                } else if (o == null) {
                    continue;
                }

                if (o instanceof Reconfigurable) {
                    Reconfigurable r = (Reconfigurable) o;
                    visited.add(r);
                    ConfigNode child = node.children.get(f.getName());
                    if (child == null || !child.subject.equals(o)) {
                        child = new ConfigNode(node, r);
                        node.children.put(f.getName(), child);
                    }
                    buildTree(child, visited);
                } else if (o.getClass().isArray()) {
                    String fieldName = f.getName();
                    Class type = o.getClass().getComponentType();
//                    System.out.println("Evaluating " + node.subject.getShortName() + "." + fieldName + "; type is " + type.toGenericString());
                    List<Reconfigurable> children = new ArrayList<>();
                    if (!Reconfigurable.class.isAssignableFrom(type)) {
//                        System.out.println("Looking at type " + type.getName() + " to see if optional");
                        if (Optional.class.isAssignableFrom(type)) {
                            Type genericTypes = f.getGenericType();
//                            System.out.println("Looking at generic parmeters " + genericTypes.getTypeName() + " for reconfigurable class, type "+genericTypes.getClass().getName());
                            if (genericTypes instanceof GenericArrayType) {
                                GenericArrayType aType = (GenericArrayType) genericTypes;
                                ParameterizedType pType = (ParameterizedType) aType.getGenericComponentType();
                                if (pType.getActualTypeArguments().length != 1) {
                                    continue;
                                }
                                Type genericType = pType.getActualTypeArguments()[0];
//                                System.out.println("Looking at type " + genericType.getTypeName() + " to see if reconfigurable");
                                if (!Reconfigurable.class.isAssignableFrom((Class) genericType)) {
                                    continue;
                                }
                            } else {
                                continue;
                            }

                            for (Optional<Reconfigurable> child : (Optional<Reconfigurable>[]) o) {
                                if (child.isPresent()) {
                                    children.add(child.get());
                                } else {
                                    children.add(null);
                                }
                            }
                        }
                    } else {
                        children = Arrays.asList((Reconfigurable[]) o);
                    }
                    visited.add(o);
                    for (int i = 0; i < children.size(); i++) {
                        Reconfigurable child = children.get(i);
                        String childName = fieldName + i;
                        if (child == null) {
                            node.children.remove(childName);
                            continue;
                        }
                        ConfigNode grandchild = node.children.get(childName);
                        if (grandchild == null || !grandchild.subject.equals(child)) {
                            grandchild = new ConfigNode(node, child);
                            node.children.put(childName, grandchild);
                        }
                        buildTree(grandchild, visited);
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @InvokableAction(
            name = "Save settings",
            description = "Save all configuration settings as defaults",
            category = "general",
            alternatives = "save preferences;save defaults"
    )
    public static void saveSettings() {
        FileOutputStream fos = null;
        {
            ObjectOutputStream oos = null;
            try {
                applySettings(BASE);
                oos = new ObjectOutputStream(new FileOutputStream(getSettingsFile()));
                oos.writeObject(BASE);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (oos != null) {
                        oos.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    @InvokableAction(
            name = "Load settings",
            description = "Load all configuration settings previously saved",
            category = "general",
            alternatives = "load preferences;revert settings;revert preferences"
    )
    public static void loadSettings() {
        {
            ObjectInputStream ois = null;
            FileInputStream fis = null;
            try {
                ois = new ObjectInputStream(new FileInputStream(getSettingsFile()));
                ConfigNode newRoot = (ConfigNode) ois.readObject();
                applyConfigTree(newRoot, BASE);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
            } catch (FileNotFoundException ex) {
//                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
                // This just means there are no settings to be saved -- just ignore it.
            } catch (IOException ex) {
                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (ois != null) {
                        ois.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public static void resetToDefaults() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static File getSettingsFile() {
        return new File(System.getProperty("user.dir"), ".jace.conf");
    }

    /**
     * Apply settings from node tree to the object model This also calls
     * "reconfigure" on objects in sequence
     *
     * @param node
     * @return True if any settings have changed in the node or any of its
     * descendants
     */
    public static boolean applySettings(ConfigNode node) {
        boolean resume = false;
        if (node == BASE) {
            resume = Emulator.computer.pause();
        }
        boolean hasChanged = false;
        if (node.changed) {
            doApply(node);
            hasChanged = true;
        }

        // Now that the object structure reflects the current configuration,
        // process reconfiguration from the children, etc.
        for (ConfigNode child : node.children.values()) {
            hasChanged |= applySettings(child);
        }

        if (node.equals(BASE) && hasChanged) {
            buildTree();
        }

        if (resume) {
            Emulator.computer.resume();
        }

        return hasChanged;
    }

    private static void applyConfigTree(ConfigNode newRoot, ConfigNode oldRoot) {
        if (oldRoot == null || newRoot == null) {
            return;
        }
        oldRoot.settings = newRoot.settings;
        if (oldRoot.subject != null) {
            doApply(oldRoot);
            buildTree(oldRoot, new HashSet());
        }
        newRoot.children.keySet().stream().forEach((childName) -> {
            //            System.out.println("Applying settings for " + childName);
            applyConfigTree(newRoot.children.get(childName), oldRoot.children.get(childName));
        });
    }

    private static void doApply(ConfigNode node) {
        List<String> removeList = new ArrayList<>();
        for (String f : node.settings.keySet()) {
            try {
                Field ff = node.subject.getClass().getField(f);
//                System.out.println("Setting " + f + " to " + node.settings.get(f));
                Object val = node.settings.get(f);
                Class valType = (val != null ? val.getClass() : null);
                Class fieldType = ff.getType();
                if (ISelection.class.isAssignableFrom(fieldType)) {
                    ISelection selection = (ISelection) ff.get(node.subject);
                    try {
                        selection.setValue(val);
                    } catch (ClassCastException c) {
                        selection.setValueByMatch(String.valueOf(val));
                    }
                    continue;
                }
                if (val == null || valType.equals(fieldType)) {
                    ff.set(node.subject, val);
                    continue;
                }
//                System.out.println(fieldType);
                val = Utility.deserializeString(String.valueOf(val), fieldType, false);
//                System.out.println("Setting "+node.subject.getName()+" property "+ff.getName()+" with value "+String.valueOf(val));
                ff.set(node.subject, val);
            } catch (NoSuchFieldException ex) {
                System.out.println("Setting " + f + " no longer exists, skipping.");
                removeList.add(f);
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        removeList.stream().forEach((f) -> {
            node.settings.remove(f);
        });

        try {
            // When settings are applied, this could very well change the object structure
            // For example, if cards or other pieces of emulation are changed around
//            System.out.println("Reconfiguring "+node.subject.getName());
            node.subject.reconfigure();
        } catch (Exception ex) {
            Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
        }

        node.changed = false;
    }

    public static void applySettings(Map<String, String> settings) {
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            Map<String, ConfigNode> shortNames = new HashMap<>();
            buildNodeMap(BASE, shortNames);

            String settingName = setting.getKey();
            String value = setting.getValue();
            String[] parts = settingName.split("\\.");
            if (parts.length != 2) {
                System.err.println("Unable to parse settting, should be in the form of DEVICE.PROPERTYNAME " + settingName);
                continue;
            }
            String deviceName = parts[0];
            String fieldName = parts[1];
            ConfigNode n = shortNames.get(deviceName.toLowerCase());
            if (n == null) {
                System.err.println("Unable to find device named " + deviceName + ", try one of these: " + Utility.join(shortNames.keySet(), ", "));
                continue;
            }

            boolean found = false;
            List<String> shortFieldNames = new ArrayList<>();
            for (String longName : n.getAllSettingNames()) {
                ConfigurableField f = null;
                try {
                    f = n.subject.getClass().getField(longName).getAnnotation(ConfigurableField.class);
                } catch (NoSuchFieldException | SecurityException ex) {
                    Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
                }
                String shortName = (f != null && !f.shortName().equals("")) ? f.shortName() : longName;
                shortFieldNames.add(shortName);

                if (fieldName.equalsIgnoreCase(longName) || fieldName.equalsIgnoreCase(shortName)) {
                    found = true;
                    n.setFieldValue(longName, value);
                    applySettings(n);
//                    n.subject.reconfigure();
                    buildTree();
                    System.out.println("Set property " + n.subject.getName() + "." + longName + " to " + value);
                    break;
                }
            }
            if (!found) {
                System.err.println("Unable to find property " + fieldName + " for device " + deviceName + ".  Try one of these :" + Utility.join(shortFieldNames, ", "));
            }
        }
    }

    private static void buildNodeMap(ConfigNode n, Map<String, ConfigNode> shortNames) {
//        System.out.println("Encountered " + n.subject.getShortName().toLowerCase());
        shortNames.put(n.subject.getShortName().toLowerCase(), n);
        n.children.entrySet().stream().forEach((c) -> {
            buildNodeMap(c.getValue(), shortNames);
        });
    }

    private static void printTree(ConfigNode n, String prefix, int i) {
        n.getAllSettingNames().stream().forEach((setting) -> {
            for (int j = 0; j < i; j++) {
                System.out.print(" ");
            }
            ConfigurableField f = null;
            try {
                f = n.subject.getClass().getField(setting).getAnnotation(ConfigurableField.class);
            } catch (NoSuchFieldException | SecurityException ex) {
                Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
            }
            String sn = (f != null && !f.shortName().equals("")) ? f.shortName() : setting;
            System.out.println(prefix + ">>" + setting + " (" + n.subject.getShortName() + "." + sn + ")");
        });
        n.children.entrySet().stream().forEach((c) -> {
            printTree(c.getValue(), prefix + "." + c.getKey(), i + 1);
        });
    }
}
