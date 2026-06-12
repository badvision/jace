package jace.hardware;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.Emulator;
import jace.apple2e.MOS65C02;

/**
 * Verifies the cycle-accurate Disk II write path end-to-end using real DOS 3.3 RWTS.
 *
 * The write test loads the actual RWTS binary ($B800-$BFFF), installs a test
 * disk with a known per-sector pattern into a CardDiskII, sets up a proper RWTS
 * IOB pointing at track 3 sector 5 with a write command, then calls $BD00 while
 * ticking the CPU and drive in lockstep.  After the run, the disk is denibblized
 * and checked: only the target sector must contain the replacement pattern.
 *
 * Using real RWTS is essential because it handles address-field detection, sync
 * bytes, and write-gate timing — a custom nibble loop only validates our own
 * feeding code, not the real hardware path.
 */
public class DiskIIWriteTest extends AbstractFXTest {

    private static final int SLOT   = 6;
    private static final int TRACK  = 3;
    private static final int SECTOR = 5;

    // Memory layout for the RWTS test:
    //   $B800-$BFFF : RWTS binary (2048 bytes)
    //   $0500       : IOB (16 bytes, page-aligned so lo byte = $00)
    //   $0600       : DCT (8 bytes)
    //   $0700       : write buffer (256 bytes, filled with $FF)
    //   $0800       : tiny trampoline: JSR $BD00 then BRK
    private static final int RWTS_BASE   = 0xB800;
    private static final int RWTS_ENTRY  = 0xBD00;
    // RWTS entry convention: A = IOB address hi byte, Y = IOB address lo byte, X = slot*16
    // $BD00: STY $48 (lo) ; STA $49 (hi) → zero-page pointer ($48)/$49 = IOB address
    private static final int IOB_ADDR_HI = 0x05;   // A on entry: IOB high byte
    private static final int IOB_ADDR_LO = 0x00;   // Y on entry: IOB low byte
    private static final int IOB_ADDR    = (IOB_ADDR_HI << 8) | IOB_ADDR_LO;
    private static final int DCT_ADDR    = 0x0600;
    private static final int BUF_ADDR    = 0x0700;
    private static final int TRAMPOLINE  = 0x0800;

    // RWTS IOB command byte values (from DOS 3.3 RWTS disassembly):
    //   $BDB5: ROR on command byte → bit 0 into Carry; PHP saves it.
    //   $BDB7: BCS $BDBC → if Carry SET (bit0=1), skips JSR $B800 (encoder) = READ path.
    //   $BE33: BCC $BE51 → if Carry CLEAR (bit0=0), branches to $B82A (write-to-disk) = WRITE path.
    //   Command 2 (0x02, bit 0 = 0): Carry clear → $B800 (encode) then $B82A (write to disk) = WRITE
    //   Command 1 (0x01, bit 0 = 1): Carry set → skip encode, $B8DC (read from disk) = READ
    private static final int CMD_WRITE = 0x02;

    private FloppyDisk disk;
    private CardDiskII diskCard;
    private File       tempDisk;
    private byte[]     originalDsk;

    @Before
    public void setUp() throws IOException {
        originalDsk = buildPatternDsk();

        tempDisk = File.createTempFile("diskii_write_test", ".dsk");
        tempDisk.deleteOnExit();
        Files.write(tempDisk.toPath(), originalDsk);

        diskCard = new CardDiskII();
        diskCard.drive1.insertDisk(tempDisk);

        // Park the head on the target track so RWTS won't need to seek.
        diskCard.drive1.halfTrack      = TRACK * 2;
        diskCard.drive1.trackStartOffset = TRACK * FloppyDisk.TRACK_NIBBLE_LENGTH;
        diskCard.drive1.nibbleOffset   = 0;
        diskCard.drive1.tickCount      = 0;

        Emulator.withMemory(mem -> mem.addCard(diskCard, SLOT));

        disk = diskCard.drive1.disk;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Round-trip sanity check: nibblize then denibblize must produce the
     * original DSK bytes verbatim.
     */
    @Test
    public void roundTripNibblizeIsLossless() throws IOException {
        byte[] roundTripped = denibblizeToDsk(disk);
        assertArrayEquals("Nibblize/denibblize round-trip must be lossless",
                originalDsk, roundTripped);
    }

    /**
     * Real-RWTS write test.
     *
     * Loads the extracted DOS 3.3 RWTS binary into $B800, builds an IOB for a
     * write to T3/S5, calls $BD00, ticks the CPU and the Disk II drive in
     * lockstep until RWTS returns, then verifies only the target sector changed.
     */
    @Test
    public void rwtsWriteUpdatesTargetSectorOnly() throws Exception {
        byte[] rwtsBytes = loadResource("/jace/hardware/rwts.bin");
        assertEquals("RWTS binary must be exactly 2048 bytes", 2048, rwtsBytes.length);

        // --- Load RWTS into emulator RAM at $B800 ---
        Emulator.withMemory(mem -> {
            for (int i = 0; i < rwtsBytes.length; i++) {
                mem.write(RWTS_BASE + i, rwtsBytes[i], false, false);
            }
        });

        // --- Replacement sector data ---
        byte[] replacement = new byte[256];
        java.util.Arrays.fill(replacement, (byte) 0xFF);

        // --- Write buffer: copy replacement into $0700 ---
        Emulator.withMemory(mem -> {
            for (int i = 0; i < 256; i++) {
                mem.write(BUF_ADDR + i, replacement[i], false, false);
            }
        });

        // --- DCT (Device Characteristics Table) at $0600 ---
        // +0: device type (0)
        // +1: flags (0)
        // +2: slot * 16 ($60)
        // +3: current track (pre-set to TRACK so no seek required)
        // +4: reserved (0)
        // +5: drive (1)
        final int slotTimes16 = SLOT * 16;
        Emulator.withMemory(mem -> {
            mem.write(DCT_ADDR + 0, (byte) 0x00, false, false);
            mem.write(DCT_ADDR + 1, (byte) 0x00, false, false);
            mem.write(DCT_ADDR + 2, (byte) slotTimes16, false, false);
            mem.write(DCT_ADDR + 3, (byte) TRACK, false, false);
            mem.write(DCT_ADDR + 4, (byte) 0x00, false, false);
            mem.write(DCT_ADDR + 5, (byte) 0x01, false, false);
        });

        // --- Initialise RWTS per-slot track state ---
        // RWTS's seek routine ($BE5A→$BE8B) uses two per-slot tables indexed by Y=slot=6:
        //   $04F8+6 = $04FE : "drive 1" current-track table (for drive 1, BIT $35 → N=0)
        //   $0478+6 = $047E : "drive 2" current-track table (for drive 2, BIT $35 → N=1)
        // For drive 1 (our case):
        //   $BE77: LDA abs,Y $04F8 → reads $04FE
        //   $BE7A: STA abs $0478  → copies to absolute $0478
        //   then JMP $B9A0 with A = desired-track
        //   $B9A4: CMP abs $0478 → desired vs old $04FE; BEQ → skip seek if equal
        // After address-field found ($BDED):
        //   CPY abs $0478 → found-track(3) vs $0478(still=$04FE); BEQ → proceed
        // Seeding $04FE = TRACK tells RWTS the head is already on track 3, so the
        // seek no-ops immediately ($B9A7: BEQ $B9FC → RTS) and address-field search
        // starts immediately at nibble offset 0 of the pre-positioned track.
        Emulator.withMemory(mem -> {
            mem.write(0x04FE, (byte) TRACK, false, false);  // drive-1 per-slot track for slot 6
        });

        // --- IOB (I/O Parameter Block) at $0500 ---
        // +0: type (1)
        // +1: slot * 16
        // +2: drive (1)
        // +3: volume (0 = accept any)
        // +4: track
        // +5: sector
        // +6,7: DCT address lo/hi
        // +8,9: buffer address lo/hi
        // +A,B: byte count (1 page = 1)
        // +C: command (2 = write)
        // +D: error return (written by RWTS)
        // +E: volume found (written by RWTS)
        // +F: slot * 16 copy
        final int dctLo = DCT_ADDR & 0xFF;
        final int dctHi = (DCT_ADDR >> 8) & 0xFF;
        final int bufLo = BUF_ADDR & 0xFF;
        final int bufHi = (BUF_ADDR >> 8) & 0xFF;
        Emulator.withMemory(mem -> {
            mem.write(IOB_ADDR + 0x0, (byte) 0x01, false, false);   // type
            mem.write(IOB_ADDR + 0x1, (byte) slotTimes16, false, false);
            mem.write(IOB_ADDR + 0x2, (byte) 0x01, false, false);   // drive 1
            mem.write(IOB_ADDR + 0x3, (byte) 0x00, false, false);   // any volume
            mem.write(IOB_ADDR + 0x4, (byte) TRACK, false, false);
            mem.write(IOB_ADDR + 0x5, (byte) SECTOR, false, false);
            mem.write(IOB_ADDR + 0x6, (byte) dctLo, false, false);
            mem.write(IOB_ADDR + 0x7, (byte) dctHi, false, false);
            mem.write(IOB_ADDR + 0x8, (byte) bufLo, false, false);
            mem.write(IOB_ADDR + 0x9, (byte) bufHi, false, false);
            mem.write(IOB_ADDR + 0xA, (byte) 0x01, false, false);   // 1 page
            mem.write(IOB_ADDR + 0xB, (byte) 0x00, false, false);
            mem.write(IOB_ADDR + 0xC, (byte) CMD_WRITE, false, false);
            mem.write(IOB_ADDR + 0xD, (byte) 0x00, false, false);
            mem.write(IOB_ADDR + 0xE, (byte) 0x00, false, false);
            mem.write(IOB_ADDR + 0xF, (byte) slotTimes16, false, false);
        });

        // --- Trampoline at $0800: load registers then JSR $BD00 / BRK ---
        // RWTS entry convention ($BD00): A = IOB hi byte, Y = IOB lo byte, X = slot*16
        // $BD00: STY $48 (lo) ; STA $49 (hi) → forms zero-page 16-bit pointer to IOB
        // We embed the register loads in the trampoline so RWTS sees correct A/Y/X values.
        final int iobHi = IOB_ADDR_HI;
        final int iobLo = IOB_ADDR_LO;
        Emulator.withMemory(mem -> {
            int addr = TRAMPOLINE;
            // LDA #IOB_HI   (A = IOB high byte)
            mem.write(addr++, (byte) 0xA9, false, false);
            mem.write(addr++, (byte) iobHi, false, false);
            // LDY #IOB_LO   (Y = IOB low byte)
            mem.write(addr++, (byte) 0xA0, false, false);
            mem.write(addr++, (byte) iobLo, false, false);
            // LDX #SLOT*16  (X = slot*16)
            mem.write(addr++, (byte) 0xA2, false, false);
            mem.write(addr++, (byte) (SLOT * 16), false, false);
            // JSR $BD00
            mem.write(addr++, (byte) 0x20, false, false);
            mem.write(addr++, (byte) (RWTS_ENTRY & 0xFF), false, false);
            mem.write(addr++, (byte) ((RWTS_ENTRY >> 8) & 0xFF), false, false);
            // BRK (signals completion — we detect PC here before it fires)
            mem.write(addr, (byte) 0x00, false, false);
        });

        // --- Configure CPU and drive, then run ---
        MOS65C02 cpu = (MOS65C02) Emulator.withComputer(c -> c.getCpu(), null);
        cpu.setProgramCounter(TRAMPOLINE);
        // Registers will be loaded by the trampoline code above.

        // Drive must be on and in read mode initially; RWTS switches to write mode itself.
        diskCard.drive1.driveOn   = true;
        diskCard.drive1.writeMode = false;
        diskCard.drive1.nibbleOffset = 0;
        diskCard.drive1.tickCount    = 0;

        // Do NOT call cpu.resume() — the background Motherboard thread would race with our
        // manual tick loop.  cpu.tick() calls executeOpcode() directly and is safe to
        // call from the test thread without resuming the full emulator loop.

        // Budget 8 full track revolutions; RWTS needs at most one revolution to find
        // the address field plus a few hundred instructions to encode and write.
        int maxTicks = FloppyDisk.TRACK_NIBBLE_LENGTH * DiskIIDrive.TICKS_PER_NIBBLE * 8;

        int completedAt = -1;
        boolean sawWriteMode = false;
        // RWTS exit: CLC ($BE46) = success, SEC = error.  Carry is authoritative;
        // IOB+$0D may hold a stale disk-read byte on the success path.
        boolean[] rwtsCarryOnReturn = {false};
        for (int t = 0; t < maxTicks; t++) {
            cpu.setWaitCycles(0);
            cpu.tick();
            int driveTicks = cpu.getWaitCycles() + 1;  // +1 = this cycle itself
            for (int d = 0; d < driveTicks; d++) {
                diskCard.drive1.tick();
            }
            if (diskCard.drive1.writeMode) sawWriteMode = true;
            int pc = cpu.getProgramCounter();
            // Trampoline layout: LDA(2) + LDY(2) + LDX(2) + JSR(3) + BRK(1) = 10 bytes
            // After JSR $BD00 returns, PC = TRAMPOLINE+9 (the BRK sentinel).
            if (pc == TRAMPOLINE + 9) {
                completedAt = t;
                rwtsCarryOnReturn[0] = cpu.isCarryFlag();
                break;
            }
        }
        cpu.suspend();

        assertTrue("RWTS write never completed (hung in disk seek/read/write loop?)",
                completedAt >= 0);
        assertTrue("RWTS completed but write mode was never engaged", sawWriteMode);

        // RWTS signals error with carry set on return; carry clear = success.
        assertFalse("RWTS returned carry set (error)", rwtsCarryOnReturn[0]);

        // Flush the in-memory nibbles to the DSK file.
        diskCard.drive1.disk.updateTrack(TRACK);

        // Denibblize and verify.
        byte[] updatedDsk = denibblizeToDsk(disk);
        int physSector = disk.currentSectorOrder[SECTOR];

        for (int t = 0; t < FloppyDisk.TRACK_COUNT; t++) {
            for (int s = 0; s < FloppyDisk.SECTOR_COUNT; s++) {
                int offset = (t * FloppyDisk.SECTOR_COUNT + s) * 256;
                byte expected = (t == TRACK && s == physSector)
                        ? (byte) 0xFF
                        : sectorFill(t, s);
                for (int b = 0; b < 256; b++) {
                    if (updatedDsk[offset + b] != expected) {
                        fail(String.format(
                                "T%d S%d byte %d: expected 0x%02X got 0x%02X",
                                t, s, b,
                                expected & 0xFF,
                                updatedDsk[offset + b] & 0xFF));
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] loadResource(String path) throws IOException {
        try (InputStream in = DiskIIWriteTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Resource not found: " + path);
            return in.readAllBytes();
        }
    }

    /** Fill byte for track T, physical sector S in the original pattern DSK. */
    private static byte sectorFill(int track, int sector) {
        return (byte) ((track * FloppyDisk.SECTOR_COUNT + sector) & 0xFF);
    }

    /** Build a 143360-byte DSK where every sector has a unique single-byte fill. */
    private static byte[] buildPatternDsk() {
        byte[] dsk = new byte[FloppyDisk.DISK_PLAIN_LENGTH];
        for (int t = 0; t < FloppyDisk.TRACK_COUNT; t++) {
            for (int s = 0; s < FloppyDisk.SECTOR_COUNT; s++) {
                byte fill = sectorFill(t, s);
                int base = (t * FloppyDisk.SECTOR_COUNT + s) * 256;
                java.util.Arrays.fill(dsk, base, base + 256, fill);
            }
        }
        return dsk;
    }

    /**
     * Denibblize the entire in-memory FloppyDisk nibble buffer back to a
     * 143360-byte DSK image by calling updateDenibblizedTrack on every track.
     */
    private static byte[] denibblizeToDsk(FloppyDisk src) throws IOException {
        File tmp = File.createTempFile("roundtrip", ".dsk");
        tmp.deleteOnExit();
        Files.write(tmp.toPath(), new byte[FloppyDisk.DISK_PLAIN_LENGTH]);

        FloppyDisk copy = new FloppyDisk();
        copy.nibbles           = src.nibbles.clone();
        copy.currentSectorOrder = src.currentSectorOrder;
        copy.isNibblizedImage  = false;
        copy.headerLength      = 0;
        copy.diskPath          = tmp;

        for (int t = 0; t < FloppyDisk.TRACK_COUNT; t++) {
            copy.updateDenibblizedTrack(t);
        }
        return Files.readAllBytes(tmp.toPath());
    }
}
