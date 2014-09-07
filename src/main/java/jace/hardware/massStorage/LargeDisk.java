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

import jace.apple2e.MOS65C02;
import jace.core.Computer;
import jace.core.RAM;
import static jace.hardware.ProdosDriver.*;
import jace.hardware.ProdosDriver.MLI_COMMAND_TYPE;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Representation of a hard drive or 800k disk image used by CardMassStorage
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class LargeDisk implements IDisk {
    RandomAccessFile diskImage;
    File diskPath;
    // Offset in input file where data can be found
    private int dataOffset = 0;
    private int physicalBlocks = 0;
    private int logicalBlocks = 0;

    public LargeDisk(File f) {
        try {
            readDiskImage(f);
        } catch (IOException ex) {
            Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void mliFormat() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void mliRead(int block, int bufferAddress) throws IOException {
        RAM memory = Computer.getComputer().getMemory();
        if (block < physicalBlocks) {
            diskImage.seek((block * BLOCK_SIZE) + dataOffset);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                memory.write(bufferAddress + i, diskImage.readByte(), true, false);
            }
        } else {
            for (int i = 0; i < BLOCK_SIZE; i++) {
                memory.write(bufferAddress + i, (byte) 0, true, false);
            }
        }
    }

    public void mliWrite(int block, int bufferAddress) throws IOException {
        if (block < physicalBlocks) {
            RAM memory = Computer.getComputer().getMemory();
            diskImage.seek((block * BLOCK_SIZE) + dataOffset);
            byte[] buf = new byte[BLOCK_SIZE];
            for (int i = 0; i < BLOCK_SIZE; i++) {
                buf[i]=memory.readRaw(bufferAddress + i);
            }
            diskImage.write(buf);
        }
    }

    public void boot0(int slot) throws IOException {
        Computer.getComputer().getCpu().suspend();
        mliRead(0, 0x0800);
        byte slot16 = (byte) (slot << 4);
        ((MOS65C02) Computer.getComputer().getCpu()).X = slot16;
        RAM memory = Computer.getComputer().getMemory();
        memory.write(CardMassStorage.SLT16, slot16, false, false);
        memory.write(MLI_COMMAND, (byte) MLI_COMMAND_TYPE.READ.intValue, false, false);
        memory.write(MLI_UNITNUMBER, slot16, false, false);
        // Write location to block read routine to zero page
        memory.writeWord(0x048, 0x0c000 + CardMassStorage.DEVICE_DRIVER_OFFSET + (slot * 0x0100), false, false);
        ((MOS65C02) Computer.getComputer().getCpu()).setProgramCounter(0x0800);
        Computer.getComputer().getCpu().resume();
    }

    public File getPhysicalPath() {
        return diskPath;
    }

    public void setPhysicalPath(File f) throws IOException {
        diskPath = f;
    }

    private boolean read2mg(File f) {
        boolean result = false;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(getPhysicalPath());
            if (fis.read() == 0x32 && fis.read() == 0x49 && fis.read() == 0x4D && fis.read() == 0x47) {
                System.out.println("Disk is 2MG");
                // todo: read header
                dataOffset = 64;
                physicalBlocks = (int) (f.length() / BLOCK_SIZE);
                logicalBlocks = physicalBlocks;
                result = true;
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fis.close();
            } catch (IOException ex) {
                Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }

    private void readHdv(File f) {
        System.out.println("Disk is HDV");
        dataOffset = 0;
        physicalBlocks = (int) (f.length() / BLOCK_SIZE);
        logicalBlocks = physicalBlocks;
    }

    private void readDiskImage(File f) throws FileNotFoundException, IOException {
        eject();
        setPhysicalPath(f);
        if (!read2mg(f)) {
            readHdv(f);
        }
        diskImage = new RandomAccessFile(f, "rwd");
    }

    @Override
    public void eject() {
        if (diskImage != null) {
            try {
                diskImage.close();
                diskImage = null;
                setPhysicalPath(null);
            } catch (IOException ex) {
                Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public boolean isWriteProtected() {
        return diskPath == null || !diskPath.canWrite();
    }

    @Override
    public int getSize() {
        return physicalBlocks;
    }
}
