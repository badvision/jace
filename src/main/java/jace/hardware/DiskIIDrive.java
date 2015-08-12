/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
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
package jace.hardware;

import jace.core.Computer;
import jace.library.MediaConsumer;
import jace.library.MediaEntry;
import jace.library.MediaEntry.MediaFile;
import jace.state.StateManager;
import jace.state.Stateful;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import javafx.scene.control.Label;

/**
 * This implements the mechanical part of the disk drive and tracks changes to
 * disk images. The actual handling of disk images is performed in the
 * FloppyDisk class. The apple interface card portion is managed in the
 * CardDiskII class. Useful reading:
 * http://www.doc.ic.ac.uk/~ih/doc/stepper/others/example3/diskii_specs.html
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
public class DiskIIDrive implements MediaConsumer {
    Computer computer;

    public DiskIIDrive(Computer computer) {
        this.computer = computer;
    }
    
    FloppyDisk disk;
    // Number of milliseconds to wait between last write and update to disk image
    public static long WRITE_UPDATE_DELAY = 1000;
    // Flag to halt if any writes to floopy occur when updating physical disk image
    boolean diskUpdatePending = false;
    // Last time of write operation
    long lastWriteTime;
    // Managed thread to update disk image in background
    Thread writerThread;
    private final byte[][] driveHeadStepDelta = {
        {0, 0, 1, 1, 0, 0, 1, 1, -1, -1, 0, 0, -1, -1, 0, 0}, // phase 0
        {0, -1, 0, -1, 1, 0, 1, 0, 0, -1, 0, -1, 1, 0, 1, 0}, // phase 1
        {0, 0, -1, -1, 0, 0, -1, -1, 1, 1, 0, 0, 1, 1, 0, 0}, // phase 2
        {0, 1, 0, 1, -1, 0, -1, 0, 0, 1, 0, 1, -1, 0, -1, 0}}; // phase 3
    @Stateful
    public int halfTrack;
    @Stateful
    public int trackStartOffset;
    @Stateful
    public int nibbleOffset;
    @Stateful
    public boolean writeMode;
    @Stateful
    public boolean driveOn;
    @Stateful
    public int magnets;
    @Stateful
    public byte latch;
    @Stateful
    public int spinCount;
    Set<Integer> dirtyTracks;

    public void reset() {
        driveOn = false;
        magnets = 0;
        dirtyTracks = new HashSet<>();
        diskUpdatePending = false;
    }

    void step(int register) {
        // switch drive head stepper motor magnets on/off
        int magnet = (register >> 1) & 0x3;
        magnets &= ~(1 << magnet);
        magnets |= ((register & 0x1) << magnet);

        // step the drive head according to stepper magnet changes
        if (driveOn) {
            int delta = driveHeadStepDelta[halfTrack & 0x3][magnets];
            if (delta != 0) {
                int newHalfTrack = halfTrack + delta;
                if (newHalfTrack < 0) {
                    newHalfTrack = 0;
                } else if (newHalfTrack > FloppyDisk.HALF_TRACK_COUNT) {
                    newHalfTrack = FloppyDisk.HALF_TRACK_COUNT;
                }
                if (newHalfTrack != halfTrack) {
                    halfTrack = newHalfTrack;
                    trackStartOffset = (halfTrack >> 1) * FloppyDisk.TRACK_NIBBLE_LENGTH;
                    if (trackStartOffset >= FloppyDisk.DISK_NIBBLE_LENGTH) {
                        trackStartOffset = FloppyDisk.DISK_NIBBLE_LENGTH - FloppyDisk.TRACK_NIBBLE_LENGTH;
                    }
                    nibbleOffset = 0;

                    //System.out.printf("new half track %d\n", currentHalfTrack);
                }
            }
        }
    }

    void setOn(boolean b) {
        driveOn = b;
    }

    boolean isOn() {
        return driveOn;
    }

    byte readLatch() {
        byte result = 0x07f;
        if (!writeMode) {
            spinCount = (spinCount + 1) & 0x0F;
            if (spinCount > 0) {
                if (disk != null) {
                    result = disk.nibbles[trackStartOffset + nibbleOffset];
                    if (isOn()) {
                        nibbleOffset++;
                        if (nibbleOffset >= FloppyDisk.TRACK_NIBBLE_LENGTH) {
                            nibbleOffset = 0;
                        }
                    }
                } else {
                    result = (byte) 0x0ff;
                }
            }
        } else {
            spinCount = (spinCount + 1) & 0x0F;
            if (spinCount > 0) {
                result = (byte) 0x080;
            }
        }
        return result;
    }

    void write() {
        if (writeMode) {
            while (diskUpdatePending) {
                // If another thread requested writes to block (e.g. because of disk activity), wait for it to finish!
                LockSupport.parkNanos(1000);
            }
            if (disk != null) {
                // Do nothing if write-protection is enabled!
                if (getMediaEntry() == null || !getMediaEntry().writeProtected) {
                    dirtyTracks.add(trackStartOffset / FloppyDisk.TRACK_NIBBLE_LENGTH);
                    disk.nibbles[trackStartOffset + nibbleOffset++] = latch;
                    triggerDiskUpdate();
                    StateManager.markDirtyValue(disk.nibbles, computer);
                }
            }

            if (nibbleOffset >= FloppyDisk.TRACK_NIBBLE_LENGTH) {
                nibbleOffset = 0;
            }
        }
    }

    void setLatchValue(byte value) {
        if (writeMode) {
            latch = value;
        } else {
            latch = (byte) 0xFF;
        }
    }

    void setReadMode() {
        writeMode = false;
    }

    void setWriteMode() {
        writeMode = true;
    }

    private void updateDisk() {

        // Signal disk update is underway
        diskUpdatePending = true;
        // Update all tracks as necessary
        if (disk != null) {
            dirtyTracks.stream().forEach((track) -> {
                disk.updateTrack(track);
            });
        }
        // Empty out dirty list
        dirtyTracks.clear();
        // Signal disk update is completed
        diskUpdatePending = false;
    }

    private void triggerDiskUpdate() {
        lastWriteTime = System.currentTimeMillis();
        if (writerThread == null || !writerThread.isAlive()) {
            writerThread = new Thread(() -> {
                long diff;
                // Wait until there have been no virtual writes for specified delay time
                while ((diff = System.currentTimeMillis() - lastWriteTime) < WRITE_UPDATE_DELAY) {
                    // Sleep for difference of time
                    LockSupport.parkNanos(diff * 1000);
                    // Note: In the meantime, there could have been another disk write,
                    // in which case this loop will repeat again as needed.
                }
                updateDisk();
            });
            writerThread.start();
        }
    }

    void insertDisk(File diskPath) throws IOException {
        disk = new FloppyDisk(diskPath, computer);
        System.out.println("Inserting "+diskPath.getPath()+" into "+getIcon().getText());
        dirtyTracks = new HashSet<>();
        // Emulator state has changed significantly, reset state manager
        StateManager.getInstance(computer).invalidate();
    }
    private Label icon;

    @Override
    public Label getIcon() {
        return icon;
    }

    @Override
    public void setIcon(Label i) {
        icon = i;
    }
    private MediaEntry currentMediaEntry;
    private MediaFile currentMediaFile;

    @Override
    public void eject() {
        if (disk == null) {
            return;
        }
        waitForPendingWrites();
        disk = null;
        dirtyTracks = new HashSet<>();
        // Emulator state has changed significantly, reset state manager
        StateManager.getInstance(computer).invalidate();
    }

    @Override
    public void insertMedia(MediaEntry e, MediaFile f) throws IOException {
        if (!isAccepted(e, f)) {
            return;
        }
        eject();
        insertDisk(f.path);
        currentMediaEntry = e;
        currentMediaFile = f;
    }

    @Override
    public MediaEntry getMediaEntry() {
        return currentMediaEntry;
    }

    @Override
    public MediaFile getMediaFile() {
        return currentMediaFile;
    }

    @Override
    public boolean isAccepted(MediaEntry e, MediaFile f) {
        if (f == null) return false;
//        System.out.println("Type is accepted: "+f.path+"; "+e.type.toString()+": "+e.type.is140kb);
        return e.type.is140kb;
    }

    private void waitForPendingWrites() {
        while (diskUpdatePending || !dirtyTracks.isEmpty()) {
            // If the current disk has unsaved changes, wait!!!
            LockSupport.parkNanos(1000);
        }
    }
}