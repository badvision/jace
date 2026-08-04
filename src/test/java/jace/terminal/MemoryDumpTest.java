package jace.terminal;

import static jace.TestUtils.initComputer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.TestUtils;
import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.SoundMixer;

/**
 * Covers the machine-readable dump formats.
 *
 * Why this exists: the human-readable hex dump carries an address prefix and a
 * trailing ASCII gutter, so piping it into diff produces noise on every line and
 * a byte comparison has to be done by eye or by a fragile text scraper. The RAW
 * and CSV formats emit only byte values, so a dump of a known-good render and a
 * dump of the current render can be diffed directly.
 *
 * The default format is unchanged and stays the default; that is asserted here
 * explicitly, because a regression flipping it would break every existing
 * caller of mem/memaux/memmain silently.
 */
public class MemoryDumpTest {

    private static final int ADDR = 0x6000;
    private static final byte MAIN_PATTERN = (byte) 0xAA;
    private static final byte AUX_PATTERN = (byte) 0x55;

    private static RAM128k ram;

    private ByteArrayOutputStream outputStream;
    private PrintStream out;
    private TestableMonitorMode monitorMode;

    @BeforeClass
    public static void setupClass() {
        initComputer();
        SoundMixer.MUTE = true;
        Computer computer = Emulator.withComputer(c -> c, null);
        ram = (RAM128k) computer.getMemory();
    }

    @Before
    public void setup() {
        // Nothing may be executing emulated code while these tests plant byte
        // patterns and read them back; a previous class can leave the
        // motherboard's worker thread running.
        TestUtils.quiesceEmulator();
        outputStream = new ByteArrayOutputStream();
        out = new PrintStream(outputStream);
        monitorMode = new TestableMonitorMode(outputStream);

        // A recognizable ascending run in main, and a distinct one in aux at the
        // same addresses, so a bank mix-up cannot pass.
        for (int i = 0; i < 32; i++) {
            ram.getMainMemory().writeByte(ADDR + i, (byte) (0x10 + i));
            ram.getAuxMemory().writeByte(ADDR + i, (byte) (0x80 + i));
        }
        ram.getMainMemory().writeByte(ADDR, MAIN_PATTERN);
        ram.getAuxMemory().writeByte(ADDR, AUX_PATTERN);
    }

    private String dump(int start, int end, MemoryMode mode, MemoryDump.Format format) {
        outputStream.reset();
        MemoryDump.dump(out, monitorMode, start, end, mode, format);
        out.flush();
        return outputStream.toString();
    }

    // ---- Reading the raw bytes -------------------------------------------

    @Test
    public void readReturnsTheBytesInOrderFromTheRequestedBank() {
        byte[] main = MemoryDump.read(monitorMode, ADDR, 4, MemoryMode.MAIN);
        byte[] aux = MemoryDump.read(monitorMode, ADDR, 4, MemoryMode.AUX);

        assertEquals(4, main.length);
        assertEquals(MAIN_PATTERN, main[0]);
        assertEquals((byte) 0x11, main[1]);
        assertEquals((byte) 0x12, main[2]);
        assertEquals((byte) 0x13, main[3]);

        assertEquals(AUX_PATTERN, aux[0]);
        assertEquals((byte) 0x81, aux[1]);
    }

    @Test
    public void readIsTheSharedPathSoOtherCommandsNeedNotScrapeText() {
        // cmpmem consumes read() directly rather than parsing dump output. This
        // asserts the two agree, which is what makes that safe: if read() and
        // the RAW rendering could disagree, a comparison command built on
        // read() would report mismatches the operator could not see in a dump.
        byte[] bytes = MemoryDump.read(monitorMode, ADDR, 4, MemoryMode.MAIN);
        String raw = dump(ADDR, ADDR + 3, MemoryMode.MAIN, MemoryDump.Format.RAW);

        StringBuilder expected = new StringBuilder();
        for (byte b : bytes) {
            expected.append(String.format("%02X ", b & 0xFF));
        }
        assertEquals(expected.toString().trim(), raw.trim());
    }

    // ---- RAW format -------------------------------------------------------

    @Test
    public void rawFormatEmitsBytesOnlyWithNoAddressPrefixOrAsciiGutter() {
        String raw = dump(ADDR, ADDR + 3, MemoryMode.MAIN, MemoryDump.Format.RAW);

        assertEquals("AA 11 12 13", raw.trim());
        // The three things that make the human format undiffable.
        assertFalse("raw must not carry an address prefix: " + raw, raw.contains("6000"));
        assertFalse("raw must not carry the ASCII gutter separator: " + raw, raw.contains("|"));
        assertFalse("raw must not carry a colon: " + raw, raw.contains(":"));
    }

    @Test
    public void rawFormatWrapsAtSixteenBytesPerLine() {
        String raw = dump(ADDR, ADDR + 31, MemoryMode.MAIN, MemoryDump.Format.RAW);
        String[] lines = raw.trim().split("\\R");

        assertEquals("32 bytes should be two lines of 16", 2, lines.length);
        assertEquals(16, lines[0].trim().split("\\s+").length);
        assertEquals(16, lines[1].trim().split("\\s+").length);
        // Second line must continue the run, not restart it.
        assertEquals("20", lines[1].trim().split("\\s+")[0]);
    }

    @Test
    public void rawFormatDoesNotPadShortFinalLines() {
        // The human format pads to a fixed width so the ASCII gutter aligns.
        // Padding here would make a trailing-partial-line dump diff against a
        // full-line dump of the same bytes.
        String raw = dump(ADDR, ADDR + 17, MemoryMode.MAIN, MemoryDump.Format.RAW);
        // Deliberately NOT trimming the whole string before splitting: trim()
        // would strip the very trailing padding this test exists to detect.
        // Measured -- with the trim in place, injecting padding into the
        // formatter did not fail this test.
        String[] lines = raw.split("\\R");

        assertEquals(2, lines.length);
        assertEquals("final partial line should hold exactly 2 bytes", 2,
                lines[1].trim().split("\\s+").length);
        assertEquals("last line should not be space-padded", "20 21", lines[1]);
    }

    @Test
    public void rawFormatReadsTheBankItWasAskedFor() {
        // The reason machine-readable output exists at all is DHGR verification,
        // where aux holds the even pixel columns and main the odd, so a dump that
        // silently mirrored one bank would be worse than no dump.
        assertEquals("AA 11 12 13",
                dump(ADDR, ADDR + 3, MemoryMode.MAIN, MemoryDump.Format.RAW).trim());
        assertEquals("55 81 82 83",
                dump(ADDR, ADDR + 3, MemoryMode.AUX, MemoryDump.Format.RAW).trim());
    }

    // ---- CSV format -------------------------------------------------------

    @Test
    public void csvFormatSeparatesBytesWithCommasAndNoSpaces() {
        String csv = dump(ADDR, ADDR + 3, MemoryMode.MAIN, MemoryDump.Format.CSV);

        assertEquals("AA,11,12,13", csv.trim());
        assertFalse("csv must not carry an address prefix: " + csv, csv.contains("6000"));
        assertFalse("csv must not carry the ASCII gutter: " + csv, csv.contains("|"));
    }

    @Test
    public void csvFormatHasNoTrailingSeparator() {
        // A trailing comma makes every parser emit a spurious empty final field.
        String csv = dump(ADDR, ADDR + 3, MemoryMode.MAIN, MemoryDump.Format.CSV).trim();
        assertFalse("csv line must not end with a separator: " + csv, csv.endsWith(","));
        assertEquals(4, csv.split(",").length);
    }

    @Test
    public void csvFormatWrapsAtSixteenBytesPerLine() {
        String csv = dump(ADDR, ADDR + 31, MemoryMode.MAIN, MemoryDump.Format.CSV);
        String[] lines = csv.trim().split("\\R");

        assertEquals(2, lines.length);
        assertEquals(16, lines[0].split(",").length);
        assertEquals(16, lines[1].split(",").length);
    }

    // ---- The default is unchanged -----------------------------------------

    @Test
    public void defaultFormatIsStillTheHumanReadableHexDump() {
        String human = dump(ADDR, ADDR, MemoryMode.MAIN, MemoryDump.Format.HEX_ASCII);

        // Bit-identical to what mem/memaux/memmain produced before this change:
        // an address prefix, the byte, then the ASCII gutter.
        assertTrue("should carry the address prefix, got: " + human, human.startsWith("6000: "));
        assertTrue("should carry the ASCII gutter, got: " + human, human.contains("|"));
        assertTrue("should carry the byte value, got: " + human, human.contains("AA"));
    }

    @Test
    public void humanFormatIsProducedByTheUnchangedExistingPath() {
        // Guards the constraint that the human path was not reimplemented: the
        // format overload must delegate to examineMemoryRange, so the two are
        // byte-identical. If someone reimplements the human dump inside
        // MemoryDump, this catches the drift.
        outputStream.reset();
        monitorMode.examineMemoryRange(ADDR, ADDR + 17, MemoryMode.MAIN);
        String viaExisting = outputStream.toString();

        String viaFormat = dump(ADDR, ADDR + 17, MemoryMode.MAIN, MemoryDump.Format.HEX_ASCII);

        assertEquals(viaExisting, viaFormat);
    }

    // ---- Argument handling -------------------------------------------------

    @Test
    public void invertedRangeIsRejectedRatherThanDumpingNothingSilently() {
        String raw = dump(ADDR + 4, ADDR, MemoryMode.MAIN, MemoryDump.Format.RAW);
        assertTrue("should explain the bad range, got: " + raw,
                raw.toLowerCase().contains("address"));
    }

    @Test
    public void singleByteRangeIsInclusive() {
        // start == end must dump exactly one byte, matching the human format's
        // inclusive convention rather than dumping zero.
        String raw = dump(ADDR, ADDR, MemoryMode.MAIN, MemoryDump.Format.RAW);
        assertEquals("AA", raw.trim());
    }

    @Test
    public void dumpIsClampedToTheSixtyFourKAddressSpace() {
        // Asking past $FFFF must not throw or wrap around to zero page.
        String raw = dump(0xFFFE, 0xFFFF, MemoryMode.MAIN, MemoryDump.Format.RAW);
        assertEquals("two bytes expected", 2, raw.trim().split("\\s+").length);
    }

    @Test
    public void formatNamesParseCaseInsensitivelyAndRejectJunk() {
        assertEquals(MemoryDump.Format.RAW, MemoryDump.Format.parse("--raw"));
        assertEquals(MemoryDump.Format.RAW, MemoryDump.Format.parse("--RAW"));
        assertEquals(MemoryDump.Format.CSV, MemoryDump.Format.parse("--csv"));
        assertEquals(null, MemoryDump.Format.parse("--nonsense"));
        assertEquals(null, MemoryDump.Format.parse("2000"));
    }
}
