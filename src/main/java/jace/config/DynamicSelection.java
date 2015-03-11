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

import jace.core.Utility;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class DynamicSelection<T> implements ISelection<T> {
    public DynamicSelection(T defaultValue) {
        setValue(defaultValue);
    }
    abstract public boolean allowNull();
    T currentValue;
    @Override
    public T getValue() {
        if (currentValue != null || !allowNull()) {
            return currentValue;
        } else {
            Iterator<? extends T> i = getSelections().keySet().iterator();
            return i.next();
        }
    }
    
    @Override
    public void setValue(T value) {currentValue = value;}
    
    @Override
    public void setValueByMatch(String search) {
        setValue(findValueByMatch(search));
    }

    public T findValueByMatch(String search) {
        Map<? extends T, String> selections = getSelections();
        String match = Utility.findBestMatch(search, selections.values());
        if (match != null) {
            for (T key : selections.keySet()) {
                if (selections.get(key).equals(match)) {
                    return key;
                }
            }
        }
        return null;
    }
}
