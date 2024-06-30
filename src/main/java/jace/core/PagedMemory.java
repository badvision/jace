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

package jace.core;

import java.util.Arrays;

import jace.state.StateManager;
import jace.state.Stateful;

/**
 * This represents bank-switchable ram which can reside at fixed portions of the
 * computer's memory. This makes it possible to switch out memory pages in a
 * very efficient manner so that the MMU abstraction doesn't bury the rest of
 * the emulator in messy conditionals.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
public class PagedMemory {

    public enum Type {

        CARD_FIRMWARE(0x0c800),
        LANGUAGE_CARD(0x0d000),
        FIRMWARE_MAIN(0x0d000),
        FIRMWARE_80COL(0x0c300),
        SLOW_ROM(0x0c100),
        RAM(0x0000);
        int baseAddress;

        Type(int newBase) {
            baseAddress = newBase;
        }

        public int getBaseAddress() {
            return baseAddress;
        }
    }
    // This is a fixed array, used for internal-only!!
    @Stateful
    public byte[][] internalMemory = new byte[0][];
    @Stateful
    public Type type;

    /**
     * Creates a new instance of PagedMemory
     * @param size The size of the memory region, in multiples of 256
     * @param memType The type of the memory region
     */
    public PagedMemory(int size, Type memType) {
        type = memType;
        internalMemory = new byte[size >> 8][256];
        for (int i = 0; i < size; i += 256) {
            byte[] b = new byte[256];
            Arrays.fill(b, (byte) 0x00);
            internalMemory[i >> 8] = b;
        }
    }

    public PagedMemory(byte[] romData, Type memType) {
        type = memType;
        loadData(romData);
    }

    public final void loadData(byte[] romData) {
        for (int i = 0; i < romData.length; i += 256) {
            byte[] b = new byte[256];
            System.arraycopy(romData, i, b, 0, 256);
            internalMemory[i >> 8] = b;
        }
    }

    public void loadData(byte[] romData, int offset, int length) {
        for (int i = 0; i < length; i += 256) {
            byte[] b = new byte[256];
            for (int j = 0; j < 256; j++) {
                b[j] = romData[offset + i + j];
            }
            internalMemory[i >> 8] = b;
        }
    }

    public byte[][] getMemory() {
        return internalMemory;
    }

    public byte[] get(int pageNumber) {
        return internalMemory[pageNumber];
    }

    public void set(int pageNumber, byte[] bank) {
        internalMemory[pageNumber] = bank;
    }

    public byte[] getMemoryPage(int memoryBase) {
        int offset = memoryBase - type.baseAddress;
        int page = (offset >> 8) & 0x0ff;
        return internalMemory[page];
    }

    public void setBanks(int sourceStart, int sourceLength, int targetStart, PagedMemory source) {
        for (int i = 0; i < sourceLength; i++) {
            set(targetStart + i, source.get(sourceStart + i));
        }
    }

    public byte readByte(int address) {
        return getMemoryPage(address)[address & 0x0ff];
    }

    public void writeByte(int address, byte value) {
        byte[] page = getMemoryPage(address);
        StateManager.markDirtyValue(page);
        getMemoryPage(address)[address & 0x0ff] = value;
    }

    public void fillBanks(PagedMemory source) {
        byte[][] sourceMemory = source.getMemory();
        int sourceBase = source.type.getBaseAddress() >> 8;
        int thisBase = type.getBaseAddress() >> 8;
        int start = Math.max(sourceBase, thisBase);
        int sourceEnd = sourceBase + source.getMemory().length;
        int thisEnd = thisBase + getMemory().length;
        int end = Math.min(sourceEnd, thisEnd);
        for (int i = start; i < end; i++) {
            set(i - thisBase, sourceMemory[i - sourceBase]);
        }
    }
}