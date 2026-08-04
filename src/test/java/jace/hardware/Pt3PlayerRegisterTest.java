package jace.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.AbstractJaceTest;
import jace.apple2e.MOS65C02;
import jace.core.RAMListener;

/**
 * Integration test: runs the 6502 Apple II PT3 player (from vt3's
 * {@code apple2/}) under full Jace emulation — real RAM, real Mockingboard,
 * real R6522 Timer&nbsp;1 IRQ — and compares the AY register values it commits
 * to hardware against the reference values produced by {@code vt3-cli
 * --regsraw}.
 *
 * <h3>What this proves</h3>
 * Agreement over hundreds of frames exercises the whole write path:
 * 6502 execution, slot-4 I/O decoding, R6522 ORA/ORB/DDR handling, the AY
 * BDIR/BC1 bus-control state machine, and register latching. It does
 * <em>not</em> validate the PSG's internal sound generation (tone/noise/
 * envelope waveform math) — identical register values can still produce wrong
 * audio. See {@code jace.hardware.mockingboard.*Test} for that.
 *
 * <h3>Fixtures</h3>
 * All artifacts are resolved by {@link Pt3Fixtures} from the vt3 checkout
 * (override with {@code -Dvt3.home=...} or {@code $VT3_HOME}). No scratch or
 * temp directory is consulted. If an artifact is genuinely absent the test
 * <em>skips</em> with a message naming the command that produces it.
 *
 * <h3>Chip 2 coverage</h3>
 * The 6502 player only has an {@code emit_regs_chip1} debug-output routine, so
 * chip 2 is captured by snapshotting {@code SHADOW2_CMIT} out of emulated RAM
 * at each {@code irq_exit}. {@code SHADOW1_CMIT} is snapshotted the same way
 * and cross-checked against the emit stream, which validates the snapshot
 * technique itself.
 */
public class Pt3PlayerRegisterTest extends AbstractJaceTest {

    private static final int PLAYER_BASE = 0x0800;
    /**
     * Where the 6502 player reads its song from. This is a DUPLICATE of
     * {@code SONG_LOAD_ADDR} in {@code <vt3>/apple2/zp.s}, which is the single
     * source of truth — the player's {@code test_entry} seeds {@code FILE_PTR}
     * from it, so this value must match or the song is loaded somewhere the
     * player never looks and the register comparison measures that
     * disagreement instead of the player.
     *
     * <p>Derived at runtime from the {@code SONG_LOAD_ADDR} label in the ACME
     * label file that accompanies the loaded player image, so it cannot drift
     * from vt3 again. It was previously hardcoded here and was missed when vt3
     * lowered the address from $3000 to $2700.
     */
    private static int PT3_BASE;
    private static final int FRAMES = Integer.getInteger("pt3.frames", 300);
    private static final int AY_REG_COUNT = 14;

    /** Ticks to run. ~20455 CPU cycles per 50 Hz frame, plus generous headroom. */
    private static final int TOTAL_TICKS = Integer.getInteger("pt3.totalTicks", 8_000_000);

    // Resolved fixtures
    private static Path playerBin;
    private static Path pt3File;
    private static Path vt3Cli;

    // Addresses from the ACME label file
    private static int addrTestEntry;
    private static int addrPlrFlags;
    private static int addrIrqHandler;
    private static int addrIrqExit;
    private static int addrShadow1Cmit;
    private static int addrShadow2Cmit;

    private ByteArrayOutputStream captureStream;
    private PrintStream originalDebugOut;
    private final List<RAMListener> trapListeners = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Fixture resolution + label parsing
    // -------------------------------------------------------------------------
    @BeforeClass
    public static void resolveFixturesAndLabels() throws Exception {
        // emit_regs_chip1 only exists in the -DREGTRACE=1 build.
        playerBin = require(Pt3Fixtures.findRegtracePlayerBinary(),
                            "apple2/build/player_regtrace.bin",
                            "run 'make -C apple2 regtrace' in the vt3 checkout");
        Path labels = Pt3Fixtures.labelsFor(playerBin);
        require(Pt3Fixtures.firstExisting(java.util.List.of(labels)),
                labels.getFileName().toString(),
                "run 'make -C apple2 regtrace' in the vt3 checkout");
        String songName = System.getProperty("pt3.song", Pt3Fixtures.DEFAULT_PT3_NAME);
        pt3File = require(Pt3Fixtures.findPt3(songName),
                          songName,
                          "place it in jace's src/test/resources/pt3/ or vt3's apple2/songs/");
        vt3Cli = require(Pt3Fixtures.findVt3Cli(), "target/release/vt3-cli",
                         "run 'cargo build --release' in the vt3 checkout");

        // vt3's Makefile emits ACME --vicelabels format ("al C:2298 .test_entry").
        // AcmeLabelParser accepts that as well as the plain "name = $XXXX" form.
        Map<String, Integer> l = AcmeLabelParser.parse(labels);
        addrTestEntry = requireLabel(l, "test_entry", labels);
        addrPlrFlags = requireLabel(l, "PLR_FLAGS", labels);
        addrIrqHandler = requireLabel(l, "irq_handler", labels);
        addrIrqExit = requireLabel(l, "irq_exit", labels);
        addrShadow1Cmit = requireLabel(l, "SHADOW1_CMIT", labels);
        addrShadow2Cmit = requireLabel(l, "SHADOW2_CMIT", labels);
        // Single source of truth: SONG_LOAD_ADDR in <vt3>/apple2/zp.s.
        PT3_BASE = requireLabel(l, "SONG_LOAD_ADDR", labels);
        assumeTrue("player image " + playerBin + " lacks emit_regs_chip1; "
                   + "run 'make -C apple2 regtrace' in the vt3 checkout",
                   l.containsKey("emit_regs_chip1"));

        System.out.printf(
            "Fixtures: player=%s pt3=%s cli=%s%n"
            + "Labels: test_entry=$%04X irq_handler=$%04X irq_exit=$%04X "
            + "PLR_FLAGS=$%04X SHADOW1_CMIT=$%04X SHADOW2_CMIT=$%04X%n",
            playerBin, pt3File, vt3Cli,
            addrTestEntry, addrIrqHandler, addrIrqExit,
            addrPlrFlags, addrShadow1Cmit, addrShadow2Cmit);
    }

    /** Skip (not fail) with an actionable message when an artifact is missing. */
    private static Path require(java.util.Optional<Path> found, String artifact, String remedy) {
        assumeTrue(Pt3Fixtures.missingMessage(artifact, remedy), found.isPresent());
        return found.get();
    }

    private static int requireLabel(Map<String, Integer> labels, String name, Path labelFile) {
        Integer addr = labels.get(name);
        assertTrue("Label '" + name + "' not found in " + labelFile
                   + " (parsed " + labels.size() + " labels). "
                   + "If the assembler's label format changed, update AcmeLabelParser.",
                   addr != null);
        return addr;
    }

    // -------------------------------------------------------------------------
    // Load binaries, wire up capture, leave the motherboard suspended
    // -------------------------------------------------------------------------
    @Before
    public void loadBinariesAndConfigure() throws Exception {
        // commonSetup() already called cpu.resume(), which starts executing
        // whatever is in RAM (zeros == a BRK stream). That races with our binary
        // load and can clobber the IRQ vector. Stop everything first.
        cpu.suspend();
        computer.getMotherboard().suspend();

        // In test mode SoundMixer.MUTE is true, so the Mockingboard's sound
        // buffer is null, playSound() always returns false, and idleTicks climbs
        // to MAX_IDLE_TICKS — which suspends the card and stops R6522 timer
        // ticks. Nothing re-activates it without firmware access, so raise the
        // threshold out of reach.
        if (ram.getCard(4).isPresent() && ram.getCard(4).get() instanceof CardMockingboard mb4) {
            mb4.MAX_IDLE_TICKS = Integer.MAX_VALUE;
        }

        // ACME --format cbm prepends a 2-byte little-endian load address.
        byte[] player = Files.readAllBytes(playerBin);
        int loadAddr = (player[0] & 0xFF) | ((player[1] & 0xFF) << 8);
        assertEquals("player.bin should be linked for $" + Integer.toHexString(PLAYER_BASE),
                     PLAYER_BASE, loadAddr);
        for (int i = 2; i < player.length; i++) {
            ram.write(loadAddr + (i - 2), player[i], false, false);
        }

        byte[] pt3 = Files.readAllBytes(pt3File);
        for (int i = 0; i < pt3.length; i++) {
            ram.write(PT3_BASE + i, pt3[i], false, false);
        }
        System.out.printf("Loaded player (%d bytes @ $%04X) and PT3 (%d bytes @ $%04X)%n",
                          player.length - 2, loadAddr, pt3.length, PT3_BASE);

        // Point the hardware IRQ vector straight at irq_handler. Jace's
        // apple2e.rom vectors to $C3FA, which lands in slot-3 ROM space and
        // executes junk. Real hardware would dispatch via $3FE, but this test is
        // about the Mockingboard write path, not ROM IRQ dispatch.
        byte[] romPage = ram.activeRead.getMemoryPage(0xFFFE);
        assertTrue("ROM page at $FF must be present to patch the IRQ vector", romPage != null);
        romPage[0xFE] = (byte) (addrIrqHandler & 0xFF);
        romPage[0xFF] = (byte) ((addrIrqHandler >> 8) & 0xFF);

        captureStream = new ByteArrayOutputStream();
        originalDebugOut = MOS65C02.debugOut;
        MOS65C02.debugOut = new PrintStream(captureStream, true);
    }

    @org.junit.After
    public void cleanup() {
        if (originalDebugOut != null) {
            MOS65C02.debugOut = originalDebugOut;
        }
        trapListeners.forEach(ram::removeListener);
        trapListeners.clear();
    }

    private int readRam(int addr) {
        return ram.read(addr, jace.core.RAMEvent.TYPE.READ_DATA, false, false) & 0xFF;
    }

    private byte[] readShadow(int base) {
        byte[] regs = new byte[AY_REG_COUNT];
        for (int i = 0; i < AY_REG_COUNT; i++) {
            regs[i] = (byte) readRam(base + i);
        }
        return regs;
    }

    // -------------------------------------------------------------------------
    // The run: execute the player and collect one frame per IRQ
    // -------------------------------------------------------------------------
    private static final class RunResult {
        List<byte[]> emitted;          // from emit_regs_chip1 via $FC $5E
        final List<byte[]> shadow1 = new ArrayList<>();
        final List<byte[]> shadow2 = new ArrayList<>();
        final List<Integer> irqTickStamps = new ArrayList<>();
        int irqEnterCount;
        int plrFlags;
    }

    private RunResult runPlayer() {
        RunResult result = new RunResult();
        AtomicInteger irqEnters = new AtomicInteger();
        AtomicInteger tick = new AtomicInteger();

        trapListeners.add(ram.addExecutionTrap("irq_entry", addrIrqHandler, e -> {
            irqEnters.incrementAndGet();
            result.irqTickStamps.add(tick.get());
        }));
        // At irq_exit both chips' committed shadows hold this frame's values:
        // dirty_write_chip1, emit_regs_chip1 and dirty_write_chip2 have all run.
        trapListeners.add(ram.addExecutionTrap("irq_exit", addrIrqExit, e -> {
            result.shadow1.add(readShadow(addrShadow1Cmit));
            result.shadow2.add(readShadow(addrShadow2Cmit));
        }));

        cpu.setProgramCounter(addrTestEntry);
        cpu.I = false;
        cpu.resume();

        // Tick the CPU and the Mockingboard in lockstep rather than resuming the
        // Motherboard, whose background thread would race this loop. One
        // motherboard tick drives one CPU cycle and one R6522 cycle, which is
        // the real Φ2 relationship.
        CardMockingboard mockingboard = null;
        if (ram.getCard(4).isPresent() && ram.getCard(4).get() instanceof CardMockingboard mb4) {
            mockingboard = mb4;
            mockingboard.resume();
        }

        for (int i = 0; i < TOTAL_TICKS; i++) {
            tick.set(i);
            cpu.doTick();
            if (mockingboard != null) {
                mockingboard.doTick();
            }
        }

        computer.getMotherboard().suspend();
        result.irqEnterCount = irqEnters.get();
        result.plrFlags = readRam(addrPlrFlags);
        result.emitted = parseFrames(captureStream.toString());
        String dump = System.getProperty("pt3.dump", "");
        if (!dump.isBlank()) {
            try {
                StringBuilder sb = new StringBuilder();
                for (byte[] f : result.shadow1) { sb.append(hex(f)).append('\n'); }
                java.nio.file.Files.writeString(Path.of(dump + ".chip1"), sb.toString());
                sb.setLength(0);
                for (byte[] f : result.shadow2) { sb.append(hex(f)).append('\n'); }
                java.nio.file.Files.writeString(Path.of(dump + ".chip2"), sb.toString());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Test: chip 1 register frames must match vt3-cli exactly
    // -------------------------------------------------------------------------
    @Test
    public void chip1AyRegistersMatchVt3Cli() throws Exception {
        RunResult run = runPlayer();

        assertTrue("PLR_FLAGS bit1 (playing) should be set after init; got $"
                   + Integer.toHexString(run.plrFlags),
                   (run.plrFlags & 0x02) != 0);

        System.out.printf("Apple II: %d emitted frames, %d IRQs, %d shadow snapshots%n",
                          run.emitted.size(), run.irqEnterCount, run.shadow1.size());
        assertTrue("Expected at least " + FRAMES + " register frames from the Apple II player; got "
                   + run.emitted.size(), run.emitted.size() >= FRAMES);

        List<byte[]> reference = getVt3CliFrames(FRAMES, 1);
        assertEquals("vt3-cli should yield " + FRAMES + " frames", FRAMES, reference.size());

        assertFramesMatch("chip 1", run.emitted, reference);
    }

    // -------------------------------------------------------------------------
    // Test: chip 2 (the TS subfile) register frames must match vt3-cli exactly
    // -------------------------------------------------------------------------
    @Test
    public void chip2AyRegistersMatchVt3Cli() throws Exception {
        RunResult run = runPlayer();

        assertTrue("Fixture must be a TS file for chip-2 coverage: PLR_FLAGS bit0 should be set; got $"
                   + Integer.toHexString(run.plrFlags), (run.plrFlags & 0x01) != 0);
        assertTrue("Expected at least " + FRAMES + " shadow snapshots; got " + run.shadow2.size(),
                   run.shadow2.size() >= FRAMES);

        List<byte[]> reference = getVt3CliFrames(FRAMES, 2);
        assertEquals(FRAMES, reference.size());

        assertFramesMatch("chip 2", run.shadow2, reference);
    }

    // -------------------------------------------------------------------------
    // Test: the RAM-snapshot technique agrees with the player's own emit stream.
    // Without this, a chip-2 mismatch could be blamed on the snapshot method.
    // -------------------------------------------------------------------------
    @Test
    public void shadowSnapshotAgreesWithPlayerEmitStream() {
        RunResult run = runPlayer();
        assertTrue("Need frames from both capture paths",
                   run.emitted.size() >= FRAMES && run.shadow1.size() >= FRAMES);
        assertFramesMatch("chip 1 shadow-snapshot vs emit-stream", run.shadow1, run.emitted);
    }

    // -------------------------------------------------------------------------
    // Test: the R6522 Timer 1 IRQ must actually fire at ~50 Hz.
    //
    // The register-frame comparison above is blind to this: a timer running at
    // the wrong rate still produces correct register *values*, just at the wrong
    // *tempo*. The player programs T1 with $4FE7 = 20455 for 50 Hz.
    // -------------------------------------------------------------------------
    @Test
    public void timer1IrqFiresAtFiftyHertz() {
        RunResult run = runPlayer();
        assertTrue("Need at least 3 IRQs to measure an interval; got " + run.irqEnterCount,
                   run.irqTickStamps.size() >= 3);

        // Skip the first interval: it starts when the player enables the timer,
        // not at a frame boundary.
        List<Integer> stamps = run.irqTickStamps;
        long span = stamps.get(stamps.size() - 1) - (long) stamps.get(1);
        double avgInterval = (double) span / (stamps.size() - 2);

        int programmedLatch = 0x4FE7;
        System.out.printf("Timer 1: %d IRQs, mean interval %.1f CPU cycles (T1 latch=%d)%n",
                          run.irqEnterCount, avgInterval, programmedLatch);

        // Real 6522 free-run T1 period is latch+2 cycles. Allow 0.5% for the
        // handler's own read-of-T1CL and loop overhead.
        double tolerance = programmedLatch * 0.005;
        assertTrue(String.format(
            "Mean T1 IRQ interval %.1f cycles is not within %.0f of the programmed %d "
            + "(a wrong timer period makes tempo wrong even when every register value is right)",
            avgInterval, tolerance, programmedLatch),
            Math.abs(avgInterval - programmedLatch) <= tolerance);
    }

    // -------------------------------------------------------------------------
    // Comparison helper — reports the first mismatch with surrounding context
    // -------------------------------------------------------------------------
    private static final String[] REG_NAMES = {
        "R0 ToneA-lo", "R1 ToneA-hi", "R2 ToneB-lo", "R3 ToneB-hi",
        "R4 ToneC-lo", "R5 ToneC-hi", "R6 Noise", "R7 Mixer",
        "R8 AmpA", "R9 AmpB", "R10 AmpC",
        "R11 Env-lo", "R12 Env-hi", "R13 EnvType"
    };

    private static void assertFramesMatch(String what, List<byte[]> actual, List<byte[]> expected) {
        int compared = Math.min(FRAMES, Math.min(actual.size(), expected.size()));
        for (int f = 0; f < compared; f++) {
            byte[] a = actual.get(f);
            byte[] e = expected.get(f);
            for (int r = 0; r < AY_REG_COUNT; r++) {
                if ((a[r] & 0xFF) != (e[r] & 0xFF)) {
                    fail(String.format(
                        "%s frame %d %s mismatch: Apple2=$%02X reference=$%02X%n"
                        + "  Apple2    : %s%n  reference : %s%n"
                        + "  (%d of %d frames compared; %d matched before this one)",
                        what, f, REG_NAMES[r], a[r] & 0xFF, e[r] & 0xFF,
                        hex(a), hex(e), compared, FRAMES, f));
                }
            }
        }
        System.out.printf("%s: %d frames x %d registers matched exactly%n",
                          what, compared, AY_REG_COUNT);
    }

    private static String hex(byte[] regs) {
        StringBuilder sb = new StringBuilder();
        for (byte b : regs) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------
    // Parsing / reference generation
    // -------------------------------------------------------------------------
    private static List<byte[]> parseFrames(String output) {
        List<byte[]> frames = new ArrayList<>();
        for (String rawLine : output.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            if (tokens.length != AY_REG_COUNT) {
                continue;
            }
            byte[] regs = new byte[AY_REG_COUNT];
            boolean valid = true;
            for (int i = 0; i < AY_REG_COUNT; i++) {
                try {
                    regs[i] = (byte) Integer.parseInt(tokens[i], 16);
                } catch (NumberFormatException e) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                frames.add(regs);
            }
        }
        return frames;
    }

    /**
     * {@code vt3-cli --regsraw <count> --regschip <chip> --apple2 <file>}
     *
     * <p>{@code --apple2} is essential. Without it vt3-cli renders with the ZX
     * Spectrum's 1773400 Hz clock and note table; the Mockingboard's AY runs at the
     * 1022727 Hz slot bus clock (14318181.8 / 14), which is 0.577x as fast, so a
     * ZX-derived tone period sounds 9.55 semitones flat. The 6502 player carries an
     * Apple II note table, so the reference must be generated in the same units or
     * every tone register disagrees.
     *
     * <p><b>Stale-artifact trap.</b> This test consumes three separately-built
     * artifacts — {@code apple2/build/player.bin}, {@code apple2/build/labels.txt}
     * and {@code target/release/vt3-cli}. If the note table changes and only some
     * are rebuilt, you get scattered off-by-one <i>tone</i> failures that look like
     * player bugs. Both sides derive their table from the same clock, so run
     * {@code make -C <vt3>/apple2} and {@code cargo build --release} before
     * believing any tone-register mismatch. Amplitude (R8/R9/R10) mismatches are a
     * separate, genuine, still-open 6502-side finding.
     */
    private static List<byte[]> getVt3CliFrames(int count, int chip)
            throws IOException, InterruptedException {
        Process proc = new ProcessBuilder(
                vt3Cli.toString(), "--regsraw", String.valueOf(count),
                "--regschip", String.valueOf(chip), "--apple2", pt3File.toString())
            .redirectErrorStream(false)
            .start();
        String stdout = new String(proc.getInputStream().readAllBytes());
        String stderr = new String(proc.getErrorStream().readAllBytes());
        int exit = proc.waitFor();
        assertEquals("vt3-cli --regsraw --regschip " + chip + " --apple2 exited with " + exit
                     + "; stderr: " + stderr
                     + " (if --apple2 is unrecognized, rebuild vt3-cli with"
                     + " 'cargo build --release')", 0, exit);
        return parseFrames(stdout);
    }
}
