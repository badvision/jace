package jace.terminal;

import static jace.TestUtils.initComputer;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.TestUtils;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.core.Computer;
import jace.core.PagedMemory;
import jace.core.SoundMixer;
import jace.hardware.CardRamworks;

/**
 * Drives the monitor's memory-dump and memory-write commands against zero page
 * ($0000-$00FF), which is banked by the AUXZP soft switch rather than by the
 * plain RAMRD/RAMWRT main/aux split that covers $0200-$BFFF.
 *
 * The reported defect was that `mem`, `memaux` and `memmain` each returned a
 * different value for zero-page $1A and none matched what the running program
 * had stored there, and that `fill` reported success without the write landing.
 * The suspicion was that MonitorMode.resolveBank() needs a special case below
 * $0200, or that PagedMemory's base-address arithmetic makes page 0 unreachable
 * through the mainMemory/auxMemory objects.
 *
 * These tests pin the actual behaviour of that path so the claim is measured
 * rather than assumed. See also testRamworksAuxBankIsMemoryPatternInitialized,
 * which covers the one genuine defect this investigation did find: a Ramworks
 * aux bank reads back as all zeros instead of the uninitialized-RAM pattern,
 * which is what makes `memaux` report `00 00 00 00` for zero page.
 *
 * @author brobert
 */
public class MonitorModeZeroPageTest {

    /** Zero-page location from the defect report. */
    private static final int ZP_ADDR = 0x1A;
    /** Stack page, the other half of the AUXZP-banked region. */
    private static final int STACK_ADDR = 0x01C0;
    /** The byte the running program was known to have stored at $1A. */
    private static final byte KNOWN_VALUE = (byte) 0x38;
    /** A second, distinct value, for proving a write actually replaced the first. */
    private static final byte WRITTEN_VALUE = (byte) 0x99;

    private static RAM128k ram;

    private ByteArrayOutputStream outputStream;
    private TestableMonitorMode monitorMode;
    private boolean originalAuxZp;

    @BeforeClass
    public static void setupClass() {
        // Several test classes in this package fail spuriously in headless mode when
        // they skip this -- it sets headless mode and reconfigures the computer.
        initComputer();
        SoundMixer.MUTE = true;
        Computer computer = Emulator.withComputer(c -> c, null);
        ram = (RAM128k) computer.getMemory();
    }

    @Before
    public void setup() {
        // These tests set softswitches and bank contents and then assert on them, so
        // nothing may be executing emulated code concurrently -- a previous test class
        // can leave the motherboard's worker thread running, and the //e ROM will then
        // flip AUXZP and overwrite zero page behind the test's back.
        TestUtils.quiesceEmulator();
        outputStream = new ByteArrayOutputStream();
        monitorMode = new TestableMonitorMode(outputStream);
        originalAuxZp = SoftSwitches.AUXZP.getState();
    }

    @org.junit.After
    public void teardown() {
        SoftSwitches.AUXZP.getSwitch().setState(originalAuxZp);
        ram.configureActiveMemory();
    }

    private void setAuxZeroPage(boolean auxzp) {
        SoftSwitches.AUXZP.getSwitch().setState(auxzp);
        ram.configureActiveMemory();
    }

    /** The bank AUXZP currently maps zero page and the stack page to. */
    private PagedMemory bankedZeroPage() {
        return SoftSwitches.AUXZP.getState() ? ram.getAuxMemory() : ram.getMainMemory();
    }

    private String dump(int address, MemoryMode mode) {
        outputStream.reset();
        monitorMode.examineMemoryRange(address, address, mode);
        return firstByteOf(outputStream.toString());
    }

    private static String firstByteOf(String dumpLine) {
        // Format is "001A: 38                                              | 8"
        int colon = dumpLine.indexOf(':');
        return dumpLine.substring(colon + 1).trim().substring(0, 2);
    }

    private static String hex(byte value) {
        return String.format("%02X", value & 0xFF);
    }

    // ---------------------------------------------------------------- read path

    /**
     * With AUXZP off, zero page is served from main. A dump of $1A must report the
     * byte the CPU would read there, through both the active configuration and the
     * explicit MAIN selector.
     */
    @Test
    public void zeroPageReadFollowsMainBankWhenAuxZpOff() {
        setAuxZeroPage(false);
        ram.getMainMemory().writeByte(ZP_ADDR, KNOWN_VALUE);

        assertEquals("CPU's own view of $1A", hex(KNOWN_VALUE), hex(ram.readRaw(ZP_ADDR)));
        assertEquals("mem (ACTIVE) at $1A", hex(KNOWN_VALUE), dump(ZP_ADDR, MemoryMode.ACTIVE));
        assertEquals("memmain at $1A", hex(KNOWN_VALUE), dump(ZP_ADDR, MemoryMode.MAIN));
    }

    /**
     * With AUXZP on, zero page is served from aux. The explicit AUX selector and the
     * active configuration must both report the aux byte, and MAIN must still report
     * main -- an explicit selector names a physical bank, not the mapped one.
     */
    @Test
    public void zeroPageReadFollowsAuxBankWhenAuxZpOn() {
        setAuxZeroPage(true);
        ram.getAuxMemory().writeByte(ZP_ADDR, KNOWN_VALUE);
        ram.getMainMemory().writeByte(ZP_ADDR, WRITTEN_VALUE);

        assertEquals("CPU's own view of $1A", hex(KNOWN_VALUE), hex(ram.readRaw(ZP_ADDR)));
        assertEquals("mem (ACTIVE) at $1A", hex(KNOWN_VALUE), dump(ZP_ADDR, MemoryMode.ACTIVE));
        assertEquals("memaux at $1A", hex(KNOWN_VALUE), dump(ZP_ADDR, MemoryMode.AUX));
        assertEquals("memmain at $1A", hex(WRITTEN_VALUE), dump(ZP_ADDR, MemoryMode.MAIN));
    }

    /**
     * The stack page ($0100-$01FF) is banked by AUXZP alongside zero page, so it must
     * resolve the same way. Covers the second page of the setBanks(0, 2, 0, ...) span.
     */
    @Test
    public void stackPageReadResolvesSameBankAsZeroPage() {
        for (boolean auxzp : new boolean[] { false, true }) {
            setAuxZeroPage(auxzp);
            bankedZeroPage().writeByte(STACK_ADDR, KNOWN_VALUE);

            assertEquals("CPU's own view of $01C0 with AUXZP=" + auxzp,
                    hex(KNOWN_VALUE), hex(ram.readRaw(STACK_ADDR)));
            assertEquals("mem (ACTIVE) at $01C0 with AUXZP=" + auxzp,
                    hex(KNOWN_VALUE), dump(STACK_ADDR, MemoryMode.ACTIVE));
            assertEquals("explicit selector at $01C0 with AUXZP=" + auxzp, hex(KNOWN_VALUE),
                    dump(STACK_ADDR, auxzp ? MemoryMode.AUX : MemoryMode.MAIN));
        }
    }

    /**
     * The three dump commands must not disagree about a byte that only exists in one
     * bank. This is the exact shape of the defect report: `mem`, `memmain` and
     * `memaux` returning three different answers for $1A.
     */
    @Test
    public void memAndMemmainAgreeOnZeroPageWhenAuxZpOff() {
        setAuxZeroPage(false);
        ram.getMainMemory().writeByte(ZP_ADDR, KNOWN_VALUE);

        String active = dump(ZP_ADDR, MemoryMode.ACTIVE);
        String main = dump(ZP_ADDR, MemoryMode.MAIN);
        assertEquals("mem and memmain must agree when AUXZP maps main", main, active);
        assertEquals(hex(KNOWN_VALUE), active);
    }

    // --------------------------------------------------------------- write path

    /**
     * A monitor write to zero page must land where the CPU will read it. This covers
     * the `fill 1a 1d 38` reported as succeeding without taking effect.
     */
    @Test
    public void monitorFillOfZeroPageIsVisibleToCpu() {
        for (boolean auxzp : new boolean[] { false, true }) {
            setAuxZeroPage(auxzp);
            bankedZeroPage().writeByte(ZP_ADDR, KNOWN_VALUE);

            outputStream.reset();
            monitorMode.processCommand("fill 1a 1d " + hex(WRITTEN_VALUE));

            assertEquals("CPU's view of $1A after fill, AUXZP=" + auxzp,
                    hex(WRITTEN_VALUE), hex(ram.readRaw(ZP_ADDR)));
            assertEquals("CPU's view of $1D after fill, AUXZP=" + auxzp,
                    hex(WRITTEN_VALUE), hex(ram.readRaw(0x1D)));
            assertEquals("dump after fill, AUXZP=" + auxzp,
                    hex(WRITTEN_VALUE), dump(ZP_ADDR, MemoryMode.ACTIVE));
        }
    }

    /**
     * A monitor write through an explicit bank selector must reach that physical bank
     * and must not disturb the other one.
     */
    @Test
    public void monitorWriteToExplicitBankHitsThatBankOnly() {
        setAuxZeroPage(false);
        ram.getMainMemory().writeByte(ZP_ADDR, KNOWN_VALUE);
        ram.getAuxMemory().writeByte(ZP_ADDR, KNOWN_VALUE);

        // "X" selects aux, per determineMemoryMode
        monitorMode.processCommand("X1A: " + hex(WRITTEN_VALUE));

        assertEquals("aux $1A after explicit aux write",
                hex(WRITTEN_VALUE), hex(ram.getAuxMemory().readByte(ZP_ADDR)));
        assertEquals("main $1A must be untouched",
                hex(KNOWN_VALUE), hex(ram.getMainMemory().readByte(ZP_ADDR)));
    }

    // --------------------------------------------- uninitialized-RAM fill pattern

    /**
     * Uninitialized RAM must read back as the FF FF 00 00 pattern that
     * RAM128k.initMemoryPattern() lays down, in every Ramworks aux bank -- not as
     * all zeros.
     *
     * This is the measured defect. CardRamworks.generateBank() creates a bank's
     * PagedMemory without ever pattern-initializing it, and RAM128k.zeroAllRam()
     * only reaches getAuxMemory(), i.e. the bank that happens to be selected at that
     * moment. So a `memaux` of zero page reports `00 00 00 00 00 00 00 00` -- an
     * all-zero bank -- which is indistinguishable from real program data that
     * happens to be zero, and does not match the pattern main memory shows.
     */
    @Test
    public void ramworksAuxBankIsMemoryPatternInitialized() {
        org.junit.Assume.assumeTrue("Only applies to the Ramworks card", ram instanceof CardRamworks);
        CardRamworks ramworks = (CardRamworks) ram;

        // A freshly constructed card, so no cold start has run zeroAllRam() over it.
        CardRamworks fresh = new CardRamworks();
        int originalBank = ramworks.currentBank;
        try {
            for (int bank : new int[] { 0, 1, 3 }) {
                fresh.currentBank = bank;
                PagedMemory aux = fresh.getAuxMemory();
                // initMemoryPattern lays down FF FF 00 00 by offset: (i % 4) > 1 -> FF.
                assertEquals("aux bank " + bank + " page 0 offset $1A", "FF",
                        hex(aux.get(0)[0x1A]));
                assertEquals("aux bank " + bank + " page 0 offset $1B", "FF",
                        hex(aux.get(0)[0x1B]));
                assertEquals("aux bank " + bank + " page 0 offset $1C", "00",
                        hex(aux.get(0)[0x1C]));
                assertEquals("aux bank " + bank + " page 0 offset $1D", "00",
                        hex(aux.get(0)[0x1D]));
            }
        } finally {
            ramworks.currentBank = originalBank;
            ram.configureActiveMemory();
        }
    }
}
