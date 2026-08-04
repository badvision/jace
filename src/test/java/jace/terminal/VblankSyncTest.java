package jace.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.apple2e.SoftSwitches;
import jace.apple2e.VideoDHGR;
import jace.core.Motherboard;
import jace.core.Utility;
import jace.core.Video;

/**
 * Proves that VblankSync actually lands the machine on the leading edge of
 * vertical blanking, rather than merely running for a while and returning.
 *
 * The bug this tooling exists to prevent: Jace's dump/capture commands are
 * single-shot, so if they fire part-way through the active display they can
 * catch a page the program is still drawing into and report a tear as a
 * renderer defect. A "ran to VBL" primitive that doesn't verifiably stop at
 * the VBL edge would silently fail to prevent that, which is why the
 * assertions below check real frame position and not just that the call
 * returned.
 *
 * ISOLATION -- this is the important structural point. These tests do NOT
 * assert on SoftSwitches.VBL, and do NOT tick the shared emulator. Both are
 * process-global: any other test class in the suite can move the scanner or
 * flip the switch, and a leaked emulator worker thread can do so concurrently.
 * Measured, when this class read the global switch: the failing set changed
 * between otherwise identical full-suite runs, and a probe found the global
 * flipping 35 times in 300ms while the test thread sat idle.
 *
 * So each test builds its OWN Motherboard and its own scanner, and reads a
 * blanking flag that scanner sets from its own vblankStart/vblankEnd. Nothing
 * outside this class can perturb that. The scanner is still a real VideoDHGR
 * with the production Video.tick() scanline walk, so the timing under test is
 * the real timing.
 *
 * Polarity is the thing most likely to be gotten wrong here: $C019 (RDVBLBAR)
 * reads bit 7 = 1 during ACTIVE DISPLAY and 0 during VBL -- inverted from
 * intuition. Jace models that with SoftSwitches.VBL's state being true during
 * active display (Motherboard.vblankStart() sets it false). The polarity test
 * below pins that mapping down so a future "fix" inverting it fails loudly.
 */
public class VblankSyncTest extends AbstractFXTest {

    /**
     * A real VideoDHGR scanner that records its own blanking state.
     *
     * Video.tick() -- the scanline walk that drives blanking -- is inherited
     * unchanged, which is what makes the assertions in this class meaningful.
     *
     * vblankStart()/vblankEnd() are overridden to (a) record the transition in
     * an instance field this test owns, and (b) skip the JavaFX repaint, since
     * Video.vblankStart() calls Platform.runLater() and there is no toolkit in
     * a headless surefire fork. Recording locally is what buys the isolation:
     * the assertions read THIS object, not the global softswitch.
     *
     * TestUtils.MockVideoDHGR is deliberately not used: it stubs out
     * vblankEnd(), which is exactly the transition being measured.
     */
    public static class ScannerOnlyVideoDHGR extends VideoDHGR {
        /** True while this scanner is inside its own vertical blanking. */
        private volatile boolean blanking = false;

        public boolean isBlanking() {
            return blanking;
        }

        @Override
        public void vblankStart() {
            blanking = true;
            // Deliberately not calling super: skip the JavaFX repaint.
        }

        @Override
        public void vblankEnd() {
            blanking = false;
            super.vblankEnd();
        }

        @Override
        public void doPostDraw() {
            // No visible surface to post to in a headless fork.
        }

        @Override
        public String getDeviceName() {
            return "ScannerOnlyVideoDHGR";
        }
    }

    /** Our own cascade -- never the shared Emulator's. */
    private Motherboard motherboard;
    private ScannerOnlyVideoDHGR video;
    private boolean priorVideoEnabled;

    @Before
    public void setUpPrivateMachine() {
        // Video.tick() is a no-op unless video is enabled, and this is a
        // static JVM-wide flag that other test classes set false and never
        // restore. Without this the scanner never advances and nothing below
        // would be measuring anything. Restored in tearDown so this class does
        // not become the thing that perturbs someone else.
        priorVideoEnabled = Utility.isVideoEnabled();
        Utility.setVideoEnabled(true);

        // A motherboard of our own, never installed on the shared Computer and
        // never resumed into a worker thread. resumeInThread() marks it running
        // so the tick cascade actually dispatches, while leaving this thread the
        // only ticker -- see IndependentTimedDevice.resumeInThread(), which
        // exists for exactly this purpose.
        motherboard = new Motherboard(null);
        video = new ScannerOnlyVideoDHGR();
        // Video.tick() dereferences currentWriter on every cycle, and it is
        // only assigned when the video mode is configured -- normally done by
        // the softswitch handlers as the machine boots. A scanner constructed
        // directly has never been configured, so tick() would NPE on the first
        // cycle. This picks whichever writer the current softswitch state
        // implies; the choice is irrelevant here since nothing asserts on
        // rendered pixels, only on scanline position.
        video.configureVideoMode();
        motherboard.addChildDevice(video);
        motherboard.resumeInThread();
        video.resume();

        // Video is a throttled TimedDevice: its tick() is gated on the wall
        // clock unless it can inherit its parent's timing. The helpers below
        // count cascade ticks as video cycles, which is only valid if the
        // scanner ticks 1:1. Max speed makes its resync delay null, guaranteeing
        // that. Without this the counts depend on how loaded the build machine
        // is -- which is how an earlier version of this class passed in
        // isolation and failed in the full suite.
        video.setMaxSpeed(true);
    }

    @After
    public void tearDownPrivateMachine() {
        if (motherboard != null) {
            motherboard.suspend();
        }
        Utility.setVideoEnabled(priorVideoEnabled);
    }

    /** True when OUR scanner is in blanking. Reads no global state. */
    private boolean inVblank() {
        return video.isBlanking();
    }

    /** Runs the sync against our own machine rather than the shared one. */
    private int sync(int maxCycles) {
        int result = VblankSync.runToVblank(motherboard, video, this::inVblank, maxCycles);
        // runToVblank suspends on exit; re-arm so subsequent helpers can tick.
        motherboard.resumeInThread();
        video.resume();
        return result;
    }

    /**
     * Positions the scanner mid-active-display so a sync has real work to do.
     *
     * Driven off observed edges rather than a fixed cycle count: it runs INTO
     * blanking to anchor on a known point in the frame, then out of it, then a
     * few thousand cycles further. Advancing a fixed count from an arbitrary
     * position can overshoot the end of the active display and land back in
     * blanking.
     */
    private void moveToMidActiveDisplay() {
        int guard = 3 * VblankSync.CYCLES_PER_FRAME;
        int cycles = 0;
        while (!inVblank() && cycles < guard) {
            motherboard.doTick();
            cycles++;
        }
        while (inVblank() && cycles < guard) {
            motherboard.doTick();
            cycles++;
        }
        // Now just inside the active display; 6000 is well short of its 12,480
        // cycles, so we stay mid-frame.
        for (int i = 0; i < 6000 && cycles < guard; i++, cycles++) {
            motherboard.doTick();
        }
        assertFalse("precondition: mid active display", inVblank());
    }

    /**
     * Counts how many cycles remain before the scanner leaves vertical
     * blanking. Called immediately after a sync, this measures how far into
     * the blanking interval the sync actually stopped.
     */
    private int cyclesRemainingInVblank(int limit) {
        int cycles = 0;
        while (inVblank() && cycles < limit) {
            motherboard.doTick();
            cycles++;
        }
        return cycles;
    }

    // ---- Frame geometry -------------------------------------------------

    @Test
    public void frameGeometryMatchesNtscHardware() {
        // 65 cycles x 262 lines. 192 visible lines x 65 = 12,480 active
        // display; the remaining 70 lines x 65 = 4,550 are vertical blanking.
        assertEquals(17030, VblankSync.CYCLES_PER_FRAME);
        assertEquals(4550, VblankSync.VBLANK_CYCLES);
        assertEquals(12480, VblankSync.CYCLES_PER_FRAME - VblankSync.VBLANK_CYCLES);
        assertEquals(Video.CYCLES_PER_LINE * Video.TOTAL_LINES, VblankSync.CYCLES_PER_FRAME);
    }

    // ---- Polarity ------------------------------------------------------

    @Test
    public void inVblankIsTrueExactlyWhenVblSwitchIsFalse() {
        // Pins down the production accessor's reading of RDVBLBAR polarity:
        // bit 7 = 1 during active display, 0 during VBL. So the switch being
        // SET means we are NOT in blanking. This is the one test that must
        // touch the global switch, because that mapping IS what it asserts;
        // it sets both states explicitly rather than depending on any scanner.
        SoftSwitches.VBL.getSwitch().setState(true);
        assertFalse("VBL switch set (bit 7 = 1) means active display, not blanking",
                VblankSync.inVblank());

        SoftSwitches.VBL.getSwitch().setState(false);
        assertTrue("VBL switch clear (bit 7 = 0) means vertical blanking",
                VblankSync.inVblank());
    }

    @Test
    public void scannerEntersBlankingAfterExactlyTheActiveDisplayPeriod() {
        // Ties our own blanking flag to real hardware geometry: from the start
        // of one active display, blanking must begin after 12,480 cycles. If
        // the scanner's notion of the frame disagreed with the constants, every
        // other measurement here would be meaningless.
        moveToMidActiveDisplay();
        // Advance to the exact start of the next active display.
        while (!inVblank()) {
            motherboard.doTick();
        }
        while (inVblank()) {
            motherboard.doTick();
        }

        int activeCycles = 0;
        while (!inVblank() && activeCycles <= 2 * VblankSync.CYCLES_PER_FRAME) {
            motherboard.doTick();
            activeCycles++;
        }

        int expected = VblankSync.CYCLES_PER_FRAME - VblankSync.VBLANK_CYCLES;
        int slack = Video.CYCLES_PER_LINE;
        assertTrue("active display should last ~" + expected + " cycles, measured " + activeCycles,
                Math.abs(activeCycles - expected) <= slack);
    }

    // ---- The actual sync behaviour --------------------------------------

    @Test
    public void syncLeavesMachineInVblank() {
        // Start from a known position mid-active-display so the sync has real
        // work to do rather than trivially already being in blanking.
        moveToMidActiveDisplay();

        int cycles = sync(VblankSync.DEFAULT_MAX_CYCLES);

        assertTrue("sync must report the number of cycles it ran, not failure", cycles > 0);
        assertTrue("machine must genuinely be in VBL after the sync", inVblank());
    }

    @Test
    public void syncStopsAtLeadingEdgeSoTheWholeBlankingWindowRemains() {
        // This is the assertion that makes the primitive worth having: a dump
        // issued after the sync must have the entire blanking interval ahead
        // of it. If the sync stopped anywhere else in the frame -- part-way
        // through blanking, or worse mid-display -- the count below would be
        // short and a long dump could still be raced by the scanner.
        int cycles = sync(VblankSync.DEFAULT_MAX_CYCLES);
        assertTrue("sync should have reached the VBL edge", cycles > 0);

        int remaining = cyclesRemainingInVblank(2 * VblankSync.CYCLES_PER_FRAME);

        // Allow one scanline of slack: the edge is detected on the tick that
        // crosses it, and the motherboard cascade ticks its children in the
        // same pass, so landing a few cycles past the exact boundary is
        // expected. Anything materially less than a full interval means the
        // sync is not stopping at the leading edge.
        int slack = Video.CYCLES_PER_LINE;
        assertTrue("expected ~" + VblankSync.VBLANK_CYCLES + " cycles of blanking to remain, got "
                        + remaining,
                remaining > VblankSync.VBLANK_CYCLES - slack);
        assertTrue("remaining blanking should not exceed one interval, got " + remaining,
                remaining <= VblankSync.VBLANK_CYCLES + slack);
    }

    @Test
    public void syncFromInsideVblankAdvancesToTheNextEdge() {
        // Already in blanking: the sync must not return immediately with a
        // nearly-exhausted window. It should run out through the display and
        // stop at the NEXT leading edge, so the caller still gets a full
        // interval. This is the case that would otherwise silently produce
        // torn dumps for a caller that synced twice in a row.
        int first = sync(VblankSync.DEFAULT_MAX_CYCLES);
        assertTrue(first > 0);
        assertTrue("precondition: in blanking", inVblank());

        int second = sync(VblankSync.DEFAULT_MAX_CYCLES);

        assertTrue("second sync should also reach an edge", second > 0);
        assertTrue("still in blanking after second sync", inVblank());
        // Crossing a whole display period means at least the active-display
        // cycle count had to elapse.
        assertTrue("second sync must traverse the active display, ran only " + second,
                second >= VblankSync.CYCLES_PER_FRAME - VblankSync.VBLANK_CYCLES);
        int remaining = cyclesRemainingInVblank(2 * VblankSync.CYCLES_PER_FRAME);
        assertTrue("full blanking window should remain, got " + remaining,
                remaining > VblankSync.VBLANK_CYCLES - Video.CYCLES_PER_LINE);
    }

    @Test
    public void syncLeavesMachineHaltedSoDumpsAreStable() {
        // A dump taken after the sync must not be racing a still-running
        // emulator; the whole point is a stationary machine at a known frame
        // position. Calls runToVblank directly rather than via sync(), which
        // re-arms the cascade for later helpers.
        VblankSync.runToVblank(motherboard, video, this::inVblank, VblankSync.DEFAULT_MAX_CYCLES);
        assertFalse("motherboard must be halted after sync", motherboard.isRunning());
    }

    @Test
    public void syncReportsFailureRatherThanClaimingSuccessWhenItCannotReachTheEdge() {
        // A budget far too small to cross a frame must return -1, not a bogus
        // success. Callers gate their dump on this.
        int result = sync(10);
        assertEquals("insufficient cycle budget must report failure", -1, result);
    }

    @Test
    public void syncWorksWhenTheParentIsAtMaxSpeed() {
        // Regression guard: Video is a THROTTLED TimedDevice, so its tick()
        // only runs when the wall clock has caught up, unless
        // TimedDevice.useParentTiming() lets it inherit the parent's timing.
        // useParentTiming() refuses when the parent is at max speed -- which
        // happens in ordinary use (CardDiskII.requestSpeed() during disk
        // access, and the terminal's own speed command). Measured in that
        // state: runToVblank returned -1 outright.
        //
        // Honest caveat, recorded because it matters: removing the fix from
        // VblankSync does NOT make this test fail, because the throttle also
        // depends on wall-clock progress. So this documents and guards the
        // scenario; it is not proof the fix works.
        video.setMaxSpeed(false);
        motherboard.setMaxSpeed(true);
        try {
            int cycles = sync(VblankSync.DEFAULT_MAX_CYCLES);

            assertTrue("sync must reach the VBL edge even at max speed, got " + cycles,
                    cycles > 0);
            assertTrue("machine must genuinely be in VBL", inVblank());
        } finally {
            motherboard.setMaxSpeed(false);
        }
    }

    @Test
    public void syncRestoresTheVideoDeviceSpeedSettingItChanged() {
        // The fix works by forcing the video device to max speed so it ticks
        // 1:1 with the cascade. That is emulated state, and a debug command
        // must not leave it altered behind the caller's back.
        video.setMaxSpeed(false);
        assertFalse("precondition: video not already at max speed", video.isMaxSpeedEnabled());

        sync(VblankSync.DEFAULT_MAX_CYCLES);

        assertFalse("sync must restore the video device's max-speed setting",
                video.isMaxSpeedEnabled());
    }

    @Test
    public void videoDeviceIsTheRealDhgrScannerNotANoOpMock() {
        // Guards the test setup itself: if the scanner's tick() were a no-op,
        // every assertion above would pass vacuously because the scanner would
        // never move and the blanking flag would keep whatever state it had.
        assertTrue("expected a real VideoDHGR scanner", video instanceof VideoDHGR);

        int transitions = countVblTransitions(2 * VblankSync.CYCLES_PER_FRAME);

        // Over two frames the scanner must pass through both regions.
        assertTrue("scanner must toggle blanking over a frame; saw " + transitions
                + " transitions", transitions >= 2);
    }

    private int countVblTransitions(int cycles) {
        boolean last = inVblank();
        int transitions = 0;
        for (int i = 0; i < cycles; i++) {
            motherboard.doTick();
            boolean now = inVblank();
            if (now != last) {
                transitions++;
                last = now;
            }
        }
        return transitions;
    }
}
