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

package jace.hardware;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.state.StateManager;
import jace.state.Stateful;

/**
 * Representation of a 140kb floppy disk image. This also performs conversions
 * as needed. Internally, the emulator will always use a "nibblized" disk
 * representation during active use. So if any sort of dsk/do/po image is loaded
 * it will be converted first. If changes are made to the disk then the tracks
 * will be converted back into de-nibblized form prior to saving. The
 * DiskIIDrive class managed disk changes, this class is more an interface to
 * load/save various disk formats and hold the active disk image while it is in
 * use.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
public class FloppyDisk {

    @Stateful
    boolean writeProtected;
    @Stateful
    public int headerLength = 0;
    @Stateful
    public boolean isNibblizedImage;
    @Stateful
    public int volumeNumber;
    static final public int TRACK_NIBBLE_LENGTH = 0x1A00;
    static final public int TRACK_COUNT = 35;
    static final public int SECTOR_COUNT = 16;
    static final public int HALF_TRACK_COUNT = TRACK_COUNT * 2;
    static final public int DISK_NIBBLE_LENGTH = TRACK_NIBBLE_LENGTH * TRACK_COUNT;
    static final public int DISK_PLAIN_LENGTH = 143360;
    static final public int DISK_2MG_NON_NIB_LENGTH = DISK_PLAIN_LENGTH + 0x040;
    static final public int DISK_2MG_NIB_LENGTH = DISK_NIBBLE_LENGTH + 0x040;
    @Stateful
    public byte[] nibbles = new byte[DISK_NIBBLE_LENGTH];
    // Denotes the mapping of physical order (array index) to the dos 3.3 logical order (value)
    public static int[] DOS_33_SECTOR_ORDER = {
        0x00, 0x07, 0x0E, 0x06, 0x0D, 0x05, 0x0C, 0x04,
        0x0B, 0x03, 0x0A, 0x02, 0x09, 0x01, 0x08, 0x0F
    };
    // Denotes the mapping of physical order (array index) to the Prodos logical order (value)
    // Borrowed from KEGS -- thanks KEGS team!
    public static int[] PRODOS_SECTOR_ORDER = {
        0x00, 0x08, 0x01, 0x09, 0x02, 0x0a, 0x03, 0x0b,
        0x04, 0x0c, 0x05, 0x0d, 0x06, 0x0e, 0x07, 0x0f
    };
    // Sector ordering used for current disk
    @Stateful
    public int[] currentSectorOrder;
    // Location of image
    @Stateful
    public File diskPath;
    static int[] NIBBLE_62 = {
        0x96, 0x97, 0x9a, 0x9b, 0x9d, 0x9e, 0x9f, 0xa6,
        0xa7, 0xab, 0xac, 0xad, 0xae, 0xaf, 0xb2, 0xb3,
        0xb4, 0xb5, 0xb6, 0xb7, 0xb9, 0xba, 0xbb, 0xbc,
        0xbd, 0xbe, 0xbf, 0xcb, 0xcd, 0xce, 0xcf, 0xd3,
        0xd6, 0xd7, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde,
        0xdf, 0xe5, 0xe6, 0xe7, 0xe9, 0xea, 0xeb, 0xec,
        0xed, 0xee, 0xef, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6,
        0xf7, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd, 0xfe, 0xff};
    static int[] NIBBLE_62_REVERSE;

    static {
        NIBBLE_62_REVERSE = new int[256];
        for (int i = 0; i < NIBBLE_62.length; i++) {
            NIBBLE_62_REVERSE[NIBBLE_62[i] & 0x0ff] = 0x0ff & i;
        }
    }
    private static final boolean DEBUG = false;

    public FloppyDisk() throws IOException {
        // This constructor is only used for disk conversion...
    }

    public static enum SectorOrder {
        DOS(DOS_33_SECTOR_ORDER), PRODOS(PRODOS_SECTOR_ORDER), UNKNOWN(DOS_33_SECTOR_ORDER);
        public final int[] sectors;
        SectorOrder(int[] sectors) {
            this.sectors = sectors;
        }
    }

    /**
     *
     * @param diskFile
     * @throws IOException
     */
    public FloppyDisk(File diskFile) throws IOException {
        FileInputStream input = new FileInputStream(diskFile);
        readDisk(input, diskFile.getName().toUpperCase().endsWith(".PO") ? SectorOrder.PRODOS : SectorOrder.DOS);
        writeProtected = !diskFile.canWrite();
        diskPath = diskFile;
    }

    // brendanr: refactored to use input stream
    public void readDisk(InputStream diskFile, SectorOrder assumedOrder) throws IOException {
        isNibblizedImage = true;
        volumeNumber = CardDiskII.DEFAULT_VOLUME_NUMBER;
        headerLength = 0;
        SectorOrder sectorOrder = SectorOrder.UNKNOWN;
        try {
            int bytesRead = diskFile.read(nibbles);
            if (bytesRead == DISK_2MG_NIB_LENGTH) {
                bytesRead -= 0x040;
                // Try to pick up volume number from 2MG header.
                volumeNumber = ((nibbles[17] & 1) == 1) ? nibbles[16] : 254;
                nibbles = Arrays.copyOfRange(nibbles, 0x040, nibbles.length);

                headerLength = 0x040;
            }
            if (bytesRead == DISK_2MG_NON_NIB_LENGTH) {
                bytesRead -= 0x040;
                // Try to pick up correct sector ordering and volume from 2MG header.
                if (nibbles[12] == 01) {
                    sectorOrder = SectorOrder.PRODOS;
                } else {
                    sectorOrder = SectorOrder.DOS;
                }
                volumeNumber = ((nibbles[17] & 1) == 1) ? nibbles[16] : 254;
                nibbles = Arrays.copyOfRange(nibbles, 0x040, nibbles.length);

                headerLength = 0x040;
            }
            
            if (sectorOrder == SectorOrder.UNKNOWN) {
                if (isProdosVolumeBlock(nibbles, 0x0400)) {
                    System.out.println("Prodos volume block found at 0x0400");
                    sectorOrder = SectorOrder.PRODOS;
                } else if (isProdosVolumeBlock(nibbles, 0x0B00)) {
                    System.out.println("Prodos volume block found at 0x0A00");
                    sectorOrder = SectorOrder.DOS;
                }
            }
            if (sectorOrder == SectorOrder.UNKNOWN) {
                System.out.println("Assuming sector order based on file extension");
                sectorOrder = assumedOrder;
            }
            System.out.println(null == sectorOrder ? "Sector order is null" : "Sector order is " + sectorOrder.name());
            currentSectorOrder = sectorOrder.sectors;
            if (bytesRead == DISK_PLAIN_LENGTH) {
                isNibblizedImage = false;
                nibbles = nibblize(nibbles);
                if (nibbles.length != DISK_NIBBLE_LENGTH) {
                    throw new IOException("Nibblized version is wrong size (expected-actual = " + (DISK_NIBBLE_LENGTH - nibbles.length) + ")");
                }
            } else if (bytesRead != DISK_NIBBLE_LENGTH) {
                throw new IOException("Bad NIB size " + bytesRead + "; JACE only recognizes plain images " + DISK_PLAIN_LENGTH + " or nibble images " + DISK_NIBBLE_LENGTH + " sizes");
            }
        } catch (IOException ex) {
            throw ex;
        }
        StateManager.markDirtyValue(nibbles);
        StateManager.markDirtyValue(currentSectorOrder);
    }

    private boolean isProdosVolumeBlock(byte[] nibbles, int offset) {
        // First two bytes are zero (no previous block)
        if (nibbles[offset] != 0 || nibbles[offset+1] != 0) {
            return false;
        }
        // Next two bytes are either both zero or at least in the range of 3...280
        int nextBlock = (nibbles[offset+2] & 0x0ff) | (nibbles[offset+3] << 8);
        if (nextBlock == 1 || nextBlock == 2 || nextBlock > 280) {
            return false;
        }
        // Now check total blocks at offset 0x29
        int totalBlocks = (nibbles[offset+0x29] & 0x0ff) | (nibbles[offset+0x2a] << 8);
        if (totalBlocks != 280) {
            return false;
        }
        return true;
    }
    /*
     * Convert a block-format disk to a 6-by-2 nibblized encoding scheme (raw NIB disk format)
     */
    public byte[] nibblize(byte[] nibbles) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int track = 0; track < TRACK_COUNT; track++) {
            for (int sector = 0; sector < SECTOR_COUNT; sector++) {
                int gap2 = (int) ((Math.random() * 5.0) + 4);
                // 15 junk bytes
                writeJunkBytes(output, 15);
                // Address block
                writeAddressBlock(output, track, sector);
                // 4 junk bytes
                writeJunkBytes(output, gap2);
                // Data block
                nibblizeBlock(output, track, currentSectorOrder[sector], nibbles);
                // 34 junk bytes
                writeJunkBytes(output, 38 - gap2);
            }
        }
        // Write output to stdout for debugging purposes
        if (DEBUG) {
            System.out.println("Nibblized disk:");
            for (int i = 0; i < output.size(); i++) {
                System.out.print(Integer.toString(output.toByteArray()[i] & 0x0ff, 16) + " ");
                if (i % 16 == 255) {
                    System.out.println();
                }
            }
            System.out.println();
        }
        return output.toByteArray();
    }

    private void writeJunkBytes(ByteArrayOutputStream output, int i) {
        for (int b = 0; b < i; b++) {
            output.write(0x0FF);
        }
    }

    private void writeAddressBlock(ByteArrayOutputStream output, int track, int sector) throws IOException {
        output.write(0x0d5);
        output.write(0x0aa);
        output.write(0x096);

        int checksum = 00;
        // volume
        checksum ^= volumeNumber;
        output.write(getOddEven(volumeNumber));
        // track
        checksum ^= track;
        output.write(getOddEven(track));
        // sector
        checksum ^= sector;
        output.write(getOddEven(sector));
        // checksum
        output.write(getOddEven(checksum & 0x0ff));
        output.write(0x0de);
        output.write(0x0aa);
        output.write(0x0eb);
    }

    private byte[] getOddEven(int i) {
        byte[] out = new byte[2];
        out[0] = (byte) (0xAA | (i >> 1));
        out[1] = (byte) (0xAA | i);
        return out;
    }

    private int decodeOddEven(byte b1, byte b2) {
//        return (((b1 ^ 0x0AA) << 1) & 0x0ff) | ((b2 ^ 0x0AA) & 0x0ff);
        return ((((b1 << 1) | 1) & b2) & 0x0ff);
    }

    private void nibblizeBlock(ByteArrayOutputStream output, int track, int sector, byte[] nibbles) {
        int offset = ((track * SECTOR_COUNT) + sector) * 256;
        int[] temp = new int[342];
        for (int i = 0; i < 256; i++) {
            temp[i] = (nibbles[offset + i] & 0x0ff) >> 2;
        }
        int hi = 0x001;
        int med = 0x0AB;
        int low = 0x055;

        for (int i = 0; i < 0x56; i++) {
            int value = ((nibbles[offset + hi] & 1) << 5)
                    | ((nibbles[offset + hi] & 2) << 3)
                    | ((nibbles[offset + med] & 1) << 3)
                    | ((nibbles[offset + med] & 2) << 1)
                    | ((nibbles[offset + low] & 1) << 1)
                    | ((nibbles[offset + low] & 2) >> 1);
            temp[i + 256] = value;
            hi = (hi - 1) & 0x0ff;
            med = (med - 1) & 0x0ff;
            low = (low - 1) & 0x0ff;
        }
        output.write(0x0d5);
        output.write(0x0aa);
        output.write(0x0ad);

        int last = 0;
        for (int i = temp.length - 1; i > 255; i--) {
            int value = temp[i] ^ last;
            output.write(NIBBLE_62[value]);
            last = temp[i];
        }
        for (int i = 0; i < 256; i++) {
            int value = temp[i] ^ last;
            output.write(NIBBLE_62[value]);
            last = temp[i];
        }
        // Last data byte used as checksum
        output.write(NIBBLE_62[last]);
        output.write(0x0de);
        output.write(0x0aa);
        output.write(0x0eb);
    }

    public void updateTrack(Integer track) {
        // If disk is nibble image, write nibbles directly
        if (isNibblizedImage) {
            updateNibblizedTrack(track);
        }
        // Otherwise denibblize and write out
        if (!isNibblizedImage) {
            updateDenibblizedTrack(track);
        }
    }

    void updateNibblizedTrack(Integer track) {
        // Locate start of track
        try (RandomAccessFile disk = new RandomAccessFile(diskPath, "rws")) {
            // Locate start of track
            disk.seek(headerLength + track * TRACK_NIBBLE_LENGTH);
            // Update that section of the disk image
            disk.write(nibbles, track * TRACK_NIBBLE_LENGTH, TRACK_NIBBLE_LENGTH);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FloppyDisk.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FloppyDisk.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    static public boolean CHECK_NIB_SECTOR_PATTERN_ON_WRITE = true;

    void updateDenibblizedTrack(Integer track) {
        try {
            byte[] trackNibbles = new byte[TRACK_NIBBLE_LENGTH];
            byte[] trackData = new byte[SECTOR_COUNT * 256];
            // Copy track into temporary buffer
            if (DEBUG) {
                System.out.println("Nibblized track "+track);
                System.out.printf("%04d:",0);
            }
           for (int i = 0, pos = track * TRACK_NIBBLE_LENGTH; i < TRACK_NIBBLE_LENGTH; i++, pos++) {
                trackNibbles[i] = nibbles[pos];
                if (DEBUG) {
                    System.out.print(Integer.toString(nibbles[pos] & 0x0ff, 16)+" ");
                    if (i % 16 == 15) {
                        System.out.println();
                        System.out.printf("%04d:",i+1);
                    }
                }
            }
            if (DEBUG) {
               System.out.println();
            }

            int pos = 0;
            for (int i = 0; i < SECTOR_COUNT; i++) {
                // Loop through number of sectors
                pos = locatePattern(pos, trackNibbles, 0x0d5, 0x0aa, 0x096);
                // Locate track number
                int trackVerify = decodeOddEven(trackNibbles[pos + 5], trackNibbles[pos + 6]);
                // Locate sector number
                int sector = decodeOddEven(trackNibbles[pos + 7], trackNibbles[pos + 8]);
                if (DEBUG) {
                    System.out.println("Writing track " + track + ", getting address block for T" + trackVerify + ".S" + sector + " found at NIB offset "+pos);
                }
                // Skip to end of address block
                pos = locatePattern(pos, trackNibbles, 0x0de, 0x0aa /*, 0x0eb this is sometimes being written as FF??*/);
                // Locate start of sector data
                pos = locatePattern(pos, trackNibbles, 0x0d5, 0x0aa, 0x0ad);
                // Determine offset in output data for sector
                //int offset = reverseLoopkup(currentSectorOrder, sector) * 256;
                int offset = currentSectorOrder[sector] * 256;
                if (DEBUG) {
                    System.out.println("Sector "+sector+" maps to physical sector "+reverseLoopkup(currentSectorOrder, sector));
                }
                // Decode sector data
                denibblizeSector(trackNibbles, pos + 3, trackData, offset);
                // Skip to end of sector
                pos = locatePattern(pos, trackNibbles, 0x0de, 0x0aa, 0x0eb);
            }
            // Write track to disk
            RandomAccessFile disk;
            try {
                disk = new RandomAccessFile(diskPath, "rws");
                disk.seek(headerLength + track * 256 * SECTOR_COUNT);
                disk.write(trackData);
                disk.close();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(FloppyDisk.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(FloppyDisk.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (Throwable ex) {
            Logger.getLogger(FloppyDisk.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private int locatePattern(int pos, byte[] data, int... pattern) throws Throwable {
        int max = data.length;
        while (!matchPattern(pos, data, pattern)) {
            pos = (pos + 1) % data.length;
            max--;
            if (max < 0) {
                throw new Throwable("Could not match pattern!");
            }
        }
//        System.out.print("Found pattern at "+pos+": ");
//        for (int i : pattern) {System.out.print(Integer.toString( i & 0x0ff, 16)+" ");}
//        System.out.println();
        return pos;
    }

    private boolean matchPattern(int pos, byte[] data, int... pattern) {
        int matched = 0;
        for (int i : pattern) {
            int d = data[pos] & 0x0ff;
            if (d != i) {
                if (matched > 1) {
                    System.out.println("Warning: Issue when interpreting nibbilized disk data: at position " + pos + " pattern byte " + Integer.toString(i, 16) + " doesn't match " + Integer.toString(d, 16));
                }
                return false;
            }
            pos = (pos + 1) % data.length;
            matched++;
        }
        return true;
    }

    private void denibblizeSector(byte[] source, int pos, byte[] trackData, int offset) {
        int[] temp = new int[342];
        int current = pos;
        int last = 0;
        // Un-encode raw data, leaving with pre-nibblized bytes
        for (int i = temp.length - 1; i > 255; i--) {
            int t = NIBBLE_62_REVERSE[0x0ff & source[current++]];
            temp[i] = t ^ last;
            last ^= t;
        }
        for (int i = 0; i < 256; i++) {
            int t = NIBBLE_62_REVERSE[0x0ff & source[current++]];
            temp[i] = t ^ last;
            last ^= t;
        }

        // Now decode the pre-nibblized bytes
        int p = temp.length - 1;
        for (int i = 0; i < 256; i++) {
            int a = (temp[i] << 2);
            a = a + ((temp[p] & 1) << 1) + ((temp[p] & 2) >> 1);
            trackData[i + offset] = (byte) a;
            temp[p] = temp[p] >> 2;
            p--;
            if (p < 256) {
                p = temp.length - 1;
            }
        }
    }

    private int reverseLoopkup(int[] table, int value) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
