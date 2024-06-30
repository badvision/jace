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
    static final public int[] textOffset = new int[192];
    static final public int[] hiresOffset = new int[192];
    static final public int[] textRowLookup = new int[0x0400];
    static final public int[] hiresRowLookup = new int[0x02000];
    private boolean screenDirty = true;
    private boolean lineDirty = true;
    private boolean isVblank = false;

    static void initLookupTables() {
        for (int i = 0; i < 192; i++) {
            textOffset[i] = calculateTextOffset(i >> 3);
            hiresOffset[i] = calculateHiresOffset(i);
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
        video = new WritableImage(560, 192);
        visible = new WritableImage(560, 192);
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
        javafx.application.Platform.runLater(redrawScreen);
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
        addWaitCycles(waitsPerCycle);
        if (y < APPLE_SCREEN_LINES) setScannerLocation(currentWriter.getYOffset(y));
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
        Emulator.withVideo(v->v._forceRefresh());
    }

    protected void _forceRefresh() {
        lineDirty = true;
        screenDirty = true;
        forceRedrawRowCount = APPLE_SCREEN_LINES + 1;
    }

    @Override
    public String getShortName() {
        return "vid";
    }

    public Image getFrameBuffer() {
        return visible;
    }
}
