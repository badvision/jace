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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jace.Emulator;
import jace.state.Stateful;

/**
 * A softswitch is a hidden bit that lives in the MMU, it can be activated or
 * deactivated to change operating characteristics of the computer such as video
 * display mode or memory paging model. Other special softswitches access
 * keyboard and speaker ports. The underlying mechanic of softswitches is
 * managed by the RamListener/Ram model and, in the case of video modes, the
 * Video classes.
 *
 * The implementation of softswitches is in jace.apple2e.SoftSwitches
 * 
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 * @see jace.apple2e.SoftSwitches
 */
public abstract class SoftSwitch {

    @Stateful
    public Boolean state;
    private final Boolean initalState;
    private final List<RAMListener> listeners;
    private final List<Integer> exclusionActivate = new ArrayList<>();
    private final List<Integer> exclusionDeactivate = new ArrayList<>();
    private final List<Integer> exclusionQuery = new ArrayList<>();
    private final String name;
    private boolean toggleType = false;

    /**
     * Creates a new instance of SoftSwitch
     *
     * @param name
     * @param initalState
     */
    public SoftSwitch(String name, Boolean initalState) {
        this.initalState = initalState;
        this.state = initalState;
        this.listeners = new ArrayList<>();
        this.name = name;
    }

    public SoftSwitch(String name, int offAddress, int onAddress, int queryAddress, RAMEvent.TYPE changeType, Boolean initalState) {
        if (onAddress == offAddress && onAddress != -1) {
            toggleType = true;
//            System.out.println("Switch " + name + " is a toggle type switch!");
        }
        this.initalState = initalState;
        this.state = initalState;
        this.listeners = new ArrayList<>();
        this.name = name;
        int[] onAddresses = null;
        int[] offAddresses = null;
        int[] queryAddressList = null;
        if (onAddress >= 0) {
            onAddresses = new int[]{onAddress};
        }
        if (offAddress >= 0) {
            offAddresses = new int[]{offAddress};
        }
        if (queryAddress >= 0) {
            queryAddressList = new int[]{queryAddress};
        }
        init(offAddresses, onAddresses, queryAddressList, changeType);
    }

    public SoftSwitch(String name, int[] offAddrs, int[] onAddrs, int[] queryAddrs, RAMEvent.TYPE changeType, Boolean initalState) {
        this(name, initalState);
        init(offAddrs, onAddrs, queryAddrs, changeType);
    }

    private void init(int[] offAddrs, int[] onAddrs, int[] queryAddrs, RAMEvent.TYPE changeType) {
        if (toggleType) {
            List<Integer> addrs = new ArrayList<>();
            for (int i : onAddrs) {
                addrs.add(i);
            }
            Collections.sort(addrs);
            final int beginAddr = addrs.get(0);
            final int endAddr = addrs.get(addrs.size() - 1);
            for (int i = beginAddr; i < endAddr; i++) {
                if (!addrs.contains(i)) {
                    exclusionActivate.add(i);
                }
            }
            RAMListener l = new RAMListener("Softswitch toggle " + name, changeType, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(beginAddr);
                    setScopeEnd(endAddr);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    if (!exclusionActivate.contains(e.getAddress())) {
                        setState(!getState(), e);
                    }
                }
            };
            addListener(l);
        } else {
            if (onAddrs != null) {
                List<Integer> addrs = new ArrayList<>();
                for (int i : onAddrs) {
                    addrs.add(i);
                }
                Collections.sort(addrs);
                final int beginAddr = addrs.get(0);
                final int endAddr = addrs.get(addrs.size() - 1);
                for (int i = beginAddr; i < endAddr; i++) {
                    if (!addrs.contains(i)) {
                        exclusionActivate.add(i);
                    }
                }
                RAMListener l = new RAMListener("Softswitch on " + name, changeType, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                    @Override
                    protected void doConfig() {
                        setScopeStart(beginAddr);
                        setScopeEnd(endAddr);
                    }

                    @Override
                    protected void doEvent(RAMEvent e) {
                        if (e.getType().isRead()) {
                            e.setNewValue(Emulator.withComputer(c->c.getVideo().getFloatingBus(), (byte) 0));
                        }
                        if (!exclusionActivate.contains(e.getAddress())) {
                            // System.out.println("Access to "+Integer.toHexString(e.getAddress())+" ENABLES switch "+getName());
                            setState(true, e);
                        }
                    }
                };
                addListener(l);
            }

            if (offAddrs != null) {
                List<Integer> addrs = new ArrayList<>();
                for (int i : offAddrs) {
                    addrs.add(i);
                }
                final int beginAddr = addrs.get(0);
                final int endAddr = addrs.get(addrs.size() - 1);
                for (int i = beginAddr; i < endAddr; i++) {
                    if (!addrs.contains(i)) {
                        exclusionDeactivate.add(i);
                    }
                }
                RAMListener l = new RAMListener("Softswitch off " + name, changeType, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                    @Override
                    protected void doConfig() {
                        setScopeStart(beginAddr);
                        setScopeEnd(endAddr);
                    }

                    @Override
                    protected void doEvent(RAMEvent e) {
                        if (!exclusionDeactivate.contains(e.getAddress())) {
                            setState(false, e);
                            // System.out.println("Access to "+Integer.toHexString(e.getAddress())+" disables switch "+getName());
                        }
                    }
                };
                addListener(l);
            }
        }

        if (queryAddrs != null) {
            List<Integer> addrs = new ArrayList<>();
            for (int i : queryAddrs) {
                addrs.add(i);
            }
            final int beginAddr = addrs.get(0);
            final int endAddr = addrs.get(addrs.size() - 1);
            for (int i = beginAddr; i < endAddr; i++) {
                if (!addrs.contains(i)) {
                    exclusionQuery.add(i);
                }
            }
//            RAMListener l = new RAMListener(changeType, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
            RAMListener l = new RAMListener("Softswitch read state " + name, RAMEvent.TYPE.READ, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(beginAddr);
                    setScopeEnd(endAddr);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    if (!exclusionQuery.contains(e.getAddress())) {
                        e.setNewValue(0x0ff & readSwitch());
//                    System.out.println("Read from "+Integer.toHexString(e.getAddress())+" returns "+Integer.toHexString(e.getNewValue()));
                    }
                }
            };
            addListener(l);
        }
    }

    public boolean inhibit() {
        return false;
    }

    abstract protected byte readSwitch();

    protected void addListener(RAMListener l) {
        listeners.add(l);
    }

    public String getName() {
        return name;
    }

    public void reset() {
        if (initalState != null) {
            setState(initalState);
        }
    }

    public void register() {
        Emulator.withMemory(m -> {
            listeners.forEach(m::addListener);
        });
    }

    public void unregister() {
        Emulator.withMemory(m -> {
            listeners.forEach(m::removeListener);
        });
    }

    // Most softswitches act the same regardless of the ram event triggering them
    // But some softswitches are a little tricky (such as language card write) and need to assert extra conditions
    public void setState(boolean newState, RAMEvent e) {
        setState(newState);
    }

    public void setState(boolean newState) {
        if (inhibit()) {
            return;
        }
        state = newState;
        stateChanged();
    }

    public final boolean getState() {
        if (state == null) {
            return false;
        }
        return state;
    }

    abstract public void stateChanged();

    @Override
    public String toString() {
        return getName() + (getState() ? ":1" : ":0");
    }
}