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

/**
 * Card is an abstraction of an Apple ][ hardware module which can carry its own
 * ROM (both CX, a 256-byte ROM which loads into memory depending on what slot
 * the card is in) and the C8 ROM is a 2K ROM loaded at $C800 when the card is
 * active.
 *
 * This class mostly just stubs out common functionality used by many different
 * cards and provides a consistent interface for more advanced features like VBL
 * synchronization.
 * Created on February 1, 2007, 5:35 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class Card extends Device {

    private final PagedMemory cxRom;
    private final PagedMemory c8Rom;
    private int slot;
    private RAMListener ioListener;
    private RAMListener firmwareListener;
    private RAMListener c8firmwareListener;

    /**
     * Creates a new instance of Card
     *
     * @param computer
     */
    public Card(Computer computer) {
        super(computer);
        cxRom = new PagedMemory(0x0100, PagedMemory.Type.CARD_FIRMWARE, computer);
        c8Rom = new PagedMemory(0x0800, PagedMemory.Type.CARD_FIRMWARE, computer);
    }

    @Override
    public String getShortName() {
        return "s" + getSlot();
    }

    @Override
    public String getName() {
        return getDeviceName() + " (slot " + slot + ")";
    }

    abstract public void reset();

    @Override
    public void attach() {
        registerListeners();
    }

    @Override
    public void detach() {
        suspend();
        unregisterListeners();
        super.detach();
    }

    abstract protected void handleIOAccess(int register, RAMEvent.TYPE type, int value, RAMEvent e);

    abstract protected void handleFirmwareAccess(int register, RAMEvent.TYPE type, int value, RAMEvent e);

    abstract protected void handleC8FirmwareAccess(int register, RAMEvent.TYPE type, int value, RAMEvent e);

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public PagedMemory getCxRom() {
        return cxRom;
    }

    public PagedMemory getC8Rom() {
        return c8Rom;
    }

    @Override
    public void reconfigure() {
        boolean restart = suspend();
        unregisterListeners();
        if (restart) {
            resume();
        }
        registerListeners();
    }

    public void notifyVBLStateChanged(boolean state) {
        // Do nothing unless overridden
    }

    public boolean suspendWithCPU() {
        return false;
    }

    protected void registerListeners() {
        RAM memory = computer.getMemory();
        int baseIO = 0x0c080 + slot * 16;
        int baseRom = 0x0c000 + slot * 256;
        ioListener = memory.observe(RAMEvent.TYPE.ANY, baseIO, baseIO + 15, (e) -> {
            int address = e.getAddress() & 0x0f;
            handleIOAccess(address, e.getType(), e.getNewValue(), e);
        });

        firmwareListener = memory.observe(RAMEvent.TYPE.ANY, baseRom, baseRom + 255, (e) -> {
            computer.getMemory().setActiveCard(slot);
            // Sather 6-4: Writes will still go through even when CXROM inhibits slot ROM
            if (SoftSwitches.CXROM.isOff() || !e.getType().isRead()) {
                handleFirmwareAccess(e.getAddress() & 0x0ff, e.getType(), e.getNewValue(), e);
            }
        });

        c8firmwareListener = memory.observe(RAMEvent.TYPE.ANY, 0xc800, 0xcfff, (e) -> {
            if (SoftSwitches.CXROM.isOff() && SoftSwitches.INTC8ROM.isOff()
                    && computer.getMemory().getActiveSlot() == slot) {
                handleC8FirmwareAccess(e.getAddress() - 0x0c800, e.getType(), e.getNewValue(), e);
            }
        });
    }

    protected void unregisterListeners() {
        computer.getMemory().removeListener(ioListener);
        computer.getMemory().removeListener(firmwareListener);
        computer.getMemory().removeListener(c8firmwareListener);
    }
}
