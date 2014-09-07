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
package jace.tracker;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Command {
    public static enum CommandScope {global, channel}
    public static enum CommandType {
        Rest(0, 0, 0, false),
        Ay1(1, 1, 17, false),
        Ay2(2, 1, 17, false),
        Ay1and2(3, 2, 34, true),
        PitchSlide(4, 2, 2, true),
        Vibrato(5, 2, 2, true),
        VolumeSlide(6, 2, 2, true),
        Tremolo(7, 2, 2, true),
        GlobalDetune(8, 2, 2, true),
        Reserved(9, 0, 0, false),
        AllOff(0x0a, 1, 1, false),
        Reset(0x0b, 1, 1, false),
        Playback(0x0c, 1, 2, false),
        TicksPerBeat(0x0d, 1, 1, true),
        Clock(0x0e, 2, 2, false),
        Macro(0x0f, 1, 1, true);

        int number;
        int maxParameters;
        int minParameters;
        boolean isChain;
        CommandType(int num, int minParam, int maxParam, boolean chain) {
            number = num;
            isChain = chain;
            maxParameters = maxParam;
            minParameters = minParam;
        }
    }
    Integer[] parameters = {};
    
}
