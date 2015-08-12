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
package jace.state;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This represents an object graph in a serializable way. The emulator object
 * tree is not preserved in a saved state (it would be too bulky) so it is
 * important that state can be restored. This means that serialized object
 * values need to be merged to a new object graph, and that graph needs to be
 * traversed cleanly and correctly.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 * @param <T>
 */
public class ObjectGraphNode<T> implements Serializable {

    public ObjectGraphNode parent;
    public String name;
    public int index;
    public boolean isStateful;
    public boolean forceCheck;      // If true, object must always be inspected for changes -- more expensive
    public List<ObjectGraphNode> children;
    transient public WeakReference<T> source;
    transient public DirtyFlag dirty;
    transient public Class<T> type;
    public boolean isPrimitive = false;

    public static enum DirtyFlag {

        UNKNOWN, CLEAN, DIRTY
    };

    public ObjectGraphNode(Class<T> clazz) {
        children = new ArrayList<>();
        type = clazz;
        dirty = DirtyFlag.UNKNOWN;
        forceCheck = true;
    }

    public ObjectGraphNode(T obj) {
        this((Class<T>) obj.getClass());
        source = (WeakReference<T>) new WeakReference(obj);
    }

    public T getCurrentValue() {
        if (isPrimitive || type.isPrimitive()) {
            try {
                return (T) parent.type.getField(name).get(parent.getCurrentValue());
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException ex) {
                Logger.getLogger(ObjectGraphNode.class.getName()).log(Level.SEVERE, null, ex);
            }
            return null;
        } else {
            return source.get();
        }
    }

    public void markClean() {
        dirty = DirtyFlag.CLEAN;
    }

    public void markDirty() {
        dirty = DirtyFlag.DIRTY;
    }

    public boolean isDirty() {
        return dirty == DirtyFlag.DIRTY;
    }

    public void setCurrentValue(Object value) {
        if (parent == null) {
            return;
        }
        if (List.class.isAssignableFrom(parent.type)) {
            // This is a list index
            List p = (List) parent.getCurrentValue();
            p.set(index, value);
        } else if (Map.class.isAssignableFrom(parent.type)) {
            // this is a map entry
            Map p = (Map) parent.getCurrentValue();
            // TODO: If keys are not strings then this will not work -- write something to resolve keys
            p.put(name, value);
        } else if (parent.type.isArray()) {
            // This is an array index
            Object[] p = (Object[]) parent.getCurrentValue();
            if (p != null) {
                p[index] = value;
            }
        } else {
            // Must be an object member
            Object p = parent.getCurrentValue();
            try {
                parent.type.getField(name).set(p, value);
            } catch (NoSuchFieldException ex) {
                System.out.println(parent.name + "." + name);
                System.out.println("This = "+type);
                System.out.println("Parent = " + parent.type);
                System.out.println("this Is Array: " + type.isArray());
                System.out.println("parent Is Array: " + parent.type.isArray());
                Logger.getLogger(ObjectGraphNode.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(ObjectGraphNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public ObjectGraphNode find(String path) {
        String[] parts = path.split("\\.");
        ObjectGraphNode current = this;
        for (String part : parts) {
            for (ObjectGraphNode child : (List<ObjectGraphNode>) current.children) {
                if (child.name.equals(part)) {
                    current = child;
                    break;
                } else {
                    return null;
                }
            }
        }
        return current;
    }

    public boolean valueChanged(State tail) {
        if (!forceCheck || isDirty()) {
            return isDirty();
        }

        //?? Let's be lazy and just say yes for now.
        return true;

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ObjectGraphNode)) {
            return false;
        }
        ObjectGraphNode node = (ObjectGraphNode) obj;
        if (parent == null) {
            if (node.parent != null) {
                return false;
            }
        } else {
            if (node.parent == null) {
                return false;
            } else if (!node.parent.equals(parent)) {
                return false;
            }
        }
        return (name.equals(node.name) && index == node.index);
    }
}
