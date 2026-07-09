package jace.apple2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.Emulator;
import jace.TestUtils;
import jace.core.RAM;
import jace.core.Utility;
import jace.core.VideoWriter;

/**
 * Verifies that softswitch-driven video mode changes (TEXT, MIXED, PAGE2,
 * HIRES, AN3/DHIRES, 80COL, ALTCHARSET, 80STORE) are deferred by the
 * hardware-accurate number of CPU cycles before taking visible effect, rather
 * than being applied synchronously the instant the softswitch is written.
 *
 * Follows the same test isolation pattern as VideoDHGRTest/VideoNTSCTest:
 * TestUtils.setupMockVideoDHGR() creates a FRESH VideoDHGR instance (with a
 * fresh, empty pendingModeChanges queue) before every test method, so state
 * from other test classes sharing this JVM fork (which may reconfigure the
 * shared Emulator singleton's video/memory instances) cannot leak in.
 *
 * This drives the real softswitch write path (RAM.write to the I/O address,
 * which fires the registered RAMListener exactly like a real STA $C050
 * instruction would) and then calls Video.tick() cycle-by-cycle (the same
 * method the CPU driver calls once per clock) to observe exactly when the
 * new writer/mode becomes visible.
 *
 * Delay values under test (see SoftSwitches.java for the full derivation from
 * MAME PR #15247's delayed_update() call sites): TEXT/MIXED=2, PAGE2/HIRES/
 * 80STORE=1, AN3(DHIRES)/80COL/ALTCHARSET=0.
 */
public class VideoModeDelayTest extends AbstractFXTest {

    private RAM ram;
    private VideoDHGR video;

    @Before
    public void setUp() {
        // Utility.videoEnabled is a static, JVM-wide flag. Other test classes
        // (via AbstractJaceTest/AbstractFXTest) set it false and never restore
        // it, so when tests run in the same JVM (surefire's default, forkCount
        // shared across a batch) this flag can already be false by the time
        // this test runs -- and Video.tick() is a no-op whenever it's false
        // (see the guard at the top of Video.tick()). Force it true so this
        // test's tick()-driven assertions are meaningful regardless of what
        // ran before it.
        Utility.setVideoEnabled(true);

        // Fresh VideoDHGR instance per test method (see class javadoc) --
        // guarantees an empty pendingModeChanges queue and avoids any
        // possibility of a stale video/memory instance left behind by
        // another test class.
        TestUtils.setupMockVideoDHGR();
        video = (VideoDHGR) Emulator.withComputer(c -> c.getVideo(), null);
        ram = Emulator.withComputer(c -> c.getMemory(), null);

        for (SoftSwitches softswitch : SoftSwitches.values()) {
            softswitch.getSwitch().reset();
        }
        // Start from a known state: graphics mode (TEXT off), not mixed.
        SoftSwitches.TEXT.getSwitch().setState(false);
        SoftSwitches.MIXED.getSwitch().setState(false);
        SoftSwitches.PAGE2.getSwitch().setState(false);
        SoftSwitches.HIRES.getSwitch().setState(false);
        SoftSwitches.DHIRES.getSwitch().setState(false);
        SoftSwitches._80COL.getSwitch().setState(false);
        SoftSwitches.ALTCH.getSwitch().setState(false);
        SoftSwitches._80STORE.getSwitch().setState(false);
        // Each setState() call above schedules a deferred mode-change (that's
        // the feature under test), so flush the queue by ticking past the
        // longest possible delay (2) before each test's own assertions begin.
        for (int i = 0; i < 5; i++) {
            video.tick();
        }
        video.configureVideoMode();
    }

    /** Writes to a softswitch address using the real memory write path. */
    private void writeSwitch(int address) {
        ram.write(address, (byte) 0, true, false);
    }

    private VideoWriter currentWriter() {
        return video.getCurrentWriter();
    }

    @Test
    public void testTextSwitchDelayIsTwoTicks() {
        VideoWriter before = currentWriter();

        // Turn TEXT on ($C050 sets it off, $C051 sets it on per SoftSwitches.TEXT def)
        writeSwitch(0x0c051);

        // Ticks 0, 1 (i.e. the first 2 calls to tick()) must NOT yet reflect
        // the new writer -- hardware delay for TEXT on/off is 2 cycles.
        for (int i = 0; i < 2; i++) {
            assertEquals("Tick " + i + ": TEXT mode change must not be visible yet (delay=2)",
                    before, currentWriter());
            video.tick();
        }

        // From tick 2 onward, the change must be visible.
        assertNotEquals("After 2 ticks, TEXT mode change must now be visible", before, currentWriter());
    }

    @Test
    public void testHiresSwitchDelayIsOneTick() {
        VideoWriter before = currentWriter();

        // Turn HIRES on ($C056 off, $C057 on per SoftSwitches.HIRES def).
        writeSwitch(0x0c057);

        // Hardware delay for HIRES is 1 cycle: tick 0 must still show the
        // old writer, tick 1 onward must show the new writer.
        assertEquals("Tick 0: HIRES mode change must not be visible yet (delay=1)",
                before, currentWriter());
        video.tick();
        assertNotEquals("After 1 tick, HIRES mode change must now be visible", before, currentWriter());
    }
}
