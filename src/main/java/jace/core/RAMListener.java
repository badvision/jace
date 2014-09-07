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

import jace.core.RAMEvent.TYPE;

/**
 * A Ram Listener waits for a specific ram event, as specified by access type
 * (read/write/execute, etc) or a memory address, or range of addresses. The
 * subclass must define the address range (scope start/end) via the doConfig
 * method. Ram listeners are used all over the emulator, but especially in cheat
 * modules and the softswitch and I/O cards.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class RAMListener {

    private RAMEvent.TYPE type;
    private RAMEvent.SCOPE scope;
    private RAMEvent.VALUE value;
    private int scopeStart;
    private int scopeEnd;
    private int valueStart;
    private int valueEnd;
    private int valueAmount;

    /**
     * Creates a new instance of RAMListener
     */
    public RAMListener(RAMEvent.TYPE t, RAMEvent.SCOPE s, RAMEvent.VALUE v) {
        setType(t);
        setScope(s);
        setValue(v);
        doConfig();
    }

    public RAMEvent.TYPE getType() {
        return type;
    }

    public final void setType(RAMEvent.TYPE type) {
        this.type = type;
    }

    public RAMEvent.SCOPE getScope() {
        return scope;
    }

    public final void setScope(RAMEvent.SCOPE scope) {
        this.scope = scope;
    }

    public RAMEvent.VALUE getValue() {
        return value;
    }

    public final void setValue(RAMEvent.VALUE value) {
        this.value = value;
    }

    public int getScopeStart() {
        return scopeStart;
    }

    public void setScopeStart(int scopeStart) {
        this.scopeStart = scopeStart;
    }

    public int getScopeEnd() {
        return scopeEnd;
    }

    public void setScopeEnd(int scopeEnd) {
        this.scopeEnd = scopeEnd;
    }

    public int getValueStart() {
        return valueStart;
    }

    public void setValueStart(int valueStart) {
        this.valueStart = valueStart;
    }

    public int getValueEnd() {
        return valueEnd;
    }

    public void setValueEnd(int valueEnd) {
        this.valueEnd = valueEnd;
    }

    public int getValueAmount() {
        return valueAmount;
    }

    public void setValueAmount(int valueAmount) {
        this.valueAmount = valueAmount;
    }

    public boolean isRelevant(RAMEvent e) {
        // Skip event if it's not the right type
        if (type != TYPE.ANY && e.getType() != TYPE.ANY) {
            if ((type != e.getType())) {
                if (type == TYPE.READ) {
                    if (!e.getType().isRead()) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        // Skip event if it's not in the scope we care about
        if (scope != RAMEvent.SCOPE.ANY) {
            if (scope == RAMEvent.SCOPE.ADDRESS && e.getAddress() != scopeStart) {
                return false;
            } else if (scope == RAMEvent.SCOPE.RANGE && (e.getAddress() < scopeStart || e.getAddress() > scopeEnd)) {
                return false;
            }
        }

        // Skip event if the value modification is uninteresting
        if (value != RAMEvent.VALUE.ANY) {
            if (value == RAMEvent.VALUE.CHANGE_BY && e.getNewValue() - e.getOldValue() != valueAmount) {
                return false;
            } else if (value == RAMEvent.VALUE.EQUALS && e.getNewValue() != valueAmount) {
                return false;
            } else if (value == RAMEvent.VALUE.NOT_EQUALS && e.getNewValue() == valueAmount) {
                return false;
            } else if (value == RAMEvent.VALUE.RANGE && (e.getNewValue() < valueStart || e.getNewValue() > valueEnd)) {
                return false;
            }
        }

        // Ok, so we've filtered out the uninteresting stuff
        // If we've made it this far then the event is valid.
        return true;
    }

    public void handleEvent(RAMEvent e) {
        if (isRelevant(e)) {
            doEvent(e);
        }
    }

    abstract protected void doConfig();

    abstract protected void doEvent(RAMEvent e);
}
