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
package jace.applesoft;

import jace.Emulator;
import jace.core.RAM;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decode an applesoft program into a list of program lines Right now this is an
 * example/test program but it successfully tokenized the source of Lemonade
 * Stand.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class ApplesoftProgram {

    List<Line> lines = new ArrayList<>();
    public static final int startingAddressPointer = 0x067;
    public static final int BASIC_RUN = 0x0e000;
    int startingAddress = 0x0801;

    public static void main(String... args) {
        byte[] source = null;
        try {
            File f = new File("/home/brobert/Documents/Personal/a2gameserver/lib/data/games/LEMONADE#fc0801");
            FileInputStream in = new FileInputStream(f);
            source = new byte[(int) f.length()];
            in.read(source);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ApplesoftProgram.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ApplesoftProgram.class.getName()).log(Level.SEVERE, null, ex);
        }
        ApplesoftProgram test = ApplesoftProgram.fromBinary(Arrays.asList(toObjects(source)));
        System.out.println(test.toString());
    }

    public static Byte[] toObjects(byte[] bytesPrim) {
        Byte[] bytes = new Byte[bytesPrim.length];
        Arrays.setAll(bytes, n -> bytesPrim[n]);
        return bytes;
    }

    public static ApplesoftProgram fromMemory(RAM memory) {
        int startAddress = memory.readWordRaw(startingAddressPointer);
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
        return fromBinary(binary, 0x0801);
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
            if (line.trim().isEmpty()) continue;
            program.lines.add(Line.fromString(line));
        }
        //correct line linkage
        for (int i=0; i < program.lines.size(); i++) {
            if (i > 0) {
                program.lines.get(i).setPrevious(program.lines.get(i-1));
            }
            if (i < program.lines.size()-1) {
                program.lines.get(i).setNext(program.lines.get(i+1));
            }
        }
        return program;
    };

    public void run() {
        RAM memory = Emulator.computer.memory;
        Emulator.computer.pause();
        int pos = memory.readWordRaw(startingAddressPointer);
        for (Line line : lines) {
            int nextPos = pos + line.getLength() + 1;
            memory.write(pos++, (byte) (nextPos & 0x0ff), false, true);
            memory.write(pos++, (byte) (nextPos>>8 & 0x0ff), false, true);
            memory.write(pos++, (byte) (line.getNumber() & 0x0ff), false, true);
            memory.write(pos++, (byte) (line.getNumber() >> 8 & 0x0ff), false, true);
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
        
//        Emulator.computer.cpu.setProgramCounter(BASIC_RUN);
        Emulator.computer.resume();
    }
}
