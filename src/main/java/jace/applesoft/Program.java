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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decode an applesoft program into a list of program lines
 * Right now this is an example/test program but it successfully tokenized the
 * souce of Lemonade Stand.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Program {
    List<Line> lines = new ArrayList<Line>();
    int startingAddress = 0x0801;

    public static void main(String... args) {
        byte[] source = null;
        try {
            File f = new File("/home/brobert/Documents/Personal/a2gameserver/lib/data/games/LEMONADE#fc0801");
            FileInputStream in = new FileInputStream(f);
            source = new byte[(int) f.length()];
            in.read(source);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
        }
        Program test = Program.fromBinary(source);
        System.out.println(test.toString());
    }

    static Program fromBinary(byte[] binary) {
        return fromBinary(binary, 0x0801);
    }

    static Program fromBinary(byte[] binary, int startAddress) {
        Program program = new Program();
        int currentAddress = startAddress;
        int pos = 0;
        while (pos < binary.length) {
            int nextAddress = (binary[pos] & 0x0ff) + ((binary[pos+1] & 0x0ff) << 8);
            if (nextAddress == 0) break;
            int length = nextAddress - currentAddress;
            Line l = Line.fromBinary(binary, pos);
            if (l == null) break;
            program.lines.add(l);
            if (l.getLength() != length) {
                System.out.println("Line "+l.getNumber()+" parsed as "+l.getLength()+" bytes long, but that leaves "+
                        (length - l.getLength())+" bytes hidden behind next line");
            }
            pos += length;
            currentAddress = nextAddress;
        }
        return program;
    }

    @Override
    public String toString() {
        String out = "";
        for (Line l : lines)
            out += l.toString() + "\n";
        return out;
    }
}