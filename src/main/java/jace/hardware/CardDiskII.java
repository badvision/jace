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

import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.config.Reconfigurable;
import jace.core.Card;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import jace.library.MediaConsumer;
import jace.library.MediaConsumerParent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Apple Disk ][ interface implementation. This card represents the interface
 * side of the Disk ][ controller interface as well as the on-board "boot0" ROM.
 * The behavior of the actual drive stepping, reading disk images, and so on is
 * performed by DiskIIDrive and FloppyDisk, respectively. This class only serves
 * as the I/O interface portion. Created on April 21, 2007
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("Disk ][ Controller")
public class CardDiskII extends Card implements Reconfigurable, MediaConsumerParent {

    DiskIIDrive currentDrive;
    DiskIIDrive drive1 = new DiskIIDrive(computer);
    DiskIIDrive drive2 = new DiskIIDrive(computer);
    @ConfigurableField(category = "Disk", defaultValue = "254", name = "Default volume", description = "Value to use for disk volume number")
    static public int DEFAULT_VOLUME_NUMBER = 0x0FE;
    @ConfigurableField(category = "Disk", defaultValue = "true", name = "Speed boost", description = "If enabled, emulator will run at max speed during disk access")
    static public boolean USE_MAX_SPEED = true;
    @ConfigurableField(category = "Disk", defaultValue = "", shortName = "d1", name = "Drive 1 disk image", description = "Path of disk 1")
    public String disk1;
    @ConfigurableField(category = "Disk", defaultValue = "", shortName = "d2", name = "Drive 2 disk image", description = "Path of disk 1")
    public String disk2;

    public CardDiskII(Computer computer) {
        super(computer);
        try {
            loadRom("jace/data/DiskII.rom");
        } catch (IOException ex) {
            Logger.getLogger(CardDiskII.class.getName()).log(Level.SEVERE, null, ex);
        }
        drive1.setIcon(Utility.loadIconLabel("disk_ii.png"));
        drive2.setIcon(Utility.loadIconLabel("disk_ii.png"));
        reset();
    }

    @Override
    public String getDeviceName() {
        return "Disk ][ Controller";
    }

    @Override
    public void reset() {
        currentDrive = drive1;
        drive1.reset();
        drive2.reset();
        EmulatorUILogic.removeIndicators(this);
//        Motherboard.cancelSpeedRequest(this);
    }

    @SuppressWarnings("fallthrough")
    @Override
    protected void handleIOAccess(int register, RAMEvent.TYPE type, int value, RAMEvent e) {
        // handle Disk ][ registers
        switch (register) {
            case 0x0:
            case 0x1:
            case 0x2:
            case 0x3:
            case 0x4:
            case 0x5:
            case 0x6:
            case 0x7:
                currentDrive.step(register);
                break;

            case 0x8:
                // drive off
                currentDrive.setOn(false);
                EmulatorUILogic.removeIndicator(this, currentDrive.getIcon());
                break;

            case 0x9:
                // drive on
                currentDrive.setOn(true);
                EmulatorUILogic.addIndicator(this, currentDrive.getIcon());
                break;

            case 0xA:
                // drive 1
                currentDrive = drive1;
                break;

            case 0xB:
                // drive 2
                currentDrive = drive2;
                break;

            case 0xC:
                // read/write latch
                currentDrive.write();
                e.setNewValue(currentDrive.readLatch());
                if (currentDrive.isOn()) {
                    EmulatorUILogic.addIndicator(this, currentDrive.getIcon());
                }
                break;
            case 0xF:
                // write mode
                currentDrive.setWriteMode();
            case 0xD:
                // set latch
                if (e.getType() == RAMEvent.TYPE.WRITE) {
                    currentDrive.setLatchValue((byte) e.getNewValue());
                }
                e.setNewValue(currentDrive.readLatch());
                break;

            case 0xE:
                // read mode
                currentDrive.setReadMode();
                if (currentDrive.disk != null && currentDrive.disk.writeProtected) {
                    e.setNewValue(0x080);
                } else {
//                    e.setNewValue((byte) (Math.random() * 256.0));
                    e.setNewValue(0);
                }
                break;
        }
        // even addresses return the latch value
//        if (e.getType() == RAMEvent.TYPE.READ) {
//            if ((register & 0x1) == 0) {
//                e.setNewValue(currentDrive.latch);
//            } else {
//                // return floating bus value (IIRC)
//            }
//        }
        tweakTiming();
    }

    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // Do nothing: The ROM does everything
    }

    public void loadRom(String path) throws IOException {
        InputStream romFile = CardDiskII.class.getClassLoader().getResourceAsStream(path);
        final int cxRomLength = 0x100;
        byte[] romData = new byte[cxRomLength];
        try {
            if (romFile.read(romData) != cxRomLength) {
                throw new IOException("Bad Disk ][ ROM size");
            }
            getCxRom().loadData(romData);
        } catch (IOException ex) {
            throw ex;
        }
    }

    @Override
    public void tick() {
        // Do nothing (if you want 1mhz timing control, you can do that here...)
//        drive1.tick();
//        drive2.tick();
    }

    @Override
    public void reconfigure() {
        super.reconfigure();
        try {
            if (disk1 != null && !disk1.isEmpty()) {
                drive1.insertDisk(new File(disk1));
                disk1 = null;
            }
            if (disk2 != null && !disk2.isEmpty()) {
                drive2.insertDisk(new File(disk2));
                disk2 = null;
            }
        } catch (IOException ex) {
            Logger.getLogger(CardDiskII.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void tweakTiming() {
        if ((drive1.isOn() && drive1.disk != null) || (drive2.isOn() && drive2.disk != null)) {
            if (USE_MAX_SPEED) {
                computer.getMotherboard().requestSpeed(this);
            }
        } else {
            computer.getMotherboard().cancelSpeedRequest(this);
        }
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // There is no special c8 rom for this card
    }

    @Override
    public void setSlot(int slot) {
        super.setSlot(slot);
        drive1.getIcon().setText("S" + slot + "D1");
        drive2.getIcon().setText("S" + slot + "D2");
    }

    @Override
    public MediaConsumer[] getConsumers() {
        return new MediaConsumer[]{drive1, drive2};
    }
}
