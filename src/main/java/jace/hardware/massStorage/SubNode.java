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
