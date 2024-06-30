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
 * Generic representation of a mass storage disk, either an image or a virtual volume.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public interface IDisk {
    int BLOCK_SIZE = 512;
    int MAX_BLOCK = 0x07fff;

    void mliFormat() throws IOException;
    void mliRead(int block, int bufferAddress) throws IOException;
    void mliWrite(int block, int bufferAddress) throws IOException;
    void boot0(int slot) throws IOException;

    // Return size in 512k blocks
    int getSize();
    
    void eject();
    boolean isWriteProtected();

}
