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

package jace.hardware;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import jace.Emulator;
import jace.EmulatorUILogic;
import jace.library.MediaConsumer;
import jace.library.MediaEntry;
import jace.library.MediaEntry.MediaFile;
import jace.state.StateManager;
import jace.state.Stateful;
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

    public DiskIIDrive() {
    }
    
    public boolean DEBUG = false;

    FloppyDisk disk;
    // Number of milliseconds to wait between last write and update to disk image
    public static long WRITE_UPDATE_DELAY = 1000;
    // Flag to halt if any writes to floopy occur when updating physical disk image
    AtomicBoolean diskUpdatePending = new AtomicBoolean();
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
        diskUpdatePending.set(false);
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

                    if (DEBUG) {
                        System.out.printf("step %d, new half track %d\n", register, halfTrack);
                    }
                }
            }
        }
    }

    void setOn(boolean b) {
        if (DEBUG) {
            System.out.println("Drive setOn: "+b);
        }
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
            while (diskUpdatePending.get()) {
                // If another thread requested writes to block (e.g. because of disk activity), wait for it to finish!
                Thread.onSpinWait();
            }
            // Holding the lock should block any other threads from writing to disk
            synchronized (diskUpdatePending) {
                if (disk != null) {
                    // Do nothing if write-protection is enabled!
                    if (getMediaEntry() == null || !getMediaEntry().writeProtected) {
                        dirtyTracks.add(trackStartOffset / FloppyDisk.TRACK_NIBBLE_LENGTH);
                        disk.nibbles[trackStartOffset + nibbleOffset++] = latch;
                        triggerDiskUpdate();
                        StateManager.markDirtyValue(disk.nibbles);
                    }
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
        synchronized (diskUpdatePending) {
            diskUpdatePending.set(true);
            // Update all tracks as necessary
            if (disk != null) {
                dirtyTracks.stream().forEach((track) -> {
                    disk.updateTrack(track);
                });
            }
            // Empty out dirty list
            dirtyTracks.clear();
        }
        // Signal disk update is completed
        diskUpdatePending.set(false);
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
        if (DEBUG) {
            System.out.println("inserting disk " + diskPath.getAbsolutePath() + " into drive");
        }
        disk = new FloppyDisk(diskPath);
        dirtyTracks = new HashSet<>();
        // Emulator state has changed significantly, reset state manager
        Emulator.withComputer(c->StateManager.getInstance(c).invalidate());
    }
    private Optional<Label> icon;

    @Override
    public Optional<Label> getIcon() {
        return icon;
    }

    @Override
    public void setIcon(Optional<Label> i) {
        icon = i;
    }
    
    // Optionals make some things easier, but they slow down things considerably when called a lot
    // This reduces the number of Optional checks when rapidly accessing the disk drive.
    long lastAdded = 0;
    public void addIndicator() {
        long now = System.currentTimeMillis();
        if (lastAdded == 0 || now - lastAdded >= 500) {
            EmulatorUILogic.addIndicator(this, icon.get());
            lastAdded = now;
        }
    }

    public void removeIndicator() {
        if (lastAdded > 0) {
            EmulatorUILogic.removeIndicator(this, icon.get());
            lastAdded = 0;
        }
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
        Emulator.withComputer(c->StateManager.getInstance(c).invalidate());
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
        if (DEBUG) {
            System.out.println("Type is accepted: "+f.path+"; "+e.type.toString()+": "+e.type.is140kb);
        }
        return e.type.is140kb;
    }

    private void waitForPendingWrites() {
        while (diskUpdatePending.get()) {
            // If the current disk has unsaved changes, wait!!!
            Thread.onSpinWait();
        }
    }
}