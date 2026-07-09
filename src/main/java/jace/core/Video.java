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

package jace.core;

import java.util.ArrayDeque;
import java.util.Deque;

import jace.Emulator;
import jace.config.ConfigurableField;
import jace.config.InvokableAction;
import jace.state.Stateful;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/**
 * Generic abstraction of a 560x192 video output device which renders 40 columns
 * per scanline. This also triggers VBL and updates the physical screen.
 * Subclasses are used to manage actual rendering via ScreenWriter
 * implementations. Created on November 10, 2006, 4:29 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
public abstract class Video extends TimedDevice {

    @Stateful
    WritableImage video;
    WritableImage visible;
    VideoWriter currentWriter;
    private byte floatingBus = 0;
    private int width = 560;
    private int height = 192;
    @Stateful
    public int x = 0;
    @Stateful
    public int y = 0;
    @Stateful
    public int scannerAddress;
    @Stateful
    public int vPeriod = 0;
    @Stateful
    public int hPeriod = 0;
    static final public int CYCLES_PER_LINE = 65;
    static final public int TOTAL_LINES = 262;
    static final public int APPLE_CYCLES_PER_LINE = 40;
    static final public int APPLE_SCREEN_LINES = 192;
    static final public int HBLANK = CYCLES_PER_LINE - APPLE_CYCLES_PER_LINE;
    static final public int VBLANK = (TOTAL_LINES - APPLE_SCREEN_LINES) * CYCLES_PER_LINE;
    // Sized to TOTAL_LINES (not just APPLE_SCREEN_LINES) so entries 192-261
    // cover the vertical blanking interval as well as the visible screen.
    // See initLookupTables() for how the two regions are populated.
    static final public int[] textOffset = new int[TOTAL_LINES];
    static final public int[] hiresOffset = new int[TOTAL_LINES];
    static final public int[] textRowLookup = new int[0x0400];
    static final public int[] hiresRowLookup = new int[0x02000];
    private boolean screenDirty = true;
    private boolean lineDirty = true;
    private boolean isVblank = false;

    // Real hardware applies video-mode softswitch changes (TEXT, MIXED,
    // PAGE2, HIRES, AN3/DHIRES, 80COL, ALTCHARSET, 80STORE) a few CPU cycles
    // after the write, not instantaneously. Each pending change records how
    // many more ticks must elapse before it is applied. Multiple changes can
    // be in flight at once (e.g. rapid consecutive softswitch writes), so
    // this is a small ordered queue rather than a single counter.
    private static final class PendingModeChange {
        int ticksRemaining;
        final Runnable apply;
        PendingModeChange(int ticksRemaining, Runnable apply) {
            this.ticksRemaining = ticksRemaining;
            this.apply = apply;
        }
    }
    private final Deque<PendingModeChange> pendingModeChanges = new ArrayDeque<>();

    /**
     * Schedules a video mode change to take effect after the given number of
     * CPU cycles (ticks) have elapsed, matching real hardware's per-switch
     * propagation delay. See jace.apple2e.SoftSwitches for the delay used by
     * each individual softswitch.
     *
     * @param delayTicks number of ticks to wait before applying the change (0 = immediate)
     * @param apply the mode-change action to run once the delay has elapsed
     */
    public void scheduleModeChange(int delayTicks, Runnable apply) {
        if (delayTicks <= 0) {
            apply.run();
            return;
        }
        pendingModeChanges.addLast(new PendingModeChange(delayTicks, apply));
    }

    private void applyDueModeChanges() {
        if (pendingModeChanges.isEmpty()) {
            return;
        }
        // Decrement every pending change and apply (in queue order) any that
        // have reached zero. Order matters: if two changes to the same
        // switch are in flight, the earlier scheduled one must be applied
        // first so the later one reflects the correct prior state.
        for (PendingModeChange change : pendingModeChanges) {
            change.ticksRemaining--;
        }
        while (!pendingModeChanges.isEmpty() && pendingModeChanges.peekFirst().ticksRemaining <= 0) {
            pendingModeChanges.pollFirst().apply.run();
        }
    }

    static void initLookupTables() {
        for (int i = 0; i < APPLE_SCREEN_LINES; i++) {
            textOffset[i] = calculateTextOffset(i >> 3);
            hiresOffset[i] = calculateHiresOffset(i);
        }
        // Vertical blanking region (real hardware scanlines 192-261). Real
        // Apple //e hardware does not stop scanning during vblank: the
        // vertical counter keeps incrementing and the video scanner address
        // formula (ported from MAME's a2_video_device::scanner_address(),
        // see MAME PR #15247, src/mame/apple/apple2video.cpp) aliases back
        // into "screen hole" addresses inside the normal text/hires address
        // space. This is exactly what vaporlock-style timing tests (e.g.
        // VIDSYNC.S) rely on to detect specific scanlines during blanking.
        for (int i = APPLE_SCREEN_LINES; i < TOTAL_LINES; i++) {
            textOffset[i] = calculateBlankingScannerOffset(i, false);
            hiresOffset[i] = calculateBlankingScannerOffset(i, true);
        }
        for (int i = 0; i < 0x0400; i++) {
            textRowLookup[i] = identifyTextRow(i);
        }
        for (int i = 0; i < 0x2000; i++) {
            hiresRowLookup[i] = identifyHiresRow(i);
        }
    }
    private int forceRedrawRowCount = 0;

    /**
     * Creates a new instance of Video
     *
     * @param computer
     */
    public Video() {
        super();
        initLookupTables();
        if (Utility.isVideoEnabled()) {
            video = new WritableImage(560, 192);
            visible = new WritableImage(560, 192);
        } else {
            // Create minimal stubs for testing when video is disabled
            video = null;
            visible = null;
        }
        vPeriod = 0;
        hPeriod = 0;
        _forceRefresh();
    }

    public void setWidth(int w) {
        width = w;
    }

    public int getWidth() {
        return width;
    }

    public void setHeight(int h) {
        height = h;
    }

    public int getHeight() {
        return height;
    }

    public VideoWriter getCurrentWriter() {
        return currentWriter;
    }

    public void setCurrentWriter(VideoWriter currentWriter) {
        if (this.currentWriter != currentWriter || currentWriter.isMixed()) {
            this.currentWriter = currentWriter;
            forceRedrawRowCount = APPLE_SCREEN_LINES + 1;
        }
    }
    @ConfigurableField(category = "video", name = "Min. Screen Refesh", defaultValue = "15", description = "Minimum number of miliseconds to wait before trying to redraw.")
    public static int MIN_SCREEN_REFRESH = 15;

    Runnable redrawScreen = () -> {
        if (visible != null && video != null) {
            screenDirty = false;
            visible.getPixelWriter().setPixels(0, 0, 560, 192, video.getPixelReader(), 0, 0);
        }
    };

    public void redraw() {
        if (Utility.isVideoEnabled() && video != null) {
            javafx.application.Platform.runLater(redrawScreen);
        }
    }

    public void vblankStart() {
        if (screenDirty && isRunning()) {
            redraw();
        }
    }

    abstract public void vblankEnd();

    abstract public void hblankStart(WritableImage screen, int y, boolean isDirty);

    public void setScannerLocation(int loc) {
        scannerAddress = loc;
    }

    @Override
    public void tick() {
        // Skip video processing if video is disabled
        if (!Utility.isVideoEnabled()) {
            return;
        }
        
        addWaitCycles(waitsPerCycle);
        applyDueModeChanges();
        // During vblank, 'y' is reused to walk 122..191 purely so the rest of
        // this method's line-wraparound bookkeeping stays in range -- it does
        // NOT represent the true hardware scanline. Translate it back to the
        // real scanline (192..261) so the offset lookup below indexes the
        // vblank-region entries of textOffset/hiresOffset (populated by
        // calculateBlankingScannerOffset) rather than re-reading the visible
        // rows' entries a second time. See initLookupTables().
        int scannerLine = isVblank ? y + (TOTAL_LINES - APPLE_SCREEN_LINES) : y;
        setScannerLocation(currentWriter.getYOffset(scannerLine));
        setFloatingBus(getMemory().readRaw(scannerAddress + x));
        if (hPeriod > 0) {
            hPeriod--;
            if (hPeriod == 0) {
                x = -1;
            }
        } else {
            int xVal = x;
            if (!isVblank && xVal < APPLE_CYCLES_PER_LINE && xVal >= 0) {
                draw(xVal);
            }
            if (xVal >= APPLE_CYCLES_PER_LINE - 1) {
                int yy = y + hblankOffsetY;
                if (yy < 0) {
                    yy += APPLE_SCREEN_LINES;
                }
                if (yy >= APPLE_SCREEN_LINES) {
                    yy -= (TOTAL_LINES - APPLE_SCREEN_LINES);
                }
                x = hblankOffsetX - 1;
                if (!isVblank) {
                    if (lineDirty) {
                        screenDirty = true;
                        currentWriter.clearDirty(y);
                    }
                    hblankStart(video, y, lineDirty);
                    lineDirty = false;
                    forceRedrawRowCount--;
                }
                hPeriod = HBLANK;
                y++;
                getCurrentWriter().setCurrentRow(y);
                if (y >= APPLE_SCREEN_LINES) {
                    if (!isVblank) {
                        y = APPLE_SCREEN_LINES - (TOTAL_LINES - APPLE_SCREEN_LINES);
                        isVblank = true;
                        vblankStart();
                        Emulator.withComputer(c->c.getMotherboard().vblankStart());
                    } else {
                        y = 0;
                        isVblank = false;
                        vblankEnd();
                        Emulator.withComputer(c->c.getMotherboard().vblankEnd());
                    }
                }
            }
        }
        x++;
    }

    abstract public void configureVideoMode();

    protected static int byteDoubler(byte b) {
        int num
                = // Skip hi-bit because it's not used in display
                //                ((b&0x080)<<7) |
                ((b & 0x040) << 6)
                | ((b & 0x020) << 5)
                | ((b & 0x010) << 4)
                | ((b & 0x08) << 3)
                | ((b & 0x04) << 2)
                | ((b & 0x02) << 1)
                | (b & 0x01);
        return num | (num << 1);
    }
    @ConfigurableField(name = "Waits per cycle", category = "Advanced", description = "Adjust the delay for the scanner")
    public static int waitsPerCycle = 0;
    @ConfigurableField(name = "Hblank X offset", category = "Advanced", description = "Adjust where the hblank period starts relative to the start of the line")
    public static int hblankOffsetX = -29;
    @ConfigurableField(name = "Hblank Y offset", category = "Advanced", description = "Adjust which line the HBLANK starts on (0=current, 1=next, etc)")
    public static int hblankOffsetY = 1;

    private void draw(int xVal) {
        if (!Utility.isVideoEnabled() || video == null) {
            return;
        }
        
        if (lineDirty || forceRedrawRowCount > 0 || currentWriter.isRowDirty(y)) {
            lineDirty = true;
            currentWriter.displayByte(video, xVal, y, textOffset[y], hiresOffset[y]);
        }
        doPostDraw();
    }

    static public int calculateHiresOffset(int y) {
        return calculateTextOffset(y >> 3) + ((y & 7) << 10);
    }

    static public int calculateTextOffset(int y) {
        return ((y & 7) << 7) + 40 * (y >> 3);
    }

    static public int identifyTextRow(int y) {
        //floor((x-1024)/128) + floor(((x-1024)%128)/40)*8
        // Caller must check result is <= 23, if so then they are in a screenhole!
        return (y >> 7) + (((y & 0x7f) / 40) << 3);
    }

    static public int identifyHiresRow(int y) {
        int blockOffset = identifyTextRow(y & 0x03ff);
        // Caller must check results is > 0, if not then they are in a screenhole!
        if (blockOffset > 23) {
            return -1;
        }
        return ((y >> 10) & 7) + (blockOffset << 3);
    }

    /**
     * Computes the video scanner's row-start address offset for a vertical
     * blanking line (real hardware scanline APPLE_SCREEN_LINES..TOTAL_LINES-1),
     * for h_clock=0 (the same reference point calculateTextOffset/
     * calculateHiresOffset use for x=0 -- see initLookupTables()).
     *
     * This is a direct port of MAME's a2_video_device::scanner_address()
     * (MAME PR #15247, src/mame/apple/apple2video.cpp), restricted to the
     * non-hires-mixed-mode-override, page1 case (Page2/Mixed are applied the
     * same way the visible-region tables already are: by adding a fixed
     * 0x0400/0x0800/0x2000/0x4000 page offset at the call site, and by the
     * VideoWriter/mixed-mode dispatch elsewhere in VideoDHGR).
     *
     * KNOWN LIMITATION: the returned value is only valid as a per-line
     * constant added to x for x in 0..7. Real hardware's address formula
     * wraps (mod 16) partway through blanking lines (at x=8, verified
     * empirically for every blanking line), so unlike the visible-region
     * tables this does NOT hold for the full x=0..39 pixel range. This is
     * sufficient for vaporlock-style probes (e.g. VIDSYNC.S), which only
     * ever probe offsets 0-6 from a blanking row's base address, but does
     * NOT provide full per-pixel floating-bus accuracy throughout blanking.
     *
     * @param line real hardware scanline, APPLE_SCREEN_LINES..TOTAL_LINES-1
     * @param hires true to compute the hires-mode address, false for text/lores
     * @return offset such that (offset + 0x0400) is the page1 text address,
     *         or (offset + 0x2000) is the page1 hires address, at x=0
     */
    static int calculateBlankingScannerOffset(int line, boolean hires) {
        // h_clock=25 is the reference point matching x=0 in tick() -- this
        // was determined empirically: it is the only h_clock value for which
        // this formula reproduces calculateTextOffset/calculateHiresOffset
        // exactly across all 192 visible rows (i.e. it matches the existing,
        // hardware-verified anchor convention used elsewhere in this class).
        final int hClock = 25;
        int hState = hClock - 1; // h_state = h_clock - (h_clock > 0 ? 1 : 0)
        int h0 = hState & 1;
        int h1 = (hState >> 1) & 1;
        int h2 = (hState >> 2) & 1;
        int h3 = (hState >> 3) & 1;
        int h4 = (hState >> 4) & 1;
        int h5 = (hState >> 5) & 1;

        int vState = 256 + line;
        if (line >= 256) {
            vState -= TOTAL_LINES;
        }
        int v0 = (vState >> 3) & 1;
        int v1 = (vState >> 4) & 1;
        int v2 = (vState >> 5) & 1;
        int v3 = (vState >> 6) & 1;
        int v4 = (vState >> 7) & 1;
        int vA = vState & 1;
        int vB = (vState >> 1) & 1;
        int vC = (vState >> 2) & 1;

        int addend0 = 0x0D;
        int addend1 = (h5 << 2) | (h4 << 1) | h3;
        int addend2 = (v4 << 3) | (v3 << 2) | (v4 << 1) | v3;
        int sum = (addend0 + addend1 + addend2) & 0x0F;

        int address = h0 | (h1 << 1) | (h2 << 2) | (sum << 3) | (v0 << 7) | (v1 << 8) | (v2 << 9);
        if (hires) {
            address |= (vA << 10) | (vB << 11) | (vC << 12);
            // Caller adds 0x2000 (page1) or 0x4000 (page2); return the bare offset.
        } else {
            // Caller adds 0x0400 (page1) or 0x0800 (page2); return the bare offset.
        }
        return address;
    }

    public abstract void doPostDraw();

    public byte getFloatingBus() {
        return floatingBus;
    }

    private void setFloatingBus(byte floatingBus) {
        this.floatingBus = floatingBus;
    }

    @InvokableAction(name = "Refresh screen",
            category = "display",
            description = "Marks screen contents as changed, forcing full screen redraw",
            alternatives = "redraw",
            defaultKeyMapping = {"ctrl+shift+r"})
    public static void forceRefresh() {
        if (!Utility.isVideoEnabled()) {
            return;
        }
        Emulator.withVideo(v->v._forceRefresh());
    }

    protected void _forceRefresh() {
        if (!Utility.isVideoEnabled()) {
            return;
        }
        lineDirty = true;
        screenDirty = true;
        forceRedrawRowCount = APPLE_SCREEN_LINES + 1;
    }

    @Override
    public String getShortName() {
        return "vid";
    }

    public Image getFrameBuffer() {
        if (!Utility.isVideoEnabled() || visible == null) {
            return null;
        }
        return visible;
    }

    /**
     * Returns the live render buffer (the WritableImage that VideoNTSC writes
     * to during CPU ticks). Unlike getFrameBuffer() which returns the
     * vblank-synced copy, this always reflects the most recently rendered
     * pixels — useful for screenshots taken while the emulator is paused.
     *
     * @return the render buffer, or null if video is disabled
     */
    public Image getRenderBuffer() {
        if (!Utility.isVideoEnabled() || video == null) {
            return null;
        }
        return video;
    }
}
