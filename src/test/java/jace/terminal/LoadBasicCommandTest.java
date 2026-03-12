package jace.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.TestUtils;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.SoundMixer;

/**
 * Tests for the loadBasic terminal command in MainMode.
 *
 * Tests cover:
 *   1. Success: valid BASIC file loads cleanly and reports line/byte counts
 *   2. File not found: clear error message
 *   3. Line missing BASIC line number: error includes 1-based file line number
 *   4. Empty file: appropriate error message
 *   5. Usage error (no args)
 *   6. Blank lines are skipped silently
 *   7. Alias 'lbas' works
 *   8. Help text available
 *   9. printHelp() includes loadbasic
 */
public class LoadBasicCommandTest {

    static Computer computer;
    static MOS65C02 cpu;
    static RAM128k ram;

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    private PrintStream printStream;

    private JaceTerminal mockTerminal;
    private EmulatorInterface mockEmulator;
    private MainMode mainMode;

    @BeforeClass
    public static void setupClass() {
        // Initialize the emulator in headless mode so program.run() works without JavaFX
        TestUtils.initComputer();
        SoundMixer.MUTE = true;
        computer = Emulator.withComputer(c -> c, null);
        cpu = (MOS65C02) computer.getCpu();
        ram = (RAM128k) computer.getMemory();
    }

    @Before
    public void setUp() {
        originalOut = System.out;
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream);
        System.setOut(printStream);

        mockTerminal = mock(JaceTerminal.class);
        mockEmulator = mock(EmulatorInterface.class);

        when(mockTerminal.getOutput()).thenReturn(printStream);
        when(mockTerminal.getEmulator()).thenReturn(mockEmulator);

        mainMode = new MainMode(mockTerminal) {
            @Override
            public MOS65C02 getCPU() {
                return mock(MOS65C02.class);
            }
        };
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private String getOutput() {
        return outputStream.toString();
    }

    private Path writeTempFile(String content) throws IOException {
        Path tmp = Files.createTempFile("loadbasic_test_", ".bas");
        Files.write(tmp, content.getBytes());
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * A valid Applesoft BASIC listing should load cleanly.
     * The success message must include line count and byte count.
     */
    @Test
    public void testSuccessCase_validBasicFile() throws IOException {
        String basicSource =
                "10 HOME\n" +
                "20 PRINT \"HELLO, WORLD\"\n" +
                "30 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should contain 'Loaded'", out.contains("Loaded"));
        assertTrue("Output should show 3 lines", out.contains("3 lines"));
        assertTrue("Output should show byte count", out.matches("(?s).*\\d+ bytes.*"));
        assertTrue("Output should contain the filepath", out.contains(tmp.toAbsolutePath().toString()));
    }

    /**
     * When the file does not exist, the error message must mention the path.
     */
    @Test
    public void testFileNotFound() {
        String nonexistent = "/tmp/this_file_definitely_does_not_exist_loadbasic_test.bas";
        boolean result = mainMode.processCommand("loadbasic " + nonexistent);

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should mention file not found",
                out.contains("File not found") || out.contains("not found"));
        assertTrue("Output should include the filepath", out.contains(nonexistent));
    }

    /**
     * When a line has no BASIC line number, the error must include the 1-based
     * file line number of the offending line.
     */
    @Test
    public void testSyntaxError_missingLineNumber() throws IOException {
        String basicSource =
                "10 HOME\n" +
                "20 PRINT \"OK\"\n" +
                "THIS LINE HAS NO NUMBER\n" +   // file line 3
                "40 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should mention an error", out.contains("Error"));
        assertTrue("Output should identify file line 3", out.contains("line 3"));
    }

    /**
     * When the first non-blank line has no BASIC line number (file line 1),
     * the error should correctly report line 1.
     */
    @Test
    public void testSyntaxError_missingLineNumberOnFirstLine() throws IOException {
        String basicSource = "HELLO THERE\n10 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should mention an error at line 1", out.contains("line 1"));
    }

    /**
     * When the file is empty, an appropriate error should be reported.
     */
    @Test
    public void testEmptyFile() throws IOException {
        Path tmp = writeTempFile("");
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should report no lines found or similar error",
                out.contains("No BASIC lines") || out.contains("empty") || out.contains("no lines"));
    }

    /**
     * When no argument is provided, a usage message must be printed.
     */
    @Test
    public void testNoArgs_usageMessage() {
        boolean result = mainMode.processCommand("loadbasic");

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should show usage", out.contains("Usage"));
        assertTrue("Output should mention loadbasic", out.toLowerCase().contains("loadbasic"));
    }

    /**
     * Blank lines in the file should be silently skipped (not counted as errors).
     */
    @Test
    public void testBlankLinesAreSkipped() throws IOException {
        String basicSource =
                "\n" +
                "10 HOME\n" +
                "\n" +
                "20 PRINT \"HELLO\"\n" +
                "\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should show successful load (Loaded)", out.contains("Loaded"));
        assertTrue("Output should show 2 lines (blank lines excluded)", out.contains("2 lines"));
    }

    /**
     * The alias 'lbas' should be equivalent to 'loadbasic'.
     */
    @Test
    public void testAlias_lbas() {
        String nonexistent = "/tmp/alias_test_loadbasic.bas";
        boolean result = mainMode.processCommand("lbas " + nonexistent);

        String out = getOutput();
        assertTrue("Alias lbas should be recognised and return true", result);
        assertFalse("Should not report unknown command", out.contains("Unknown command"));
        assertTrue("Output should mention file not found",
                out.contains("not found") || out.contains("File not found"));
    }

    /**
     * Help text for loadbasic should be available.
     */
    @Test
    public void testHelpText() {
        boolean result = mainMode.printCommandHelp("loadbasic");

        String out = getOutput();
        assertTrue("Help should be available for loadbasic", result);
        assertTrue("Help text should mention loadbasic or BASIC",
                out.toLowerCase().contains("loadbasic") || out.contains("BASIC"));
    }

    /**
     * printHelp() should list loadbasic.
     */
    @Test
    public void testPrintHelpIncludesLoadBasic() {
        mainMode.printHelp();

        String out = getOutput();
        assertTrue("Help listing should include loadbasic", out.contains("loadbasic"));
    }

    /**
     * Lines starting with ';' (semicolon comment) should be silently skipped.
     * They must not be counted in the loaded line total.
     */
    @Test
    public void testSemicolonCommentLinesAreSkipped() throws IOException {
        String basicSource =
                "; this is a comment\n" +
                "10 HOME\n" +
                "; another comment\n" +
                "20 PRINT \"HELLO\"\n" +
                "30 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should show successful load", out.contains("Loaded"));
        assertTrue("Output should show 3 BASIC lines (comment lines excluded)", out.contains("3 lines"));
    }

    /**
     * A bare REM line (no leading line number) should be silently skipped.
     */
    @Test
    public void testRemCommentLineNoLineNumber_isSkipped() throws IOException {
        String basicSource =
                "REM this is a header comment\n" +
                "10 HOME\n" +
                "20 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should show successful load", out.contains("Loaded"));
        assertTrue("Output should show 2 BASIC lines (REM comment excluded)", out.contains("2 lines"));
    }

    /**
     * An indented semicolon comment (leading whitespace before ';') should be silently skipped.
     */
    @Test
    public void testIndentedSemicolonComment_isSkipped() throws IOException {
        String basicSource =
                "   ; indented comment\n" +
                "10 HOME\n" +
                "20 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should show successful load", out.contains("Loaded"));
        assertTrue("Output should show 2 BASIC lines (indented comment excluded)", out.contains("2 lines"));
    }

    /**
     * An indented REM comment (leading whitespace before REM, no line number) should be silently skipped.
     */
    @Test
    public void testIndentedRemComment_isSkipped() throws IOException {
        String basicSource =
                "   REM indented rem\n" +
                "10 HOME\n" +
                "20 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should show successful load", out.contains("Loaded"));
        assertTrue("Output should show 2 BASIC lines (indented REM excluded)", out.contains("2 lines"));
    }

    /**
     * Mixed file: real BASIC lines interleaved with comment lines.
     * All comment lines (both ';' and bare REM) must be ignored; only BASIC lines tokenized.
     */
    @Test
    public void testMixedCommentAndBasicLines() throws IOException {
        String basicSource =
                "; Program: Hello World\n" +
                "REM Written for JACE test\n" +
                "10 HOME\n" +
                "   ; clear screen done\n" +
                "20 PRINT \"HELLO, WORLD\"\n" +
                "   REM print done\n" +
                "30 END\n";

        Path tmp = writeTempFile(basicSource);
        boolean result = mainMode.processCommand("loadbasic " + tmp.toAbsolutePath());

        String out = getOutput();
        assertTrue("Command should be recognised and return true", result);
        assertTrue("Output should show successful load", out.contains("Loaded"));
        assertTrue("Output should show 3 BASIC lines (all comment lines excluded)", out.contains("3 lines"));
    }
}
