/**
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.terminal;

import java.util.function.BooleanSupplier;

import jace.Emulator;
import jace.apple2e.SoftSwitches;
import jace.core.Device;
import jace.core.Motherboard;
import jace.core.Video;

/**
 * Advances emulation to the leading edge of vertical blanking and halts there,
 * so that a subsequent memory dump or screen capture is frame-coherent.
 *
 * Why this exists: the terminal's dump and screenshot commands are single-shot.
 * If the emulator happens to be halted part-way through the active display when
 * they run, a page that the program under test is in the middle of drawing is
 * captured torn -- some rows from the new frame, some from the previous one.
 * That has already produced at least one false single-row defect report.
 * Halting at the start of VBL gives the caller the full 4,550-cycle blanking
 * window in which nothing is being scanned out, which is where a renderer's
 * output is expected to be complete and stable.
 *
 * Polarity note, because this is repeatedly gotten wrong: $C019 (RDVBLBAR)
 * reads bit 7 = 1 during ACTIVE DISPLAY and 0 during VBL. Jace models that
 * directly -- SoftSwitches.VBL's state is true during active display and false
 * during blanking (see Motherboard.vblankStart/vblankEnd). So "in VBL" is
 * state == false. Assembly waiting for VBL uses BPL, not BMI.
 *
 * Frame geometry (Apple //e, NTSC, no cycle stealing): 65 cycles x 262 lines =
 * 17,030 cycles per frame, of which 192 x 65 = 12,480 are active display and
 * 70 x 65 = 4,550 are vertical blanking.
 *
 * This inspects the softswitch's state field directly rather than reading
 * $C019 through the MMU, so polling does not fire RAM listeners or perturb any
 * softswitch.
 */
public final class VblankSync {

    /** 65 * 262 -- one full NTSC frame. */
    public static final int CYCLES_PER_FRAME = Video.CYCLES_PER_LINE * Video.TOTAL_LINES;

    /** 70 * 65 -- the vertical blanking interval. */
    public static final int VBLANK_CYCLES = Video.VBLANK;

    /**
     * Cycle budget for reaching the next VBL edge. Worst case is just under two
     * frames (already inside VBL, so a whole display period plus a whole frame
     * must elapse); the extra frame is slack so a legitimately slow case is not
     * mistaken for a stall.
     */
    public static final int DEFAULT_MAX_CYCLES = 3 * CYCLES_PER_FRAME;

    private VblankSync() {
    }

    /** True when the video scanner is inside vertical blanking right now. */
    public static boolean inVblank() {
        return !SoftSwitches.VBL.getSwitch().getState();
    }

    /**
     * Runs the machine until the video scanner crosses into vertical blanking,
     * then halts it. If the machine is already in VBL, this runs through the
     * rest of the blanking interval and the whole following active display, so
     * that on return the full blanking window is still ahead -- callers can
     * rely on having the entire interval available for a dump.
     *
     * @param maxCycles give up after this many cycles rather than spinning
     *                  forever if the video device is not advancing
     * @return the number of cycles executed, or -1 if the edge was not reached
     *         within maxCycles
     */
    public static int runToVblank(int maxCycles) {
        return Emulator.withComputer(computer -> {
            Motherboard motherboard = computer.getMotherboard();
            // Stop the free-running worker thread so ticks happen on this
            // thread only -- otherwise the state we poll races the emulator.
            motherboard.suspend();
            motherboard.resumeInThread();
            // The CPU and video devices are always running during normal
            // execution; if the terminal never resumed the machine they may
            // still be stopped, in which case nothing would advance. Resume
            // just those two rather than resumeAll(), which would also restart
            // devices a caller deliberately suspended.
            ensureRunning(computer.getCpu());
            Video video = computer.getVideo();
            ensureRunning(video);
            return runToVblank(motherboard, video, VblankSync::inVblank, maxCycles);
        }, -1);
    }

    /**
     * The actual sync loop, parameterised over the devices it ticks and the
     * predicate it uses to decide "am I in blanking".
     *
     * Production calls this via {@link #runToVblank(int)} with the current
     * machine and the global VBL softswitch. Tests call it with a motherboard
     * and video device they own, and a predicate reading a flag driven by that
     * device's own vblankStart/vblankEnd -- so their measurements cannot be
     * perturbed by another test class touching the process-global softswitch.
     * That is not merely convenient: SoftSwitches.VBL is JVM-global state, and a
     * test asserting on it is only as reliable as every other class in the
     * suite. Measured: the global flipped 35 times in 300ms while the test
     * thread sat idle, because a stray emulator worker thread was still ticking.
     *
     * @param motherboard the device cascade to tick
     * @param video       the scanner whose timing must run 1:1 with the cascade;
     *                    may be null, in which case no speed adjustment is made
     * @param inVblank    reports whether the scanner is currently in blanking
     * @param maxCycles   give up after this many cycles
     * @return cycles executed, or -1 if the edge was not reached in time
     */
    static int runToVblank(Motherboard motherboard, Video video,
            BooleanSupplier inVblank, int maxCycles) {
        // Video is a throttled TimedDevice: its tick is a no-op until the
        // wall clock catches up, UNLESS it can inherit the parent's timing.
        // TimedDevice.useParentTiming() refuses to do so when the
        // motherboard is at max speed or clocked faster than the video
        // device -- both of which happen in normal use (CardDiskII calls
        // requestSpeed() during disk access, and the terminal's own speed
        // command sets max speed). In that state a motherboard tick does
        // NOT advance the scanner, so counting motherboard ticks measures
        // nothing and this method returned -1 outright: measured, not
        // inferred. Putting the VIDEO device at max speed makes its own
        // resync delay null, so it ticks 1:1 with the cascade regardless of
        // how the motherboard happens to be clocked.
        boolean videoMaxSpeed = video != null && video.isMaxSpeedEnabled();
        if (video != null) {
            video.setMaxSpeed(true);
        }
        try {
            int cycles = 0;
            // Clear any head start: get onto active display first, so the
            // transition observed below is a real leading edge.
            while (inVblank.getAsBoolean() && cycles < maxCycles) {
                motherboard.doTick();
                cycles++;
            }
            boolean reached = false;
            while (cycles < maxCycles) {
                motherboard.doTick();
                cycles++;
                if (inVblank.getAsBoolean()) {
                    reached = true;
                    break;
                }
            }
            return reached ? cycles : -1;
        } finally {
            if (video != null) {
                video.setMaxSpeed(videoMaxSpeed);
            }
            motherboard.suspend();
        }
    }

    private static void ensureRunning(Device device) {
        if (device != null && !device.isRunning()) {
            device.resume();
        }
    }
}
