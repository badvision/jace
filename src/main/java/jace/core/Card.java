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
public abstract class Card extends TimedDevice {

    private PagedMemory cxRom;
    private PagedMemory c8Rom;
    private int slot;
    private RAMListener ioListener;
    private RAMListener firmwareListener;
    private RAMListener c8firmwareListener;

    /**
     * Creates a new instance of Card
     *
     * @param computer
     */
    public Card(boolean isThrottled) {
        super(isThrottled);
        cxRom = new PagedMemory(0x0100, PagedMemory.Type.CARD_FIRMWARE);
        c8Rom = new PagedMemory(0x0800, PagedMemory.Type.CARD_FIRMWARE);
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
        // Emulator.whileSuspended(c-> {
            unregisterListeners();
            registerListeners();
        // });
    }

    public void notifyVBLStateChanged(boolean state) {
        // Do nothing unless overridden
    }

    protected void registerListeners() {
        int baseIO = 0x0c080 + slot * 16;
        int baseRom = 0x0c000 + slot * 256;
        ioListener = getMemory().observe("Slot " + getSlot() + " " + getDeviceName() + " IO access", RAMEvent.TYPE.ANY, baseIO, baseIO + 15, (e) -> {
            int address = e.getAddress() & 0x0f;
            handleIOAccess(address, e.getType(), e.getNewValue(), e);
        });

        firmwareListener = getMemory().observe("Slot " + getSlot() + " " + getDeviceName() + " CX Firmware access", RAMEvent.TYPE.ANY, baseRom, baseRom + 255, (e) -> {
            getMemory().setActiveCard(slot);
            // Sather 6-4: Writes will still go through even when CXROM inhibits slot ROM
            if (SoftSwitches.CXROM.isOff() || !e.getType().isRead()) {
                handleFirmwareAccess(e.getAddress() & 0x0ff, e.getType(), e.getNewValue(), e);
            }
        });

        c8firmwareListener = getMemory().observe("Slot " + getSlot() + " " + getDeviceName() + " C8 Firmware access", RAMEvent.TYPE.ANY, 0xc800, 0xcfff, (e) -> {
            if (SoftSwitches.CXROM.isOff() && SoftSwitches.INTC8ROM.isOff()
                    && getMemory().getActiveSlot() == slot) {
                handleC8FirmwareAccess(e.getAddress() - 0x0c800, e.getType(), e.getNewValue(), e);
            }
        });
    }

    protected void unregisterListeners() {
        getMemory().removeListener(ioListener);
        getMemory().removeListener(firmwareListener);
        getMemory().removeListener(c8firmwareListener);
    }
}
