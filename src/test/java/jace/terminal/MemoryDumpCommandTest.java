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
 * Covers the --raw / --csv wiring on mem, memaux and memmain.
 *
 * The negative assertions matter most: without a flag the output must stay
 * byte-for-byte what it was, because scripts already parse it.
 */
public class MemoryDumpCommandTest {

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
        // No emulated code may run while these tests plant patterns and read
        // them back; a previous class can leave a worker thread going.
        TestUtils.quiesceEmulator();
        out = new ByteArrayOutputStream();
        mainMode = new TestableMainMode(out);

        for (int i = 0; i < 8; i++) {
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

    // ---- Default output is unchanged --------------------------------------

    @Test
    public void memWithoutAFlagStillProducesTheHumanReadableDump() {
        String output = run("mem 6000 6003");

        assertTrue("should carry the address prefix, got: " + output,
                output.contains("6000: "));
        assertTrue("should carry the ASCII gutter, got: " + output, output.contains("|"));
    }

    @Test
    public void memauxWithoutAFlagStillProducesTheHumanReadableDump() {
        String output = run("memaux 6000 6003");

        assertTrue("should carry the address prefix, got: " + output,
                output.contains("6000: "));
        assertTrue("should carry the ASCII gutter, got: " + output, output.contains("|"));
    }

    // ---- --raw -------------------------------------------------------------

    @Test
    public void memRawEmitsBytesOnly() {
        String output = run("mem 6000 6003 --raw").trim();

        assertEquals("10 11 12 13", output);
        assertFalse("no address prefix expected: " + output, output.contains("6000:"));
        assertFalse("no ASCII gutter expected: " + output, output.contains("|"));
    }

    @Test
    public void memauxRawReadsTheAuxBankNotMain() {
        // The reason this format exists: comparing the two halves of a DHGR
        // pixel column. A flag that silently dropped the bank selector would
        // make every comparison pass vacuously.
        assertEquals("80 81 82 83", run("memaux 6000 6003 --raw").trim());
        assertEquals("10 11 12 13", run("memmain 6000 6003 --raw").trim());
    }

    @Test
    public void rawFlagPositionDoesNotMatter() {
        // Guards the naive parse that treats args[0]/args[1] as the addresses:
        // with the flag first, that would try to parse "--raw" as hex.
        assertEquals("10 11 12 13", run("mem --raw 6000 6003").trim());
        assertEquals("10 11 12 13", run("mem 6000 --raw 6003").trim());
    }

    @Test
    public void aliasesAcceptTheFlagToo() {
        assertEquals("80 81 82 83", run("mx 6000 6003 --raw").trim());
        assertEquals("10 11 12 13", run("mm 6000 6003 --raw").trim());
    }

    // ---- --csv -------------------------------------------------------------

    @Test
    public void memCsvEmitsCommaSeparatedBytes() {
        assertEquals("10,11,12,13", run("mem 6000 6003 --csv").trim());
    }

    // ---- Argument errors ---------------------------------------------------

    @Test
    public void missingAddressesPrintUsageMentioningTheFlags() {
        String output = run("mem 6000");
        assertTrue("should print usage, got: " + output, output.contains("Usage: mem"));
        assertTrue("usage should document the formats, got: " + output,
                output.contains("--raw"));
    }

    @Test
    public void unrecognizedFlagDoesNotSilentlyResolveToAMachineFormat() {
        // Format.parse returns null for anything it does not recognize rather
        // than falling back to a format, so a typo'd flag leaves the default
        // human dump in place. It must not silently produce --raw output, which
        // a caller checking for the address prefix would then misread.
        String output = run("mem 6000 6003 --nonsense");

        assertTrue("a typo'd flag must leave the human dump in place, got: " + output,
                output.contains("6000: ") && output.contains("|"));
    }
}
