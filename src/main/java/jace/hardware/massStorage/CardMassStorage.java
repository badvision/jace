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
package jace.hardware.massStorage;

import jace.EmulatorUILogic;
import jace.apple2e.MOS65C02;
import jace.config.Name;
import jace.core.Card;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import jace.hardware.ProdosDriver;
import jace.hardware.SmartportDriver;
import jace.library.MediaConsumer;
import jace.library.MediaConsumerParent;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hard disk and 800k floppy (smartport) controller card. HDV and 2MG images are
 * both supported.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("Mass Storage Device")
public class CardMassStorage extends Card implements MediaConsumerParent {

    MassStorageDrive drive1;
    MassStorageDrive drive2;

    public CardMassStorage(Computer computer) {
        super(computer);
        drive1 = new MassStorageDrive();
        drive2 = new MassStorageDrive();
        drive1.setIcon(Utility.loadIconLabel("drive-harddisk.png"));
        drive2.setIcon(Utility.loadIconLabel("drive-harddisk.png"));
        drive1.onInsert(this::reconfigure);
        currentDrive = drive1;
    }

    @Override
    public void setSlot(int slot) {
        super.setSlot(slot);
        drive1.getIcon().setText("S" + getSlot() + "D1");
        drive2.getIcon().setText("S" + getSlot() + "D2");
    }

    @Override
    public String getDeviceName() {
        return "Mass Storage Device";
    }
    // boot0 stores cards*16 of boot device here
    static int SLT16 = 0x02B;
    // "rom" offset where device driver is called by MLI
//    static int DEVICE_DRIVER_OFFSET = 0x042;
    static int DEVICE_DRIVER_OFFSET = 0x0A;
    byte[] cardSignature = new byte[]{
        (byte) 0x0a9 /*NOP*/, 0x020, (byte) 0x0a9, 0x00,
        (byte) 0x0a9, 0x03 /*currentDisk cards*/, (byte) 0x0a9, 0x03c /*currentDisk cards*/,
        (byte) 0xd0, 0x07, 0x60, (byte) 0x0b0,
        0x01 /*firmware cards*/, 0x18, (byte) 0x0b0, 0x5a
    };
    Card theCard = this;
    public MassStorageDrive currentDrive;

    public IDisk getCurrentDisk() {
        if (currentDrive != null) {
            return currentDrive.getCurrentDisk();
        }
        return null;
    }
    ProdosDriver driver = new ProdosDriver(computer) {
        @Override
        public boolean changeUnit(int unit) {
            currentDrive = unit == 0 ? drive1 : drive2;
            return getCurrentDisk() != null;
        }

        @Override
        public int getSize() {
            return getCurrentDisk() != null ? getCurrentDisk().getSize() : 0;
        }

        @Override
        public boolean isWriteProtected() {
            return getCurrentDisk() != null ? getCurrentDisk().isWriteProtected() : true;
        }

        @Override
        public void mliFormat() throws IOException {
            getCurrentDisk().mliFormat();
        }

        @Override
        public void mliRead(int block, int bufferAddress) throws IOException {
            getCurrentDisk().mliRead(block, bufferAddress, computer.getMemory());
        }

        @Override
        public void mliWrite(int block, int bufferAddress) throws IOException {
            getCurrentDisk().mliWrite(block, bufferAddress, computer.getMemory());
        }

        @Override
        public Card getOwner() {
            return theCard;
        }
    };

    @Override
    public void reconfigure() {
        unregisterListeners();
        if (computer.getCpu() != null) {
            int pc = computer.getCpu().getProgramCounter();
            if (drive1.getCurrentDisk() != null && getSlot() == 7 && (pc >= 0x0c65e && pc <= 0x0c66F)) {
                    // If the computer is in a loop trying to boot from cards 6, fast-boot from here instead
                // This is a convenience to boot a hard-drive if the emulator has started waiting for a currentDisk
                currentDrive = drive1;
                EmulatorUILogic.simulateCtrlAppleReset();
            }
        }
        registerListeners();
    }

    @Override
    public void reset() {
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // There is no c8 rom for this card
    }

    @Override
    protected void handleFirmwareAccess(int offset, TYPE type, int value, RAMEvent e) {
        MOS65C02 cpu = (MOS65C02) computer.getCpu();
//                System.out.println(e.getType()+" "+Integer.toHexString(e.getAddress())+" from instruction at  "+Integer.toHexString(cpu.getProgramCounter()));
        if (type.isRead()) {
//            Emulator.getFrame().addIndicator(this, currentDrive.getIcon());
            if (drive1.getCurrentDisk() == null && drive2.getCurrentDisk() == null) {
                e.setNewValue(0);
                return;
            }
            if (type == TYPE.EXECUTE) {
                // Virtual functions, handle accordingly
                String error;
                if (offset == 0x00) {
                    // NOP unless otherwise specified
                    e.setNewValue(0x0ea);
                    try {
                        if (drive1.getCurrentDisk() != null) {
                            currentDrive = drive1;
                            getCurrentDisk().boot0(getSlot(), computer);
                        } else {
                            // Patch for crash on start when no image is mounted
                            e.setNewValue(0x060);
                        }
                        return;
                    } catch (IOException ex) {
                        Logger.getLogger(CardMassStorage.class.getName()).log(Level.SEVERE, null, ex);
                        error = ex.getMessage();
                        // Jump to the basic interpreter for now
                        cpu.setProgramCounter(0x0dfff);
                        int address = 0x0480;
                        for (char c : error.toCharArray()) {
                            computer.getMemory().write(address++, (byte) (c + 0x080), false, false);
                        }
                    }
                } else {
                    if (offset == DEVICE_DRIVER_OFFSET) {
                        driver.handleMLI();
                    } else if (offset == DEVICE_DRIVER_OFFSET + 3) {
                        smartport.handleSmartport();
                    } else {
                        System.out.println("Call to unknown handler " + Integer.toString(e.getAddress(), 16) + "-- returning");
                    }
                    /* act like RTS was called */
                    e.setNewValue(0x060);
                }
            }
            if (offset < 16) {
                e.setNewValue(cardSignature[offset]);
            } else {
                switch (offset) {
                    // Disk capacity = 65536 blocks
                    case 0x0FC:
                        e.setNewValue(0x0ff);
                        break;
                    case 0x0FD:
                        e.setNewValue(0x07f);
                        break;
                    // Status bits
                    case 0x0FE:
                        e.setNewValue(0x0D7);
                        break;
                    case 0x0FF:
                        e.setNewValue(DEVICE_DRIVER_OFFSET);
                }
            }
        }
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        // Ignore IO registers
    }

    @Override
    public void tick() {
        // Nothing is done per CPU cycle
    }
    SmartportDriver smartport = new SmartportDriver(computer) {
        @Override
        public boolean changeUnit(int unitNumber) {
            currentDrive = unitNumber == 1 ? drive1 : drive2;
            return getCurrentDisk() != null;
        }

        @Override
        public void read(int blockNum, int buffer) throws IOException {
            getCurrentDisk().mliRead(blockNum, buffer, computer.getMemory());
        }

        @Override
        public void write(int blockNum, int buffer) throws IOException {
            getCurrentDisk().mliWrite(blockNum, buffer, computer.getMemory());
        }

        @Override
        public ERROR_CODE returnStatus(int dataBuffer, int[] params) {
            return ERROR_CODE.NO_ERROR;
        }
    };

    @Override
    public MediaConsumer[] getConsumers() {
        return new MediaConsumer[]{drive1, drive2};
    }
}
