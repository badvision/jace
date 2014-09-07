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

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a line of applesoft basic, having a line number and a list of program commands.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Line {

    private static char STATEMENT_BREAK = ':'; // delimits multiple commands, the colon character
    private int number;
    private Line next;
    private Line previous;
    private List<Command> commands = new ArrayList<Command>();
    private int length;

    /**
     * @return the number
     */
    public int getNumber() {
        return number;
    }

    /**
     * @param number the number to set
     */
    public void setNumber(int number) {
        this.number = number;
    }

    /**
     * @return the next
     */
    public Line getNext() {
        return next;
    }

    /**
     * @param next the next to set
     */
    public void setNext(Line next) {
        this.next = next;
    }

    /**
     * @return the previous
     */
    public Line getPrevious() {
        return previous;
    }

    /**
     * @param previous the previous to set
     */
    public void setPrevious(Line previous) {
        this.previous = previous;
    }

    /**
     * @return the commands
     */
    public List<Command> getCommands() {
        return commands;
    }

    /**
     * @param commands the commands to set
     */
    public void setCommands(List<Command> commands) {
        this.commands = commands;
    }

    /**
     * @return the length
     */
    public int getLength() {
        return length;
    }

    /**
     * @param length the length to set
     */
    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public String toString() {
        String out = String.valueOf(getNumber());
        boolean isFirst = true;
        for (Command c : commands) {
            if (!isFirst) out += STATEMENT_BREAK;
            out += c.toString();
            isFirst = false;
        }
        return out;
    }

    static Line fromBinary(byte[] binary, int pos) {
        Line l = new Line();
        int lineNumber = (binary[pos+2] & 0x0ff) + ((binary[pos+3] & 0x0ff) << 8);
        l.setNumber(lineNumber);
        pos += 4;
        Command c = new Command();
        int size = 5;
        while (binary[pos] != 0) {
            size++;
            if (binary[pos] == STATEMENT_BREAK) {
                l.commands.add(c);
                c = new Command();
            } else {
                Command.ByteOrToken bt = new Command.ByteOrToken(binary[pos]);
                c.parts.add(bt);
            }
            pos++;
        }
        l.commands.add(c);
        l.length = size;
        return l;
    }
}