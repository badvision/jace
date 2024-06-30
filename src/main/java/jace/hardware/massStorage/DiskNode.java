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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Prodos file/directory node abstraction. This provides a lot of the glue for
 * maintaining some sort of state across the virtual prodos volume and the
 * physical disk folder or file represented by this node.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class DiskNode {

    public enum EntryType {
        DELETED(0),
        SEEDLING(1),
        SAPLING(2),
        TREE(3),
        SUBDIRECTORY(0x0D),
        SUBDIRECTORY_HEADER(0x0E),
        VOLUME_HEADER(0x0F);
        public int code;

        EntryType(int c) {
            code = c;
        }
    }
    boolean allocated = false;
    long allocationTime = -1L;
    long lastCheckTime = -1L;
    private int baseBlock = -1;
    List<DiskNode> additionalNodes;
    ProdosVirtualDisk ownerFilesystem;
    File physicalFile;
    DiskNode parent;
    List<DiskNode> children;
    private EntryType type;
    private String name;

    public DiskNode(ProdosVirtualDisk fs) throws IOException {
        init(fs);
        fs.allocateEntry(this);
    }

    public DiskNode(ProdosVirtualDisk fs, int blockNumber) throws IOException {
        init(fs);
        fs.allocateEntryNear(this, blockNumber);
    }
    
    private void init(ProdosVirtualDisk fs) throws IOException {
        additionalNodes = new ArrayList<>();
        children = new ArrayList<>();
        setOwnerFilesystem(fs);
    }

    public boolean checkFile() throws IOException {
        if (physicalFile == null) {
            return false;
        }
        if (physicalFile.lastModified() != lastCheckTime) {
            lastCheckTime = physicalFile.lastModified();
            refresh();
            return false;
        }
        return true;
    }

    public void allocate() throws IOException {
        if (!allocated) {
            doAllocate();
            allocationTime = System.currentTimeMillis();
            allocated = true;
        }
    }

    public void deallocate() {
        if (allocated) {
            ownerFilesystem.deallocateEntry(this);
            doDeallocate();
            allocationTime = -1L;
            allocated = false;
            additionalNodes.clear();
            // NOTE: This is recursive!
            getChildren().stream().forEach((node) -> {
                node.deallocate();
            });
        }
    }

    public void refresh() throws IOException {
        deallocate();
        doRefresh();
        allocate();        
    }

    public DiskNode getNodeSequence(int num) {
        if (num == 0) {
            return this;
        } else if (num > 0 && num <= additionalNodes.size()) {
            return additionalNodes.get(num-1);
        } else {
            return null;
        }        
    }
    
    /**
     * @return the allocated
     */
    public boolean isAllocated() {
        return allocated;
    }

    /**
     * @return the allocationTime
     */
    public long getAllocationTime() {
        return allocationTime;
    }

    /**
     * @return the lastCheckTime
     */
    public long getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * @return the baseBlock
     */
    public int getBaseBlock() {
        return baseBlock;
    }

    /**
     * @param baseBlock the baseBlock to set
     */
    public void setBaseBlock(int baseBlock) {
        this.baseBlock = baseBlock;
    }

    /**
     * @return the ownerFilesystem
     */
    public ProdosVirtualDisk getOwnerFilesystem() {
        return ownerFilesystem;
    }

    /**
     * @param ownerFilesystem the ownerFilesystem to set
     * @throws IOException
     */
    private void setOwnerFilesystem(ProdosVirtualDisk ownerFilesystem) throws IOException {
        this.ownerFilesystem = ownerFilesystem;
    }

    /**
     * @return the physicalFile
     */
    public File getPhysicalFile() {
        return physicalFile;
    }

    /**
     * @param physicalFile the physicalFile to set
     */
    public void setPhysicalFile(File physicalFile) {
        this.physicalFile = physicalFile;
        setName(physicalFile.getName());
        lastCheckTime = physicalFile.lastModified();
    }

    /**
     * @return the parent
     */
    public DiskNode getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(DiskNode parent) {
        this.parent = parent;
    }

    /**
     * @return the children
     */
    public List<DiskNode> getChildren() {
        return children;
    }

    /**
     * @param children the children to set
     */
    public void setChildren(List<DiskNode> children) {
        this.children = children;
    }

    public void addChild(DiskNode child) {
        child.setParent(this);
        children.add(child);
    }

    public void removeChild(DiskNode child) {
        children.remove(child);
    }
    
    public boolean hasChildNamed(String name) {
        return findChildByFilename(name).isPresent();
    }
    
    private Optional<DiskNode> findChildByFilename(String name) {
        return getChildren().stream().filter((child) -> child.getPhysicalFile().getName().equals(name)).findFirst();
    }    

    /**
     * @return the type
     */
    public EntryType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(EntryType type) {
        this.type = type;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = (name.length() > 15 ? name.substring(0, 15) : name).toUpperCase();
    }

    public abstract void doDeallocate();

    public abstract void doAllocate() throws IOException;

    public abstract void doRefresh();

    public abstract void readBlock(int sequence, byte[] buffer) throws IOException;
    
    public abstract int getLength();

    public void readBlock(byte[] buffer) throws IOException {
        checkFile();
        readBlock(0, buffer);
    }
}
