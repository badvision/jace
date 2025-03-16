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
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for MediaEntry class
 * @author brobert
 */
public class MediaEntryTest {
    
    /**
     * Test MediaEntry constructor and default values
     */
    @Test
    public void testDefaultValues() {
        MediaEntry entry = new MediaEntry();
        
        assertEquals(0, entry.id);
        assertFalse(entry.isLocal);
        assertNull(entry.source);
        assertNull(entry.name);
        assertEquals(0, entry.keywords.length);
        assertNull(entry.category);
        assertNull(entry.description);
        assertNull(entry.year);
        assertNull(entry.author);
        assertNull(entry.publisher);
        assertNull(entry.screenshotURL);
        assertNull(entry.boxFrontURL);
        assertNull(entry.boxBackURL);
        assertFalse(entry.favorite);
        assertNull(entry.type);
        assertNull(entry.auxtype);
        assertFalse(entry.writeProtected);
        assertNull(entry.files);
    }
    
    /**
     * Test MediaEntry toString method with name set
     */
    @Test
    public void testToStringWithName() {
        MediaEntry entry = new MediaEntry();
        entry.name = "Test Media";
        
        assertEquals("Test Media", entry.toString());
    }
    
    /**
     * Test MediaEntry toString method with null or empty name
     */
    @Test
    public void testToStringWithNoName() {
        MediaEntry entry = new MediaEntry();
        entry.name = null;
        assertEquals("No name", entry.toString());
        
        entry.name = "";
        assertEquals("No name", entry.toString());
    }
    
    /**
     * Test MediaFile inner class
     */
    @Test
    public void testMediaFile() {
        MediaEntry.MediaFile file = new MediaEntry.MediaFile();
        
        assertEquals(0, file.checksum);
        assertNull(file.path);
        assertFalse(file.activeVersion);
        assertNull(file.label);
        assertEquals(0, file.lastRead);
        assertEquals(0, file.lastWritten);
        assertFalse(file.temporary);
        
        // Test setting values
        file.checksum = 12345;
        file.path = new File("/tmp/test");
        file.activeVersion = true;
        file.label = "Test Label";
        file.lastRead = 1000;
        file.lastWritten = 2000;
        file.temporary = true;
        
        assertEquals(12345, file.checksum);
        assertEquals(new File("/tmp/test"), file.path);
        assertTrue(file.activeVersion);
        assertEquals("Test Label", file.label);
        assertEquals(1000, file.lastRead);
        assertEquals(2000, file.lastWritten);
        assertTrue(file.temporary);
    }
    
    /**
     * Test creating a MediaEntry with files
     */
    @Test
    public void testMediaEntryWithFiles() {
        MediaEntry entry = new MediaEntry();
        entry.name = "Test With Files";
        entry.files = new ArrayList<>();
        
        MediaEntry.MediaFile file1 = new MediaEntry.MediaFile();
        file1.label = "File 1";
        file1.path = new File("/tmp/file1");
        
        MediaEntry.MediaFile file2 = new MediaEntry.MediaFile();
        file2.label = "File 2";
        file2.path = new File("/tmp/file2");
        file2.activeVersion = true;
        
        entry.files.add(file1);
        entry.files.add(file2);
        
        assertEquals(2, entry.files.size());
        assertEquals("File 1", entry.files.get(0).label);
        assertEquals("File 2", entry.files.get(1).label);
        assertEquals(new File("/tmp/file1"), entry.files.get(0).path);
        assertEquals(new File("/tmp/file2"), entry.files.get(1).path);
        assertFalse(entry.files.get(0).activeVersion);
        assertTrue(entry.files.get(1).activeVersion);
    }
} 