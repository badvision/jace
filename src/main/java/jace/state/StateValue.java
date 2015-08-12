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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * This represents a serializable value of an object
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 * @param <T>
 */
public class StateValue<T> implements Serializable {

    public ObjectGraphNode<T> node;
    public Class<T> type;
    public T value;

    public StateValue(ObjectGraphNode<T> node) {
        this.node = node;
        this.type = this.node.type;
//        if (node.name.equals("Z")) {
//            System.out.println("Z >>>> "+ String.valueOf(node.getCurrentValue()));
//            System.out.println(String.valueOf(copyObject(node.getCurrentValue())));
//        }
        value = copyObject(this.node.getCurrentValue());
//        System.out.print(node.parent != null ? node.parent.name + "." : "");
//        System.out.print(node.name);
//        System.out.print("==");
//        System.out.println(value == null ? "NULL" : value.getClass().toString());                
    }

    public void mergeValue(StateValue<T> previous) {
        // Do nothing -- this is here in case it is necessary to implement partial changes
    }

    private T copyObject(T currentValue) {
        if (currentValue == null) return null;
        if (type.isPrimitive()) {
            return currentValue;
        }
        if (!type.isArray()) {
            try {
                return (T) type.getMethod("clone").invoke(currentValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                try {
                    // Use serialization to build a deep copy
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(currentValue);

                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    return (T) ois.readObject();
                } catch (IOException | ClassNotFoundException e) {
                    return null;
                }
            }
        } else if (type.isArray()) {
            // Cross fingers!  I hope this works and I don't have to investigate primite types further...
            if (currentValue instanceof byte[]) {
                byte[] array = (byte[]) currentValue;
                return (T) Arrays.copyOf(array, array.length);
            } else {
                Object[] array = (Object[]) currentValue;
                return (T) Arrays.copyOf(array, array.length);
            }
        }

        return currentValue;
    }
}
