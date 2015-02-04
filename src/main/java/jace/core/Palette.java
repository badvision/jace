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
package jace.core;

import javafx.scene.paint.Color;

/**
 * Fixed color palette -- only used for the older DHGR renderer (the new NTSC renderer uses its own YUV conversion and builds its own palettes)
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Palette {
    private Palette() {}

    static public final int BLACK = 0;
    static public final int VIOLET = 3;
    static public final int BLUE = 6;
    static public final int ORANGE = 9;
    static public final int GREEN = 12;
    static public final int WHITE = 15;
    
    static public Color[] color;
    static {
        color = new Color[16];
        color[ 0] = Color.rgb(0,  0,  0);
        color[ 1] = Color.rgb(208,  0, 48);
        color[ 2] = Color.rgb(  0,  0,128);
        color[ 3] = Color.rgb(255,  0,255);
        color[ 4] = Color.rgb(  0,128,  0);
        color[ 5] = Color.rgb(128,128,128);
        color[ 6] = Color.rgb(  0,  0,255);
        color[ 7] = Color.rgb( 96,160,255);
        color[ 8] = Color.rgb(128, 80,  0);
        color[ 9] = Color.rgb(255,128,  0);
        color[10] = Color.rgb(192,192,192);
        color[11] = Color.rgb(255,144,128);
        color[12] = Color.rgb(  0,255,  0);
        color[13] = Color.rgb(255,255,  0);
        color[14] = Color.rgb( 64,255,144);
        color[15] = Color.rgb(255,255,255);
    }
}