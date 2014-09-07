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
        }
        setType(EntryType.TREE);
        return EntryType.TREE;
    }

    @Override
    public void setName(String name) {
        String[] parts = name.split("\\.");
        FileType t = null;
        int offset = 0;
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
            name = "";
            for (int i = 0; i < parts.length - 1; i++) {
                name += (i > 0 ? "." + parts[i] : parts[i]);
            }
            if (extParts[extParts.length - 1].equals("SYSTEM")) {
                name += ".SYSTEM";
            }
        }
        if (t == null) {
            t = FileType.UNKNOWN;
        }
        if (offset == 0) {
            offset = t.defaultLoadAddress;
        }
        fileType = t.code;
        loadAddress = offset;

        // Pass usable name (stripped of file extension and other type info) as name
        super.setName(name);
    }

    public FileNode(ProdosVirtualDisk ownerFilesystem, File file) throws IOException {
        setOwnerFilesystem(ownerFilesystem);
        setPhysicalFile(file);
    }

    @Override
    public void doDeallocate() {
    }

    @Override
    public void doAllocate() throws IOException {
        int dataBlocks = (int) ((getPhysicalFile().length() / ProdosVirtualDisk.BLOCK_SIZE) + 1);
        int treeBlocks = 0;
        if (dataBlocks > 1 && dataBlocks < 257) {
            treeBlocks = 1;
        } else {
            treeBlocks = 1 + (dataBlocks / 256);
        }
        for (int i = 1; i < dataBlocks + treeBlocks; i++) {
            new SubNode(i, this);
        }
    }

    @Override
    public void doRefresh() {
    }

    @Override
    public void readBlock(int block, byte[] buffer) throws IOException {
//        System.out.println("Read block "+block+" of file "+getName());
        switch (this.getType()) {
            case SEEDLING:
                readFile(buffer, 0);
                break;
            case SAPLING:
                if (block > 0) {
                    readFile(buffer, (block - 1));
                } else {
                    // Generate seedling index block
                    generateIndex(buffer, 0, 256);
                }
                break;
            case TREE:
                int dataBlocks = (int) ((getPhysicalFile().length() / ProdosVirtualDisk.BLOCK_SIZE) + 1);
                int treeBlocks = (dataBlocks / 256);
                if (block == 0) {
                    generateIndex(buffer, 0, treeBlocks);
                } else if (block < treeBlocks) {
                    int start = treeBlocks + (block - 1 * 256);
                    int end = Math.min(start + 256, treeBlocks);
                    generateIndex(buffer, treeBlocks, end);
                } else {
                    readFile(buffer, (block - treeBlocks));
                }
                break;
        }
    }

    private void readFile(byte[] buffer, int start) throws IOException {
        FileInputStream f = new FileInputStream(physicalFile);
        f.skip(start * ProdosVirtualDisk.BLOCK_SIZE);
        f.read(buffer, 0, ProdosVirtualDisk.BLOCK_SIZE);
        f.close();
    }

    private void generateIndex(byte[] buffer, int indexStart, int indexLimit) {
        int pos = 0;
        for (int i = indexStart; pos < 256 && i < indexLimit && i < additionalNodes.size(); i++, pos++) {
            buffer[pos] = (byte) (additionalNodes.get(i).baseBlock & 0x0ff);
            buffer[pos + 256] = (byte) ((additionalNodes.get(i).baseBlock >> 8) & 0x0ff);
        }
    }
}
