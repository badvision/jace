package jace.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.Emulator;
import jace.TestUtils;
import jace.core.Motherboard;
import jace.core.Utility;

/**
 * Covers the terminal command layer for VBL synchronization: the bare `runvbl`
 * primitive, and the opt-in `--vbl` option on `screenshot`.
 *
 * The behavioural contract that matters most here is the NEGATIVE one --
 * `screenshot <path>` with no flag must still capture immediately, because
 * existing automation depends on it. A regression making synchronization
 * unconditional would be invisible in a test that only exercised `--vbl`.
 *
 * SCOPE, deliberately narrow. These tests assert on command WIRING -- that the
 * command is registered, that it takes (or does not take) the synchronization
 * path, and that it leaves the machine halted. They do NOT assert on the
 * machine's frame position. VblankSyncTest owns that, and does it against a
 * privately-owned motherboard and scanner.
 *
 * The reason is that these tests necessarily drive the shared Emulator machine,
 * because that is what the commands operate on -- and the shared machine's
 * scanner phase is process-global. Measured: a probe found the global VBL state
 * changing 35 times in 300ms while this test thread sat idle, driven by an
 * emulator worker thread that TestUtils.quiesceEmulator() could not reach
 * because nothing held a reference to its motherboard any more. An assertion on
 * frame position here would therefore be reporting the suite's thread hygiene,
 * not this feature's correctness. The observable used instead -- what the
 * command printed -- cannot be perturbed by another thread.
 */
public class VblankCommandTest extends AbstractFXTest {

    private TestableMainMode mainMode;
    private ByteArrayOutputStream out;
    private Motherboard motherboard;
    private boolean priorVideoEnabled;

    @Before
    public void setUpCommandMode() {
        priorVideoEnabled = Utility.isVideoEnabled();
        Utility.setVideoEnabled(true);
        // Stop any emulator worker thread a previous test class left running
        // before touching the shared machine. This does not catch every case
        // (see the class comment), which is why nothing below depends on frame
        // position -- but leaving a known ticker running would be worse.
        TestUtils.quiesceEmulator();
        // A real scanner with the JavaFX repaint suppressed. Needed because the
        // sync will not report success unless the video device actually
        // advances, so a no-op mock would make every test here fail.
        TestUtils.setupMockVideo(VblankSyncTest.ScannerOnlyVideoDHGR.class);
        motherboard = Emulator.withComputer(c -> c.getMotherboard(), null);
        motherboard.suspend();

        out = new ByteArrayOutputStream();
        mainMode = new TestableMainMode(out);
    }

    @After
    public void tearDownCommandMode() {
        // These tests run commands that resume the machine; leaving it running
        // would make this class the thing that perturbs the next one.
        TestUtils.quiesceEmulator();
        Utility.setVideoEnabled(priorVideoEnabled);
    }

    private String run(String command) {
        out.reset();
        assertTrue("command should be recognized: " + command,
                mainMode.processCommand(command));
        return out.toString();
    }

    // ---- runvbl ---------------------------------------------------------

    @Test
    public void runvblIsRegisteredAndReportsASuccessfulSync() {
        String output = run("runvbl");

        // "Synced to VBL after N cycles" is printed only on the success path;
        // the failure path prints "Could not reach a VBL edge" and nothing else.
        // So this distinguishes the two, which a bare "command was recognized"
        // assertion would not.
        assertTrue("runvbl should report a completed sync, got: " + output,
                output.contains("Synced to VBL after "));
        assertFalse("runvbl should not have reported failure, got: " + output,
                output.contains("Could not reach"));
    }

    @Test
    public void runvblAliasWorks() {
        String output = run("rv");

        assertTrue("rv alias must sync just like runvbl, got: " + output,
                output.contains("Synced to VBL after "));
    }

    @Test
    public void runvblHaltsTheMachine() {
        // The point of the primitive is that a dump issued after it is not
        // racing the emulator, so a halted machine is part of the contract.
        run("runvbl");

        assertFalse("machine must be halted so a following dump is stable",
                motherboard.isRunning());
    }

    @Test
    public void runvblReportsTheAvailableBlankingWindow() {
        // The operator needs to know how much time the dump has; reporting the
        // interval is how the command is self-documenting at the prompt.
        String output = run("runvbl");

        assertTrue("should report the blanking budget, got: " + output,
                output.contains(String.valueOf(VblankSync.VBLANK_CYCLES)));
    }

    // ---- screenshot --vbl ------------------------------------------------

    @Test
    public void screenshotWithVblFlagSyncsFirst() throws Exception {
        File png = File.createTempFile("jace-vbl-", ".png");
        png.deleteOnExit();

        String output = run("screenshot " + png.getAbsolutePath() + " --vbl");

        assertTrue("--vbl must synchronize before capturing, got: " + output,
                output.contains("Synced to VBL"));
        assertTrue("PNG should have been written", png.length() > 0);
    }

    @Test
    public void screenshotWithoutFlagDoesNotSyncAndCapturesImmediately() throws Exception {
        // The compatibility guarantee: no flag, no frame synchronization. If
        // this fails, existing automation silently changed timing behaviour.
        File png = File.createTempFile("jace-novbl-", ".png");
        png.deleteOnExit();

        String output = run("screenshot " + png.getAbsolutePath());

        assertFalse("default capture must not synchronize, got: " + output,
                output.contains("Synced to VBL"));
        assertTrue("PNG should still have been written", png.length() > 0);
    }

    @Test
    public void screenshotFlagOrderDoesNotMatter() throws Exception {
        File png = File.createTempFile("jace-vblfirst-", ".png");
        png.deleteOnExit();

        // Flag before the path must be accepted too, so callers building
        // command strings programmatically aren't tripped up by argument order.
        String output = run("screenshot --vbl " + png.getAbsolutePath());

        assertTrue("--vbl before the filename must still sync, got: " + output,
                output.contains("Synced to VBL"));
        assertTrue("PNG should have been written", png.length() > 0);
    }

    @Test
    public void screenshotTreatsTheFlagAsAFlagNotAFilename() throws Exception {
        // Guards against the naive parse that takes args[0] as the path: with
        // the flag first, that would try to write a file literally named
        // "--vbl" in the working directory and never write the real one.
        File png = File.createTempFile("jace-notaname-", ".png");
        png.deleteOnExit();
        File wrong = new File("--vbl");

        run("screenshot --vbl " + png.getAbsolutePath());

        assertTrue("the real target should have been written", png.length() > 0);
        assertFalse("must not have created a file named --vbl", wrong.exists());
    }

    @Test
    public void screenshotWithNoArgumentsPrintsUsage() {
        String output = run("screenshot");
        assertTrue("should print usage, got: " + output, output.contains("Usage: screenshot"));
        assertTrue("usage should document the flag as optional, got: " + output,
                output.contains("[--vbl]"));
    }
}
