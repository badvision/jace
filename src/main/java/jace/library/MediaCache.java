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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.core.Utility;
import jace.library.MediaEntry.MediaFile;

/**
 * Holds all information about media titles, manages low-level operations of
 * downloading images from online sources, and also manages the persistence of
 * the media library
 *
 * @author brobert
 */
public class MediaCache implements Serializable {

    public static int DELAY_BEFORE_PERSISTING_LIBRARY = 2000;
    public static MediaCache LOCAL_LIBRARY;

    public static MediaEntry getMediaFromFile(File draggedFile) {
        MediaEntry entry = new MediaEntry();
        MediaFile file = new MediaFile();
        file.path = draggedFile;
        file.temporary = false;
        file.activeVersion = true;
        entry.files = new ArrayList<>();
        entry.files.add(file);
        entry.isLocal = true;
        entry.type = DiskType.determineType(draggedFile);
        return entry;
    }

    public static MediaEntry getMediaFromUrl(String url) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public Set<Long> favorites;
    public Map<String, Set<Long>> nameLookup;
    public Map<String, Set<Long>> categoryLookup;
    public Map<String, Set<Long>> keywordLookup;
    public Map<Long, MediaEntry> mediaLookup;
    public long lastDirtyMarker;

    public MediaCache() {
        favorites = new HashSet<>();
        nameLookup = new HashMap<>();
        categoryLookup = new HashMap<>();
        keywordLookup = new HashMap<>();
        mediaLookup = new HashMap<>();
    }

    public static MediaCache getLocalLibrary() {
        if (LOCAL_LIBRARY == null) {
            LOCAL_LIBRARY = new MediaCache();
            LOCAL_LIBRARY.readLibraryFromDisk();
        }
        return LOCAL_LIBRARY;

    }

    private void cleanup() {
        cleanup(nameLookup);
        cleanup(categoryLookup);
        cleanup(keywordLookup);
    }

    private void cleanup(Map<String, Set<Long>> lookup) {
        Set<String> remove = new HashSet<>();
        lookup.entrySet().stream().forEach((entry) -> {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                remove.add(entry.getKey());
            } else {
                boolean hasSomething = false;
                for (Iterator<Long> l = entry.getValue().iterator(); l.hasNext();) {
                    if (mediaLookup.containsKey(l.next())) {
                        hasSomething = true;
                    } else {
                        l.remove();
                    }
                }
                if (!hasSomething) {
                    remove.add(entry.getKey());
                }
            }
        });
        lookup.keySet().removeAll(remove);
    }

    public void add(MediaEntry e) {
        e.isLocal = this.equals(LOCAL_LIBRARY);
        // Randomize ID if it is not already set
        // This is a combination of the string hash as well as a 8-bit sequence number
        if (e.id == 0 || e.id == -1) {
            e.id = e.name.hashCode();
            e.id <<= 8;
            while (mediaLookup.containsKey(e.id)) {
                e.id++;
            }
        }
        mediaLookup.put(e.id, e);
        cacheEntry(nameLookup, e.name, e.id);
        cacheEntry(categoryLookup, e.category, e.id);
        if (e.favorite) {
            favorites.add(e.id);
        }
        for (String s : e.keywords) {
            cacheEntry(keywordLookup, s, e.id);
        }
        markDirty();
    }

    public void remove(MediaEntry e) {
        mediaLookup.remove(e.id);
        removeFiles(e);
        cleanup();
        markDirty();
    }

    public void update(MediaEntry e) {
        remove(e);
        add(e);
    }

    private void cacheEntry(Map<String, Set<Long>> cache, String key, long id) {
        Set<Long> ids = cache.get(key);
        if (ids == null) {
            ids = new HashSet<>();
            cache.put(key, ids);
        }
        ids.add(id);
    }

    public static File getMediaLibraryFolder() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.equals("")) {
            userHome = ".";
        }
        File f = new File(new File(userHome, ".jace"), "mediaLibrary");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    // Remove file(s) associated with media entry
    private void removeFiles(MediaEntry e) {
        if (e.files == null) {
            return;
        }
        e.files.stream().forEach((f) -> {
            f.path.delete();
        });
        Utility.gripe("All disk images for " + e.name + " have been deleted.");
    }

    public MediaEntry.MediaFile getCurrentFile(MediaEntry e, boolean isPermanent) {
        if (e == null) {
            return null;
        }
        if (e.files == null || e.files.isEmpty()) {
            e.files = new ArrayList<>();
            getLocalLibrary().add(e);
            getLocalLibrary().createBlankFile(e, "Initial", !isPermanent);
//            getLocalLibrary().downloadImage(e, e.files.get(0), true);
        }
        for (MediaEntry.MediaFile f : e.files) {
            if (f.activeVersion) {
                return f;
            }
        }
        e.files.get(0).activeVersion = true;
        return e.files.get(0);
    }

    public void saveFile(MediaEntry e, InputStream data) {
//        saveFile(getCurrentFile(e, MediaLibrary.CREATE_LOCAL_ON_SAVE), data);
    }

    public void saveFile(MediaFile f, InputStream data) {
        // TODO: If file is temporary but is supposed to be created local when saved, then move the save to a permanent file!!
//        if (f.temporary && MediaLibrary.CREATE_LOCAL_ON_SAVE) {
//            f = convertTemporaryFileToLocal(f);
//        }
        FileOutputStream fos = null;
        f.lastWritten = System.currentTimeMillis();
        try {
            fos = new FileOutputStream(f.path, false);
            byte[] b = new byte[4096];
            while (data.available() > 0) {
                int read = data.read(b);
                fos.write(b, 0, read);
            }
            fos.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
            Utility.gripe("Could not write disk for " + f.path + " -- File not found!");
        } catch (IOException ex) {
            Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
            Utility.gripe("Could not write disk for " + f.path + " -- I/O Exception: " + ex.getMessage());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    boolean disableWrites = false;

    public void readLibraryFromDisk() {
        disableWrites = true;
        ObjectInputStream in = null;
        try {
            File mediaCatalogFile = getMediaLibaryCatalog();
            if (!mediaCatalogFile.exists()) {
                return;
            }
            FileInputStream fileStream = new FileInputStream(mediaCatalogFile);
            in = new ObjectInputStream(fileStream);
            while (fileStream.available() > 0) {
                MediaEntry e = (MediaEntry) in.readObject();
                add(e);
            }
        } catch (IOException | ClassNotFoundException ex) {
            Utility.gripe(ex.getMessage());
            Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            disableWrites = false;
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void writeLibraryToDisk() {
        ObjectOutputStream out = null;
        try {
            File mediaCatalogFile = getMediaLibaryCatalog();
            out = new ObjectOutputStream(new FileOutputStream(mediaCatalogFile));
            for (MediaEntry e : mediaLookup.values()) {
                out.writeObject(e);
            }
            out.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    Thread writerWorker;

    public void markDirty() {
        // We don't really care if a remote library is changing
        if (disableWrites || !this.equals(getLocalLibrary())) {
            return;
        }
        lastDirtyMarker = System.nanoTime();
        if (writerWorker == null || !writerWorker.isAlive()) {
            writerWorker = new Thread(new Runnable() {
                long timeCheck = 0;

                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(DELAY_BEFORE_PERSISTING_LIBRARY / 2);
                            // Wait half the delay before capturing the value
                            // this will help avoid unnecessary delays when the
                            // library is updated a bunch in a small interval of time
                            timeCheck = lastDirtyMarker;
                            Thread.sleep(DELAY_BEFORE_PERSISTING_LIBRARY / 2);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
                            continue;
                        }
                        // If the invalidation time stamp changed again wait longer
                        if (timeCheck != lastDirtyMarker) {
                            continue;
                        }
                        writeLibraryToDisk();
                        // If the invalidation stamp wasn't changed then the worker can exit
                        if (timeCheck == lastDirtyMarker) {
                            break;
                        }
                    }
                }
            });
            writerWorker.start();
        }
    }

    private MediaFile downloadTempCopy(MediaEntry e) {
//        downloadImage(e, getCurrentFile(e, false), isDownloading);
        return getCurrentFile(e, false);
    }
    static boolean isDownloading = false;
//
//    public void downloadImage(final MediaEntry e, final MediaFile target, boolean wait) {
//        isDownloading = true;
//        Utility.runModalProcess("Loading disk image...", () -> {
//            InputStream in = null;
//            try {
//                URI uri = null;
//                try {
//                    uri = new URI(e.source);
//                } catch (URISyntaxException ex) {
//                    File f = new File(e.source);
//                    if (f.exists()) {
//                        uri = f.toURI();
//                    }
//                }
//                if (uri == null) {
//                    Utility.gripe("Unable to resolve path: " + e.source);
//                    return;
//                }
//                in = uri.toURL().openStream();
//                saveFile(target, in);
//            } catch (MalformedURLException ex) {
//                Utility.gripe("Unable to resolve path: " + e.source);
//                Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
//            } catch (IOException ex) {
//                Utility.gripe("Unable to download file: " + ex.getMessage());
//                Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
//            } finally {
//                isDownloading = false;
//                try {
//                    if (in != null) {
//                        in.close();
//                    }
//                } catch (IOException ex) {
//                    Logger.getLogger(MediaCache.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            }
//        });
//        if (wait) {
//            int timeout = 10000;
//            while (timeout > 0 && isDownloading) {
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException ex) {
//                    return;
//                }
//                timeout -= 100;
//            }
//        }
//    }

    private void createBlankFile(MediaEntry e, String label, boolean isTemporary) {
        MediaFile f = new MediaEntry.MediaFile();
        e.files.add(f);
        f.activeVersion = true;
        f.checksum = 0L;
        f.label = label;
        f.lastWritten = System.currentTimeMillis();
        f.temporary = isTemporary;
        // Now generate new file path
        File mediaFolder = isTemporary ? getTempDirectory() : getMediaLibraryFolder();
        String name = e.name.replaceAll("[^0-9A-Za-z]", "");
        String s1 = e.name.length() > 0 ? name.substring(0, 1) : "_";
        String s2 = e.name.length() > 1 ? name.substring(1, 2) : "_";
        File sub1 = new File(mediaFolder, "_" + s1);
        File sub2 = new File(sub1, "_" + s2);
        sub2.mkdirs();
        f.path = new File(sub2, name + "_" + System.nanoTime());
        if (isTemporary) {
            sub1.deleteOnExit();
            sub2.deleteOnExit();
            f.path.deleteOnExit();
        }
    }

    @SuppressWarnings("unused")
    private MediaFile resolveLocalCopy(MediaEntry e) {
        if (!e.isLocal) {
            e = findLocalEntry(e);
        }
        // If this is a local entry, load the current file
        if (e.isLocal) {
            MediaFile f = getCurrentFile(e, true);
            if (f != null && f.path.exists()) {
                return f;
            }
        }
        // If there is no current file, download it
//        if (MediaLibrary.CREATE_LOCAL_ON_LOAD) {
//            getLocalLibrary().add(e);
//            MediaFile f = getCurrentFile(e, true);
//            downloadImage(e, f, true);
//            return f;
//        } else {
            return downloadTempCopy(e);
//        }
        
    }

    public MediaEntry findLocalEntry(MediaEntry e) {
        for (MediaEntry entry : getLocalLibrary().mediaLookup.values()) {
            if (entry.source.equals(e.source))
                return entry;
        }
        return null;
    }

    private File getTempDirectory() {
        String temp = System.getProperty("java.io.tmpdir");
        File tempDir = null;
        if (temp != null) {
            tempDir = new File(temp);
            if (tempDir.exists() && tempDir.isDirectory()) {
                return tempDir;
            }
            tempDir = new File(getMediaLibraryFolder(), "temp");
            tempDir.mkdirs();
            tempDir.deleteOnExit();
        }
        return tempDir;
    }

    @SuppressWarnings("unused")
    private MediaFile convertTemporaryFileToLocal(MediaFile f) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private File getMediaLibaryCatalog() {
        return new File(getMediaLibraryFolder(), "mediacache.db");
    }
}
