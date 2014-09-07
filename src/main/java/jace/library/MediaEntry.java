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
