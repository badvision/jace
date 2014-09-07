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
package jace.hardware.massStorage;

import jace.library.MediaConsumer;
import jace.library.MediaEntry;
import jace.library.MediaEntry.MediaFile;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;

/**
 *
 * @author brobert
 */
public class MassStorageDrive implements MediaConsumer {
    IDisk disk = null;
    ImageIcon icon = null;
    
    public ImageIcon getIcon() {
        return icon;
    }

    public void setIcon(ImageIcon i) {
        icon = i;
    }

    MediaEntry currentEntry;
    MediaFile currentFile;
    
    public void insertMedia(MediaEntry e, MediaFile f) throws IOException {
        eject();
        currentEntry = e;
        currentFile = f;
        disk= readDisk(currentFile.path);
    }

    public MediaEntry getMediaEntry() {
        return currentEntry;
    }

    public MediaFile getMediaFile() {
        return currentFile;
    }

    public boolean isAccepted(MediaEntry e, MediaFile f) {
        return e.type.isProdosOrdered;
    }

    public void eject() {
        if (disk != null) {
            disk.eject();
            disk = null;
        }
    }
    
     private IDisk readDisk(File f) {
        if (f.isFile()) {
            return new LargeDisk(f);
        } else if (f.isDirectory()) {
            try {
                return new ProdosVirtualDisk(f);
            } catch (IOException ex) {
                System.out.println("Unable to open virtual disk: " + ex.getMessage());
                Logger.getLogger(CardMassStorage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }    

   public IDisk getCurrentDisk() {
        return disk;
    }
}