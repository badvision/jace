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

import java.io.IOException;

/**
 * Subnode is a generic node (block) used up by a file. Thes nodes do nothing
 * more than just occupy space in the freespace bitmap. The file node itself
 * keeps track of its subnodes and figures out what each subnode should
 * "contain". The subnodes themselves don't track anything though.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class SubNode extends DiskNode {

    int sequenceNumber;

    public SubNode(int seq, DiskNode parent) throws IOException {
        super(parent.getOwnerFilesystem());
        init(seq, parent);
    }

    public SubNode(int seq, DiskNode parent, int baseBlock) throws IOException {
        super(parent.getOwnerFilesystem(), baseBlock);
        init(seq, parent);
    }

    private void init(int seq, DiskNode parent) throws IOException {
        sequenceNumber = seq;
        setParent(parent);
        parent.additionalNodes.add(this);
    }

    @Override
    public String getName() {
        return parent.getName() + "; block "+sequenceNumber;
    }
    
    @Override
    public void doDeallocate() {
    }

    @Override
    public void doAllocate() {
    }

    @Override
    public void doRefresh() {
    }

    @Override
    public void readBlock(int sequence, byte[] buffer) throws IOException {
        parent.readBlock(sequenceNumber, buffer);
    }

    @Override
    public int getLength() {
        return IDisk.BLOCK_SIZE;
    }
}
