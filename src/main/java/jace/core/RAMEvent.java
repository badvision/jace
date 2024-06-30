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

package jace.core;

import jace.apple2e.SoftSwitches;

/**
 * A RAM event is defined as anything that causes a read or write to the
 * mainboard RAM of the computer. This could be the result of an indirect
 * address fetch (indirect addressing) as well as direct or indexed operator
 * addressing modes.
 *
 * It is also possible to track if the read is an opcode read, indicating that
 * the CPU is executing the given memory location at that moment.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class RAMEvent {

    public interface RAMEventHandler {
        void handleEvent(RAMEvent e);
    }

    public enum TYPE {

        READ(true),
        READ_DATA(true),
        READ_OPERAND(true),
        EXECUTE(true),
        WRITE(false),
        ANY(false);
        boolean read;

        TYPE(boolean r) {
            this.read = r;
        }

        public boolean isRead() {
            return read;
        }
    }

    public enum SCOPE {

        ADDRESS,
        RANGE,
        ANY
    }

    public enum VALUE {

        ANY,
        RANGE,
        EQUALS,
        NOT_EQUALS,
        CHANGE_BY
    }

    private TYPE type;
    private SCOPE scope;
    private VALUE value;
    private int address, oldValue, newValue;
    private boolean valueIntercepted = false;

    /**
     * Creates a new instance of RAMEvent
     * @param t
     * @param s
     * @param v
     * @param address
     * @param oldValue
     * @param newValue
     */
    public RAMEvent(TYPE t, SCOPE s, VALUE v, int address, int oldValue, int newValue) {
        setType(t);
        setScope(s);
        setValue(v);
        this.setAddress(address);
        this.setOldValue(oldValue);
        this.setNewValue(newValue);
    }

    public TYPE getType() {
        return type;
    }

    public final void setType(TYPE type) {
        if (type == TYPE.ANY) {
            throw new RuntimeException("Event type=Any is reserved for listeners, not for triggering events!");
        }
        this.type = type;
    }

    public SCOPE getScope() {
        return scope;
    }

    public final void setScope(SCOPE scope) {
        this.scope = scope;
    }

    public VALUE getValue() {
        return value;
    }

    public final void setValue(VALUE value) {
        this.value = value;
    }

    public int getAddress() {
        return address;
    }

    public final void setAddress(int address) {
        this.address = address;
    }

    public int getOldValue() {
        return oldValue;
    }

    public final void setOldValue(int oldValue) {
        this.oldValue = oldValue;
    }

    public int getNewValue() {
        return newValue;
    }

    public final void setNewValue(int newValue) {
        this.newValue = newValue;
        valueIntercepted = true;
    }

    public final boolean isIntercepted() {
        return valueIntercepted;
    }

    public boolean isMainMemory() {
        if (type.isRead() && SoftSwitches.RAMRD.isOn()) {
            return false;
        } else if (!type.isRead() && SoftSwitches.RAMWRT.isOn()) {
            return false;
        } else if (address < 0x0200) {
            // Check if zero page is pointed to auxiliary memory
            return SoftSwitches.AUXZP.isOff();
        }
        if ((address >= 0x400 && address < 0x0800) || (address >= 0x2000 && address < 0x4000)) {
            if (SoftSwitches._80STORE.isOn() && SoftSwitches.PAGE2.isOn()) {                
                return false;
            }
        }
        return true;
    }
}
