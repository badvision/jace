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

import jace.Emulator;
import jace.config.ConfigurableField;
import jace.config.Reconfigurable;
import jace.core.Card;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JPanel;

/**
 * Main entry point of the media library Serves as an interface between the UI
 * and the media cache Also provides a touchpoint for emulator configuration
 * options
 *
 * @author brobert
 */
public class MediaLibrary implements Reconfigurable {

    public static MediaLibrary instance;

    public static MediaLibrary getInstance() {
        if (instance == null) {
            instance = new MediaLibrary();
        }
        return instance;
    }
    //--------------------------------------------------------------------------
    @ConfigurableField(category = "Library", defaultValue = "true", name = "Auto-add", description = "Automatically download and save local copies of disks when played.")
    public static boolean CREATE_LOCAL_ON_LOAD = true;
    @ConfigurableField(category = "Library", defaultValue = "true", name = "Keep local copy", description = "Automatically download and save local copies of disks when written.")
    public static boolean CREATE_LOCAL_ON_SAVE = true;

    @Override
    public String getName() {
        return "Media Library";
    }

    @Override
    public String getShortName() {
        return "media";
    }

    @Override
    public void reconfigure() {
        rebuildDriveList();
    }
    //--------------------------------------------------------------------------
    MediaManagerUI userInterface;

    public JPanel buildUserInterface() {
        userInterface = new MediaManagerUI();
        rebuildDriveList();
        rebuildTabs();
        return userInterface;
    }

    public void rebuildDriveList() {
        if (userInterface == null) {
            return;
        }
        userInterface.Drives.removeAll();
        for (Card card : Emulator.computer.memory.getAllCards()) {
            if (card == null || !(card instanceof MediaConsumerParent)) {
                continue;
            }
            MediaConsumerParent parent = (MediaConsumerParent) card;
            GridBagLayout layout = (GridBagLayout) userInterface.Drives.getLayout();
            GridBagConstraints c = new GridBagConstraints();            
            for (MediaConsumer consumer : parent.getConsumers()) {
                DriveIcon drive = new DriveIcon(consumer);
                drive.setSize(100, 70);
                drive.setPreferredSize(new Dimension(100, 70));
                c.gridwidth = GridBagConstraints.REMAINDER;
                layout.setConstraints(drive, c);
                userInterface.Drives.add(drive);
            }
        }
        userInterface.Drives.revalidate();
    }

    private void rebuildTabs() {
        userInterface.Libraries.removeAll();
        MediaCache localLibraryCache = MediaCache.getLocalLibrary();
        MediaLibraryUI localLibrary = new MediaLibraryUI();
        localLibrary.setName("Local");
        localLibrary.setCache(localLibraryCache);
        localLibrary.setLocal(true);
        userInterface.Libraries.add(localLibrary);
        userInterface.Libraries.revalidate();
    }

    public MediaEditUI buildEditInstance(MediaLibraryUI library, MediaEntry entry) {
        MediaEditUI form = new MediaEditUI();
        if (entry == null) {
            // create form
            form.setCreate(true);
            form.populate(new MediaEntry());
        } else {
            form.setCreate(false);
            form.populate(entry);
        }
        form.setParentLibary(library);        
        return form;
    }
}