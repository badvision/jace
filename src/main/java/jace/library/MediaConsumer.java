/*
 * Copyright (C) 2013 brobert.
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
package jace.library;

import jace.library.MediaEntry.MediaFile;
import java.io.IOException;
import javax.swing.ImageIcon;

/**
 *
 * @author brobert
 */
public interface MediaConsumer {
    public ImageIcon getIcon();
    public void setIcon(ImageIcon i);
    public void insertMedia(MediaEntry e, MediaFile f) throws IOException;
    public MediaEntry getMediaEntry();
    public MediaFile getMediaFile();
    public boolean isAccepted(MediaEntry e, MediaFile f);
    public void eject();
}
