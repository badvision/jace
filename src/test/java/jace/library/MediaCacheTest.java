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
import java.io.IOException;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for MediaCache class
 * @author brobert
 */
public class MediaCacheTest {
    
    private File tempDir;
    private File testFile;
    
    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("mediacache-test").toFile();
        
        // Create a test file in the temp directory
        testFile = new File(tempDir, "test.dsk");
        testFile.createNewFile();
        byte[] testData = new byte[143360]; // Standard 140K disk size
        Files.write(testFile.toPath(), testData);
    }
    
    @After
    public void tearDown() {
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        tempDir.delete();
    }
    
    /**
     * Test MediaCache constructor
     */
    @Test
    public void testConstructor() {
        MediaCache cache = new MediaCache();
        
        assertNotNull(cache.favorites);
        assertTrue(cache.favorites.isEmpty());
        
        assertNotNull(cache.nameLookup);
        assertTrue(cache.nameLookup.isEmpty());
        
        assertNotNull(cache.categoryLookup);
        assertTrue(cache.categoryLookup.isEmpty());
        
        assertNotNull(cache.keywordLookup);
        assertTrue(cache.keywordLookup.isEmpty());
        
        assertNotNull(cache.mediaLookup);
        assertTrue(cache.mediaLookup.isEmpty());
        
        assertEquals(0, cache.lastDirtyMarker);
    }
    
    /**
     * Test getMediaFromFile static method
     */
    @Test
    public void testGetMediaFromFile() {
        MediaEntry entry = MediaCache.getMediaFromFile(testFile);
        
        assertNotNull(entry);
        assertTrue(entry.isLocal);
        assertEquals(DiskType.FLOPPY140_DO, entry.type);
        
        assertNotNull(entry.files);
        assertEquals(1, entry.files.size());
        
        MediaEntry.MediaFile file = entry.files.get(0);
        assertEquals(testFile, file.path);
        assertFalse(file.temporary);
        assertTrue(file.activeVersion);
    }
    
    /**
     * Test getMediaFromUrl static method (currently throws UnsupportedOperationException)
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetMediaFromUrl() {
        MediaCache.getMediaFromUrl("http://example.com/disk.dsk");
    }
    
    /**
     * Test getLocalLibrary static method - creates a new instance if not already existing
     */
    @Test
    public void testGetLocalLibrary() {
        // Reset the LOCAL_LIBRARY to ensure we're testing initialization
        MediaCache.LOCAL_LIBRARY = null;
        
        MediaCache cache = MediaCache.getLocalLibrary();
        assertNotNull(cache);
        
        // Should be the same instance when called again
        MediaCache cache2 = MediaCache.getLocalLibrary();
        assertSame(cache, cache2);
    }
} 