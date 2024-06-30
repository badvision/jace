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
        if (font == null || !initialized) {
            initalize();
        }
        return font[c][yOffset];
    }

    private static void initalize() {
        font = new int[256][8];
        InputStream in = Font.class.getResourceAsStream("/jace/data/font.png");
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
        initialized = true;
    }

    /**
     * Creates a new instance of Font
     */
    private Font() {
    }

}
