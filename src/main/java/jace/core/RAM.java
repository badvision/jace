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
import java.util.Optional;

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
    public Optional<Card>[] cards;
    // card 0 = 80 column card firmware / system rom
    public int activeSlot = 0;
    protected final Computer computer;

    /**
     * Creates a new instance of RAM
     *
     * @param computer
     */
    public RAM(Computer computer) {
        this.computer = computer;
        listeners = new ArrayList<>();
        cards = new Optional[8];
        for (int i = 0; i < 8; i++) {
            cards[i] = Optional.empty();
        }
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

    public Optional<Card>[] getAllCards() {
        return cards;
    }

    public Optional<Card> getCard(int slot) {
        if (slot >= 1 && slot <= 7) {
            return cards[slot];
        }
        return Optional.empty();
    }

    public void addCard(Card c, int slot) {
        removeCard(slot);
        cards[slot] = Optional.of(c);
        c.setSlot(slot);
        c.attach();
    }

    public void removeCard(Card c) {
        c.suspend();
        c.detach();
        removeCard(c.getSlot());
    }

    public void removeCard(int slot) {
        cards[slot].ifPresent(Card::suspend);
        cards[slot].ifPresent(Card::detach);
        cards[slot] = Optional.empty();
    }

    abstract public void configureActiveMemory();

    public void write(int address, byte b, boolean generateEvent, boolean requireSynchronization) {
        byte[] page = activeWrite.getMemoryPage(address);
        if (page == null) {
            if (generateEvent) {
                callListener(RAMEvent.TYPE.WRITE, address, 0, b, requireSynchronization);
            }
        } else {
            int offset = address & 0x0FF;
            byte old = page[offset];
            if (generateEvent) {
                page[offset] = callListener(RAMEvent.TYPE.WRITE, address, old, b, requireSynchronization);
            } else {
                page[offset] = b;
            }
        }
    }

    public void writeWord(int address, int w, boolean generateEvent, boolean requireSynchronization) {
        write(address, (byte) (w & 0x0ff), generateEvent, requireSynchronization);
        write(address + 1, (byte) (w >> 8), generateEvent, requireSynchronization);
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
        listeners.stream().forEach((l) -> {
            addListenerRange(l);
        });
    }

    public RAMListener observe(RAMEvent.TYPE type, int address, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(type, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(address);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                handler.handleEvent(e);
            }
        });
    }

    public RAMListener observe(RAMEvent.TYPE type, int address, boolean auxFlag, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(type, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(address);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                if (isAuxFlagCorrect(e, auxFlag)) {
                    handler.handleEvent(e);
                }
            }
        });
    }

    public RAMListener observe(RAMEvent.TYPE type, int addressStart, int addressEnd, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(type, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(addressStart);
                setScopeEnd(addressEnd);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                handler.handleEvent(e);
            }
        });
    }

    public RAMListener observe(RAMEvent.TYPE type, int addressStart, int addressEnd, boolean auxFlag, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(type, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(addressStart);
                setScopeEnd(addressEnd);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                if (isAuxFlagCorrect(e, auxFlag)) {
                    handler.handleEvent(e);
                }
            }
        });
    }

    private boolean isAuxFlagCorrect(RAMEvent e, boolean auxFlag) {
        if (e.getAddress() < 0x0100) {
            if (SoftSwitches.AUXZP.getState() != auxFlag) {
                return false;
            }
        } else if (SoftSwitches.RAMRD.getState() != auxFlag) {
            return false;
        }
        return true;
    }

    public RAMListener addListener(final RAMListener l) {
        boolean restart = computer.pause();
        if (listeners.contains(l)) {
            return l;
        }
        listeners.add(l);
        addListenerRange(l);
        if (restart) {
            computer.resume();
        }
        return l;
    }

    public void removeListener(final RAMListener l) {
        boolean restart = computer.pause();
        listeners.remove(l);
        refreshListenerMap();
        if (restart) {
            computer.resume();
        }
    }

    public byte callListener(RAMEvent.TYPE t, int address, int oldValue, int newValue, boolean requireSyncronization) {
        List<RAMListener> activeListeners;
        if (requireSyncronization) {
            computer.getCpu().suspend();
        }
        if ((address & 0x0FF00) == 0x0C000) {
            activeListeners = ioListenerMap[address & 0x0FF];
            if (activeListeners == null && t.isRead()) {
                if (requireSyncronization) {
                    computer.getCpu().resume();
                }
                return computer.getVideo().getFloatingBus();
            }
        } else {
            activeListeners = listenerMap[(address >> 8) & 0x0ff];
        }
        if (activeListeners != null) {
            RAMEvent e = new RAMEvent(t, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, address, oldValue, newValue);
            activeListeners.stream().forEach((l) -> {
                l.handleEvent(e);
            });
            if (requireSyncronization) {
                computer.getCpu().resume();
            }
            return (byte) e.getNewValue();
        }
        if (requireSyncronization) {
            computer.getCpu().resume();
        }
        return (byte) newValue;
    }

    abstract protected void loadRom(String path) throws IOException;

    abstract public void attach();

    abstract public void detach();
}
