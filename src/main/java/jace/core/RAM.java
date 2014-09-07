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

import jace.apple2e.SoftSwitches;
import jace.config.Reconfigurable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * RAM is a 64K address space of paged memory. It also manages sets of memory
 * listeners, used by I/O as well as emulator add-ons (and cheats). RAM also
 * manages cards in the emulator because they are tied into the MMU memory
 * bankswitch logic.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class RAM implements Reconfigurable {
    public PagedMemory activeRead;
    public PagedMemory activeWrite;
    public List<RAMListener> listeners;
    public List<RAMListener>[] listenerMap;
    public List<RAMListener>[] ioListenerMap;
    protected Card[] cards;
    // card 0 = 80 column card firmware / system rom
    public int activeSlot = 0;

    /**
     * Creates a new instance of RAM
     */
    public RAM() {
        listeners = new Vector<RAMListener>();
        cards = new Card[8];
        refreshListenerMap();
    }

    public void setActiveCard(int slot) {
        if (activeSlot != slot) {
            activeSlot = slot;
            configureActiveMemory();
        } else if (!SoftSwitches.CXROM.getState()) {
            configureActiveMemory();
        }
    }

    public int getActiveSlot() {
        return activeSlot;
    }

    public Card[] getAllCards() {
        return cards;
    }

    public Card getCard(int slot) {
        if (slot >= 1 && slot <= 7) {
            return cards[slot];
        }
        return null;
    }

    public void addCard(Card c, int slot) {
        cards[slot] = c;
        c.setSlot(slot);
        c.attach();
    }

    public void removeCard(Card c) {
        c.suspend();
        c.detach();
        removeCard(c.getSlot());
    }

    public void removeCard(int slot) {
        if (cards[slot] != null) {
            cards[slot].suspend();
            cards[slot].detach();
        }
        cards[slot] = null;
    }

    abstract public void configureActiveMemory();

    public byte write(int address, byte b, boolean generateEvent, boolean requireSynchronization) {
        byte[] page = activeWrite.getMemoryPage(address);
        byte old = 0;
        if (page == null) {
            if (generateEvent) {
                callListener(RAMEvent.TYPE.WRITE, address, old, b, requireSynchronization);
            }
        } else {
            int offset = address & 0x0FF;
            old = page[offset];
            if (generateEvent) {
                b = callListener(RAMEvent.TYPE.WRITE, address, old, b, requireSynchronization);
            }
            page[offset] = b;
        }
        return old;
    }

    public void writeWord(int address, int w, boolean generateEvent, boolean requireSynchronization) {
        int lsb = write(address, (byte) (w & 0x0ff), generateEvent, requireSynchronization);
        int msb = write(address + 1, (byte) (w >> 8), generateEvent, requireSynchronization);
//        int oldValue = msb << 8 + lsb;
    }

    public byte readRaw(int address) {
        //    if (address >= 65536) return 0;
        return activeRead.getMemoryPage(address)[address & 0x0FF];
    }

    public byte read(int address, RAMEvent.TYPE eventType, boolean triggerEvent, boolean requireSyncronization) {
        //    if (address >= 65536) return 0;
        byte value = activeRead.getMemoryPage(address)[address & 0x0FF];
//        if (triggerEvent || ((address & 0x0FF00) == 0x0C000)) {
        if (triggerEvent || (address & 0x0FFF0) == 0x0c030) {
            value = callListener(eventType, address, value, value, requireSyncronization);
        }
        return value;
    }

    public int readWordRaw(int address) {
        int lsb = 0x00ff & readRaw(address);
        int msb = (0x00ff & readRaw(address + 1)) << 8;
        return msb + lsb;
    }

    public int readWord(int address, RAMEvent.TYPE eventType, boolean triggerEvent, boolean requireSynchronization) {
        int lsb = 0x00ff & read(address, eventType, triggerEvent, requireSynchronization);
        int msb = (0x00ff & read(address + 1, eventType, triggerEvent, requireSynchronization)) << 8;
        int value = msb + lsb;
//        if (generateEvent) {
//            callListener(RAMEvent.TYPE.READ, address, value, value);
//        }
        return value;
    }

    private void mapListener(RAMListener l, int address) {
        if ((address & 0x0FF00) == 0x0C000) {
            int index = address & 0x0FF;
            List<RAMListener> ioListeners = ioListenerMap[index];
            if (ioListeners == null) {
                ioListeners = new ArrayList<>();
                ioListenerMap[index] = ioListeners;
            }
            if (!ioListeners.contains(l)) {
                ioListeners.add(l);
            }
        } else {
            int index = address >> 8;
            List<RAMListener> otherListeners = listenerMap[index];
            if (otherListeners == null) {
                otherListeners = new ArrayList<>();
                listenerMap[index] = otherListeners;
            }
            if (!otherListeners.contains(l)) {
                otherListeners.add(l);
            }
        }
    }

    private void addListenerRange(RAMListener l) {
        if (l.getScope() == RAMEvent.SCOPE.ADDRESS) {
            mapListener(l, l.getScopeStart());
        } else {
            int start = 0;
            int end = 0x0ffff;
            if (l.getScope() == RAMEvent.SCOPE.RANGE) {
                start = l.getScopeStart();
                end = l.getScopeEnd();
            }
            for (int i = start; i <= end; i++) {
                mapListener(l, i);
            }
        }
    }

    private void refreshListenerMap() {
        listenerMap = new ArrayList[256];
        ioListenerMap = new ArrayList[256];
        for (RAMListener l : listeners) {
            addListenerRange(l);
        }
    }

    public void addListener(final RAMListener l) {
        boolean restart = Computer.pause();
        if (listeners.contains(l)) {
            return;
        }
        listeners.add(l);
        addListenerRange(l);
        if (restart) {
            Computer.resume();
        }
    }

    public void removeListener(final RAMListener l) {
        boolean restart = Computer.pause();
        listeners.remove(l);
        refreshListenerMap();
        if (restart) {
            Computer.resume();
        }
    }

    public byte callListener(RAMEvent.TYPE t, int address, int oldValue, int newValue, boolean requireSyncronization) {
        List<RAMListener> activeListeners = null;
        if (requireSyncronization) {
            Computer.getComputer().getCpu().suspend();
        }
        if ((address & 0x0FF00) == 0x0C000) {
            activeListeners = ioListenerMap[address & 0x0FF];
            if (activeListeners == null && t.isRead()) {
                if (requireSyncronization) {
                    Computer.getComputer().getCpu().resume();
                }
                return Computer.getComputer().getVideo().getFloatingBus();
            }
        } else {
            activeListeners = listenerMap[(address >> 8) & 0x0ff];
        }
        if (activeListeners != null) {
            RAMEvent e = new RAMEvent(t, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, address, oldValue, newValue);
            for (RAMListener l : activeListeners) {
                l.handleEvent(e);
            }
            if (requireSyncronization) {
                Computer.getComputer().getCpu().resume();
            }
            return (byte) e.getNewValue();
        }
        if (requireSyncronization) {
            Computer.getComputer().getCpu().resume();
        }
        return (byte) newValue;
    }

    abstract protected void loadRom(String path) throws IOException;

    public void dump() {
        for (int i = 0; i < 0x0FFFF; i += 16) {
            System.out.print(Integer.toString(i, 16));
            System.out.print(":");
            String part1 = "";
            String part2 = "";
            for (int j = 0; j < 16; j++) {
                int a = i + j;
                int br = 0x0FF & activeRead.getMemory()[i >> 8][i & 0x0ff];
                String s1 = Integer.toString(br, 16);
                System.out.print(' ');
                if (s1.length() == 1) {
                    System.out.print('0');
                }
                System.out.print(s1);

                /*
                 try {
                 int bw = 0;
                 bw = 0x0FF & activeWrite.getMemory().get(a/256)[a%256];
                 String s2 = (br == bw) ? "**" : Integer.toString(bw,16);
                 System.out.print(' ');
                 if (s2.length()==1) System.out.print('0');
                 System.out.print(s2);
                 } catch (NullPointerException ex) {
                 System.out.print(" --");
                 }
                 */
            }
            System.out.println();
//            System.out.println(Integer.toString(i, 16)+":"+part1+" -> "+part2);
        }
    }
    
    abstract public void attach();
    abstract public void detach();
}