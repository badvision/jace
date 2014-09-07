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
package jace;

import jace.hardware.FloppyDisk;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Generic disk conversion utility, using the FloppyDisk nibblize/denibblize to
 * convert between DSK and NIB formats (wherever possible anyway)
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class ConvertDiskImage {

    public static void main(String... args) {
        if (args.length != 2) {
            showHelp();
            return;
        }
        File in = new File(args[0]);
        File out = new File(args[1]);
        if (!in.exists()) {
            showHelp();
            System.out.println("Cannot find input file: " + args[0]);
            return;
        }
        if (out.exists()) {
            showHelp();
            System.out.println("Output file already exists!: " + args[1]);
            return;
        }
        String ext = args[1].substring(args[1].length() - 3);
        boolean writeNibblized = false;
        boolean writeProdosOrdered = false;
        if (ext.equalsIgnoreCase("NIB")) {
            System.out.println("Preparing to write NIB image");
            writeNibblized = true;
        } else if (ext.equalsIgnoreCase(".DO") || ext.equalsIgnoreCase("DSK")) {
            System.out.println("Preparing to write DOS 3.3 ordered disk image");
            writeNibblized = false;
            writeProdosOrdered = false;
        } else if (ext.equalsIgnoreCase(".PO")) {
            System.out.println("Preparing to write Prodos ordered image");
            writeNibblized = false;
            writeProdosOrdered = true;
        } else {
            showHelp();
            System.out.println("Could not understand desired output format");
            return;
        }

        // First read in the disk image, this decodes the disk as necessary
        FloppyDisk theDisk = null;
        try {
            theDisk = new FloppyDisk(in);
        } catch (IOException ex) {
            System.out.println("Couldn't read disk image");
            ex.printStackTrace();
            return;
        }
        if (!writeNibblized) {
            // Now change the disk image to point to a new file and adjust the sector ordering        
            System.out.println("Writing disk image with " + (writeProdosOrdered ? "prodos" : "dos 3.3") + " sector ordering");
            theDisk.diskPath = out;
            theDisk.isNibblizedImage = true;
            theDisk.currentSectorOrder = writeProdosOrdered ? FloppyDisk.PRODOS_SECTOR_ORDER : FloppyDisk.DOS_33_SECTOR_ORDER;
            theDisk.headerLength = 0;
            for (int i = 0; i < FloppyDisk.TRACK_COUNT; i++) {
                theDisk.updateTrack(i);
            }
        } else {
            FileOutputStream fos = null;
            System.out.println("Writing NIB image");
            try {
                fos = new FileOutputStream(out);
                fos.write(theDisk.nibbles);
                fos.close();
            } catch (IOException ex) {
                System.err.println("Error writing NIB image: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                try {
                    fos.close();
                } catch (IOException ex) {
                    System.err.println("Error closing NIB image: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
        System.out.println("Finished converting disk image.");
    }

    private static void showHelp() {
        for (String s : new String[]{
                    "ConvertDiskImage",
                    "----------------",
                    "Usage: java -cp jace.jar jace.ConvertDiskImage DISK_INPUT_NAME DISK_OUTPUT_NAME",
                    "where DISK_INPUT_NAME is the path of a valid disk image, ",
                    "and DISK_OUTPUT_NAME is the path where you want to ",
                    "save the converted disk image.",
                    "Supported input formats: ",
                    "        DSK (assumes DO), DO, PO, 2MG (140kb), NIB",
                    "Supported output formats: ",
                    "        DO/DSK, PO, NIB"
                }) {
            System.out.println(s);
        }
    }
}
