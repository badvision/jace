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
import jace.core.Computer;
import jace.core.RAM;
import static jace.hardware.ProdosDriver.*;
import jace.hardware.ProdosDriver.MLI_COMMAND_TYPE;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Representation of a Prodos Volume which maps to a physical folder on the
 * actual hard drive. This is used by CardMassStorage in the event the disk path
 * is a folder and not a disk image. FreespaceBitmap and the various Node
 * classes are used to represent the filesystem structure.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class ProdosVirtualDisk implements IDisk {

    public static int VOLUME_START = 2;
    public static int FREESPACE_BITMAP_START = 6;
    byte[] ioBuffer;
    File physicalRoot;
    Map<Integer, DiskNode> physicalMap;
    DirectoryNode rootDirectory;
    FreespaceBitmap freespaceBitmap;

    public ProdosVirtualDisk(File rootPath) throws IOException {
        ioBuffer = new byte[BLOCK_SIZE];
        initDiskStructure();
        setPhysicalPath(rootPath);
    }

    @Override
    public void mliRead(int block, int bufferAddress, RAM memory) throws IOException {
//        System.out.println("Read block " + block + " to " + Integer.toHexString(bufferAddress));
        DiskNode node = physicalMap.get(block);
        Arrays.fill(ioBuffer, (byte) (block & 0x0ff));
        if (node == null) {
            System.out.println("Reading unknown block " + Integer.toHexString(block));
            for (int i = 0; i < BLOCK_SIZE; i++) {
                memory.write(bufferAddress + i, (byte) 0, false, false);
            }
        } else {
//            if (node.getPhysicalFile() == null) {
//                System.out.println("reading block "+block+ " from directory structure to "+Integer.toHexString(bufferAddress));
//            } else {
//                System.out.println("reading block "+block+ " from "+node.getPhysicalFile().getName()+" to "+Integer.toHexString(bufferAddress));
//            }
            node.readBlock(ioBuffer);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                memory.write(bufferAddress + i, ioBuffer[i], false, false);
            }
        }
//        for (int i=0; i < 512; i++) {
//            if (i % 32 == 0 && i > 0) System.out.println();
//            System.out.print(((ioBuffer[i]&0x0ff)<16 ? "0" : "") + Integer.toHexString(ioBuffer[i] & 0x0ff) + " ");
//        }
//        System.out.println();
    }

    @Override
    public void mliWrite(int block, int bufferAddress, RAM memory) throws IOException {
        System.out.println("Write block " + block + " to " + Integer.toHexString(bufferAddress));
        throw new IOException("Write not implemented yet!");
//        DiskNode node = physicalMap.get(block);
//        RAM memory = computer.getMemory();
//        if (node == null) {
//            // CAPTURE WRITES TO UNUSED BLOCKS
//        } else {
//            node.readBlock(block, ioBuffer);
//            for (int i=0; i < BLOCK_SIZE; i++) {
//                memory.write(bufferAddress+i, ioBuffer[i], false);
//            }
//        }
    }

    @Override
    public void mliFormat() {
        throw new UnsupportedOperationException("Formatting for this type of media is not supported!");
    }

    public File locateFile(File rootPath, String string) {
        File mostLikelyMatch = null;
        for (File f : rootPath.listFiles()) {
            if (f.getName().equalsIgnoreCase(string)) {
                return f;
            }
            // This is not sufficient, a more deterministic approach should be taken
            if (string.toUpperCase().startsWith(f.getName().toUpperCase())) {
                if (mostLikelyMatch == null || f.getName().length() > mostLikelyMatch.getName().length()) {
                    mostLikelyMatch = f;
                }
            }
        }
        return mostLikelyMatch;
    }

    public int getNextFreeBlock() throws IOException {
        // Don't allocate Zero block for anything!
        //        for (int i = 0; i < MAX_BLOCK; i++) {
        for (int i = 2; i < MAX_BLOCK; i++) {
            if (!physicalMap.containsKey(i)) {
                return i;
            }
        }
        throw new IOException("Virtual Disk Full!");
    }

    // Mark space occupied by nodes as free (remove allocation mapping)
    public void deallocateEntry(DiskNode node) {
        // Only de-map nodes if the allocation table is actually pointing to the nodes!
        if (physicalMap.get(node.getBaseBlock()) != null && physicalMap.get(node.getBaseBlock()).equals(node)) {
            physicalMap.remove(node.getBaseBlock());
        }
        node.additionalNodes.stream().filter((sub)
                -> (physicalMap.get(sub.getBaseBlock()) != null && physicalMap.get(sub.getBaseBlock()).equals(sub))).
                forEach((sub) -> {
                    physicalMap.remove(sub.getBaseBlock());
                });
    }

    // Is the specified block in use?
    public boolean isAllocated(int i) {
        return (physicalMap.containsKey(i));
    }

    @Override
    public void boot0(int slot, Computer computer) throws IOException {
        File prodos = locateFile(physicalRoot, "PRODOS.SYS");
        if (prodos == null || !prodos.exists()) {
            throw new IOException("Unable to locate PRODOS.SYS");
        }
        computer.getCpu().suspend();
        byte slot16 = (byte) (slot << 4);
        ((MOS65C02) computer.getCpu()).X = slot16;
        RAM memory = computer.getMemory();
        memory.write(CardMassStorage.SLT16, slot16, false, false);
        memory.write(MLI_COMMAND, (byte) MLI_COMMAND_TYPE.READ.intValue, false, false);
        memory.write(MLI_UNITNUMBER, slot16, false, false);
        // Write location to block read routine to zero page
        memory.writeWord(0x048, 0x0c000 + CardMassStorage.DEVICE_DRIVER_OFFSET + (slot * 0x0100), false, false);
        EmulatorUILogic.brun(prodos, 0x02000);
        computer.getCpu().resume();
    }

    public File getPhysicalPath() {
        return physicalRoot;
    }

    private void initDiskStructure() throws IOException {
        physicalMap = new HashMap<>();
        freespaceBitmap = new FreespaceBitmap(this, FREESPACE_BITMAP_START);
    }
    
    private void setPhysicalPath(File f) throws IOException {
        if (physicalRoot != null && physicalRoot.equals(f)) {
            return;
        }
        physicalRoot = f;
        if (!physicalRoot.exists() || !physicalRoot.isDirectory()) {
            try {
                throw new IOException("Root path must be a directory that exists!");
            } catch (IOException ex) {
                Logger.getLogger(ProdosVirtualDisk.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        // Root directory ALWAYS starts on block 2!
        rootDirectory = new DirectoryNode(this, physicalRoot, VOLUME_START, true);
        rootDirectory.setName("VIRTUAL");
        rootDirectory.allocate();
    }

    @Override
    public void eject() {
        // Nothing to do here...
    }

    @Override
    public boolean isWriteProtected() {
        return true;
    }

    @Override
    public int getSize() {
        return 0x0ffff;
    }
}
