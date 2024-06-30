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

import static java.lang.Character.isDigit;

import java.util.ArrayList;
import java.util.List;

import jace.applesoft.Command.TOKEN;

/**
 * Representation of a line of applesoft basic, having a line number and a list
 * of program commands.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Line {

    private static final char STATEMENT_BREAK = ':'; // delimits multiple commands, the colon character

    private int number = -1;
    private Line next;
    private Line previous;
    private List<Command> commands = new ArrayList<>();
    private int length = 0;

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
            if (!isFirst) {
                out += STATEMENT_BREAK;
            }
            out += c.toString();
            isFirst = false;
        }
        return out;
    }

    static Line fromBinary(List<Byte> binary, int pos) {
        Line l = new Line();
        int lineNumber = (binary.get(pos + 2) & 0x0ff) + ((binary.get(pos + 3) & 0x0ff) << 8);
        l.setNumber(lineNumber);
        pos += 4;
        Command c = new Command();
        int size = 5;
        while (binary.get(pos) != 0) {
            size++;
            if (binary.get(pos) == STATEMENT_BREAK) {
                l.commands.add(c);
                c = new Command();
            } else {
                Command.ByteOrToken bt = new Command.ByteOrToken(binary.get(pos));
                c.parts.add(bt);
            }
            pos++;
        }
        l.commands.add(c);
        l.length = size;
        return l;
    }

    static Line fromString(String lineString) {
        Line l = new Line();
        boolean inString = false;
        boolean hasLineNumber = false;
        boolean isComment = false;
        Command currentCommand = new Command();
        l.commands.add(currentCommand);
        l.length = 5; // 4 pointer bytes + 1 null byte at the end
        String upperLineString = lineString.toUpperCase();
        for (int i = 0; i < lineString.length(); i++) {
            if (!hasLineNumber) {
                int lineNumber = 0;
                for (; i < lineString.length() && isDigit(lineString.charAt(i)); i++) {
                    lineNumber = lineNumber * 10 + lineString.charAt(i) - '0';
                }
                i--;
                l.setNumber(lineNumber);
                hasLineNumber = true;
            } else if (inString || isComment) {
                if (!isComment && lineString.charAt(i) == '"') {
                    inString = false;
                }
                currentCommand.parts.add(new Command.ByteOrToken((byte) lineString.charAt(i)));
                l.length++;
            } else if (lineString.charAt(i) == '"') {
                inString = true;
                currentCommand.parts.add(new Command.ByteOrToken((byte) lineString.charAt(i)));
                l.length++;
            } else if (lineString.charAt(i) == STATEMENT_BREAK) {
                currentCommand = new Command();
                l.commands.add(currentCommand);
                l.length++;                
            } else if (lineString.charAt(i) == '?') {
                Command.ByteOrToken part = new Command.ByteOrToken(TOKEN.PRINT);
                currentCommand.parts.add(part);
                l.length++;                
            } else if (lineString.charAt(i) == ' ') {
                continue;
            } else {
                TOKEN match = Command.TOKEN.findMatch(upperLineString, i);
                if (match != null) {
                    Command.ByteOrToken part = new Command.ByteOrToken(match);
                    currentCommand.parts.add(part);
                    if (match == TOKEN.REM || match == TOKEN.DATA) {
                        isComment = true;
                    }
                    for (int j=0; j < match.toString().length(); j++, i++) {
                        while (i < lineString.length() && lineString.charAt(i) == ' ') {
                            i++;
                        }
                    }
                    if (!isComment) {
                        i--;
                    }
                    l.length++;
                } else {
                    if (lineString.charAt(i) != ' ') {
                        currentCommand.parts.add(new Command.ByteOrToken((byte) upperLineString.charAt(i)));
                        l.length++;                        
                    }
                }
            }
        }
        return l;
    }
}