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
package jace.hardware;

import jace.apple2e.MOS65C02;
import jace.core.Card;
import jace.core.Computer;
import jace.core.RAM;
import java.io.IOException;

/**
 * Helper functions for prodos drivers
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class ProdosDriver {
    Computer computer;

    public ProdosDriver(Computer computer) {
        this.computer = computer;
    }
    
    public static int MLI_COMMAND = 0x042;
    public static int MLI_UNITNUMBER = 0x043;
    public static int MLI_BUFFER_ADDRESS = 0x044;
    public static int MLI_BLOCK_NUMBER = 0x046;
    
    public static enum MLI_RETURN {
        NO_ERROR(0), IO_ERROR(0x027), NO_DEVICE(0x028), WRITE_PROTECTED(0x02B);
        public int intValue;

        MLI_RETURN(int val) {
            intValue = val;
        }
    }

    public static enum MLI_COMMAND_TYPE {
        STATUS(0x0), READ(0x01), WRITE(0x02), FORMAT(0x03);
        public int intValue;

        MLI_COMMAND_TYPE(int val) {
            intValue = val;
        }

        public static MLI_COMMAND_TYPE fromInt(int value) {
            for (MLI_COMMAND_TYPE c : values()) {
                if (c.intValue == value) {
                    return c;
                }
            }
            return null;
        }
    }

    abstract public boolean changeUnit(int unitNumber);
    abstract public int getSize();
    abstract public boolean isWriteProtected();
    abstract public void mliFormat() throws IOException;
    abstract public void mliRead(int block, int bufferAddress) throws IOException;
    abstract public void mliWrite(int block, int bufferAddress) throws IOException;
    abstract public Card getOwner();
    
    public void handleMLI() {
        int returnCode = prodosMLI().intValue;
        MOS65C02 cpu = (MOS65C02) computer.getCpu();
        cpu.A = returnCode;
        // Clear carry flag if no error, otherwise set carry flag
        cpu.C = (returnCode == 0x00) ? 00 : 01;
    }
    
    private MLI_RETURN prodosMLI() {
        try {
            RAM memory = computer.getMemory();
            int cmd = memory.readRaw(MLI_COMMAND);
            MLI_COMMAND_TYPE command = MLI_COMMAND_TYPE.fromInt(cmd);
            int unit = (memory.readWordRaw(MLI_UNITNUMBER) & 0x080) > 0 ? 1 : 0;
            if (changeUnit(unit) == false) {
                return MLI_RETURN.NO_DEVICE;
            }
            int block = memory.readWordRaw(MLI_BLOCK_NUMBER);
            int bufferAddress = memory.readWordRaw(MLI_BUFFER_ADDRESS);
//            System.out.println(getOwner().getName()+" MLI Call "+command+", unit "+unit+" Block "+block+" --> "+Integer.toHexString(bufferAddress));
            if (command == null) {
                System.out.println(getOwner().getName()+" Mass storage given bogus command (" + Integer.toHexString(cmd) + "), returning I/O error");
                return MLI_RETURN.IO_ERROR;
            }
            switch (command) {
                case STATUS:
                    int blocks = getSize();
                    MOS65C02 cpu = (MOS65C02) computer.getCpu();
                    cpu.X = blocks & 0x0ff;
                    cpu.Y = (blocks >> 8) & 0x0ff;
                    if (isWriteProtected()) {
                        return MLI_RETURN.WRITE_PROTECTED;
                    }
                    break;
                case FORMAT:
                    mliFormat();
                case READ:
                    mliRead(block, bufferAddress);
                    break;
                case WRITE:
                    mliWrite(block, bufferAddress);
                    break;
                default:
                    System.out.println(getOwner().getName()+" MLI given bogus command (" + Integer.toHexString(cmd) + " = " + command.name() + "), returning I/O error");
                    return MLI_RETURN.IO_ERROR;
            }
            return MLI_RETURN.NO_ERROR;
        } catch (UnsupportedOperationException ex) {
            return MLI_RETURN.WRITE_PROTECTED;
        } catch (IOException ex) {
            System.out.println(getOwner().getName()+" Encountered IO Error, returning error: " + ex.getMessage());
            return MLI_RETURN.IO_ERROR;
        }
    }
}