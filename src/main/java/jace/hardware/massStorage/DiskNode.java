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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    int baseBlock = -1;
    List<DiskNode> additionalNodes;
    ProdosVirtualDisk ownerFilesystem;
    File physicalFile;
    DiskNode parent;
    List<DiskNode> children;
    private EntryType type;
    private String name;

    public DiskNode() {
        additionalNodes = new ArrayList<>();
        children = new ArrayList<>();
    }

    public boolean checkFile() throws IOException {
        allocate();
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
            ownerFilesystem.allocateEntry(this);
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

    public void refresh() {
        ownerFilesystem.deallocateEntry(this);
        doRefresh();
        allocationTime = System.currentTimeMillis();
        allocated = true;
        ownerFilesystem.allocateEntry(this);
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
    public void setOwnerFilesystem(ProdosVirtualDisk ownerFilesystem) throws IOException {
        this.ownerFilesystem = ownerFilesystem;
        if (baseBlock == -1) {
            setBaseBlock(ownerFilesystem.getNextFreeBlock());
        }
        ownerFilesystem.allocateEntry(this);
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
        children.add(child);
    }

    public void removeChild(DiskNode child) {
        children.remove(child);
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
        if (name.length() > 15) {
            name = name.substring(0, 15);
        }
        this.name = name.toUpperCase();
    }

    public abstract void doDeallocate();

    public abstract void doAllocate() throws IOException;

    public abstract void doRefresh();

    public abstract void readBlock(int sequence, byte[] buffer) throws IOException;

    public void readBlock(byte[] buffer) throws IOException {
        checkFile();
        readBlock(0, buffer);
    }
}
