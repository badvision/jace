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
package jace.core;

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

    public enum TYPE {

        READ(true),
        READ_DATA(true),
        EXECUTE(true),
        READ_OPERAND(true),
        WRITE(false),
        ANY(false);
        boolean read = false;

        TYPE(boolean r) {
            this.read = r;
        }

        public boolean isRead() {
            return read;
        }
    };

    public enum SCOPE {

        ADDRESS,
        RANGE,
        ANY
    };

    public enum VALUE {

        ANY,
        RANGE,
        EQUALS,
        NOT_EQUALS,
        CHANGE_BY
    };
    private TYPE type;
    private SCOPE scope;
    private VALUE value;
    private int address, oldValue, newValue;

    /**
     * Creates a new instance of RAMEvent
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
    }
}
