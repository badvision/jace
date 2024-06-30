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

package jace.hardware;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.core.RAM;
import jace.hardware.massStorage.CardMassStorage;

/**
 * Generic abstraction of a smartport device.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class SmartportDriver {
    
    public enum ERROR_CODE {
        NO_ERROR(0), INVALID_COMMAND(0x01), BAD_PARAM_COUNT(0x04), INVALID_UNIT(0x011), INVALID_CODE(0x021), BAD_BLOCK_NUMBER(0x02d);
        int intValue;
        ERROR_CODE(int c) {
            intValue = c;
        }
    }
    
    public void handleSmartport() {
        Emulator.withComputer(computer -> {
            int returnCode = callSmartport().intValue;
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            cpu.A = returnCode;
            // Clear carry flag if no error, otherwise set carry flag
            cpu.C = (returnCode == 0x00) ? 0 : 1;
        });
    }

    private ERROR_CODE callSmartport() {
        return Emulator.withComputer(computer -> {
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            RAM ram = computer.getMemory();
            int callAddress = cpu.popWord() + 1;
            int command = ram.readRaw(callAddress);
            boolean extendedCall = command >= 0x040;
    //            command &= 0x0f;
            // Modify stack so that RTS goes to the right place after the smartport device call
            // Kludge due to the CPU not getting the faked RTS opcode
            cpu.setProgramCounter(callAddress + (extendedCall ? 4 : 2));

            // Calculate parameter address block
            int parmAddr;
            if (!extendedCall) {
                parmAddr = ram.readWordRaw(callAddress + 1);
            } else {
                // Extended calls -- not gonna happen on this platform anyway
                int parmAddrLo = ram.readWordRaw(callAddress + 1);
                int parmAddrHi = ram.readWordRaw(callAddress + 3);
                parmAddr = parmAddrHi << 16 | parmAddrLo;
            }
            // Now process command
            // System.out.println(String.format("Received %s command %d with address block %s", (extendedCall ? "extended" : "normal"), command, Integer.toHexString(parmAddr)));
            // byte numParms = ram.readRaw(parmAddr);
            int[] params = new int[16];
            for (int i = 0; i < 16; i++) {
                int value = 0x0ff & ram.readRaw(parmAddr + i);
                params[i] = value;
                // System.out.print(Integer.toHexString(value) + " ");
            }
            // System.out.println();
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
                        // System.out.println("reading "+blockNum+" to $"+Integer.toHexString(dataBuffer));
                        return ERROR_CODE.NO_ERROR;
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
        }, ERROR_CODE.NO_ERROR);
    }

    abstract public boolean changeUnit(int unitNumber);
    abstract public void read(int blockNum, int buffer) throws IOException;
    abstract public void write(int blockNum, int buffer) throws IOException;
    abstract public ERROR_CODE returnStatus(int dataBuffer, int[] params);
}
