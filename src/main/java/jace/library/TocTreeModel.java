/*
 * Copyright (C) 2013 brobert.
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
package jace.library;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 *
 * @author brobert
 */
public class TocTreeModel implements TreeModel {
    String name;
    boolean twoLevel = true;
    Map<String, Map<String, Set<Long>>> tree = new TreeMap<String, Map<String, Set<Long>>>() {
        @Override
        public String toString() {
            return name;
        }
    };

    public void addItems(String parent, final String sub, Set<Long> entries) {
        if (entries == null || entries.isEmpty()) return;
        Map<String, Set<Long>> parentNode = tree.get(parent);
        if (parentNode == null) {
            parentNode = new TreeMap<String, Set<Long>>();
            tree.put(parent, parentNode);
        }
        Set<Long> allEntries = parentNode.get(sub);
        if (allEntries == null) {
            allEntries = new TreeSet<Long>() {
                @Override
                public String toString() {
                    return sub;
                }
            };
            parentNode.put(sub, allEntries);
        }
        allEntries.addAll(entries);
    }

    public Object getRoot() {
        return tree;
    }

    public Object getChild(Object parent, int index) {
        if (parent == getRoot()) {
            return tree.keySet().toArray()[index];
        }
        if (parent instanceof String) {
            if (tree.get(parent) != null) {
                return tree.get(parent).values().toArray()[index];
            }
        }
        return null;
    }

    public int getChildCount(Object parent) {
        if (parent == getRoot()) {
            return tree.keySet().size();
        }
        if (twoLevel && parent instanceof String) {
            if (tree.get(parent) != null) {
                return tree.get(parent).values().size();
            }
        }
        
        return 0;
    }

    public boolean isLeaf(Object node) {
        return getChildCount(node) == 0;
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
        // Do nothing...
    }

    public int getIndexOfChild(Object parent, Object child) {
        if (parent instanceof String) {
            String n = (String) parent;
            int index = 0;
            for (String c : tree.get(parent).keySet()) {
                if (c.equals(child)) {
                    return index;
                }
                index++;
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

    public Set<Long> getEntries(Object selection) {
        if (selection.equals(this)) return getEntries(tree);
        if (selection instanceof Set) return (Set<Long>) selection;
        if (Map.class.isInstance(selection)) {
            Set<Long> all =  new LinkedHashSet<Long>();
             for (Object val : ((Map) selection).values()) {
                 Set<Long> entries = getEntries(val);
                 if (entries != null) all.addAll(getEntries(val));
            }
            return all;
        }
        if (selection instanceof String) {
            Set<Long> values = new LinkedHashSet<Long>();
            Map<String, Set<Long>> children = tree.get(String.valueOf(selection));
            for (Set<Long> val : children.values()) {
                values.addAll(val);
            }
            return values;
        }
        return null;
    }    
}