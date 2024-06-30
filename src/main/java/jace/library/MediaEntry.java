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
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author brobert
 */
public class MediaEntry implements Serializable {

    @Override
    public String toString() {
        return (name == null || name.length() == 0) ? "No name" : name;
    }
    
    public long id;
    public boolean isLocal;
    public String source;
    public String name;
    public String[] keywords = new String[0];
    public String category;
    public String description;
    public String year;
    public String author;
    public String publisher;
    public String screenshotURL;
    public String boxFrontURL;
    public String boxBackURL;
    public boolean favorite;
    public DiskType type;
    public String auxtype;
    public boolean writeProtected;
    public List<MediaFile> files;

    public static class MediaFile implements Serializable {
        public long checksum;
        public File path;
        public boolean activeVersion;
        public String label;
        public long lastRead;
        public long lastWritten;
        volatile public boolean temporary = false;
    }    
}
