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
 * Maintain freespace and node allocation
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class FreespaceBitmap extends DiskNode {

    int size = (ProdosVirtualDisk.MAX_BLOCK + 1) / 8 / ProdosVirtualDisk.BLOCK_SIZE;

    public FreespaceBitmap(ProdosVirtualDisk fs, int start) throws IOException {
        super(fs, start);
        allocate();
    }

    @Override
    public void doDeallocate() {
//
    }

    @Override
    public void doAllocate() throws IOException {
        for (int i = 1; i < size; i++) {
            SubNode subNode = new SubNode(i, this, getBaseBlock());
        }
    }

    @Override
    public void doRefresh() {
//
    }

    @Override
    public void readBlock(int sequence, byte[] buffer) throws IOException {
        int startBlock = sequence * ProdosVirtualDisk.BLOCK_SIZE * 8;
        int endBlock = (sequence + 1) * ProdosVirtualDisk.BLOCK_SIZE * 8;
        for (int i = startBlock; i < endBlock; i++) {
            if (!getOwnerFilesystem().isBlockAllocated(i)) {
                int pos = (i - startBlock) / 8;
                int bit = 1 << (i % 8);
                buffer[pos] |= bit;
            }
        }
    }

    @Override
    public int getLength() {
        return (1 + getChildren().size()) * IDisk.BLOCK_SIZE;
    }
}
