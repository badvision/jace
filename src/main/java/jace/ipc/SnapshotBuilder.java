package jace.ipc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jace.Emulator;
import jace.apple2e.RAM128k;
import jace.core.PagedMemory;
import jace.core.RAM;

/**
 * Builds the binary snapshot payload for a K2C_SEND_SNAPSHOT response.
 *
 * The snapshot is a fixed 1,327,104-byte (IpcConstants.SNAP_TOTAL_SIZE) little-endian blob
 * that Cyrene reads to inspect the full Apple IIe state.
 *
 * One ByteBuffer is pre-allocated and reused across calls to avoid repeated
 * 1.27 MB heap allocation on every snapshot request.
 */
public class SnapshotBuilder {

    private final ByteBuffer buf;

    public SnapshotBuilder() {
        buf = ByteBuffer.allocate(IpcConstants.SNAP_TOTAL_SIZE);
        buf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Build a complete snapshot blob.
     *
     * @param nextOp   The next/current CPU operation (may be null — fields zero-filled)
     * @param stopCond Stop condition code (e.g. IpcConstants.STOP_BREAKPOINT)
     * @param bpId     Breakpoint identifier (meaningful when stopCond == STOP_BREAKPOINT)
     * @return         Byte array of exactly IpcConstants.SNAP_TOTAL_SIZE bytes
     */
    public byte[] build(CyreneOperation nextOp, byte stopCond, int bpId) {
        buf.clear();

        // Zero-fill the entire buffer first so all unspecified regions are 0x00.
        while (buf.hasRemaining()) {
            buf.put((byte) 0);
        }
        buf.rewind();

        // --- Operation data block (bytes 0..58) ---
        writeOperationBlock(nextOp);

        // --- Stop condition (byte 100) and breakpoint id (bytes 101..104) ---
        buf.position(IpcConstants.SNAP_OFF_STOP_COND);
        buf.put(stopCond);

        buf.position(IpcConstants.SNAP_OFF_BREAKPOINT);
        buf.putInt(bpId);

        // --- DOC registers (bytes 512..737) ---
        // The only non-zero byte in this region: offset 514 = SNAP_OFF_DOC_REG + 2 = silence byte.
        buf.position(IpcConstants.SNAP_OFF_DOC_REG + 2);
        buf.put(IpcConstants.DOC_SILENCE_BYTE);

        // --- Battery RAM (bytes 768..1023): zero-fill (already done) ---

        // --- RAM banks ---
        writeRamBanks();

        // Return the fully assembled snapshot as a byte array.
        return buf.array().clone();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Encode the CyreneOperation into the 59-byte op block at SNAP_OFF_NEXT_OP.
     * All unused fields within the block are already zero (buffer was cleared above).
     */
    private void writeOperationBlock(CyreneOperation op) {
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP);

        if (op == null) {
            // Leave entire op block zero-filled except the fixed discriminator bytes.
            buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_ROM_VERSION);
            buf.put(IpcConstants.ROM_VERSION_APPLE_IIE);
            buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_RAM_BANKS);
            buf.put(IpcConstants.RAM_BANKS_APPLE_IIE);
            return;
        }

        // A, X, Y — 16-bit LE (upper byte is 0 for 8-bit 65C02)
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_A);
        buf.putShort((short) (op.a & 0xFFFF));

        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_X);
        buf.putShort((short) (op.x & 0xFFFF));

        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_Y);
        buf.putShort((short) (op.y & 0xFFFF));

        // PC — 16-bit LE
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_PC);
        buf.putShort((short) (op.pc & 0xFFFF));

        // PBR / DBR — 0x00 for IIe (no banking)
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_PBR);
        buf.put((byte) 0x00);
        buf.put((byte) 0x00);  // DBR follows immediately

        // Stack — 16-bit LE; upper byte is 0x01 (stack lives at $01xx)
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_STACK);
        buf.put((byte) (op.stack & 0xFF));
        buf.put((byte) 0x01);

        // Direct Page — 0x0000 for IIe
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_DIRECT_PAGE);
        buf.putShort((short) 0x0000);

        // Status flags
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_STATUS);
        buf.put(op.flags);

        // Opcode + three operand bytes
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_OPCODE);
        buf.put(op.opcode);
        buf.put(op.op1);
        buf.put(op.op2);
        buf.put(op.op3);

        // Read/write/jump addresses — 24-bit stored in 3 bytes LE
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_READ_ADDR);
        put24(op.readAddr);

        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_WRITE_ADDR);
        put24(op.writeAddr);

        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_JUMP_ADDR);
        put24(op.jumpAddr);

        // Read/write values — 16-bit LE
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_READ_VAL);
        buf.putShort((short) (op.readVal & 0xFFFF));

        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_WRITE_VAL);
        buf.putShort((short) (op.writeVal & 0xFFFF));

        // Operation flags
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_FLAGS);
        buf.put(op.opFlags);

        // ROM version and RAM banks discriminators
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_ROM_VERSION);
        buf.put(IpcConstants.ROM_VERSION_APPLE_IIE);

        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_RAM_BANKS);
        buf.put(IpcConstants.RAM_BANKS_APPLE_IIE);

        // Vert/horiz counters
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_VERTCNT);
        buf.put(op.vertcnt);

        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_HORIZCNT);
        buf.put(op.horizcnt);

        // GOID — 8-byte LE
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_GOID);
        buf.putLong(op.goid);

        // GCC — 8-byte LE
        buf.position(IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_GCC);
        buf.putLong(op.gcc);
    }

    /**
     * Write a 24-bit value as 3 bytes, little-endian, at the current buffer position.
     */
    private void put24(int value) {
        buf.put((byte) (value & 0xFF));
        buf.put((byte) ((value >> 8) & 0xFF));
        buf.put((byte) ((value >> 16) & 0xFF));
    }

    /**
     * Write Bank 00 (main RAM), Bank E0 (aux RAM), Bank E1 (zero-fill), ROM, and DOC RAM
     * into the snapshot.  All reads are performed inside whileSuspended to ensure a
     * consistent memory state.
     */
    private void writeRamBanks() {
        Emulator.whileSuspended(computer -> {
            RAM memory = computer.getMemory();
            if (!(memory instanceof RAM128k)) {
                return;
            }
            RAM128k ram128 = (RAM128k) memory;

            // --- Bank 00: 64KB main address space ($0000-$FFFF) ---
            // Read from activeRead which reflects the current MMU configuration.
            // Pages $C0 (I/O space) are left as zero — reads from I/O have side effects.
            PagedMemory activeRead = ram128.activeRead;
            buf.position(IpcConstants.SNAP_OFF_BANK00);
            for (int page = 0; page < 256; page++) {
                byte[] pageData = activeRead.get(page);
                if (pageData != null && page != 0xC0) {
                    buf.put(pageData, 0, 256);
                } else {
                    // I/O page or unmapped: zero-fill
                    for (int i = 0; i < 256; i++) {
                        buf.put((byte) 0);
                    }
                }
            }

            // --- Bank E0: 64KB auxiliary RAM ---
            PagedMemory auxMem = ram128.getAuxMemory();
            buf.position(IpcConstants.SNAP_OFF_BANKE0);
            if (auxMem != null) {
                byte[][] auxPages = auxMem.getMemory();
                for (int page = 0; page < 256; page++) {
                    if (page < auxPages.length && auxPages[page] != null) {
                        buf.put(auxPages[page], 0, 256);
                    } else {
                        for (int i = 0; i < 256; i++) {
                            buf.put((byte) 0);
                        }
                    }
                }
            }
            // If auxMem is null the region remains zero-filled from the initial clear.

            // --- Bank E1: zero-fill (IIgs extended RAM, not present in IIe) ---
            // Already zero from initial clear.

            // --- ROM: 128KB (first 64KB zero, second 64KB = IIe ROM $C000-$FFFF) ---
            // First 64KB: zero (already cleared).
            // Second 64KB: pages $C0-$FF from the ROM PagedMemory objects.
            // ROM layout:
            //   cPageRom covers $C100-$CFFF (SLOW_ROM base $C100, 0x1000 = 16 pages)
            //   rom covers $D000-$FFFF (FIRMWARE_MAIN base $D000, 0x3000 = 48 pages)
            buf.position(IpcConstants.SNAP_OFF_ROM + 65536);  // second 64KB
            // Write the 256-byte I/O page ($C0xx) as zero (already at correct buf position)
            for (int i = 0; i < 256; i++) {
                buf.put((byte) 0);
            }
            // $C100-$CFFF: cPageRom (16 pages)
            PagedMemory cPageRom = ram128.getcPageRom();
            byte[][] cPages = cPageRom.getMemory();
            for (int page = 0; page < 16 && page < cPages.length; page++) {
                if (cPages[page] != null) {
                    buf.put(cPages[page], 0, 256);
                } else {
                    for (int i = 0; i < 256; i++) {
                        buf.put((byte) 0);
                    }
                }
            }
            // $D000-$FFFF: rom (48 pages)
            PagedMemory romMem = ram128.getRom();
            byte[][] romPages = romMem.getMemory();
            for (int page = 0; page < 48 && page < romPages.length; page++) {
                if (romPages[page] != null) {
                    buf.put(romPages[page], 0, 256);
                } else {
                    for (int i = 0; i < 256; i++) {
                        buf.put((byte) 0);
                    }
                }
            }

            // --- DOC RAM: zero-fill (IIgs only, already cleared) ---
        });
    }
}
