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

import jace.Emulator;
import jace.apple2e.SoftSwitches;
import jace.config.ConfigurableField;
import jace.config.InvokableAction;
import jace.config.Reconfigurable;
import jace.core.Computer;
import jace.core.PagedMemory;
import jace.core.Video;
import java.awt.image.BufferedImage;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class StateManager implements Reconfigurable {

    private static StateManager instance;

    public static StateManager getInstance(Computer computer) {
        if (instance == null) {
            instance = new StateManager(computer);
        }
        return instance;
    }
    State alphaState;
    Set<ObjectGraphNode> allStateVariables;
    WeakHashMap<Object, ObjectGraphNode> objectLookup;
    long maxMemory = -1;
    long freeRequired = -1;
    @ConfigurableField(category = "Emulator", name = "Max states", description = "How many states can be captured, oldest states are automatically truncated.", defaultValue = "150")
    public int maxStates = 100;
    @ConfigurableField(category = "Emulator", name = "Capture frequency", description = "How often states are captured, in relation to each VBL (1 = 60 states/second, 2 = 30 states/second, 3 = 15 states/second, etc", defaultValue = "3")
    public int captureFrequency = 3;
    private ObjectGraphNode<BufferedImage> imageGraphNode;

    Computer computer;

    private StateManager(Computer computer) {
        this.computer = computer;
    }

    private void buildStateMap() {
        allStateVariables = new LinkedHashSet<>();
        objectLookup = new WeakHashMap<>();
        ObjectGraphNode emulator = new ObjectGraphNode(Emulator.instance);
        emulator.name = "Emulator";
        Set visited = new HashSet();
        buildStateMap(emulator, visited);

        // Also track all softswitches
        for (SoftSwitches s : SoftSwitches.values()) {
            final SoftSwitches ss = s;
            ObjectGraphNode switchNode = new ObjectGraphNode(s);
            switchNode.name = s.toString();
            ObjectGraphNode switchVar = new ObjectGraphNode(s.getSwitch()) {
                @Override
                /**
                 * This is a more efficient way of updating the softswitch
                 * states And really in a way this works out much better to
                 * ensure that memory and graphics pages are set up correctly
                 * when resuming states.
                 */
                public void setCurrentValue(Object value) {
                    Boolean b = (Boolean) value;
                    ss.getSwitch().setState(b);
                }

                @Override
                public Object getCurrentValue() {
                    return ss.getSwitch().getState();
                }
            };
            switchVar.name = "switch";
            switchVar.parent = switchNode;
            allStateVariables.add(switchVar);
            objectLookup.put(s, switchNode);
            objectLookup.put(s.getSwitch(), switchVar);
        }
    }

    private void buildStateMap(ObjectGraphNode node, Set visited) {
        if (visited.contains(node)) {
            return;
        }
        Object currentValue = node.getCurrentValue();
        if (currentValue == null || visited.contains(currentValue)) {
            return;
        }
        visited.add(node);
        visited.add(currentValue);
        objectLookup.put(node.getCurrentValue(), node);
        for (Field f : node.getCurrentValue().getClass().getFields()) {
            try {
                Object o = f.get(node.getCurrentValue());
                if (o == null) {
                    continue;
                }
                Annotation a = f.getAnnotation(Stateful.class);
                ObjectGraphNode child = new ObjectGraphNode(o);
                child.name = f.getName();
                child.parent = node;
                if (a != null) {
                    child.isStateful = true;
                    addStateVariable(child, f);
                }
                if (!f.getType().isPrimitive() && !f.getType().isArray()) {
                    // This is not stateful, but examine its children just in case
                    buildStateMap(child, visited);
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(StateManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * The node and field correspond to an object member field that has a
     *
     * @Stateful annotation. This method has to make sense of this and plan out
     * how states should be captured for this field.
     * @param node
     * @param f
     */
    private void addStateVariable(ObjectGraphNode node, Field f) {
//        if (f == null) {
//            System.out.println("State var: "+node.parent.name+"."+node.name+ ">>" + node.index);
//        } else {
//            System.out.println("State var: "+node.parent.name+"."+node.name+ ">>" + f.getDeclaringClass().getName() + "." + f.getName());
//        }
        // For paged memory, video and softswiches we might have to do something
        // more sophosticated.
        Class type = node.getCurrentValue().getClass();
        if (PagedMemory.class.isAssignableFrom(type)) {
            addMemoryPages(node, f);
        } else if (BufferedImage.class.isAssignableFrom(type)) {
            addVideoFrame(node, f);
        } else if (List.class.isAssignableFrom(type)) {
            List l = (List) node.getCurrentValue();
            Type fieldGenericType = f.getGenericType();
//            Class genericType = Object.class;
//            if (fieldGenericType instanceof ParameterizedType) {
//                genericType = (Class) ((ParameterizedType) fieldGenericType).getActualTypeArguments()[0];
//            } else {
//                System.out.println("NOT PARAMATERIZED!");
//            }
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i) != null) {
//                    ObjectGraphNode inode = new ObjectGraphNode(genericType);
                    Object obj = l.get(i);
                    if (obj == null) {
                        continue;
                    }
                    ObjectGraphNode inode = new ObjectGraphNode(obj);
//                    inode.source = new WeakReference();
                    inode.parent = node;
                    inode.name = String.valueOf(i);
                    inode.index = i;
                    // Build this recursively because it might be a nested type (e.g. a list of maps)
                    // If it isn't a nested type then the default case shoud apply.
                    addStateVariable(inode, null);
                }
            }
        } else if (Map.class.isAssignableFrom(type)) {
            // TODO:
            // Walk through members of the map, etc.
            // This will at least let RamWorks memory pages work with state management
        } else {
            // This is the default case, just capture the field and move on
            allStateVariables.add(node);
            node.isPrimitive = f.getType().isPrimitive();
            // Since we can only guess how state changes just assume we have to check for changes every time.
            node.forceCheck = true;
        }
    }

    /**
     * Track a stateful video framebuffer.
     *
     * @param objectGraphNode
     * @param f
     */
    private void addVideoFrame(ObjectGraphNode<BufferedImage> node, Field f) {
        imageGraphNode = node;
    }

    /**
     * Track a stateful set of memory.
     *
     * @param objectGraphNode
     * @param f
     */
    private void addMemoryPages(ObjectGraphNode<PagedMemory> node, Field f) {
        PagedMemory mem = node.getCurrentValue();
        ObjectGraphNode<byte[][]> internalmem = new ObjectGraphNode<>(mem.internalMemory);
        internalmem.parent = node;
        internalmem.name = "internalMemory";
        for (int i = 0; i < mem.internalMemory.length; i++) {
            byte[] memPage = mem.internalMemory[i];
            if (memPage == null) {
                continue;
            }
            ObjectGraphNode<byte[]> page = new ObjectGraphNode<>(memPage);
            page.parent = internalmem;
            page.name = String.valueOf(i);
            page.index = i;
            page.forceCheck = false;
            allStateVariables.add(page);
            objectLookup.put(mem.internalMemory[i], page);
        }
    }

    public static void markDirtyValue(Object o, Computer computer) {
        StateManager manager = getInstance(computer);
        if (manager.objectLookup == null) {
            return;
        }
        ObjectGraphNode node = manager.objectLookup.get(o);
        if (node == null) {
            return;
        }
        node.markDirty();
    }

    /**
     *
     * @return
     */
    @Override
    public String getName() {
        return "State Manager";
    }

    /**
     *
     * @return
     */
    @Override
    public String getShortName() {
        return "state";
    }

    /**
     * If reconfigure is called, it means the emulator state has changed too
     * greatly and we need to abandon captured states and start from scratch.
     */
    @Override
    public void reconfigure() {
        boolean resume = computer.pause();
        isValid = false;

        // Now figure out how much memory we're allowed to eat
        maxMemory = Runtime.getRuntime().maxMemory();
        // If we have less than 2% heap remaining then states will be recycled
        freeRequired = maxMemory / 50L;
        frameCounter = captureFrequency;
        if (resume) {
            computer.resume();
        }
    }
    boolean isValid = false;

    public void invalidate() {
        isValid = false;
    }
    int stateCount = 0;

    public void captureState() {
        // If the state graph is invalidated it means we have to abandon all
        // previously captured states.  This helps ensure that rewinding will
        // not result in an unintended or invalid state.
        if (!isValid) {
            alphaState = null;
            if (allStateVariables != null) {
                allStateVariables.clear();
            }
            allStateVariables = null;
            // This will probably result in a lot of invalidated objects
            // so it's a good idea to suggest to the JVM to reclaim memory now.
            System.gc();

            if (Emulator.instance == null) {
                return;
            }

            // Re-examine the object structure of the emulator in case it changed
            buildStateMap();
            System.out.println(allStateVariables.size() + " variables tracked per state");
            System.out.println(objectLookup.entrySet().size() + " objects tracked in emulator model");
            isValid = true;
            stateCount = 0;
        }
        if (alphaState == null) {
            System.gc();
            alphaState = captureAlphaState();
            alphaState.tail = alphaState;
        } else {
            if (Runtime.getRuntime().freeMemory() <= freeRequired) {
                invalidate();
                return;
            }
            while (stateCount >= maxStates) {
                removeOldestState();
                stateCount--;
            }

            State newState = captureDeltaState(alphaState.tail);
//            State newState = (stateCount % 2 == 0) ? captureDeltaState(alphaState.tail) : captureAlphaState();
            alphaState.tail.addState(newState);
            alphaState.tail = newState;
        }
        // Now capture the current screen
        alphaState.tail.screenshot = getScreenshot();
        stateCount++;
    }

    private Image getScreenshot() {
        Image screen = computer.getVideo().getFrameBuffer();
        return new WritableImage(screen.getPixelReader(), (int) screen.getWidth(), (int) screen.getHeight());
    }

    private State captureAlphaState() {
        State s = new State();
        s.deltaState = false;
        allStateVariables.stream().map((node) -> {
            s.put(node, new StateValue(node));
            return node;
        }).forEach((node) -> {
            node.markClean();
        });
        return s;
    }

    private State captureDeltaState(State tail) {
        State s = new State();
        s.deltaState = true;
        allStateVariables.stream().filter((node) -> !(!node.valueChanged(tail))).map((node) -> {
            // If there are no changes to this node value, don't waste memory on it.
            s.put(node, new StateValue(node));
            return node;
        }).forEach((node) -> {
            node.markClean();
        });
        return s;

    }

    private void removeOldestState() {
        if (alphaState == null) {
            return;
        }
        if (alphaState.nextState == null) {
            alphaState = null;
        } else {
            alphaState = alphaState.deleteNext();
        }
    }
    // Don't capture for the first few seconds of emulation.  This is sort of a
    // hack but is also a very elegant way to help the emulator avoid wasting
    // time at start since the emulator state changes a lot at first.
    int frameCounter = 200;

    /**
     * Every time the Apple reaches VBL there is a screen update event The rest
     * of the emulator is notified after the video frame was redrawn, so this is
     * the best time to capture the state.
     */
    public void notifyVBLActive() {
        frameCounter--;
        if (frameCounter > 0) {
            return;
        }
        frameCounter = captureFrequency;
        captureState();
    }

    @InvokableAction(
            name = "Rewind",
            alternatives = "Timewarp",
            description = "Go back 1 second",
            defaultKeyMapping = {"ctrl+shift+Open Bracket"}
    )
    public static void beKindRewind() {
        StateManager manager = getInstance(Emulator.computer);
        new Thread(()->manager.rewind(60 / manager.captureFrequency)).start();
    }

    public void rewind(int numStates) {
        boolean resume = computer.pause();
        State state = alphaState.tail;
        while (numStates > 0 && state.previousState != null) {
            state = state.previousState;
            numStates--;
        }
        state.apply();
        alphaState.tail = state;
        state.nextState = null;
        Video.forceRefresh();
        System.gc();
        if (resume) {
            computer.resume();
        }
    }
}
