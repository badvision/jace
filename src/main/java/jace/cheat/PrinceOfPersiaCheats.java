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
package jace.cheat;

import jace.Emulator;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.config.ConfigurableField;
import jace.core.Computer;
import jace.core.PagedMemory;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * Prince of Persia game cheats.  This would not have been possible without the
 * source. I am eternally grateful to Jordan Mechner both for creating this
 * game, and for being so kind to release the source code to it so that we can
 * learn how it works. Where possible, I've indicated where I found the various
 * game variables in the original source so that it might help anyone else
 * trying to learn how this game works.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class PrinceOfPersiaCheats extends Cheats implements MouseListener {

    @ConfigurableField(category = "Hack", name = "Feather fall", defaultValue = "false", description = "Fall like a feather!")
    public static boolean velocityHack;
    // Game memory locations
    // Source: https://github.com/jmechner/Prince-of-Persia-Apple-II/blob/master/01%20POP%20Source/Source/GAMEEQ.S
    @ConfigurableField(category = "Hack", name = "Invincibility", defaultValue = "false", description = "Warning: will crash game if you are impaled")
    public static boolean invincibilityHack;
    @ConfigurableField(category = "Hack", name = "Infinite Time", defaultValue = "false", description = "Freeze the clock")
    public static boolean timeHack;
    @ConfigurableField(category = "Hack", name = "Sleepy Time", defaultValue = "false", description = "Enemies won't react")
    public static boolean sleepHack;
    @ConfigurableField(category = "Hack", name = "Can haz sword?", defaultValue = "false", description = "Start with sword in level 1")
    public static boolean swordHack;
    @ConfigurableField(category = "Hack", name = "Mouse", defaultValue = "false", description = "Left click kills/opens, Right click teleports")
    public static boolean mouseHack;
    boolean mouseRegistered = false;
    public static int PREV = 0x02b;
    public static int SPREV = 0x02e;
    public static int CharPosn = 0x040;
    public static int CharX = 0x041;
    public static int CharY = 0x042;
    public static int CharFace = 0x043;
    public static int CharBlockX = 0x44;
    public static int CharBlockY = 0x45;
    public static int CharAction = 0x46;
    public static int CharXVel = 0x47;
    public static int CharYVel = 0x48;
    public static int CharSeq = 0x49; // Word
    public static int CharScrn = 0x4b;
    public static int CharRepeat = 0x4c;
    public static int CharID = 0x4d;
    public static int CharSword = 0x4e;
    public static int CharLife = 0x4f;
    public static int KidX = 0x051;
    public static int KidY = 0x052;
    public static int KidFace = 0x53;
    public static int KidBlockX = 0x54;
    public static int KidBlockY = 0x55;
    public static int KidAction = 0x56;
    public static int KidScrn = 0x5b;
    public static int ShadBlockX = 0x64;
    public static int ShadBlockY = 0x65;
    public static int ShadLife = 0x06f;
    // Source: https://github.com/jmechner/Prince-of-Persia-Apple-II/blob/master/02%20POP%20Disk%20Routines/CP.525/RYELLOW1.S
    public static int deprotectCheckYellow = 0x07c;
    public static int NumTrans = 0x096;
    public static int OppStrength = 0x0cc;
    public static int KidStrength = 0x0ce;
    public static int EnemyAlert = 0x0d1;
    public static int ChgOppStr = 0x0d2;
    // Source: https://github.com/jmechner/Prince-of-Persia-Apple-II/blob/master/02%20POP%20Disk%20Routines/CP.525/PURPLE.MAIN.S
    public static int deprotectCheckPurple = 0x0da;
    public static int Heoric = 0x0d3;
    public static int InEditor = 0x0202;
    public static int MinLeft = 0x0300;
    public static int hasSword = 0x030a;
    public static int mobtables = 0x0b600;
    public static int trloc = mobtables;
    public static int trscrn = trloc + 0x020;
    public static int trdirec = trscrn + 0x020;
    // Blueprint (map level data)0
    public static int BlueSpec = 0x0b9d0;
    public static int LinkLoc = 0x0bca0;
    public static int LinkMap = 0x0bda0;
    public static int Map = 0x0bea0;
    public static int MapInfo = 0x0bf00;
    public static final int RedBufs = 0x05e00;
    public static final int RedBuf = RedBufs + 90;
    // Source: https://github.com/jmechner/Prince-of-Persia-Apple-II/blob/master/01%20POP%20Source/Source/EQ.S
    public static final int WipeBuf = RedBuf + 90;
    public static final int MoveBuf = WipeBuf + 30;
    // Object types
    // Source: https://github.com/jmechner/Prince-of-Persia-Apple-II/blob/master/01%20POP%20Source/Source/MOVEDATA.S
    public static int space = 0;
    public static int floor = 1;
    public static int spikes = 2;
    public static int posts = 3;
    public static int gate = 4;
    public static int dpressplate = 5;
    public static int pressplate = 6;
    public static int panelwif = 7;
    public static int pillarbottom = 8;
    public static int pillartop = 9;
    public static int flask = 10;
    public static int loose = 11;
    public static int panelwof = 12;
    public static int mirror = 13;
    public static int rubble = 14;
    public static int upressplate = 15;
    public static int exit = 16;
    public static int exit2 = 17;
    public static int slicer = 18;
    public static int torch = 19;
    public static int block = 20;
    public static int bones = 21;
    public static int sword = 22;
    public static int window = 23;
    public static int window2 = 24;
    public static int archbot = 25;
    public static int archtop1 = 26;
    public static int archtop2 = 27;
    public static int archtop3 = 28;
    public static int archtop4 = 29;
    // This is the correct value for an open exit door.
    public static int ExitOpen = 172;

    public PrinceOfPersiaCheats(Computer computer) {
        super(computer);
    }

    @Override
    protected String getDeviceName() {
        return ("Prince of Persia");
    }
    
    @Override
    public void tick() {
        // Do nothing
    }

    @Override
    public void attach() {
        if (velocityHack) {
            addCheat(new RAMListener(RAMEvent.TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(CharYVel);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    registerMouse();
                    if (!SoftSwitches.AUXZP.getState()) {
                        return;
                    }
                    int newVel = e.getNewValue();
                    if (newVel > 5) {
                        newVel = 1;
                    }
                    e.setNewValue(newVel & 0x0ff);
                }
            });
        }

        if (invincibilityHack) {
            addCheat(new RAMListener(RAMEvent.TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(KidStrength);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    registerMouse();
                    if (!SoftSwitches.AUXZP.getState()) {
                        return;
                    }
                    e.setNewValue(3);
                }
            });
        }
        if (sleepHack) {
            addCheat(new RAMListener(RAMEvent.TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(EnemyAlert);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    registerMouse();
                    if (!SoftSwitches.AUXZP.getState()) {
                        return;
                    }
                    e.setNewValue(0);
                }
            });
        }
        if (swordHack) {
            addCheat(new RAMListener(RAMEvent.TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(hasSword);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    registerMouse();
                    if (!SoftSwitches.AUXZP.getState()) {
                        return;
                    }
                    e.setNewValue(1);
                }
            });
        }
        if (timeHack) {
            addCheat(new RAMListener(RAMEvent.TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(MinLeft);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    registerMouse();
                    if (!SoftSwitches.AUXZP.getState()) {
                        return;
                    }
                    e.setNewValue(0x069);
                }
            });
        }
        if (mouseHack) {
            if (Emulator.getScreen() != null) {
                Emulator.getScreen().addMouseListener(this);
            } else {
                mouseRegistered = true;
            }
        } else {
            if (Emulator.getScreen() != null) {
                Emulator.getScreen().removeMouseListener(this);
            }
        }
    }

    @Override
    public void detach() {
        super.detach();
        if (Emulator.getScreen() != null) {
            Emulator.getScreen().removeMouseListener(this);
        }
        mouseRegistered = false;
    }
    public static int BlueType = 0x0b700;

    public void registerMouse() {
        if (mouseRegistered) {
            Component drawingArea = Emulator.getScreen();
            if (drawingArea == null) {
                return;
            }
            Emulator.getScreen().addMouseListener(this);
            mouseRegistered = false;
        }
    }

    @Override
    public void mouseClicked(MouseEvent me) {
        Component drawingArea = Emulator.getScreen();
        if (drawingArea == null) {
            return;
        }
        Point currentMouseLocation = MouseInfo.getPointerInfo().getLocation();
        Point topLeft = drawingArea.getLocationOnScreen();
        Double x = (currentMouseLocation.x - topLeft.x) / drawingArea.getSize().getWidth();
        // Offset y by three pixels to account for tiles above
        Double y = (currentMouseLocation.y - topLeft.y) / drawingArea.getSize().getHeight() - 0.015625;
        // Now we have the x and y coordinates ranging from 0 to 1.0, scale to POP values
        int row = y < 0 ? -1 : (int) (y * 3);
        int col = (int) (x * 10);

        // Do a check if we are at the bottom of the tile, the user might have been clicking on the tile to the right.
        // This accounts for the isometric view and allows a little more flexibility, not to mention warping behind gates
        // that are on the left edge of the screen!
        int yCoor = ((int) (y * 192) % 63);
        if (yCoor >= 47) {
            double yOffset = 1.0 - (((double) yCoor - 47.0) / 16.0);
            int xCoor = ((int) (x * 280) % 28);
            double xOffset = ((double) xCoor) / 28.0;
            if (xOffset <= yOffset) {
                col--;
            }
        }

        // Note: POP uses a 255-pixel horizontal axis, Pixels 0-57 are offscreen to the left
        // and 198-255 offscreen to the right.

//        System.out.println("Clicked on " + col + "," + row + " -- screen " + (x * 280) + "," + (y * 192));
        RAM128k mem = (RAM128k) computer.getMemory();
        PagedMemory auxMem = mem.getAuxMemory();

        if (me.getButton() == MouseEvent.BUTTON1) {
            // Left click hacks
            // See if there is an opponent we can kill off.
            int opponentX = auxMem.readByte(ShadBlockX);
            int opponentY = auxMem.readByte(ShadBlockY);
            int opponentLife = auxMem.readByte(ShadLife);
            // If there is a guy near where the user clicked and he's alive, then kill 'em.
            if (opponentLife != 0 && opponentY == row && Math.abs(col - opponentX) <= 1) {
                //            System.out.println("Enemy at " + opponentX + "," + opponentY + "; life=" + opponentLife);
                // Occasionally, if the code is at the right spot this will cause the special effect of a hit to appear
                auxMem.writeByte(ChgOppStr, (byte) -opponentLife);
                // And this will kill the dude pretty much right away.
                auxMem.writeByte(ShadLife, (byte) 0);
            } else if (row >= 0 && col >= 0) {
                // Try to perform actions on the block clicked as well as to the left and right of it.
                // This opens gates and exits.
                performAction(row, col, 1);
                performAction(row, col - 1, 1);
                performAction(row, col + 1, 1);
            }
        } else {
            // Right/middle click == warp
            byte warpX = (byte) (x * 140 + 58);
            // This aliases the Y coordinate so the prince is on the floor at the correct spot.
            byte warpY = (byte) ((row * 63) + 54);
//            System.out.println("Warping to " + warpX + "," + warpY);
            auxMem.writeByte(KidX, warpX);
            auxMem.writeByte(KidY, warpY);
            auxMem.writeByte(KidBlockX, (byte) col);
            auxMem.writeByte(KidBlockY, (byte) row);
            // Set action to bump into a wall so it can reset the kid's feet on the ground correctly.
            // Not sure if this has any real effect but things seem to be working (so I'll just leave this here...)
            auxMem.writeByte(KidAction, (byte) 5);
        }
    }

    /**
     *
     * @param row
     * @param col
     * @param direction
     */
    public void performAction(int row, int col, int direction) {
        RAM128k mem = (RAM128k) computer.getMemory();
        PagedMemory auxMem = mem.getAuxMemory();
        byte currentScrn = auxMem.readByte(KidScrn);
        if (col < 0) {
            col += 10;
            int scrnLeft = auxMem.readByte(Map + ((currentScrn - 1) * 4));
            if (scrnLeft == 0) {
                return;
            }
            currentScrn = (byte) scrnLeft;
            byte prev = auxMem.readByte(PREV + row);
            byte sprev = auxMem.readByte(SPREV + row);
            // If the block to the left is gate, let's lie about it being open... for science
            // This causes odd-looking screen behavior but it gets the job done.
            if (prev == 4) {
                // Update the temp variable that represents that object
                auxMem.writeByte(SPREV + row, (byte) 255);
                // And also update the blueprint
                auxMem.writeByte(BlueSpec + ((scrnLeft - 1) * 30) + row * 10 + 9, (byte) 255);
            }
//            System.out.println("Looking at room to left, row "+row+": "+Integer.toHexString(prev)+","+Integer.toHexString(sprev));
        } else if (col >= 10) {
            // This code will probably never be called but here just in case.
            col -= 10;
            int scrnRight = auxMem.readByte(Map + ((currentScrn - 1) * 4) + 1);
            if (scrnRight == 0) {
                return;
            }
            currentScrn = (byte) scrnRight;
        }
        int numTransition = auxMem.readByte(NumTrans);
        byte clickedLoc = (byte) (row * 10 + col);
        // Figure out what kind of block is there
        int blockType = auxMem.readByte(BlueType + (currentScrn - 1) * 30 + row * 10 + col) & 0x01f;
        if (blockType == exit2 || blockType == exit) {
            // Open the exit by changing the map data and adding the tiles to the move buffer
            auxMem.writeByte(BlueSpec + (currentScrn - 1) * 30 + row * 10 + col, (byte) ExitOpen);
            direction = 1;
            // Tell the graphics engine that this piece has moved.
            auxMem.writeByte(MoveBuf + row * 10 + col, (byte) 2);
        }
        if (blockType == gate || blockType == exit2 || blockType == exit) {
            // If the object in question can be opened (exit or gate) add it to the transitional animation buffer
            //System.out.print("Triggering screen " + currentScrn + " at pos " + clickedLoc);
            boolean addTransition = false;
            if (numTransition == 0) {
                addTransition = true;
            } else {
                addTransition = true;
                for (int i = 1; i <= numTransition; i++) {
                    byte scrn = auxMem.readByte(trscrn + i);
                    byte loc = auxMem.readByte(trloc + i);
                    if (scrn == currentScrn && loc == clickedLoc) {
                        // Entry already exists, just change its direction
                        auxMem.writeByte(trdirec + i, (byte) direction);
                        addTransition = false;
                        break;
                    }
                }
                if (addTransition && numTransition >= 0x20) {
                    addTransition = false;
                }
            }
            // If the object was not in the animation buffer, add it.
            if (addTransition) {
                numTransition++;
                auxMem.writeByte(trdirec + numTransition, (byte) direction);
                auxMem.writeByte(trscrn + numTransition, currentScrn);
                auxMem.writeByte(trloc + numTransition, clickedLoc);
                auxMem.writeByte(NumTrans, (byte) numTransition);
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent me) {
    }

    @Override
    public void mouseReleased(MouseEvent me) {
    }

    @Override
    public void mouseEntered(MouseEvent me) {
    }

    @Override
    public void mouseExited(MouseEvent me) {
    }
}