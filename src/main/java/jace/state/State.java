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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import javafx.scene.image.Image;

/**
 * A state is nothing more than a map of captured variable values, except that a
 * state can also be a delta (set of changes) from a previous state. This allows
 * states to occupy a lot less memory and be more efficiently managed. States
 * are chained together in a linked list, using a special delete method to
 * ensure that delta states are properly merged.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@SuppressWarnings("all")
public class State extends HashMap<ObjectGraphNode, StateValue> {

    boolean deltaState;
    State previousState;
    State nextState;
    // Tail is only correct on the head node, everything else will likely be null
    State tail;
    Image screenshot;

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
        Set<ObjectGraphNode> applied = new LinkedHashSet<>();
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