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

import jace.core.Utility;
import jace.hardware.FloppyDisk;
import java.awt.Image;
import java.io.File;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 *
 * @author brobert
 */
public enum DiskType {
    SINGLELOAD("Single-load binary", false, false, "rom_image.png"), 
    FLOPPY140_NIB("140kb nibble disk image", true, false, "525_floppy.png"),
    FLOPPY140_DO("140kb Dos-ordered disk", true, false, "525_floppy.png"), 
    FLOPPY140_PO("140kb Prodos-ordered disk", true, true, "525_floppy.png"),
    FLOPPY140_2MG("140kb disk with 2MG header", true, false, "525_floppy.png"), 
    FLOPPY800("800kb disk (2mg or raw)", false, true, "35_floppy.png"),
    LARGE("Hard drive image (2mg or raw)", false, true, "harddrive.png"),
    VIRTUAL("Virtual Prodos Volume", false, true, "harddrive.png");

    public boolean isProdosOrdered = false;
    public boolean is140kb = false;
    public String description;
    public Image diskIcon;
    DiskType(String desc, boolean is140, boolean po, String iconPath) {
        description = desc;
        is140kb = is140;
        isProdosOrdered = po;
        diskIcon = Utility.loadIcon(iconPath).getImage();
    }
    
    static public DiskType determineType(File file) {
        if (!file.exists()) return null;
        if (file.isDirectory()) return VIRTUAL;
        if (file.getName().toLowerCase().endsWith("hdv")) {
            return LARGE;
        }
        if (file.getName().toLowerCase().endsWith("nib")) {
            return FLOPPY140_NIB;
        }
        if (file.getName().toLowerCase().endsWith("dsk")) {
            return FLOPPY140_DO;
        }
        long length = file.length();
        if (length <= 64*1024) return SINGLELOAD;
        if (length == FloppyDisk.DISK_2MG_NIB_LENGTH || length == FloppyDisk.DISK_2MG_NON_NIB_LENGTH)
            return FLOPPY140_2MG;
        if (length == FloppyDisk.DISK_NIBBLE_LENGTH)
            return FLOPPY140_NIB;
        if (length == FloppyDisk.DISK_PLAIN_LENGTH) {
            if (file.getName().toLowerCase().endsWith(".po")) return FLOPPY140_PO;
            return FLOPPY140_DO;
        }
        if (Math.abs( (800 * 1024) - length) <= 1024) {
            return FLOPPY800;
        }
        return LARGE;
    }    
}
