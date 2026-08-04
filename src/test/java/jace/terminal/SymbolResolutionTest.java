package jace.terminal;

import static jace.TestUtils.initComputer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.TestUtils;
import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.SoundMixer;

/**
 * Verifies that terminal commands accept symbolic label names in place of hex
 * addresses, and that unrecognized names fail loudly instead of resolving to $0000.
 *
 * These tests drive real commands end to end — {@code symbols}, then {@code mem} /
 * {@code memaux} / {@code break} / {@code go} — rather than only exercising the
 * parser, because the thing worth proving is that a name reaches the same code path
 * the hex address reaches today.
 *
 * The fixture symbol files under src/test/resources were produced by real ACME 0.97
 * ({@code acme --symbollist ... --vicelabels ...}) so the accepted formats are the
 * assembler's actual output, not a guess at it. They are committed here rather than
 * generated at test time so the suite needs no assembler installed and no files from
 * any other repository.
 */
public class SymbolResolutionTest {

    /** Addresses ACME assigned in the fixture. Asserted, not assumed. */
    private static final int ENTRY = 0x4000;
    private static final int MAINLOOP = 0x4006;
    private static final int AFTER_DRAW = 0x4009;
    private static final int DRAW_SCENE = 0x4012;
    private static final int FRAME_COUNT = 0x1A;
    private static final int HARRY_X = 0x1D;

    private static final byte MAIN_PATTERN = (byte) 0xAA;
    private static final byte AUX_PATTERN = (byte) 0x55;

    private static RAM128k ram;

    private ByteArrayOutputStream out;
    private TestableMainMode mainMode;

    @BeforeClass
    public static void setupClass() {
        initComputer();
        SoundMixer.MUTE = true;
        Computer computer = Emulator.withComputer(c -> c, null);
        ram = (RAM128k) computer.getMemory();
    }

    @Before
    public void setup() {
        // goCommandAcceptsSymbolicName runs a real "go", which resumes the motherboard's
        // worker thread. Left running, the emulated ROM executes concurrently with the
        // remaining tests -- flipping softswitches and overwriting the memory patterns
        // they write. Stop the machine before every test, and again afterwards so it
        // cannot leak into another test class.
        TestUtils.quiesceEmulator();
        SymbolTable.clear();
        out = new ByteArrayOutputStream();
        mainMode = new TestableMainMode(out);
    }

    @After
    public void tearDown() {
        TestUtils.quiesceEmulator();
        SymbolTable.clear();
    }

    private static Path fixture(String name) {
        Path path = Paths.get("src/test/resources/jace/terminal", name);
        assertTrue("Fixture missing: " + path, Files.isReadable(path));
        return path;
    }

    private String run(String command) {
        out.reset();
        mainMode.processCommand(command);
        return out.toString();
    }

    private void loadFixture(String name) {
        String result = run("symbols " + fixture(name));
        assertTrue("symbols command should report a load, got: " + result,
                   result.contains("Loaded"));
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    @Test
    public void symbolsCommandLoadsAcmeSymbollistFormat() throws IOException {
        loadFixture("symfixture.labels");
        assertEquals(Integer.valueOf(MAINLOOP), SymbolTable.getSymbols().get("mainloop"));
        assertEquals(Integer.valueOf(FRAME_COUNT), SymbolTable.getSymbols().get("frame_count"));
    }

    @Test
    public void symbolsCommandLoadsAcmeViceLabelsFormat() {
        loadFixture("symfixture.vice.labels");
        assertEquals(Integer.valueOf(AFTER_DRAW), SymbolTable.getSymbols().get("after_draw"));
        assertEquals(Integer.valueOf(HARRY_X), SymbolTable.getSymbols().get("harry_x"));
    }

    @Test
    public void bothAcmeFormatsAgreeOnEveryAddress() throws IOException {
        assertEquals("acme --symbollist and --vicelabels must describe the same program",
                     SymbolTable.parse(fixture("symfixture.labels")),
                     SymbolTable.parse(fixture("symfixture.vice.labels")));
    }

    @Test
    public void symbolsWithNoArgumentListsLoadedSymbols() {
        loadFixture("symfixture.labels");
        String listing = run("symbols");
        assertTrue(listing, listing.contains("mainloop"));
        assertTrue(listing, listing.contains("$4006"));
    }

    @Test
    public void symbolsClearForgetsEverything() {
        loadFixture("symfixture.labels");
        assertTrue(run("symbols clear").contains("cleared"));
        assertEquals(0, SymbolTable.size());
    }

    // ------------------------------------------------------------------
    // Real commands driven by name
    // ------------------------------------------------------------------

    @Test
    public void memCommandAcceptsSymbolicName() {
        ram.getMainMemory().writeByte(MAINLOOP, MAIN_PATTERN);
        loadFixture("symfixture.labels");

        String byName = run("mem mainloop mainloop");
        String byHex = run("mem 4006 4006");

        assertEquals("A named address must dump exactly what the hex address dumps",
                     byHex, byName);
        assertTrue("Dump should show the byte written at $4006, got: " + byName,
                   byName.toUpperCase().contains("AA"));
    }

    @Test
    public void memauxCommandAcceptsSymbolicZeroPageName() {
        ram.getAuxMemory().writeByte(FRAME_COUNT, AUX_PATTERN);
        loadFixture("symfixture.vice.labels");

        String byName = run("memaux frame_count frame_count");
        assertEquals(run("memaux 1a 1a"), byName);
        assertTrue("Should read the aux byte at $1A, got: " + byName,
                   byName.toUpperCase().contains("55"));
    }

    @Test
    public void breakCommandAcceptsSymbolicNameAndSetsBreakpointAtThatAddress() {
        run("break clear");
        loadFixture("symfixture.labels");

        run("break after_draw");
        String list = run("break");

        assertTrue("Breakpoint should be listed at $4009, got: " + list,
                   list.toUpperCase().contains(String.format("%04X", AFTER_DRAW)));
        run("break clear");
    }

    @Test
    public void goCommandAcceptsSymbolicName() {
        loadFixture("symfixture.labels");
        String result = run("go draw_scene");
        assertTrue("go should report execution at $4012, got: " + result,
                   result.toUpperCase().contains(Integer.toHexString(DRAW_SCENE).toUpperCase()));
    }

    @Test
    public void monitorFillCommandAcceptsSymbolicNamesAndWritesThatAddress() {
        // Monitor's named commands resolve through AddressWithMode, a different code
        // path from MainMode.parseHexAddress. Proven by observing the memory write.
        ram.getMainMemory().writeByte(ENTRY, (byte) 0x00);
        loadFixture("symfixture.labels");

        TestableMonitorMode monitor = new TestableMonitorMode(out);
        out.reset();
        monitor.processCommand("fill entry entry AA");

        assertEquals("fill by name must write the byte at $4000",
                     MAIN_PATTERN, ram.getMainMemory().readByte(ENTRY));
        assertEquals("and must not touch the following byte",
                     (byte) 0x00, ram.getMainMemory().readByte(ENTRY + 1));
    }

    @Test
    public void monitorPathAlsoLetsHexWinOverASymbolNamedLikeHex() {
        // AddressWithMode must apply the same hex-wins rule as MainMode, or the two
        // entry points would disagree about what "abcd" means.
        run("symbols " + fixture("hexnamed.labels"));
        assertEquals(0xABCD, AddressWithMode.parse("abcd", MemoryMode.ACTIVE).getAddress());
        assertEquals(0x1234, AddressWithMode.parse(":abcd", MemoryMode.ACTIVE).getAddress());
    }

    @Test
    public void wozniakPatternSyntaxRemainsHexOnly() {
        // Documented limitation, pinned so it is a decision rather than a surprise:
        // the terse Wozniak forms (<addr>.<addr>, <addr>G, <addr>:<bytes>) are matched
        // by hex-only regexes and are deliberately left alone. Use the named commands
        // (mem / go / poke) when passing a symbol.
        loadFixture("symfixture.labels");
        TestableMonitorMode monitor = new TestableMonitorMode(out);
        out.reset();
        assertFalse("entry.entry should not be recognized as a range dump",
                    monitor.processCommand("entry.entry"));
    }

    // ------------------------------------------------------------------
    // Hex must keep working exactly as before
    // ------------------------------------------------------------------

    @Test
    public void bareHexStillWorksWithNoSymbolsLoaded() {
        ram.getMainMemory().writeByte(0x6000, MAIN_PATTERN);
        assertEquals(0, SymbolTable.size());
        String dump = run("mem 6000 6000");
        assertTrue("Hex input must work with no symbol file loaded, got: " + dump,
                   dump.toUpperCase().contains("AA"));
    }

    @Test
    public void bareHexStillWorksAfterSymbolsAreLoaded() {
        ram.getMainMemory().writeByte(0x6000, MAIN_PATTERN);
        loadFixture("symfixture.labels");
        String dump = run("mem 6000 6000");
        assertTrue("Loading symbols must not disturb hex input, got: " + dump,
                   dump.toUpperCase().contains("AA"));
    }

    @Test
    public void hexWinsOverASymbolNamedLikeHex() {
        // A label literally called "abcd" is valid hex. Hex must win so existing
        // scripts cannot change meaning when a symbol file is loaded.
        run("symbols " + fixture("hexnamed.labels"));
        assertEquals(0x1234, SymbolTable.resolve("abcd"));
        assertEquals("Bare hex token must resolve as hex, not as the same-named symbol",
                     0xABCD, SymbolTable.resolveAddressOrSymbol("abcd", s -> { }));
        assertEquals("Leading ':' selects the symbol",
                     0x1234, SymbolTable.resolveAddressOrSymbol(":abcd", s -> { }));
    }

    @Test
    public void hexSymbolCollisionIsReportedNotSilent() {
        String result = run("symbols " + fixture("hexnamed.labels"));
        assertTrue(result, result.contains("Loaded"));
        String dump = run("mem abcd abcd");
        assertTrue("A hex/symbol collision must be announced, got: " + dump,
                   dump.contains("both valid hex and a symbol"));
    }

    // ------------------------------------------------------------------
    // Failing loudly
    // ------------------------------------------------------------------

    @Test
    public void bogusNameFailsCleanlyRatherThanResolvingToZero() {
        ram.getMainMemory().writeByte(0x0000, MAIN_PATTERN);
        loadFixture("symfixture.labels");

        String result = run("mem no_such_label no_such_label");

        assertTrue("Failure must name the offending symbol, got: " + result,
                   result.contains("no_such_label"));
        assertTrue("Failure must say the symbol is unknown, got: " + result,
                   result.toLowerCase().contains("unknown symbol"));
        assertFalse("A bogus name must not silently dump $0000, got: " + result,
                    result.contains("0000:"));
    }

    @Test
    public void bogusNameFailsCleanlyOnBreakCommand() {
        loadFixture("symfixture.labels");
        String result = run("break no_such_label");
        assertTrue("break with a bogus name must report it, got: " + result,
                   result.contains("no_such_label"));
        String list = run("break");
        assertFalse("No breakpoint may be created at $0000 from a bogus name, got: " + list,
                    list.contains("$0000"));
        run("break clear");
    }

    @Test
    public void nameFailureMessageMentionsHowToLoadSymbolsWhenNoneAreLoaded() {
        assertEquals(0, SymbolTable.size());
        String result = run("mem mainloop mainloop");
        assertTrue("With no symbol file loaded the message should say so, got: " + result,
                   result.contains("no symbol file loaded"));
    }

    @Test
    public void lookupIsCaseSensitiveAndExactWithNoPartialMatching() {
        loadFixture("symfixture.labels");
        assertFalse("Case must not be folded", SymbolTable.isKnown("MAINLOOP"));
        assertFalse("A prefix must not match", SymbolTable.isKnown("main"));
        assertFalse("A suffix must not match", SymbolTable.isKnown("loop"));
        assertTrue(SymbolTable.isKnown("mainloop"));

        String result = run("mem mainloo mainloo");
        assertTrue("A near-miss must fail rather than resolve to the near neighbour, got: "
                   + result, result.toLowerCase().contains("unknown symbol"));
    }

    @Test
    public void conflictingDefinitionsOfOneNameAreAmbiguousNotLastOneWins() {
        loadFixture("symfixture.labels");
        run("symbols " + fixture("conflict.labels"));

        String result = run("mem mainloop mainloop");
        assertTrue("A name defined at two addresses must fail as ambiguous, got: " + result,
                   result.toLowerCase().contains("ambiguous symbol"));
        assertFalse("An ambiguous name must not resolve", SymbolTable.isKnown("mainloop"));
    }

    @Test
    public void loadingANonSymbolFileReportsItRatherThanLoadingNothingQuietly() throws IOException {
        Path notSymbols = Files.createTempFile("jace-not-symbols", ".txt");
        try {
            Files.write(notSymbols, java.util.List.of("this file", "has no labels"));
            String result = run("symbols " + notSymbols);
            assertTrue("Should say no symbols were found, got: " + result,
                       result.contains("No symbols found"));
        } finally {
            Files.deleteIfExists(notSymbols);
        }
    }

    @Test
    public void missingSymbolFileIsReported() {
        String result = run("symbols /nonexistent/path/to/labels.txt");
        assertTrue(result, result.contains("not found or not readable"));
    }
}
