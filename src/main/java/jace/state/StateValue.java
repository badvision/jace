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

    @SuppressWarnings("all")
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
