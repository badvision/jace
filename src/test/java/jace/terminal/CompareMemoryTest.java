package jace.terminal;

import static jace.TestUtils.initComputer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.TestUtils;
import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.SoundMixer;

/**
 * Covers cmpmem: compares a memory range against an expected byte list and
 * reports only the mismatches.
 *
 * Why only mismatches: verifying a DHGR render means comparing hundreds of bytes
 * at a time, and a dump of all of them buries the two that are wrong. Reporting
 * offsets of differing bytes turns "something is off in this row" into an exact
 * byte position.
 *
 * The command consumes MemoryDump.read() directly rather than parsing the output
 * of a dump command, so the comparison cannot be broken by a display-format
 * change and cannot lose information where the display format is lossy.
 */
public class CompareMemoryTest {

    private static final int ADDR = 0x6000;

    private static RAM128k ram;

    private TestableMainMode mainMode;
    private ByteArrayOutputStream out;

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
        // patterns and compare against them.
        TestUtils.quiesceEmulator();
        out = new ByteArrayOutputStream();
        mainMode = new TestableMainMode(out);

        // Distinct ascending runs in each bank at the same addresses, so a bank
        // mix-up cannot pass.
        for (int i = 0; i < 16; i++) {
            ram.getMainMemory().writeByte(ADDR + i, (byte) (0x10 + i));
            ram.getAuxMemory().writeByte(ADDR + i, (byte) (0x80 + i));
        }
    }

    private String run(String command) {
        out.reset();
        assertTrue("command should be recognized: " + command,
                mainMode.processCommand(command));
        return out.toString();
    }

    // ---- Matching ---------------------------------------------------------

    @Test
    public void identicalBytesReportNoMismatches() {
        String output = run("cmpmem main 6000 10 11 12 13");

        assertTrue("should report a clean match, got: " + output,
                output.toLowerCase().contains("match"));
        // No offsets should appear, because there is nothing to report.
        assertFalse("a clean comparison must not list any offsets, got: " + output,
                output.contains("+0"));
    }

    @Test
    public void auxBankIsComparedWhenAuxIsRequested() {
        // The reason the bank selector exists: aux holds the even DHGR pixel
        // columns. Comparing aux bytes against the main bank would make every
        // verification meaningless.
        assertTrue(run("cmpmem aux 6000 80 81 82 83").toLowerCase().contains("match"));
        assertTrue(run("cmpmem main 6000 10 11 12 13").toLowerCase().contains("match"));
    }

    @Test
    public void bankSelectorIsNotIgnored() {
        // Asking for aux with main's expected bytes must FAIL. Without this, a
        // command that silently read one bank would pass the test above.
        String output = run("cmpmem aux 6000 10 11 12 13");
        assertTrue("aux compared against main's bytes must mismatch, got: " + output,
                output.contains("+0"));
    }

    // ---- Reporting mismatches ---------------------------------------------

    @Test
    public void reportsOnlyTheMismatchingBytesWithTheirOffsets() {
        // Expect 4 bytes where the third is wrong. Only that one should appear.
        String output = run("cmpmem main 6000 10 11 FF 13");

        assertTrue("should report the mismatch at offset 2, got: " + output,
                output.contains("+2"));
        assertFalse("matching offset 0 must not be reported, got: " + output,
                output.contains("+0"));
        assertFalse("matching offset 1 must not be reported, got: " + output,
                output.contains("+1"));
        assertFalse("matching offset 3 must not be reported, got: " + output,
                output.contains("+3"));
    }

    @Test
    public void mismatchReportNamesBothTheExpectedAndActualByte() {
        // An offset alone does not tell the operator which direction the error
        // went, which is the first thing they need to know.
        String output = run("cmpmem main 6000 10 11 FF 13");

        assertTrue("should name the expected byte FF, got: " + output, output.contains("FF"));
        assertTrue("should name the actual byte 12, got: " + output, output.contains("12"));
    }

    @Test
    public void mismatchReportGivesTheAbsoluteAddressNotJustTheOffset() {
        // Offset 2 from $6000 is $6002; the operator needs the address to go
        // look at it, and the offset to index into their expected list.
        String output = run("cmpmem main 6000 10 11 FF 13");

        assertTrue("should carry the absolute address 6002, got: " + output,
                output.toUpperCase().contains("6002"));
    }

    @Test
    public void reportsEveryMismatchNotJustTheFirst() {
        // Stopping at the first difference would force the operator to iterate
        // once per bad byte, which for a row of DHGR data is the whole job.
        String output = run("cmpmem main 6000 FF 11 FF 13 FF");

        assertTrue("offset 0 should be reported, got: " + output, output.contains("+0"));
        assertTrue("offset 2 should be reported, got: " + output, output.contains("+2"));
        assertTrue("offset 4 should be reported, got: " + output, output.contains("+4"));
    }

    @Test
    public void reportsACountOfMismatches() {
        // A count lets a script decide pass/fail without parsing every line.
        String output = run("cmpmem main 6000 FF 11 FF 13");

        assertTrue("should report 2 mismatches, got: " + output, output.contains("2"));
    }

    // ---- Byte list parsing -------------------------------------------------

    @Test
    public void byteListIsParsedAsHexNotDecimal() {
        // Every other address and byte argument in this monitor is hex; a byte
        // list read as decimal would silently compare the wrong values. Memory
        // holds 0x10 at ADDR, so a hex "10" matches and a decimal 10 would not.
        assertTrue("hex 10 should match the byte 0x10, got: " + run("cmpmem main 6000 10"),
                run("cmpmem main 6000 10").toLowerCase().contains("match"));
    }

    @Test
    public void commaSeparatedByteListsAreAcceptedSoCsvDumpsCanBePastedBack() {
        // --csv emits "10,11,12,13"; requiring the operator to reformat it
        // before feeding it back would defeat the point of having both commands.
        String output = run("cmpmem main 6000 10,11,12,13");

        assertTrue("comma-separated list should compare clean, got: " + output,
                output.toLowerCase().contains("match"));
    }

    @Test
    public void spaceSeparatedRawDumpOutputCanBePastedBack() {
        String output = run("cmpmem main 6000 10 11 12 13");
        assertTrue("space-separated list should compare clean, got: " + output,
                output.toLowerCase().contains("match"));
    }

    @Test
    public void dollarPrefixedBytesAreAccepted() {
        String output = run("cmpmem main 6000 $10 $11");
        assertTrue("$-prefixed bytes should be accepted, got: " + output,
                output.toLowerCase().contains("match"));
    }

    // ---- Argument errors ---------------------------------------------------

    @Test
    public void missingArgumentsPrintUsage() {
        assertTrue(run("cmpmem").contains("Usage: cmpmem"));
        assertTrue(run("cmpmem main").contains("Usage: cmpmem"));
        assertTrue("a bank and address with no byte list is not a comparison",
                run("cmpmem main 6000").contains("Usage: cmpmem"));
    }

    @Test
    public void unknownBankSelectorIsRejectedRatherThanGuessed() {
        // Silently defaulting the bank would produce a comparison against
        // memory the caller did not ask about, and report it as authoritative.
        String output = run("cmpmem sideways 6000 10");

        assertTrue("should reject the unknown bank, got: " + output,
                output.toLowerCase().contains("bank"));
        assertFalse("must not report a result for a bank it could not resolve, got: " + output,
                output.toLowerCase().contains("match"));
    }

    @Test
    public void nonHexByteInTheListIsReportedRatherThanTreatedAsZero() {
        // Parsing "GG" as 0 would silently compare against the wrong value and
        // could report a spurious mismatch, or worse a spurious match.
        String output = run("cmpmem main 6000 10 GG 12");

        assertTrue("should report the invalid byte, got: " + output,
                output.toLowerCase().contains("invalid"));
        assertFalse("must not report a comparison result, got: " + output,
                output.toLowerCase().contains("mismatch"));
    }

    @Test
    public void byteListRunningPastTheAddressSpaceIsRejected() {
        String output = run("cmpmem main FFFF 10 11 12");
        assertTrue("should reject a range past $FFFF, got: " + output,
                output.toLowerCase().contains("address space")
                        || output.toLowerCase().contains("exceed"));
    }

    @Test
    public void activeBankSelectorFollowsTheSoftswitchConfiguration() {
        // "active" is the third meaningful selector: it reads whatever the
        // machine currently has mapped, which is what you want when verifying
        // what the CPU would actually see.
        String output = run("cmpmem active 6000 10 11 12 13");
        assertFalse("active should be accepted as a bank, got: " + output,
                output.contains("Usage: cmpmem"));
        assertFalse("active must not be rejected as unknown, got: " + output,
                output.toLowerCase().contains("unknown bank"));
    }

    // ---- Consuming the raw path, not scraped text ---------------------------

    @Test
    public void comparisonAgreesWithTheRawDumpOfTheSameRange() {
        // cmpmem reads bytes through MemoryDump.read(); --raw renders the same
        // call. Feeding one command's output into the other must compare clean.
        // If they could disagree, cmpmem would be reporting on memory the
        // operator cannot inspect.
        String raw = run("memaux 6000 6003 --raw").trim();
        String output = run("cmpmem aux 6000 " + raw);

        assertTrue("a raw dump fed back to cmpmem must match, got: " + output
                + " (raw was: " + raw + ")", output.toLowerCase().contains("match"));
    }

    @Test
    public void mismatchCountIsExactForALongRange() {
        // Exercises the loop over more than one 16-byte line, where an
        // off-by-one in line handling would show up.
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            // Corrupt exactly three of the sixteen expected bytes.
            boolean corrupt = (i == 0 || i == 7 || i == 15);
            expected.append(String.format("%02X ", corrupt ? 0xFF : (0x10 + i)));
        }

        String output = run("cmpmem main 6000 " + expected.toString().trim());

        assertTrue("offset 0 expected, got: " + output, output.contains("+0"));
        assertTrue("offset 7 expected, got: " + output, output.contains("+7"));
        assertTrue("offset 15 expected, got: " + output, output.contains("+15"));
        assertFalse("offset 8 matches and must not be listed, got: " + output,
                output.contains("+8 "));
        assertTrue("should report exactly 3 mismatches, got: " + output,
                output.contains("3 mismatch"));
    }

    @Test
    public void comparisonDoesNotDisturbTheBytesItRead() {
        // A verification command that perturbed memory would invalidate the
        // very render it was checking.
        run("cmpmem main 6000 FF FF FF FF");

        assertEquals((byte) 0x10, ram.getMainMemory().readByte(ADDR));
        assertEquals((byte) 0x11, ram.getMainMemory().readByte(ADDR + 1));
    }
}
