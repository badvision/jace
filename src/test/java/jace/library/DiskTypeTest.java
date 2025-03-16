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

import jace.hardware.FloppyDisk;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the DiskType class
 * @author brobert
 */
public class DiskTypeTest {
    
    private File tempDir;
    
    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("disktype-test").toFile();
    }
    
    @After
    public void tearDown() {
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        tempDir.delete();
    }
    
    /**
     * Test determineType for various file extensions
     */
    @Test
    public void testDetermineTypeByExtension() throws Exception {
        // Test for .hdv extension
        File hdvFile = new File(tempDir, "test.hdv");
        hdvFile.createNewFile();
        assertEquals(DiskType.LARGE, DiskType.determineType(hdvFile));
        
        // Test for .nib extension
        File nibFile = new File(tempDir, "test.nib");
        nibFile.createNewFile();
        assertEquals(DiskType.FLOPPY140_NIB, DiskType.determineType(nibFile));
        
        // Test for .dsk extension
        File dskFile = new File(tempDir, "test.dsk");
        dskFile.createNewFile();
        assertEquals(DiskType.FLOPPY140_DO, DiskType.determineType(dskFile));
    }
    
    /**
     * Test determineType based on file size
     */
    @Test
    public void testDetermineTypeBySize() throws Exception {
        // Test for small file (SINGLELOAD)
        File smallFile = new File(tempDir, "small.bin");
        smallFile.createNewFile();
        byte[] smallData = new byte[32 * 1024]; // 32K
        Files.write(smallFile.toPath(), smallData);
        assertEquals(DiskType.SINGLELOAD, DiskType.determineType(smallFile));
        
        // Test for PO file
        File poFile = new File(tempDir, "disk.po");
        poFile.createNewFile();
        byte[] poData = new byte[(int) FloppyDisk.DISK_PLAIN_LENGTH];
        Files.write(poFile.toPath(), poData);
        assertEquals(DiskType.FLOPPY140_PO, DiskType.determineType(poFile));
        
        // Test for DO file (same size as PO but different extension)
        File doFile = new File(tempDir, "disk.do");
        doFile.createNewFile();
        byte[] doData = new byte[(int) FloppyDisk.DISK_PLAIN_LENGTH];
        Files.write(doFile.toPath(), doData);
        assertEquals(DiskType.FLOPPY140_DO, DiskType.determineType(doFile));
        
        // Test for NIB file by size
        File nibSizeFile = new File(tempDir, "nibsize.bin");
        nibSizeFile.createNewFile();
        byte[] nibData = new byte[(int) FloppyDisk.DISK_NIBBLE_LENGTH];
        Files.write(nibSizeFile.toPath(), nibData);
        assertEquals(DiskType.FLOPPY140_NIB, DiskType.determineType(nibSizeFile));
    }
    
    /**
     * Test determineType for directory
     */
    @Test
    public void testDetermineTypeForDirectory() {
        File directory = new File(tempDir, "test-dir");
        directory.mkdir();
        assertEquals(DiskType.VIRTUAL, DiskType.determineType(directory));
    }
    
    /**
     * Test determineType for null or non-existent file
     */
    @Test
    public void testDetermineTypeForNullOrNonExistent() {
        assertNull(DiskType.determineType(null));
        assertNull(DiskType.determineType(new File("non-existent-file")));
    }
} 