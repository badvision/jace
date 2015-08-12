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
import java.io.FileFilter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prodos directory node
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class DirectoryNode extends DiskNode implements FileFilter {
//    public static int FILE_ENTRY_SIZE = 38;
    public static int FILE_ENTRY_SIZE = 0x027;
    public DirectoryNode(ProdosVirtualDisk ownerFilesystem, File physicalDir, int baseBlock) throws IOException {
        setBaseBlock(baseBlock);
        init(ownerFilesystem, physicalDir);
    }

    public DirectoryNode(ProdosVirtualDisk ownerFilesystem, File physicalDir) throws IOException {
        init(ownerFilesystem, physicalDir);
    }


    private void init(ProdosVirtualDisk ownerFilesystem, File physicalFile) throws IOException {
        setPhysicalFile(physicalFile);
        setType(EntryType.SUBDIRECTORY);
        setName(physicalFile.getName());
        setOwnerFilesystem(ownerFilesystem);
    }

    @Override
    public void doDeallocate() {
    }

    @Override
    public void doAllocate() {
        File[] files = physicalFile.listFiles(this);
        int numEntries = files.length;
        int numBlocks = 1;
        // First block has 12 entries, subsequent blocks have 13 entries
        if (numEntries > 12) {
            numBlocks += (numEntries - 12) / 13;
        }

        for (File f : files) {
            addFile(f);
        }
        Collections.sort(children, (DiskNode o1, DiskNode o2) -> o1.getName().compareTo(o2.getName()));
    }

    @Override
    public void doRefresh() {
    }

    @Override
    /**
     * Checks contents of subdirectory for changes as well as directory itself (super class)
     */
    public boolean checkFile() throws IOException {
        boolean success = true;
        if (!super.checkFile()) {
            return false;
        }
        HashSet<String> realFiles = new HashSet<>();
        File[] realFileList = physicalFile.listFiles(this);
        for (File f : realFileList) {
            realFiles.add(f.getName());
        }
        for (Iterator<DiskNode> i = getChildren().iterator(); i.hasNext(); ) {
            DiskNode node = i.next();
            if (realFiles.contains(node.getPhysicalFile().getName())) {
                realFiles.remove(node.getPhysicalFile().getName());
            } else {
                i.remove();
                success = false;
            }
            if (node.isAllocated()) {
                if (!(node instanceof DirectoryNode) && !node.checkFile()) {
                    success = false;
                }
            }
        }
        if (!realFiles.isEmpty()) {
            success = false;
            // New files showed up -- deal with them!
            realFiles.stream().forEach((fileName) -> {
                addFile(new File(physicalFile, fileName));
            });
        }
        return success;
    }

    @Override
    public void readBlock(int block, byte[] buffer) throws IOException {
        checkFile();
        if (block == 0) {
            generateHeader(buffer);
            for (int i=0; i < 12 && i < children.size(); i++)
            generateFileEntry(buffer, 4 + (i+1) * FILE_ENTRY_SIZE, i);
        } else {
            int start = (block * 13) - 1;
            int end = start + 13;
            int offset = 4;

            for (int i=start; i < end && i < children.size(); i++) {
                // TODO: Add any parts that are not file entries.
                generateFileEntry(buffer, offset, i);
                offset += FILE_ENTRY_SIZE;
            }
        }
    }

    @Override
    public boolean accept(File file) {
        if (file.getName().endsWith("~")) return false;
        char c = file.getName().charAt(0);
        if (c == '.' || c == '~') {
            return false;
        }
        return !file.isHidden();
    }

    /**
     * Generate the directory header found in the base block of a directory
     * @param buffer where to write data
     */
    @SuppressWarnings("static-access")
    private void generateHeader(byte[] buffer) {
//        System.out.println("Generating directory header");
        // Previous block = 0
        generateWord(buffer, 0,0);
        // Next block
        int nextBlock = 0;
        if (!additionalNodes.isEmpty())
            nextBlock = additionalNodes.get(0).baseBlock;
        generateWord(buffer, 0x02, nextBlock);
        // Directory header + name length
        // Volumme header = 0x0f0; Subdirectory header = 0x0e0
        buffer[4]= (byte) ((baseBlock == 0x02 ? 0x0f0 : 0x0E0) + getName().length());
        generateName(buffer, 5, this);
        for (int i=0x014 ; i <= 0x01b; i++)
            buffer[i] = 0;
        generateTimestamp(buffer, 0x01c, getPhysicalFile().lastModified());
        // Prodos 1.9
        buffer[0x020] = 0x019;
        // Minimum version = 0 (no min)
        buffer[0x021] = 0x000;
        // Directory may be read/written to, may not be destroyed or renamed
        buffer[0x022] = 0x03;
        // Entry size
        buffer[0x023] = (byte) FILE_ENTRY_SIZE;
        // Entries per block
        buffer[0x024] = (byte) 0x0d;
        // Directory items count
        generateWord(buffer, 0x025, children.size());
        // Volume bitmap pointer
        generateWord(buffer, 0x027, ownerFilesystem.freespaceBitmap.baseBlock);
        // Total number of blocks
        generateWord(buffer, 0x029, ownerFilesystem.MAX_BLOCK);
    }

    /**
     * Generate the entry of a directory
     * @param buffer where to write data
     * @param offset starting offset in buffer to write
     * @param fileNumber number of file (indexed in Children array) to write
     */
    private void generateFileEntry(byte[] buffer, int offset, int fileNumber) throws IOException {
//        System.out.println("Generating entry for "+children.get(fileNumber).getName());
        DiskNode child = children.get(fileNumber);
        // Entry Type and length
        buffer[offset] = (byte) ((child.getType().code << 4) + child.getName().length());
        // Name
        generateName(buffer, offset+1, child);
        // File type
        buffer[offset + 0x010] = (byte) ((child instanceof DirectoryNode) ? 0x0f : ((FileNode) child).fileType);
        // Key pointer
        generateWord(buffer, offset + 0x011, child.getBaseBlock());
        // Blocks used -- will report only one unless file is actually allocated
//        child.allocate();
        generateWord(buffer, offset + 0x013, 1 + child.additionalNodes.size());
        // EOF
        // TODO: Verify this is the right thing to do -- is EOF total length or a modulo?
        int length = ((int) child.physicalFile.length()) & 0x0ffffff;
        generateWord(buffer, offset + 0x015, length & 0x0ffff);
        buffer[offset + 0x017] = (byte) ((length >> 16) & 0x0ff);
        // Creation date
        generateTimestamp(buffer, offset + 0x018, child.physicalFile.lastModified());
        // Version = 1.9
        buffer[offset + 0x01c] = 0x19;
        // Minimum version = 0
        buffer[offset + 0x01d] = 0;
        // Access = all granted
        buffer[offset + 0x01e] = (byte) 0x0ff;
        // AUX type
        if (child instanceof FileNode)
            generateWord(buffer, offset + 0x01f, ((FileNode) child).loadAddress);
        // Modification date
        generateTimestamp(buffer, offset + 0x021, child.physicalFile.lastModified());
        // Key pointer for directory
        generateWord(buffer, offset + 0x025, getBaseBlock());
    }

    private void generateTimestamp(byte[] buffer, int offset, long date) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(date);

        // yyyyyyym mmmddddd - Byte 0,1
        // ---hhhhh --mmmmmm - Byte 2,3
//        buffer[offset+1] = (byte) (((c.get(Calendar.YEAR) - 1990) << 1) + ((c.get(Calendar.MONTH)>> 3) & 1));
       buffer[offset+0] = 0;
       buffer[offset+1] = 0;
       buffer[offset+2] = 0;
       buffer[offset+3] = 0;
//        buffer[offset+2] = (byte) ((c.get(Calendar.MONTH)>> 3) & 1);
//        buffer[offset+3] = (byte) (((c.get(Calendar.MONTH)&7) + c.get(Calendar.DAY_OF_MONTH)) & 0x0ff);
//        buffer[offset+0] = (byte) c.get(Calendar.HOUR_OF_DAY);
//        buffer[offset+1] = (byte) c.get(Calendar.MINUTE);
    }

    private void generateWord(byte[] buffer, int i, int value) {
        // Little endian format
        buffer[i] = (byte) (value & 0x0ff);
        buffer[i+1] = (byte) ((value >> 8) & 0x0ff);
    }

    private void generateName(byte[] buffer, int offset, DiskNode node) {
        for (int i=0; i < node.getName().length(); i++) {
            buffer[offset+i] = (byte) node.getName().charAt(i);
        }
    }

    private void addFile(File file) {
        try {
            if (file.isDirectory()) {
                addChild(new DirectoryNode(getOwnerFilesystem(), file));
            } else {
                addChild(new FileNode(getOwnerFilesystem(), file));
            }
        } catch (IOException ex) {
            Logger.getLogger(DirectoryNode.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
