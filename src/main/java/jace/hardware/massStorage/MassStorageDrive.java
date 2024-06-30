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

package jace.hardware.massStorage;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.library.MediaConsumer;
import jace.library.MediaEntry;
import jace.library.MediaEntry.MediaFile;
import javafx.scene.control.Label;

/**
 *
 * @author brobert
 */
public class MassStorageDrive implements MediaConsumer {
    IDisk disk = null;
    Optional<Label> icon = null;
    
    @Override
    public Optional<Label> getIcon() {
        return icon;
    }

    /**
     *
     * @param i
     */
    @Override
    public void setIcon(Optional<Label> i) {
        icon = i;
    }

    MediaEntry currentEntry;
    MediaFile currentFile;
    
    /**
     *
     * @param e
     * @param f
     * @throws IOException
     */
    @Override
    public void insertMedia(MediaEntry e, MediaFile f) throws IOException {
        eject();
        currentEntry = e;
        currentFile = f;
        disk= readDisk(currentFile.path);
        if (postInsertAction != null) {
            postInsertAction.run();
        }
    }

    Runnable postInsertAction = null;
    public void onInsert(Runnable r) {
        postInsertAction = r;
    }
    
    /**
     *
     * @return
     */
    @Override
    public MediaEntry getMediaEntry() {
        return currentEntry;
    }

    /**
     *
     * @return
     */
    @Override
    public MediaFile getMediaFile() {
        return currentFile;
    }

    /**
     *
     * @param e
     * @param f
     * @return
     */
    @Override
    public boolean isAccepted(MediaEntry e, MediaFile f) {
        return e.type.isProdosOrdered;
    }

    /**
     *
     */
    @Override
    public void eject() {
        if (disk != null) {
            disk.eject();
            disk = null;
        }
    }
    
     private IDisk readDisk(File f) {
        if (f.isFile()) {
            return new LargeDisk(f);
        } else if (f.isDirectory()) {
            try {
                return new ProdosVirtualDisk(f);
            } catch (IOException ex) {
                System.out.println("Unable to open virtual disk: " + ex.getMessage());
                Logger.getLogger(CardMassStorage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }    

   public IDisk getCurrentDisk() {
        return disk;
    }
}