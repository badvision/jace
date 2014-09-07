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

import java.awt.Image;
import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;

/**
 *
 * @author brobert
 */
class DiskTransferHandler extends TransferHandler {

    public DiskTransferHandler() {
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }
    MediaEntry currentEntry = null;

    @Override
    protected Transferable createTransferable(JComponent c) {
        JList list = (JList) c;
        MediaEntry entry = (MediaEntry) list.getSelectedValue();
        System.out.println("Transferrable --> " + entry.name);
        currentEntry = entry;
        return new TransferableMediaEntry(entry);
    }

    @Override
    public Image getDragImage() {
        if (currentEntry == null) {
            return super.getDragImage();
        } else {
            return currentEntry.type.diskIcon;
        }
    }
}
