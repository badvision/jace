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

import static jace.hardware.ProdosDriver.MLI_COMMAND;
import static jace.hardware.ProdosDriver.MLI_UNITNUMBER;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.SoftSwitches;
import jace.core.RAM;
import jace.hardware.ProdosDriver.MLI_COMMAND_TYPE;

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
    // private int logicalBlocks;

    public LargeDisk(File f) {
        try {
            readDiskImage(f);
        } catch (IOException ex) {
            Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void mliFormat() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void mliRead(int block, int bufferAddress) throws IOException {
        AtomicReference<IOException> error = new AtomicReference<>();
        Emulator.withMemory(memory -> {
            try {
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
            } catch (IOException ex) {
                error.set(ex);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
    }

    @Override
    public void mliWrite(int block, int bufferAddress) throws IOException {
        AtomicReference<IOException> error = new AtomicReference<>();
        Emulator.withMemory(memory -> {
            try {
                if (block < physicalBlocks) {
                    diskImage.seek((block * BLOCK_SIZE) + dataOffset);
                    byte[] buf = new byte[BLOCK_SIZE];
                    for (int i = 0; i < BLOCK_SIZE; i++) {
                        buf[i]=memory.readRaw(bufferAddress + i);
                    }
                    diskImage.write(buf);
                }
            } catch (IOException ex) {
                error.set(ex);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
    }

    @Override
    public void boot0(int slot) {
        Emulator.withComputer(c->
            c.getCpu().whilePaused(()->{
                try {
    //                System.out.println("Loading boot0 to $800");
                    mliRead(0, 0x0800);
                } catch (IOException ex) {
                    Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
                }
                byte slot16 = (byte) (slot << 4);
    //            System.out.println("X = "+Integer.toHexString(slot16));
                ((MOS65C02) c.getCpu()).X = slot16;
                RAM memory = c.getMemory();
                SoftSwitches.AUXZP.getSwitch().setState(false);
                SoftSwitches.LCBANK1.getSwitch().setState(false);
                SoftSwitches.LCRAM.getSwitch().setState(false);
                SoftSwitches.LCWRITE.getSwitch().setState(true);
                SoftSwitches.RAMRD.getSwitch().setState(false);
                SoftSwitches.RAMWRT.getSwitch().setState(false);
                SoftSwitches.CXROM.getSwitch().setState(false);
                SoftSwitches.SLOTC3ROM.getSwitch().setState(false);
                SoftSwitches.INTC8ROM.getSwitch().setState(false);

                memory.write(CardMassStorage.SLT16, slot16, false, false);
                memory.write(MLI_COMMAND, (byte) MLI_COMMAND_TYPE.READ.intValue, false, false);
                memory.write(MLI_UNITNUMBER, slot16, false, false);
                // Write location to block read routine to zero page
                memory.writeWord(0x048, 0x0c000 + CardMassStorage.DEVICE_DRIVER_OFFSET + (slot * 0x0100), false, false);
    //            System.out.println("JMP $800 issued");
                c.getCpu().setProgramCounter(0x0800);
            })
        );
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
                // logicalBlocks = physicalBlocks;
                result = true;
            }
        } catch (IOException ex) {
            Logger.getLogger(LargeDisk.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
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
        // logicalBlocks = physicalBlocks;
    }

    private void readDiskImage(File f) throws IOException {
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
