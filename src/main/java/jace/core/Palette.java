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