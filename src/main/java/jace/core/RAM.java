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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import jace.Emulator;
import jace.apple2e.SoftSwitches;
import jace.config.Reconfigurable;
import jace.core.RAMEvent.TYPE;

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
    private final Set<RAMListener> listeners = new ConcurrentSkipListSet<>();
    @SuppressWarnings("unchecked")
    private final Set<RAMListener>[] listenerMap = (Set<RAMListener>[]) new Set[256];
    @SuppressWarnings("unchecked")
    private final Set<RAMListener>[] ioListenerMap = (Set<RAMListener>[]) new Set[256];
    @SuppressWarnings("unchecked")
    public Optional<Card>[] cards = (Optional<Card>[]) new Optional[8];
    // card 0 = 80 column card firmware / system rom
    public int activeSlot = 0;

    /**
     * Creates a new instance of RAM
     *
     * @param computer
     */
    public RAM() {
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
        configureActiveMemory();
    }

    public void removeCard(Card c) {
        removeCard(c.getSlot());
    }

    public void removeCard(int slot) {
        cards[slot].ifPresent(Card::suspend);
        cards[slot].ifPresent(Card::detach);
        cards[slot] = Optional.empty();
    }

    abstract public void configureActiveMemory();
    public void copyFrom(RAM other) {
        cards = other.cards;
        activeSlot = other.activeSlot;
        listeners.addAll(other.listeners);
        refreshListenerMap();
        configureActiveMemory();
    }

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
        write(address + 1, (byte) ((w >> 8) & 0x0ff), generateEvent, requireSynchronization);
    }

    public byte readRaw(int address) {
        //    if (address >= 65536) return 0;
        return activeRead.getMemoryPage(address)[address & 0x0FF];
    }

    public byte read(int address, RAMEvent.TYPE eventType, boolean triggerEvent, boolean requireSyncronization) {
        //    if (address >= 65536) return 0;
        byte value = activeRead.getMemoryPage(address)[address & 0x0FF];
//        if (triggerEvent || ((address & 0x0FF00) == 0x0C000)) {
//        if (triggerEvent || (address & 0x0FFF0) == 0x0c030) {
        if (triggerEvent) {
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
        return msb + lsb;
    }

    // This is used by opcodes that wrap around page boundaries
    public int readWordPageWraparound(int address, TYPE eventType, boolean triggerEvent, boolean requireSynchronization) {
        int lsb = 0x00ff & read(address, eventType, triggerEvent, requireSynchronization);
        int addr1 = ((address + 1) & 0x0ff) | (address & 0x0ff00);
        int msb = (0x00ff & read(addr1, eventType, triggerEvent, requireSynchronization)) << 8;
        return msb + lsb;
    }

    private synchronized void mapListener(RAMListener l, int address) {
        if ((address & 0x0FF00) == 0x0C000) {
            int index = address & 0x0FF;
            Set<RAMListener> ioListeners = ioListenerMap[index];
            if (ioListeners == null) {
                ioListeners = new ConcurrentSkipListSet<>();
                ioListenerMap[index] = ioListeners;
            }
            ioListeners.add(l);
        } else {
            int index = (address >> 8) & 0x0FF;
            Set<RAMListener> otherListeners = listenerMap[index];
            if (otherListeners == null) {
                otherListeners = new ConcurrentSkipListSet<>();
                listenerMap[index] = otherListeners;
            }
            otherListeners.add(l);
        }
    }

    private void addListenerRange(RAMListener l) {
        if (l.getScope() == RAMEvent.SCOPE.ADDRESS) {
            mapListener(l, l.getScopeStart());
        } else {
            int start = l.getScopeStart();
            int end = l.getScopeEnd();
            if (l.getScope() == RAMEvent.SCOPE.ANY) {
                start = 0;
                end = 0x0FFFF;
            }
            for (int i = start; i <= end; i++) {
                mapListener(l, i);
            }
        }
    }

    private void refreshListenerMap() {
        // Wipe out existing maps
        for (int i = 0; i < 256; i++) {
            listenerMap[i] = null;
            ioListenerMap[i] = null;
        }
        listeners.forEach(this::addListenerRange);
    }

    public RAMListener observeOnce(String observerationName, RAMEvent.TYPE type, int address, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(observerationName, type, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(address);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                handler.handleEvent(e);
                unregister();
            }
        });
    }

    public RAMListener observe(String observerationName, RAMEvent.TYPE type, int address, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(observerationName, type, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
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

    public RAMListener observe(String observerationName, RAMEvent.TYPE type, int address, Boolean auxFlag, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(observerationName, type, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
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

    public RAMListener observe(String observerationName, RAMEvent.TYPE type, int addressStart, int addressEnd, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(observerationName, type, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
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

    public RAMListener observe(String observerationName, RAMEvent.TYPE type, int addressStart, int addressEnd, Boolean auxFlag, RAMEvent.RAMEventHandler handler) {
        return addListener(new RAMListener(observerationName, type, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
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

    private boolean isAuxFlagCorrect(RAMEvent e, Boolean auxFlag) {
        if (e.getAddress() < 0x0100) {
            return SoftSwitches.AUXZP.getState() == auxFlag;
        } else if (e.getAddress() >= 0x0C000 && e.getAddress() <= 0x0CFFF) {
            // I/O page doesn't care about the aux flag
            return true;
        } else return auxFlag == null || SoftSwitches.RAMRD.getState() == auxFlag;
    }

    public RAMListener addListener(final RAMListener l) {
        if (l == null) {
            return l;
        }
        if (listeners.contains(l)) {
            removeListener(l);
        }
        listeners.add(l);
        Emulator.whileSuspended((c)->addListenerRange(l));
        return l;
    }
    
    public RAMListener addExecutionTrap(String observerationName, int address, Consumer<RAMEvent> handler) {
        RAMListener listener = new RAMListener(observerationName, RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(address);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                handler.accept(e);
            }
        };
        addListener(listener);
        return listener;
    }

    public void removeListener(final RAMListener l) {
        if (l == null) {
            return;
        }
        if (!listeners.contains(l)) {
            return;
        }
        listeners.remove(l);
        Emulator.whileSuspended(c->refreshListenerMap());
    }

    private byte _callListener(RAMEvent.TYPE t, int address, int oldValue, int newValue) {
        Set<RAMListener> activeListeners;
        if ((address & 0x0FF00) == 0x0C000) {
            activeListeners = ioListenerMap[address & 0x0FF];
            if (activeListeners == null && t.isRead()) {
                return Emulator.withComputer(c->c.getVideo().getFloatingBus(), (byte) 0);
            }
        } else {
            activeListeners = listenerMap[(address >> 8) & 0x0ff];
        }
        if (activeListeners != null && !activeListeners.isEmpty()) {
            RAMEvent e = new RAMEvent(t, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, address, oldValue, newValue);
            activeListeners.forEach((l) -> l.handleEvent(e));
            if (!e.isIntercepted() && (address & 0x0FF00) == 0x0C000) {
                return Emulator.withComputer(c->c.getVideo().getFloatingBus(), (byte) 0);
            }
            return (byte) e.getNewValue();
        }
        return (byte) newValue;
    }

    public byte callListener(RAMEvent.TYPE t, int address, int oldValue, int newValue, boolean requireSyncronization) {
        if (requireSyncronization && Emulator.withComputer(c->c.getMotherboard().isDeviceThread(), false)) {
            AtomicInteger returnValue = new AtomicInteger();
            Emulator.withComputer(computer -> {
                computer.getCpu().whileSuspended(()-> returnValue.set(_callListener(t, address, oldValue, newValue)));
                _callListener(t, address, oldValue, newValue);
            });
            return (byte) returnValue.get();
        } else {
            return _callListener(t, address, oldValue, newValue);
        }
    }

    abstract protected void loadRom(String path) throws IOException;

    abstract public void attach();

    public void detach() {
        detachListeners.forEach(Runnable::run);
    }

    abstract public void performExtendedCommand(int i);

    abstract public String getState();
    
    abstract public void resetState();

    List<Runnable> detachListeners = new ArrayList<>();
    public void onDetach(Runnable r) {
        detachListeners.add(r);
    }
}
