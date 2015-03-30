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

import jace.state.Stateful;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private Boolean initalState;
    private List<RAMListener> listeners;
    private final List<Integer> exclusionActivate = new ArrayList<>();
    private final List<Integer> exclusionDeactivate = new ArrayList<>();
    private final List<Integer> exclusionQuery = new ArrayList<>();
    private String name;
    private boolean toggleType = false;
    protected Computer computer;

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
            RAMListener l = new RAMListener(changeType, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(beginAddr);
                    setScopeEnd(endAddr);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    if (!exclusionActivate.contains(e.getAddress())) {
                        //                        System.out.println("Access to "+Integer.toHexString(e.getAddress())+" ENABLES switch "+getName());
                        setState(!getState());
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
                RAMListener l = new RAMListener(changeType, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                    @Override
                    protected void doConfig() {
                        setScopeStart(beginAddr);
                        setScopeEnd(endAddr);
                    }

                    @Override
                    protected void doEvent(RAMEvent e) {
                        if (e.getType().isRead()) {
                            e.setNewValue(computer.getVideo().getFloatingBus());
                        }
                        if (!exclusionActivate.contains(e.getAddress())) {
                            //                        System.out.println("Access to "+Integer.toHexString(e.getAddress())+" ENABLES switch "+getName());
                            setState(true);
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
                RAMListener l = new RAMListener(changeType, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                    @Override
                    protected void doConfig() {
                        setScopeStart(beginAddr);
                        setScopeEnd(endAddr);
                    }

                    @Override
                    protected void doEvent(RAMEvent e) {
                        if (!exclusionDeactivate.contains(e.getAddress())) {
                            setState(false);
//                          System.out.println("Access to "+Integer.toHexString(e.getAddress())+" disables switch "+getName());
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
            RAMListener l = new RAMListener(RAMEvent.TYPE.READ, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
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

    public void register(Computer computer) {
        this.computer = computer;
        RAM m = computer.getMemory();
        listeners.stream().forEach((l) -> {
            m.addListener(l);
        });
    }

    public void unregister() {
        RAM m = computer.getMemory();
        listeners.stream().forEach((l) -> {
            m.removeListener(l);
        });
        this.computer = null;
    }

    public void setState(boolean newState) {
        if (inhibit()) {
            return;
        }
//        if (this != SoftSwitches.VBL.getSwitch() &&
//            this != SoftSwitches.KEYBOARD.getSwitch())
//            System.out.println("Switch "+name+" set to "+newState);
        state = newState;
        /*
         if (queryAddresses != null) {
         RAM m = computer.getMemory();
         for (int i:queryAddresses) {
         byte old = m.read(i, false);
         m.write(i, (byte) (old & 0x7f | (state ? 0x080:0x000)), false);
         }
         }
         */
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