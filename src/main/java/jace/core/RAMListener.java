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

import jace.Emulator;
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
public abstract class RAMListener implements RAMEvent.RAMEventHandler, Comparable<RAMListener> {
    
    private RAMEvent.TYPE type;
    private RAMEvent.SCOPE scope;
    private RAMEvent.VALUE value;
    private int scopeStart;
    private int scopeEnd;
    private int valueStart;
    private int valueEnd;
    private int valueAmount;
    private String name;

    /**
     * Creates a new instance of RAMListener
     * @param name
     * @param t
     * @param s
     * @param v
     */
    public RAMListener(String name, RAMEvent.TYPE t, RAMEvent.SCOPE s, RAMEvent.VALUE v) {
        setName(name);
        setType(t);
        setScope(s);
        setValue(v);
        doConfig();
    }

    public void unregister() {
        Emulator.withMemory(m -> m.removeListener(this));
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        // Skip event if it's not in the scope we care about
        if (scope != RAMEvent.SCOPE.ANY) {
            if (scope == RAMEvent.SCOPE.ADDRESS && e.getAddress() != scopeStart) {
                return false;
            } else if (scope == RAMEvent.SCOPE.RANGE && (e.getAddress() < scopeStart || e.getAddress() > scopeEnd)) {
                return false;
            }
        }

        // Skip event if it's not the right type
        if (!(type == TYPE.ANY || type == e.getType() || (type == TYPE.READ && e.getType().isRead()))) {
            return false;
        }
        
        // Skip event if the value modification is uninteresting
        if (value != RAMEvent.VALUE.ANY) {
            if (value == RAMEvent.VALUE.CHANGE_BY && e.getNewValue() - e.getOldValue() != valueAmount) {
                return false;
            } else if (value == RAMEvent.VALUE.EQUALS && e.getNewValue() != valueAmount) {
                return false;
            } else if (value == RAMEvent.VALUE.NOT_EQUALS && e.getNewValue() == valueAmount) {
                return false;
            } else return value != RAMEvent.VALUE.RANGE || (e.getNewValue() >= valueStart && e.getNewValue() <= valueEnd);
        }

        // Ok, so we've filtered out the uninteresting stuff
        // If we've made it this far then the event is valid.
        return true;
    }
    
    @Override
    public final void handleEvent(RAMEvent e) {
        if (isRelevant(e)) {
            doEvent(e);
        }
    }

    abstract protected void doConfig();

    abstract protected void doEvent(RAMEvent e);

    @Override
    public int compareTo(RAMListener o) {
        if (o.name.equals(name)) {
           if (o.scopeStart == scopeStart) {
                if (o.scopeEnd == scopeEnd) {
                    if (o.type == type) {
                        // Ignore hash codes -- combination of name, address range and type should identify similar listeners.
                        return (int) 0;
                    } else {
                        return Integer.compare(o.type.ordinal(), type.ordinal());
                    }
                } else {
                    return Integer.compare(o.scopeEnd, scopeEnd);
                }
            } else {
                return Integer.compare(o.scopeStart, scopeStart);
            }
        } else {
            return o.name.compareTo(name);
        }
    }
    
    /**
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || ! (o instanceof RAMListener) ) {
            return false;
        }
        return this.compareTo((RAMListener) o) == 0;
    }
}
