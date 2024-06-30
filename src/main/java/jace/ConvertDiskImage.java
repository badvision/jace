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

package jace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import jace.hardware.FloppyDisk;

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
        boolean writeNibblized;
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
        FloppyDisk theDisk;
        try {
            theDisk = new FloppyDisk(in);
        } catch (IOException ex) {
            System.out.println("Couldn't read disk image");
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
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                } catch (IOException ex) {
                    System.err.println("Error closing NIB image: " + ex.getMessage());
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
