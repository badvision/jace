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

import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * A state is nothing more than a map of captured variable values, except that a
 * state can also be a delta (set of changes) from a previous state. This allows
 * states to occupy a lot less memory and be more efficiently managed. States
 * are chained together in a linked list, using a special delete method to
 * ensure that delta states are properly merged.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class State extends HashMap<ObjectGraphNode, StateValue> implements Serializable {

    boolean deltaState;
    State previousState;
    State nextState;
    // Tail is only correct on the head node, everything else will likely be null
    State tail;
    BufferedImage screenshot;

    /**
     * Removing the next state allows a LRU buffer of states -- but states can't
     * simple be discarded, they have to be more carefully managed because most
     * states only track deltas. Things like memory have to be merged correctly.
     *
     * This state will merge with the next state and then remove it from the
     * linked list.
     *
     * @return
     */
    public State deleteNext() {
        if (nextState == null) {
            return null;
        }
        if (nextState.deltaState) {
            putAll(nextState);

            nextState = nextState.nextState;
            
            if (nextState == null) {
                tail = this;
            }
            return this;
        } else {
            nextState.tail = tail;
            nextState.previousState = previousState;
            return nextState;
        }
    }

    public void addState(State newState) {
        newState.previousState = this;
        nextState = newState;
    }

    public void apply() {
        Set<ObjectGraphNode> applied = new HashSet<ObjectGraphNode>();
        State current = this;
        while (current != null) {
            for (StateValue val : current.values()) {
                if (!applied.contains(val.node)) {
        System.out.print(val.node.parent.parent != null ? val.node.parent.parent.name + "." : "");
        System.out.print(val.node.parent != null ? val.node.parent.name + "." : "");
        System.out.print(val.node.name);
        System.out.print("==");
        System.out.println(val.value == null ? "NULL" : val.value.getClass().toString());                
                    val.node.setCurrentValue(val.value);
                    applied.add(val.node);
                }
            }
            if (!current.deltaState) {
                current = null;
            } else {
                current = current.previousState;
            }
        }
    }
}