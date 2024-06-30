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

    @SuppressWarnings("all")
    public void valueForPathChanged(TreePath path, Object newValue) {
        // Do nothing...
    }

    public int getIndexOfChild(Object parent, Object child) {
        if (parent instanceof String) {
            // String n = (String) parent;
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

    @SuppressWarnings("all")
    public Set<Long> getEntries(Object selection) {
        if (selection.equals(this)) return getEntries(tree);
        if (selection instanceof Set) return (Set<Long>) selection;
        if (selection instanceof Map) {
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