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

import java.awt.image.BufferedImage;

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

    public abstract void displayByte(BufferedImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset);

    // This is used to support composite mixed-mode writers so that we can talk to the writer being used for a scanline
    public VideoWriter actualWriter() {
        return this;
    }

    public abstract int getYOffset(int y);
    // Dirty flags allow us to know if a scanline has or has not changed
    // Very useful for knowing if we should bother drawing changes
    private final boolean[] dirtyFlags = new boolean[192];

    public void markDirty(int y) {
        actualWriter().dirtyFlags[y] = true;
    }

    public void clearDirty(int y) {
        actualWriter().dirtyFlags[y] = false;
    }

    public boolean isRowDirty(int y) {
        return actualWriter().dirtyFlags[y];
    }

    public boolean isMixed() {
        return false;
    }
}
