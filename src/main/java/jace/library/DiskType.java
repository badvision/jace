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

package jace.library;

import java.io.File;
import java.util.Optional;

import jace.core.Utility;
import jace.hardware.FloppyDisk;
import javafx.scene.image.Image;

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
    public Optional<Image> diskIcon;
    DiskType(String desc, boolean is140, boolean po, String iconPath) {
        description = desc;
        is140kb = is140;
        isProdosOrdered = po;
        diskIcon = Utility.loadIcon(iconPath);
    }
    
    static public DiskType determineType(File file) {
        if (file == null || !file.exists()) return null;
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
