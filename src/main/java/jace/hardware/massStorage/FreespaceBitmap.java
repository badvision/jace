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
            // SubNode subNode = 
            new SubNode(i, this, getBaseBlock());
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
