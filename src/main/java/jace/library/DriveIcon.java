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

import jace.library.MediaEntry.MediaFile;
import jace.ui.OutlinedLabel;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;

/**
 *
 * @author brobert
 */
public class DriveIcon extends OutlinedLabel {

    final MediaConsumer target;

    public DriveIcon(MediaConsumer mediaTarget) {
        super(mediaTarget.getIcon());
        target = mediaTarget;
        setText(target.getIcon().getDescription());
        setDropTarget(new DropTarget() {            
            @Override
            public synchronized void drop(DropTargetDropEvent dtde) {
                try {
                    String data = dtde.getTransferable().getTransferData(DataFlavor.stringFlavor).toString();
                    long id = Long.parseLong(data);
                    MediaEntry e = MediaCache.getLocalLibrary().mediaLookup.get(id);
                    // Once other libraries are implemented, make sure to alias locally!
//                    MediaEntry entry = MediaCache.getLocalLibrary().findLocalEntry(e);
                    System.out.println("Inserting "+e.name+" into "+target.toString());
                    MediaFile f = MediaCache.getLocalLibrary().getCurrentFile(e, true);
                    target.isAccepted(e, f);
                    target.insertMedia(e, f);
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                    dtde.rejectDrop();
                }
            }
            
        });
    }
}
