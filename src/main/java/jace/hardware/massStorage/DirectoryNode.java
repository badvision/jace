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

import static jace.hardware.massStorage.IDisk.BLOCK_SIZE;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prodos directory node
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class DirectoryNode extends DiskNode implements FileFilter {

    public static final byte STANDARD_PERMISSIONS = (byte) 0x0c3;
    public static final int PRODOS_VERSION = 0x023;
    public static final int FILE_ENTRY_SIZE = 0x027;
    public static final int ENTRIES_PER_BLOCK = (ProdosVirtualDisk.BLOCK_SIZE - 4) / FILE_ENTRY_SIZE;
    private boolean isRoot;

    private List<DiskNode> directoryEntries;
    
    public DirectoryNode(ProdosVirtualDisk ownerFilesystem, File physicalDir, int baseBlock, boolean root) throws IOException {
        super(ownerFilesystem, baseBlock);
        init(ownerFilesystem, physicalDir, root);
    }

    public DirectoryNode(ProdosVirtualDisk ownerFilesystem, File physicalDir, boolean root) throws IOException {
        super(ownerFilesystem);
        init(ownerFilesystem, physicalDir, root);
    }

    private void init(ProdosVirtualDisk ownerFilesystem, File physicalFile, boolean root) throws IOException {
        isRoot = root;
        directoryEntries = new ArrayList<>();
        setPhysicalFile(physicalFile);
        setType(EntryType.SUBDIRECTORY);
        setName(physicalFile.getName());
        allocate();
    }

    @Override
    public void doDeallocate() {
    }

    @Override
    public void doAllocate() throws IOException {
        for (int i = 1; i < getBlockCount(); i++) {
            if (isRoot) {
                new SubNode(i, this, getOwnerFilesystem().getNextFreeBlock(3));
            } else {
                new SubNode(i, this);
            }
        }

        for (File f : physicalFile.listFiles()) {
            addFile(f);
        }
        Collections.sort(children, (DiskNode o1, DiskNode o2) -> o1.getName().compareTo(o2.getName()));
    }

    @Override
    public void doRefresh() {
    }

    @Override
    /**
     * Checks contents of subdirectory for changes as well as directory itself
     * (super class)
     */
    public boolean checkFile() throws IOException {
        boolean success = true;
        if (!allocated) {
            allocate();
        } else {
            try {
                if (!super.checkFile()) {
                    return false;
                }
                HashSet<String> realFiles = new HashSet<>();
                File[] realFileList = physicalFile.listFiles(this);
                for (File f : realFileList) {
                    realFiles.add(f.getName());
                }
                for (Iterator<DiskNode> i = directoryEntries.iterator(); i.hasNext();) {
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
            } catch (IOException ex) {
                return false;
            }
        }
        return success;
    }

    @Override
    public void readBlock(int block, byte[] buffer) throws IOException {
        checkFile();
        int start = 0;
        int end = 0;
        int offset = 4;
        generatePointers(buffer, block);
//        System.out.println("Directory "+getName()+" sequence "+block+"; physical block "+getNodeSequence(block).getBaseBlock());
        if (block == 0) {
            generateHeader(buffer);
            offset += FILE_ENTRY_SIZE;
            end = ENTRIES_PER_BLOCK - 1;
        } else {
            start = (block * ENTRIES_PER_BLOCK) - 1;
            end = start + ENTRIES_PER_BLOCK;
        }
        for (int i = start; i < end && i < directoryEntries.size(); i++, offset += FILE_ENTRY_SIZE) {
            // TODO: Add any parts that are not file entries.
//            System.out.println("Entry "+i+": "+children.get(i).getName()+"; offset "+offset);
            generateFileEntry(buffer, offset, i);
        }
    }

    @Override
    public boolean accept(File file) {
        if (file.getName().endsWith("~")) {
            return false;
        }
        char c = file.getName().charAt(0);
        if (c == '.' || c == '~') {
            return false;
        }
        return !file.isHidden();
    }

    private void generatePointers(byte[] buffer, int sequence) {
        DiskNode prev = getNodeSequence(sequence - 1);
        DiskNode next = getNodeSequence(sequence + 1);
//        System.out.println("Sequence "+sequence+" prev="+(prev != null ? prev.getBaseBlock() : 0)+"; next="+(next != null ? next.getBaseBlock() : 0));
        // Previous block (or 0)
        generateWord(buffer, 0, prev != null ? prev.getBaseBlock() : 0);
        // Next block (or 0)
        generateWord(buffer, 0x02, next != null ? next.getBaseBlock() : 0);
    }

    /**
     * Generate the directory header found in the base block of a directory
     *
     * @param buffer where to write data
     */
    @SuppressWarnings("static-access")
    private void generateHeader(byte[] buffer) {
        // Directory header + name length
        // Volumme header = 0x0f0; Subdirectory header = 0x0e0
        buffer[4] = (byte) ((isRoot ? 0x0F0 : 0x0E0) | getName().length());
        generateName(buffer, 5, this);
        if (!isRoot) {
            buffer[0x014] = 0x075;
            buffer[0x015] = PRODOS_VERSION;
            buffer[0x017] = STANDARD_PERMISSIONS;
            buffer[0x018] = FILE_ENTRY_SIZE;
            buffer[0x019] = ENTRIES_PER_BLOCK;
        }
        generateTimestamp(buffer, 0x01c, getPhysicalFile().lastModified());
        // Prodos 1.0 = 0
        buffer[0x020] = PRODOS_VERSION;
        // Minimum version = 0 (no min)
        buffer[0x021] = 0x000;
        // Directory may be read/written to, may not be destroyed or renamed
        buffer[0x022] = STANDARD_PERMISSIONS;
        // Entry size
        buffer[0x023] = (byte) FILE_ENTRY_SIZE;
        // Entries per block
        buffer[0x024] = (byte) ENTRIES_PER_BLOCK;
        // Directory items count
        generateWord(buffer, 0x025, directoryEntries.size()+1);
        if (isRoot) {
            // Volume bitmap pointer
            generateWord(buffer, 0x027, ownerFilesystem.freespaceBitmap.getBaseBlock());
            // Total number of blocks
            generateWord(buffer, 0x029, ownerFilesystem.MAX_BLOCK);
        } else {
            // According to the Beneath Apple Prodos supplement
            int indexInParent = getParent().getChildren().indexOf(this) + 2;
            int parentBlock = getParent().getNodeSequence(indexInParent / ENTRIES_PER_BLOCK).getBaseBlock();
            // Parent pointer
            generateWord(buffer, 0x027, parentBlock);
            buffer[0x029] = (byte) (indexInParent % ENTRIES_PER_BLOCK);
            buffer[0x02a] = (byte) FILE_ENTRY_SIZE;
        }
    }

    /**
     * Generate the entry of a directory
     *
     * @param buffer where to write data
     * @param offset starting offset in buffer to write
     * @param fileNumber number of file (indexed in Children array) to write
     */
    private void generateFileEntry(byte[] buffer, int offset, int fileNumber) throws IOException {
        DiskNode child = directoryEntries.get(fileNumber);
        // Entry Type and length
        buffer[offset] = (byte) ((child.getType().code << 4) | child.getName().length());
        // Name
        generateName(buffer, offset + 1, child);
        // File type
        buffer[offset + 0x010] = (byte) ((child instanceof DirectoryNode) ? 0x0f : ((FileNode) child).fileType);
        // Key pointer
        generateWord(buffer, offset + 0x011, child.getBaseBlock());
        // Blocks used -- will report only one unless file is actually allocated
        generateWord(buffer, offset + 0x013, child.additionalNodes.size() + 1);
        // EOF (file size or directory structure size
        int length = child.getLength();
        length &= 0x0ffffff;
        generateWord(buffer, offset + 0x015, length & 0x0ffff);
        buffer[offset + 0x017] = (byte) ((length >> 16) & 0x0ff);
        // Creation date
        generateTimestamp(buffer, offset + 0x018, child.physicalFile.lastModified());
        // Version = 1.0
        buffer[offset + 0x01c] = PRODOS_VERSION;
        // Minimum version = 0
        buffer[offset + 0x01d] = 0;
        // Access = Read-only
        buffer[offset + 0x01e] = STANDARD_PERMISSIONS;
        // AUX type
        if (child instanceof FileNode) {
            generateWord(buffer, offset + 0x01f, ((FileNode) child).loadAddress);
        }
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
        buffer[offset + 0] = (byte) (((((c.get(Calendar.MONTH) + 1) & 7) << 5) | c.get(Calendar.DAY_OF_MONTH)) & 0x0ff);
        buffer[offset + 1] = (byte) (((c.get(Calendar.YEAR) - 2000) << 1) | ((c.get(Calendar.MONTH) + 1) >> 3));
        buffer[offset + 2] = (byte) c.get(Calendar.MINUTE);
        buffer[offset + 3] = (byte) c.get(Calendar.HOUR_OF_DAY);
    }

    private void generateWord(byte[] buffer, int i, int value) {
        // Little endian format
        buffer[i] = (byte) (value & 0x0ff);
        buffer[i + 1] = (byte) ((value >> 8) & 0x0ff);
    }

    private void generateName(byte[] buffer, int offset, DiskNode node) {
        for (int i = 0; i < node.getName().length() && i < 15; i++) {
            buffer[offset + i] = (byte) node.getName().charAt(i);
        }
    }
    
    // private Optional<DiskNode> findChildByFilename(String name) {
    //     return directoryEntries.stream().filter((child) -> child.getPhysicalFile().getName().equals(name)).findFirst();
    // }    
    
    private void addFile(File file) {
        if (!hasChildNamed(file.getName())) {
            try {
                if (file.isDirectory()) {
                    addFileEntry(new DirectoryNode(getOwnerFilesystem(), file, false));
                } else {
                    addFileEntry(new FileNode(getOwnerFilesystem(), file));
                }
            } catch (IOException ex) {
                Logger.getLogger(DirectoryNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void addFileEntry(DiskNode entry) {
        directoryEntries.add(entry);
        entry.setParent(this);
    }

    @Override
    public int getLength() {
        return getBlockCount() * BLOCK_SIZE;
    }

    private int getBlockCount() {
        return isRoot ? 4 : 1 + (physicalFile.listFiles().length / ENTRIES_PER_BLOCK);        
    }
}
