package jace.ipc;

import java.util.logging.Logger;

import jace.Emulator;

/**
 * Handles C2K_WRITE_DATA frames by writing payload bytes into Apple IIe RAM.
 *
 * Payload layout:
 *   bytes 0-3  : little-endian 24-bit address (bits 16-23 are the high byte)
 *   bytes 4+   : data bytes to write sequentially starting at address
 *
 * Addresses with bits 16-23 non-zero are silently ignored (DOC RAM / IIgs only).
 */
class MemoryWriteHandler {

    private static final Logger LOG = Logger.getLogger(MemoryWriteHandler.class.getName());

    void apply(byte[] payload) {
        if (payload == null || payload.length < 4) {
            LOG.warning("MemoryWriteHandler: payload too short (" +
                    (payload == null ? "null" : payload.length) + " bytes), ignoring");
            return;
        }

        int address = (payload[0] & 0xFF)
                | ((payload[1] & 0xFF) << 8)
                | ((payload[2] & 0xFF) << 16)
                | ((payload[3] & 0xFF) << 24);

        int highByte = (address >> 16) & 0xFF;
        if (highByte != 0) {
            // DOC RAM or IIgs extended address — not supported on Apple IIe
            return;
        }

        int base = address & 0xFFFF;
        for (int i = 4; i < payload.length; i++) {
            int writeAddr = (base + (i - 4)) & 0xFFFF;
            byte value = payload[i];
            Emulator.withMemory(mem -> mem.write(writeAddr, value, false, false));
        }
    }
}
