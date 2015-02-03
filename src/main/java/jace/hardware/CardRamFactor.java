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
package jace.hardware;

import jace.Emulator;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.Computer;
import jace.core.Motherboard;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.state.Stateful;
import jace.core.Utility;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;

/**
 * This card strives to be a clone of the Applied Engineering RamFactor card
 * http://www.downloads.reactivemicro.com/Public/Apple%20II%20Items/Hardware/RAMFactor/RAMFactor%20v1.5.pdf
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
@Name("RamFactor")
public class CardRamFactor extends Card {
    int ADDR1 = 0;
    int ADDR2 = 1;
    int ADDR3 = 2;
    int DATA = 3;
    int BANK_SELECT = 0x0f;
    @ConfigurableField(category = "memory", name = "Ram size", description = "Size of card ram in KB", shortName = "size", defaultValue = "8192")
    public int RAM_SIZE = 8192;
    int actualSize = RAM_SIZE * 1024;
    // Important: pointer of current ram read/write for slinky access
    @Stateful
    int addressPointer = 0x0ffffff;
    @Stateful
    int firmwareBank = 0;
    @ConfigurableField(category = "performance", name = "Speed Boost", description = "Boost emulator speed when RAM in use", shortName = "boostSpeed", defaultValue = "false")
    public boolean speedBoost = false;
    
    @Override
    public String getDeviceName() {
        return "RamFactor";
    }
    ImageIcon indicator;
    public CardRamFactor(Computer computer) {
        super(computer);
        indicator=Utility.loadIcon("ram.png");
        try {
            loadRom("jace/data/RAMFactor14.rom");
        } catch (IOException ex) {
            Logger.getLogger(CardRamFactor.class.getName()).log(Level.SEVERE, null, ex);
        }
        allocateMemory(actualSize);
        updateFirmwareMemory();        
    }

    @Override
    public void reset() {
        firmwareBank = 0;
        updateFirmwareMemory();
    }
    
    @Override
    public void reconfigure() {
        actualSize = RAM_SIZE * 1024;
        allocateMemory(actualSize);
        updateFirmwareMemory();
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
//        Emulator.getFrame().addIndicator(this, indicator);
        value &= 0x0ff;
        switch (register) {
            case 0:
            case 4:
                // Lo-byte of pointer
                if (type.isRead()) {
                    e.setNewValue(addressPointer & 0x0ff);
                } else {
                    addressPointer = (addressPointer & 0x0ffff00) | value;
                    if (RAM_SIZE <= 1024) {
                        addressPointer |= 0x0f00000;
                    }
                }
                break;
            case 1:
            case 5:
                // Mid-byte of pointer
                if (type.isRead()) {
                    e.setNewValue((addressPointer >> 8) & 0x0ff);
                } else {
                    addressPointer = (addressPointer & 0x0ff00ff) | (value << 8);
                    if (RAM_SIZE <= 1024) {
                        addressPointer |= 0x0f00000;
                    }
                }
                break;
            case 2:
            case 6:
                // Hi-byte of pointer
                if (type.isRead()) {
                    if (RAM_SIZE <= 1024) {
                        e.setNewValue(0x0f0 | ((addressPointer >> 16) & 0x0ff));
                    } else {
                        e.setNewValue((addressPointer >> 16) & 0x0ff);
                    }
                } else {
                    addressPointer = (addressPointer & 0x00ffff) | (value << 16);
                    if (RAM_SIZE <= 1024) {
                        addressPointer |= 0x0f00000;
                    }
                }
                break;
            case 3:
            case 7:
                if (type.isRead()) {
                    e.setNewValue(readMemory(addressPointer));
                } else {
                    writeMemory(addressPointer, (byte) value);
                }
                addressPointer++;
                // Keep the pointer in range
                addressPointer &= 0x0ffffff;
                break;
            case 15: {
                // Firmware bank select
                if (type == TYPE.WRITE) {
                    firmwareBank = value;
                    updateFirmwareMemory();
                }
            }
            default:                
                if (type.isRead()) {
                    e.setNewValue(0x0ff);
                }
                break;
        }
    }
    
    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        if (speedBoost) {
            computer.getMotherboard().requestSpeed(this);
        }
    }

    @Override
    public void tick() {
        // Do nothing
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        if (speedBoost) {
            computer.getMotherboard().requestSpeed(this);
        }
    }

    @Stateful
    public byte[] cardRam;
    int ADDRESS_MASK = 0x07FFFFF;
    private byte readMemory(int i) {
        while (i >= cardRam.length) {
            i -= cardRam.length;
        }
        return cardRam[i];
    }

    private void writeMemory(int i, byte newValue) {
        while (i >= cardRam.length) {
            i -= cardRam.length;
        }
        cardRam[i] = newValue;
    }

    private void allocateMemory(int size) {
        if (cardRam != null && cardRam.length == size) return;
        cardRam = new byte[size];
        Arrays.fill(cardRam, (byte) 0);
    }

    @Override
    public void setSlot(int slot) {
        super.setSlot(slot);
        indicator.setDescription("Slot "+getSlot());
        // Rom has different images for each slot
        updateFirmwareMemory();
    }
    
    final int cxRomLength = 0x02000;
    byte[] romData = new byte[cxRomLength];
    public void loadRom(String path) throws IOException {
        InputStream romFile = CardRamFactor.class.getClassLoader().getResourceAsStream(path);
        try {
            if (romFile.read(romData) != cxRomLength) {
                throw new IOException("Bad RamFactor rom size");
            }
            updateFirmwareMemory();
        } catch (IOException ex) {
            throw ex;
        }
    }

    private void updateFirmwareMemory() {
        int romOffset = 0;
        if ((firmwareBank&1) == 1) {
            romOffset = 0x01000;
        }
        getCxRom().loadData(romData, romOffset + getSlot()*0x0100, 256);        
        getC8Rom().loadData(romData, romOffset + 0x0800, 0x0800);
    }
}