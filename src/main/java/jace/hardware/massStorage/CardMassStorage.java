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

package jace.hardware.massStorage;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.EmulatorUILogic;
import jace.apple2e.MOS65C02;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import jace.hardware.ProdosDriver;
import jace.hardware.SmartportDriver;
import jace.library.MediaCache;
import jace.library.MediaConsumer;
import jace.library.MediaConsumerParent;
import jace.library.MediaEntry;

/**
 * Hard disk and 800k floppy (smartport) controller card. HDV and 2MG images are
 * both supported.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("Mass Storage Device")
public class CardMassStorage extends Card implements MediaConsumerParent {

    @ConfigurableField(category = "Disk", shortName = "d1", name = "Drive 1 disk image", description = "Path of disk 1")
    public String disk1;
    @ConfigurableField(category = "Disk", shortName = "d2", name = "Drive 2 disk image", description = "Path of disk 2")
    public String disk2;
    MassStorageDrive drive1;
    MassStorageDrive drive2;

    public CardMassStorage() {
        super(false);
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
        drive1.getIcon().ifPresent(icon -> icon.setText("S" + getSlot() + "D1"));
        drive2.getIcon().ifPresent(icon -> icon.setText("S" + getSlot() + "D2"));
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
    ProdosDriver driver = new ProdosDriver() {
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
            return getCurrentDisk() == null || getCurrentDisk().isWriteProtected();
        }

        @Override
        public void mliFormat() throws IOException {
            getCurrentDisk().mliFormat();
        }

        @Override
        public void mliRead(int block, int bufferAddress) throws IOException {
            getCurrentDisk().mliRead(block, bufferAddress);
        }

        @Override
        public void mliWrite(int block, int bufferAddress) throws IOException {
            getCurrentDisk().mliWrite(block, bufferAddress);
        }

        @Override
        public Card getOwner() {
            return theCard;
        }
    };

    @Override
    public void reconfigure() {
        unregisterListeners();
        if (disk1 != null && !disk1.isEmpty()) {
            try {
                MediaEntry entry = MediaCache.getMediaFromFile(new File(disk1));
                disk1 = null;
                drive1.insertMedia(entry, entry.files.get(0));
            } catch (IOException ex) {
                Logger.getLogger(CardMassStorage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (disk2 != null && !disk2.isEmpty()) {
            try {
                MediaEntry entry = MediaCache.getMediaFromFile(new File(disk2));
                disk2 = null;
                drive2.insertMedia(entry, entry.files.get(0));
            } catch (IOException ex) {
                Logger.getLogger(CardMassStorage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Emulator.withComputer(computer -> { 
            if (computer.getCpu() != null) {
                int pc = computer.getCpu().getProgramCounter();
                if (drive1.getCurrentDisk() != null && getSlot() == 7 && (pc >= 0x0c65e && pc <= 0x0c66F)) {
                    // If the computer is in a loop trying to boot from cards 6, fast-boot from here instead
                    // This is a convenience to boot a hard-drive if the emulator has started waiting for a currentDisk
                    System.out.println("Fast-booting to mass storage drive");
                    currentDrive = drive1;
                    EmulatorUILogic.simulateCtrlAppleReset();
                }
            }
        });
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
//        System.out.println(e.getType()+" "+Integer.toHexString(e.getAddress())+" from instruction at  "+Integer.toHexString(cpu.getProgramCounter()));
        if (type.isRead()) {
            Emulator.withComputer(c->{
                currentDrive.getIcon().ifPresent(icon -> EmulatorUILogic.addIndicator(this, icon));
            });
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
                            // Reset stack pointer on boot helps prevent random crashes!
                            Emulator.withComputer(computer -> ((MOS65C02) computer.getCpu()).STACK = 0x0ff);
                            getCurrentDisk().boot0(getSlot());
                        } else {
                            // Patch for crash on start when no image is mounted
                            e.setNewValue(0x060);
                        }
                        return;
                    } catch (IOException ex) {
                        Logger.getLogger(CardMassStorage.class.getName()).log(Level.SEVERE, null, ex);
                        error = ex.getMessage();
                        // Jump to the basic interpreter for now
                        Emulator.withComputer(computer -> computer.getCpu().setProgramCounter(0x0dfff));
                        int address = 0x0480;
                        for (char c : error.toCharArray()) {
                            getMemory().write(address++, (byte) (c + 0x080), false, false);
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
                    case 0x0FC -> e.setNewValue(0x0ff); // Disk capacity = 65536 blocks
                    case 0x0FD -> e.setNewValue(0x07f);
                    case 0x0FE -> e.setNewValue(0x0D7); // Status bits
                    case 0x0FF -> e.setNewValue(DEVICE_DRIVER_OFFSET);
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
    SmartportDriver smartport = new SmartportDriver() {
        @Override
        public boolean changeUnit(int unitNumber) {
            currentDrive = unitNumber == 1 ? drive1 : drive2;
            return getCurrentDisk() != null;
        }

        @Override
        public void read(int blockNum, int buffer) throws IOException {
            getCurrentDisk().mliRead(blockNum, buffer);
        }

        @Override
        public void write(int blockNum, int buffer) throws IOException {
            getCurrentDisk().mliWrite(blockNum, buffer);
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
