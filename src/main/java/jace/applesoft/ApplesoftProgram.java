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

package jace.applesoft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jace.Emulator;
import jace.core.RAM;
import jace.core.RAMEvent;
import jace.core.RAMListener;

/**
 * Decode an applesoft program into a list of program lines Right now this is an
 * example/test program but it successfully tokenized the source of Lemonade
 * Stand.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class ApplesoftProgram {

    List<Line> lines = new ArrayList<>();
    public static final int START_OF_PROG_POINTER = 0x067;
    public static final int END_OF_PROG_POINTER = 0x0AF;            
    public static final int VARIABLE_TABLE = 0x069;
    public static final int ARRAY_TABLE = 0x06b;
    public static final int VARIABLE_TABLE_END = 0x06d;
    public static final int STRING_TABLE = 0x06f;
    public static final int HIMEM = 0x073;
    public static final int BASIC_RUN = 0x0e000;
    public static final int RUNNING_FLAG = 0x076;
    public static final int NOT_RUNNING = 0x0FF;
    public static final int GOTO_CMD = 0x0D944;  //actually starts at D93E
    public static final int START_ADDRESS = 0x0801;

    public static Byte[] toObjects(byte[] bytesPrim) {
        Byte[] bytes = new Byte[bytesPrim.length];
        Arrays.setAll(bytes, n -> bytesPrim[n]);
        return bytes;
    }

    public static ApplesoftProgram fromMemory(RAM memory) {
        int startAddress = memory.readWordRaw(START_OF_PROG_POINTER);
        int nextCheck = memory.readWordRaw(startAddress);
        int pos = startAddress;
        List<Byte> bytes = new ArrayList<>();
        while (nextCheck != 0) {
            while (pos < nextCheck + 2) {
                bytes.add(memory.readRaw(pos++));
            }
            nextCheck = memory.readWordRaw(nextCheck);
        }
        return fromBinary(bytes, startAddress);
    }

    public static ApplesoftProgram fromBinary(List<Byte> binary) {
        return fromBinary(binary, START_ADDRESS);
    }

    public static ApplesoftProgram fromBinary(List<Byte> binary, int startAddress) {
        ApplesoftProgram program = new ApplesoftProgram();
        int currentAddress = startAddress;
        int pos = 0;
        while (pos < binary.size()) {
            int nextAddress = (binary.get(pos) & 0x0ff) + ((binary.get(pos + 1) & 0x0ff) << 8);
            if (nextAddress == 0) {
                break;
            }
            int length = nextAddress - currentAddress;
            Line l = Line.fromBinary(binary, pos);
            if (l == null) {
                break;
            }
            program.lines.add(l);
            if (l.getLength() != length) {
                System.out.println("Line " + l.getNumber() + " parsed as " + l.getLength() + " bytes long, but that leaves "
                        + (length - l.getLength()) + " bytes hidden behind next line");
            }
            pos += length;
            currentAddress = nextAddress;
        }
        return program;
    }

    @Override
    public String toString() {
        String out = "";
        out = lines.stream().map((l) -> l.toString() + "\n").reduce(out, String::concat);
        return out;
    }

    public static ApplesoftProgram fromString(String programSource) {
        ApplesoftProgram program = new ApplesoftProgram();
        for (String line : programSource.split("\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            program.lines.add(Line.fromString(line));
        }
        //correct line linkage
        for (int i = 0; i < program.lines.size(); i++) {
            if (i > 0) {
                program.lines.get(i).setPrevious(program.lines.get(i - 1));
            }
            if (i < program.lines.size() - 1) {
                program.lines.get(i).setNext(program.lines.get(i + 1));
            }
        }
        return program;
    }

    public void run() {
        Emulator.whileSuspended(c-> {
            int programStart = c.getMemory().readWordRaw(START_OF_PROG_POINTER);
            int programEnd = programStart + getProgramSize();
            if (isProgramRunning()) {
                whenReady(()->{
                    relocateVariables(programEnd);
                    injectProgram();
                });
            } else {
                injectProgram();
                clearVariables(programEnd);
            }
        });
    }
    
    private void injectProgram() {
        Emulator.withMemory(memory->{
            int pos = memory.readWordRaw(START_OF_PROG_POINTER);
            for (Line line : lines) {
                int nextPos = pos + line.getLength();
                memory.writeWord(pos, nextPos, false, true);
                pos += 2;
                memory.writeWord(pos, line.getNumber(), false, true);
                pos += 2;
                boolean isFirst = true;
                for (Command command : line.getCommands()) {
                    if (!isFirst) {
                        memory.write(pos++, (byte) ':', false, true);
                    }
                    isFirst = false;
                    for (Command.ByteOrToken part : command.parts) {
                        memory.write(pos++, part.getByte(), false, true);
                    }
                }
                memory.write(pos++, (byte) 0, false, true);
            }
            memory.write(pos++, (byte) 0, false, true);
            memory.write(pos++, (byte) 0, false, true);
            memory.write(pos++, (byte) 0, false, true);
            memory.write(pos++, (byte) 0, false, true);        
        });
    }
    
    private boolean isProgramRunning() {
        return Emulator.withComputer(c->(c.getMemory().readRaw(RUNNING_FLAG) & 0x0FF) != NOT_RUNNING, false);
    }
    
    /**
     * If the program is running, wait until it advances to the next line
     */
    private void whenReady(Runnable r) {
        Emulator.withMemory(memory->{
            memory.addListener(new RAMListener("Applesoft: Trap GOTO command", RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(GOTO_CMD);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    r.run();
                    memory.removeListener(this);
                }
            });
        });
    }

    /**
     * Rough approximation of the CLEAR command at $D66A.
     * http://www.txbobsc.com/scsc/scdocumentor/D52C.html
     * @param programEnd Program ending address
     */
    private void clearVariables(int programEnd) {
        Emulator.withMemory(memory->{
            memory.writeWord(ARRAY_TABLE, programEnd, false, true);
            memory.writeWord(VARIABLE_TABLE, programEnd, false, true);
            memory.writeWord(VARIABLE_TABLE_END, programEnd, false, true);
            memory.writeWord(END_OF_PROG_POINTER, programEnd, false, true);        
        });
    }
    
    /**
     * Move variables around to accommodate bigger program
     * @param programEnd Program ending address
     */
    private void relocateVariables(int programEnd) {
        Emulator.withMemory(memory->{
            int currentEnd = memory.readWordRaw(END_OF_PROG_POINTER);
            memory.writeWord(END_OF_PROG_POINTER, programEnd, false, true);
            if (programEnd > currentEnd) {
                int diff = programEnd - currentEnd;
                int himem = memory.readWordRaw(HIMEM);
                for (int i=himem - diff; i >= programEnd; i--) {
                    memory.write(i+diff, memory.readRaw(i), false, true);
                }
                memory.writeWord(VARIABLE_TABLE, memory.readWordRaw(VARIABLE_TABLE) + diff, false, true);
                memory.writeWord(ARRAY_TABLE, memory.readWordRaw(ARRAY_TABLE) + diff, false, true);
                memory.writeWord(VARIABLE_TABLE_END, memory.readWordRaw(VARIABLE_TABLE_END) + diff, false, true);
                memory.writeWord(STRING_TABLE, memory.readWordRaw(STRING_TABLE) + diff, false, true);
            }
        });
    }

    private int getProgramSize() {
        int size = lines.stream().collect(Collectors.summingInt(Line::getLength)) + 4;
        return size;
    }

    public int getLength() {
        return lines.size();
    }
}
