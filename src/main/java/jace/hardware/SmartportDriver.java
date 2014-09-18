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
import jace.core.Computer;
import jace.core.RAM;
import jace.hardware.massStorage.CardMassStorage;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic abstraction of a smartport device.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class SmartportDriver {
    Computer computer;

    public SmartportDriver(Computer computer) {
        this.computer = computer;
    }
    
    public static enum ERROR_CODE {
        NO_ERROR(0), INVALID_COMMAND(0x01), BAD_PARAM_COUNT(0x04), INVALID_UNIT(0x011), INVALID_CODE(0x021), BAD_BLOCK_NUMBER(0x02d);
        int intValue;
        ERROR_CODE(int c) {
            intValue = c;
        }
    }
    
    public void handleSmartport() {
        int returnCode = callSmartport().intValue;
        MOS65C02 cpu = (MOS65C02) computer.getCpu();
        cpu.A = returnCode;
        // Clear carry flag if no error, otherwise set carry flag
        cpu.C = (returnCode == 0x00) ? 00 : 01;
    }

    private ERROR_CODE callSmartport() {
        MOS65C02 cpu = (MOS65C02) computer.getCpu();
        RAM ram = computer.getMemory();
        int callAddress = cpu.popWord() + 1;
        int command = ram.readRaw(callAddress);
        boolean extendedCall = command >= 0x040;
//            command &= 0x0f;
        // Modify stack so that RTS goes to the right place after the smartport device call
        //cpu.pushWord(callAddress + (extendedCall ? 5 : 3));
        // Kludge due to the CPU not getting the faked RTS opcode
        cpu.setProgramCounter(callAddress + (extendedCall ? 5 : 3));

        // Calculate parameter address block
        int parmAddr = 0;
        if (!extendedCall) {
            parmAddr = ram.readWordRaw(callAddress + 1);
        } else {
            // Extended calls -- not gonna happen on this platform anyway
            int parmAddrLo = ram.readWordRaw(callAddress + 1);
            int parmAddrHi = ram.readWordRaw(callAddress + 3);
            parmAddr = parmAddrHi << 16 | parmAddrLo;
        }
        // Now process command
        System.out.println("Received command " + command + " with address block " + Integer.toHexString(parmAddr));
        byte numParms = ram.readRaw(parmAddr);
        int[] params = new int[16];
        for (int i = 0; i < 16; i++) {
            int value = 0x0ff & ram.readRaw(parmAddr + i);
            params[i] = value;
            System.out.print(Integer.toHexString(value) + " ");
        }
        System.out.println();
        int unitNumber = params[1];
        if (!changeUnit(unitNumber)) {
            System.out.println("Invalid unit: "+unitNumber);
            return ERROR_CODE.INVALID_UNIT;
        }
        int dataBuffer = params[2] | (params[3] << 8);
        
        try {
            switch (command) {
                case 0: //Status
                    return returnStatus(dataBuffer, params);
                case 1: //Read Block
                    int blockNum = params[4] | (params[5] << 8) | (params[6] << 16);
                    read(blockNum, dataBuffer);
                    return ERROR_CODE.NO_ERROR;
                    //                  System.out.println("reading "+blockNum+" to $"+Integer.toHexString(dataBuffer));
                case 2: //Write Block
                    blockNum = params[4] | (params[5] << 8) | (params[6] << 16);
                    write(blockNum, dataBuffer);
                    return ERROR_CODE.NO_ERROR;
                case 3: //Format
                case 4: //Control
                case 5: //Init
                case 6: //Open
                case 7: //Close
                case 8: //Read
                case 9: //Write
                default:
                    System.out.println("Unimplemented command "+command);
                    return ERROR_CODE.INVALID_COMMAND;
            }
        } catch (IOException ex) {
            Logger.getLogger(CardMassStorage.class.getName()).log(Level.SEVERE, null, ex);
            return ERROR_CODE.INVALID_CODE;
        }
    }

    abstract public boolean changeUnit(int unitNumber);
    abstract public void read(int blockNum, int buffer) throws IOException;
    abstract public void write(int blockNum, int buffer) throws IOException;
    abstract public ERROR_CODE returnStatus(int dataBuffer, int[] params);
}
