package jace.terminal;

import java.io.PrintStream;

/**
 * Machine-readable renderings of a memory range, for automated verification.
 *
 * The existing hex dump is built for a human at a prompt: every line carries a
 * four-digit address prefix and a trailing ASCII gutter, and short final lines
 * are space-padded so that gutter stays aligned. All three properties are
 * actively harmful to a script. The address prefix means two dumps of the same
 * bytes at different addresses never compare equal; the gutter adds a second,
 * lossy copy of every byte that diff reports on; and the padding means a dump
 * ending on a partial line does not match a dump of the same bytes that does
 * not. So a caller wanting to compare a render against a known-good capture
 * either eyeballs it or writes a scraper that re-parses the human format.
 *
 * RAW and CSV emit byte values and nothing else, so they can be diffed directly.
 *
 * ADDITIVE BY CONSTRUCTION. This class does not modify MonitorMode. The
 * human-readable path is reached by delegating to the existing
 * examineMemoryRange(int, int, MemoryMode), whose signature and output are
 * untouched -- HEX_ASCII is literally a call to it, which is why the two cannot
 * drift apart. MonitorModeAuxMemoryTest's expected "6000: AA ... | ." shape is
 * therefore unaffected, and a test here asserts the delegation holds.
 *
 * Byte reads go through MonitorMode.readMemory(addr, mode, false):
 *   - triggerEvents == false, so a debug dump does not fire RAM listeners and
 *     perturb the state it exists to observe.
 *   - it resolves MAIN/AUX to the physical PagedMemory bank rather than reading
 *     through the active configuration, so an AUX dump cannot silently mirror
 *     MAIN. That distinction is the whole point for DHGR work, where aux holds
 *     the even pixel columns and main the odd.
 */
public final class MemoryDump {

    /** Bytes per line in the machine-readable formats. Matches the human dump. */
    static final int BYTES_PER_LINE = 16;

    private MemoryDump() {
    }

    /** Output renderings of a memory range. */
    public enum Format {
        /**
         * The pre-existing human-readable dump: address prefix, hex bytes,
         * ASCII gutter. The default, so existing callers are unaffected.
         */
        HEX_ASCII,
        /** Space-separated uppercase hex bytes. Nothing else. */
        RAW,
        /** Comma-separated uppercase hex bytes, no trailing separator. */
        CSV;

        /**
         * Parses a command-line format selector.
         *
         * @param arg the argument to parse, with or without a leading "--"
         * @return the matching format, or null if the argument is not a format
         *         selector -- callers use null to mean "this was an address, not
         *         a flag", so junk must not silently resolve to a default
         */
        public static Format parse(String arg) {
            if (arg == null) {
                return null;
            }
            String name = arg.startsWith("--") ? arg.substring(2) : arg;
            switch (name.toLowerCase()) {
                case "raw":
                    return RAW;
                case "csv":
                    return CSV;
                case "hex":
                    return HEX_ASCII;
                default:
                    return null;
            }
        }
    }

    /**
     * Reads a range of bytes from an explicitly selected bank without
     * triggering memory listeners.
     *
     * Exposed so commands that need the bytes themselves -- cmpmem, the DHGR
     * region decoder -- consume this rather than re-parsing rendered text. A
     * scraper would couple those commands to the display format and would
     * silently lose information wherever the format is lossy.
     *
     * @param monitor the monitor providing bank-aware reads
     * @param startAddress first address, inclusive
     * @param byteCount number of bytes; clamped so the read stays within 64K
     * @param mode MAIN or AUX to force a physical bank, ACTIVE to follow the
     *        current softswitch configuration
     * @return the bytes read, in address order
     */
    public static byte[] read(MonitorMode monitor, int startAddress, int byteCount,
            MemoryMode mode) {
        int start = startAddress & 0xFFFF;
        int count = Math.max(0, Math.min(byteCount, 0x10000 - start));
        byte[] bytes = new byte[count];
        for (int i = 0; i < count; i++) {
            bytes[i] = monitor.readMemory(start + i, mode, false);
        }
        return bytes;
    }

    /**
     * Renders an inclusive address range in the requested format.
     *
     * @param output where to write
     * @param monitor the monitor providing bank-aware reads
     * @param startAddr first address, inclusive
     * @param endAddr last address, inclusive
     * @param mode MAIN, AUX, or ACTIVE
     * @param format the rendering; HEX_ASCII delegates to the existing dump
     */
    public static void dump(PrintStream output, MonitorMode monitor, int startAddr, int endAddr,
            MemoryMode mode, Format format) {
        if (endAddr < startAddr) {
            output.println("End address must be greater than or equal to start address");
            return;
        }
        if (format == null || format == Format.HEX_ASCII) {
            // Delegate rather than reimplement, so the human format has exactly
            // one definition and cannot drift from what callers already parse.
            monitor.examineMemoryRange(startAddr, endAddr, mode);
            return;
        }

        byte[] bytes = read(monitor, startAddr, endAddr - startAddr + 1, mode);
        String separator = (format == Format.CSV) ? "," : " ";
        for (int offset = 0; offset < bytes.length; offset += BYTES_PER_LINE) {
            int lineBytes = Math.min(BYTES_PER_LINE, bytes.length - offset);
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < lineBytes; i++) {
                if (i > 0) {
                    line.append(separator);
                }
                line.append(String.format("%02X", bytes[offset + i] & 0xFF));
            }
            // Deliberately no padding of short final lines: padding would make a
            // dump ending on a partial line fail to compare equal to a dump of
            // the same bytes that ends on a full one.
            output.println(line);
        }
    }
}
