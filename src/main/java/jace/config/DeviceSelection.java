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

package jace.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 * @param <C> Enum class which implements DeviceEnum
 */
// C is an enum class which implements DeviceEnum
@SuppressWarnings("all")
public class DeviceSelection<C extends Enum & DeviceEnum> extends DynamicSelection<C> {

    Class<C> enumClass;
    boolean nullAllowed = false;

    public DeviceSelection(Class<C> enumClass, C defaultValue) {
        super(defaultValue);
        if (defaultValue == null) {
            nullAllowed = true;
        }
        this.enumClass = enumClass;
    }

    public DeviceSelection(Class<C> enumClass, C defaultValue, boolean nullAllowed) {
        this(enumClass, defaultValue);
        this.nullAllowed = nullAllowed;
    }

    @Override
    public LinkedHashMap<C, String> getSelections() {
        LinkedHashMap<C, String> selections = new LinkedHashMap<>();
        if (allowNull()) {
            selections.put(null, "***Empty***");
        }
        // Sort enum constants by getName
        List<C> sorted = new ArrayList<>();
        sorted.addAll(Arrays.asList(enumClass.getEnumConstants()));
        Collections.sort(sorted, (C o1, C o2) -> o1.getName().compareTo(o2.getName()));
        for (C c : enumClass.getEnumConstants()) {
            selections.put(c, c.getName());
        }
        return selections;
    }

    @Override
    public boolean allowNull() {
        return nullAllowed;
    }

    @Override
    public void setValue(C value) {
        Object v = value;
        if (v != null && v instanceof String) {
            super.setValueByMatch((String) v);
            return;
        }
        super.setValue(value);
    }
}