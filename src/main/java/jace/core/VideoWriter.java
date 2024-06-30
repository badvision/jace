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

import javafx.scene.image.WritableImage;

/**
 * VideoWriter is an abstraction of a graphics display mode that knows how to
 * render a scanline a certain way (lo-res, hi-res, text, etc) over a specific
 * range of memory (as determined by getYOffset.) Dirty flags are used to mark
 * scanlines that were altered and require redraw. This is the key to only
 * updating the screen as needed instead of drawing all the time at the expense
 * of CPU.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class VideoWriter {

    int currentRow = -1;
    public void setCurrentRow(int y) {
        currentRow = y;
    }

    public abstract void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset);

    // This is used to support composite mixed-mode writers so that we can talk to the writer being used for a scanline
    public VideoWriter actualWriter() {
        return this;
    }

    public abstract int getYOffset(int y);
    // Dirty flags allow us to know if a scanline has or has not changed
    // Very useful for knowing if we should bother drawing changes
    private final boolean[] dirtyFlags = new boolean[192];

    boolean updatedDuringRaster = false;
    public void markDirty(int y) {
        actualWriter().dirtyFlags[y] = true;
        if (y == currentRow) {
            updatedDuringRaster = true;
        }
    }

    public void clearDirty(int y) {
        if (!updatedDuringRaster) {
            actualWriter().dirtyFlags[y] = false;
        }
        updatedDuringRaster = false;
    }

    public boolean isRowDirty(int y) {
        return actualWriter().dirtyFlags[y];
    }

    public boolean isMixed() {
        return false;
    }
}
