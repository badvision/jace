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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Representation of a prodos file with a known file type and having a known
 * size (either seedling, sapling or tree)
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class FileNode extends DiskNode {

    public enum FileType {

        UNKNOWN(0x00, 0x0000),
        ADB(0x019, 0x0000),
        AWP(0x01a, 0x0000),
        ASP(0x01b, 0x0000),
        BAD(0x01, 0x0000),
        BIN(0x06, 0x0300),
        CLASS(0xED, 0x0000),
        BAS(0xfc, 0x0801),
        CMD(0x0f0, 0x0000),
        INT(0xfa, 0x0801),
        IVR(0xfb, 0x0000),
        PAS(0xef, 0x0000),
        REL(0x0Fe, 0x0000),
        SHK(0x0e0, 0x08002),
        SDK(0x0e0, 0x08002),
        SYS(0x0ff, 0x02000),
        SYSTEM(0x0ff, 0x02000),
        TXT(0x04, 0x0000),
        U01(0x0f1, 0x0000),
        U02(0x0f2, 0x0000),
        U03(0x0f3, 0x0000),
        U04(0x0f4, 0x0000),
        U05(0x0f5, 0x0000),
        U06(0x0f6, 0x0000),
        U07(0x0f7, 0x0000),
        U08(0x0f8, 0x0000),
        VAR(0x0FD, 0x0000);
        public int code = 0;
        public int defaultLoadAddress = 0;

        FileType(int code, int addr) {
            this.code = code;
            this.defaultLoadAddress = addr;
        }
    }
    public int fileType = 0x00;
    public int loadAddress = 0x00;
    public static int SEEDLING_MAX_SIZE = ProdosVirtualDisk.BLOCK_SIZE;
    public static int SAPLING_MAX_SIZE = ProdosVirtualDisk.BLOCK_SIZE * 128;

    @Override
    public EntryType getType() {
        long fileSize = getPhysicalFile().length();
        if (fileSize <= SEEDLING_MAX_SIZE) {
            setType(EntryType.SEEDLING);
            return EntryType.SEEDLING;
        } else if (fileSize <= SAPLING_MAX_SIZE) {
            setType(EntryType.SAPLING);
            return EntryType.SAPLING;
        } else {
            setType(EntryType.TREE);
            return EntryType.TREE;
        }
    }

    @Override
    public void setName(String name) {
        String[] parts = name.replaceAll("[^A-Za-z0-9]", ".").split("\\.");
        FileType t = FileType.UNKNOWN;
        int offset = 0;
        String prodosName = name;
        if (parts.length > 1) {
            String extension = parts[parts.length - 1].toUpperCase();
            String[] extParts = extension.split("#");
            if (extParts.length == 2) {
                offset = Integer.parseInt(extParts[1], 16);
                extension = extParts[0];
            }
            try {
                t = FileType.valueOf(extension);
            } catch (IllegalArgumentException ex) {
                System.out.println("Not sure what extension " + extension + " is!");
            }
            prodosName = "";
            for (int i = 0; i < parts.length - 1; i++) {
                prodosName += (i > 0 ? "." + parts[i] : parts[i]);
            }
            if (extParts[extParts.length - 1].equals("SYSTEM")) {
                prodosName += ".SYSTEM";
            }
        }
        if (offset == 0) {
            offset = t.defaultLoadAddress;
        }
        fileType = t.code;
        loadAddress = offset;

        // Pass usable name (stripped of file extension and other type info) as name
        super.setName(prodosName);
    }

    public FileNode(ProdosVirtualDisk ownerFilesystem, File file) throws IOException {
        setOwnerFilesystem(ownerFilesystem);
        setPhysicalFile(file);
        setName(file.getName());
    }

    @Override
    public void doDeallocate() {
    }

    @Override
    public void doAllocate() throws IOException {
        int dataBlocks = (int) ((getPhysicalFile().length()+ProdosVirtualDisk.BLOCK_SIZE-1) / ProdosVirtualDisk.BLOCK_SIZE);
        int treeBlocks =(((dataBlocks * 2) + (ProdosVirtualDisk.BLOCK_SIZE-2)) / ProdosVirtualDisk.BLOCK_SIZE);
        if (treeBlocks > 1) treeBlocks++;
//        if (dataBlocks > 1 && (dataBlocks*2) < ProdosVirtualDisk.BLOCK_SIZE) {
//            treeBlocks = 1;
//        } else {
//            treeBlocks = 1 + (dataBlocks * 2 / ProdosVirtualDisk.BLOCK_SIZE);
//        }
        System.out.println("Allocating "+(dataBlocks + treeBlocks)+" blocks for file "+getName()+"; data "+dataBlocks+"; tree "+treeBlocks);
        for (int i = 0; i < dataBlocks + treeBlocks; i++) {
            new SubNode(i, this);
        }
        setBaseBlock(additionalNodes.get(0).getBaseBlock());
    }

    @Override
    public void doRefresh() {
    }

    @Override
    public void readBlock(int block, byte[] buffer) throws IOException {
//        System.out.println("Read block "+block+" of file "+getName());
        int dataBlocks = (int) ((getPhysicalFile().length()+ProdosVirtualDisk.BLOCK_SIZE-1) / ProdosVirtualDisk.BLOCK_SIZE);
        int treeBlocks =(((dataBlocks * 2) + (ProdosVirtualDisk.BLOCK_SIZE-2)) / ProdosVirtualDisk.BLOCK_SIZE);
        if (treeBlocks > 1) treeBlocks++;
        switch (this.getType()) {
            case SEEDLING:
                readFile(buffer, 0);
                break;
            case SAPLING:
                if (block > 0) {
                    readFile(buffer, (block - 1));
                } else {
                    // Generate seedling index block
                    generateIndex(buffer, 0, dataBlocks);
                }
                break;
            case TREE:
                if (block == 0) {
                    System.out.println("Reading index for "+getName());
                    generateIndex(buffer, 1, treeBlocks);
                } else if (block <= treeBlocks) {
                    System.out.println("Reading tree block "+block+" for "+getName());
                    int start = treeBlocks + ((block - 1) * 256);
                    int end = treeBlocks + dataBlocks;
                    generateIndex(buffer, start, end);
                } else {
                    readFile(buffer, (block - treeBlocks - 1));
                }
                break;
        }
    }

    private void readFile(byte[] buffer, int start) throws IOException {
        try (FileInputStream f = new FileInputStream(physicalFile)) {
            f.skip(start * ProdosVirtualDisk.BLOCK_SIZE);
            f.read(buffer, 0, ProdosVirtualDisk.BLOCK_SIZE);
        }
    }

    private void generateIndex(byte[] buffer, int indexStart, int indexLimit) {
        System.out.println("Index block contents:");
        Arrays.fill(buffer, (byte) 0);
        for (int i = indexStart, count=0; count < 256 && i < indexLimit && i < additionalNodes.size(); i++, count++) {
            int base = additionalNodes.get(i).baseBlock;
            System.out.print(Integer.toHexString(base)+":");            
            buffer[count] = (byte) (base & 0x0ff);
            buffer[count + 256] = (byte) (base >> 8);
        }
        System.out.println();
        for (int i=0; i < 256; i++) {
            System.out.printf("%02X ",buffer[i]&0x0ff);
        }
        System.out.println();
        for (int i=256; i < 512; i++) {
            System.out.printf("%02X ",buffer[i]&0x0ff);
        }
        System.out.println();
    }
}