package jace.hardware;
import static jace.hardware.FloppyDisk.PRODOS_SECTOR_ORDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.hardware.FloppyDisk.SectorOrder;

public class FloppyDiskTest extends AbstractFXTest {

    private FloppyDisk floppyDisk;

    @Before
    public void setUp() throws IOException {
        floppyDisk = new FloppyDisk();
    }

    @Test
    public void readDisk_ValidDiskFile_Success() throws IOException {
        // Create a sample disk file
        byte[] diskData = new byte[232960];
        File diskFile = File.createTempFile("test_disk", ".dsk");
        diskFile.deleteOnExit();
        ByteArrayInputStream diskInputStream = new ByteArrayInputStream(diskData);

        // Read the disk file
        floppyDisk.readDisk(diskInputStream, SectorOrder.DOS);

        // Verify the disk properties
        assert(floppyDisk.isNibblizedImage);
        assertEquals(254, floppyDisk.volumeNumber);
        assertEquals(0, floppyDisk.headerLength);
        assertEquals(232960, floppyDisk.nibbles.length);
        assertEquals("Sector order not null", true, null != floppyDisk.currentSectorOrder);
        assertNull(floppyDisk.diskPath);
    }

    @Test
    public void nibblize_ValidNibbles_Success() throws IOException {
        // Create a sample nibbles array
        byte[] nibbles = new byte[FloppyDisk.DISK_NIBBLE_LENGTH];
        for (int i = 0; i < nibbles.length; i++) {
            nibbles[i] = (byte) (i % 256);
        }
        floppyDisk.currentSectorOrder = PRODOS_SECTOR_ORDER;
        // Nibblize the nibbles array
        byte[] nibblizedData = floppyDisk.nibblize(nibbles);

        // Verify the nibblized data
        assertEquals(FloppyDisk.DISK_NIBBLE_LENGTH, nibblizedData.length);
//        for (int i = 0; i < nibblizedData.length; i++) {
//            assertEquals((i % 256) >> 2, nibblizedData[i]);
//        }
    }
}