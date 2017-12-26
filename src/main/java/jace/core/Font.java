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

import java.io.InputStream;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

/**
 * Represents the Apple ][ character font used in text modes. Created on January
 * 16, 2007, 8:16 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Font {

    static public int[][] font;
    static public boolean initialized = false;

    static public int getByte(int c, int yOffset) {
        if (!initialized) {
            initalize();
        }
        return font[c][yOffset];
    }

    private static void initalize() {
        initialized = true;
        font = new int[256][8];
        Thread fontLoader = new Thread(() -> {
            InputStream in = Font.class.getClassLoader().getResourceAsStream("jace/data/font.png");
            Image image = new Image(in);
            PixelReader reader = image.getPixelReader();
            for (int i = 0; i < 256; i++) {
                int x = (i >> 4) * 13 + 2;
                int y = (i & 15) * 13 + 4;
                for (int j = 0; j < 8; j++) {
                    int row = 0;
                    for (int k = 0; k < 7; k++) {
                        Color color = reader.getColor((7 - k) + x, j + y);
                        boolean on = color.getRed() != 0;
                        row = (row << 1) | (on ? 0 : 1);
                    }
                    font[i][j] = row;
                }
            }
        });
        fontLoader.start();
    }

    /**
     * Creates a new instance of Font
     */
    private Font() {
    }

}
