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

package jace.apple2e;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jace.Emulator;
import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.config.InvokableAction;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.Video;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Provides a clean color monitor simulation, complete with text-friendly
 * palette and mixed color/bw (mode 7) rendering. This class extends the
 * VideoDHGR class to provide all necessary video writers and other rendering
 * mechanics, and then overrides the actual output routines (showBW, showDhgr)
 * with more suitable (and much prettier) alternatives. Rather than draw to the
 * video buffer every cycle, rendered screen info is pushed into a buffer with
 * mask bits (to indicate B&W vs color) And the actual conversion happens at the
 * end of the scanline during the HBLANK period. This video rendering was
 * inspired by Blargg but was ultimately rewritten from scratch once the color
 * palette was implemented.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class VideoNTSC extends VideoDHGR {

    @ConfigurableField(name = "Text palette", shortName = "textPalette", defaultValue = "false", description = "Use text-friendly color palette")
    public boolean useTextPalette = false;
    final int[][] SOLID_PALETTE = new int[4][128];
    final int[][] TEXT_PALETTE = new int[4][128];
    int[][] activePalette = SOLID_PALETTE;
    @ConfigurableField(name = "Video 7", shortName = "video7", defaultValue = "true", description = "Enable Video 7 RGB rendering support")
    public boolean enableVideo7 = true;
    // Scanline represents 560 bits, divided up into 28-bit words
    final int[] scanline = new int[20];
    final public int[] divBy28 = new int[560];

    protected boolean[] colorActive = new boolean[80];
    int rowStart = 0;
    
    public VideoNTSC() {
        super();
        initDivideTables();
        initNtscPalette();
        registerStateListeners();
    }

    public enum VideoMode {
        TextFriendly("Text-friendly color"),
        Color("Color"),
        Mode7TextFriendly("Mode7 with Text-friendly palette"),
        Mode7("Mode7 Mixed RGB"),
        Monochrome("Mono"),
        Greenscreen("Green"),
        Amber("Amber");
        String name;

        VideoMode(String n) {
            name = n;
        }
    }

    static int currentMode = -1;

    @InvokableAction(name = "Toggle video mode",
            category = "video",
            alternatives = "Gfx mode;color;b&w;monochrome",
            defaultKeyMapping = {"ctrl+shift+g"})
    public static void changeVideoMode() {
        currentMode = (currentMode + 1) % VideoMode.values().length;
        Emulator.withVideo(v->((VideoNTSC) v)._setVideoMode(VideoMode.values()[currentMode], true));
    }

    public static void setVideoMode(VideoMode newMode, boolean showNotification) {
        Emulator.withVideo(v->((VideoNTSC) v)._setVideoMode(newMode, showNotification));
    }

    private void _setVideoMode(VideoMode newMode, boolean showNotification) {
        Emulator.withVideo(v-> {
            VideoNTSC thiss = (VideoNTSC) v;
            thiss.monochomeMode = false;
            WHITE = Color.WHITE;
            switch (newMode) {
                case Amber -> {
                    thiss.monochomeMode = true;
                    WHITE = Color.web("ff8000");
                }
                case Greenscreen -> {
                    thiss.monochomeMode = true;
                    WHITE = Color.web("0ccc68");
                }
                case Monochrome -> thiss.monochomeMode = true;
                case Color -> {
                    thiss.useTextPalette = false;
                    thiss.enableVideo7 = false;
                }
                case Mode7 -> {
                    thiss.useTextPalette = false;
                    thiss.enableVideo7 = true;
                }
                case Mode7TextFriendly -> {
                    thiss.useTextPalette = true;
                    thiss.enableVideo7 = true;
                }
                case TextFriendly -> {
                    thiss.useTextPalette = true;
                    thiss.enableVideo7 = false;
                }
            }
            thiss.activePalette = thiss.useTextPalette ? TEXT_PALETTE : SOLID_PALETTE;
            if (showNotification) {
                EmulatorUILogic.notify("Video mode: " + newMode.name);
            }
            forceRefresh();
        });
    }

    @Override
    protected void showBW(WritableImage screen, int x, int y, int dhgrWord) {
        if (x < 0) return;
        int pos = divBy28[x];
        if (rowStart < 0) {
            rowStart = pos;
        }
        colorActive[pos * 4] = colorActive[pos * 4 + 1] = colorActive[pos * 4 + 2] = colorActive[pos * 4 + 3] = false;
        scanline[pos] = dhgrWord;
    }

    @Override
    protected void showDhgr(WritableImage screen, int x, int y, int dhgrWord) {
        if (x < 0) return;
        int pos = divBy28[x];
        if (rowStart < 0) {
            rowStart = pos;
        }
        colorActive[pos * 4] = colorActive[pos * 4 + 1] = colorActive[pos * 4 + 2] = colorActive[pos * 4 + 3] = true;
        scanline[pos] = dhgrWord;
    }

    @Override
    protected void displayLores(WritableImage screen, int xOffset, int y, int rowAddress) {
        if (xOffset < 0) return;
        int data = ((RAM128k) getMemory()).getMainMemory().readByte(rowAddress + xOffset) & 0x0FF;
        int pos = xOffset >> 1;
        if (rowStart < 0) {
            rowStart = pos;
        }
        colorActive[xOffset * 2] = true;
        colorActive[xOffset * 2 + 1] = true;
        if ((xOffset & 1) == 0) {
            int pat = scanline[pos] & 0x0fffc000;
            if ((y & 7) < 4) {
                data &= 15;
            } else {
                data >>= 4;
            }
            pat |= data | data << 4 | data << 8 | (data & 3) << 12;
            scanline[pos] = pat;
        } else {
            int pat = scanline[pos] & 0x03fff;
            if ((y & 7) < 4) {
                data &= 15;
            } else {
                data >>= 4;
            }
            pat |= (data & 12) << 12 | data << 16 | data << 20 | data << 24;
            scanline[pos] = pat;
        }
    }

    @Override
    protected void displayDoubleLores(WritableImage screen, int xOffset, int y, int rowAddress) {
        if (xOffset < 0) return;
        int pos = xOffset >> 1;
        if (rowStart < 0) {
            rowStart = pos;
        }
        colorActive[xOffset * 2] = colorActive[xOffset * 2 + 1] = true;
        int c1 = ((RAM128k) getMemory()).getAuxVideoMemory().readByte(rowAddress + xOffset) & 0x0FF;
        if ((y & 7) < 4) {
            c1 &= 15;
        } else {
            c1 >>= 4;
        }
        int c2 = ((RAM128k) getMemory()).getMainMemory().readByte(rowAddress + xOffset) & 0x0FF;
        if ((y & 7) < 4) {
            c2 &= 15;
        } else {
            c2 >>= 4;
        }
        int pat;
        if ((xOffset & 0x01) == 0) {
            pat = c1 | (c1 & 7) << 4;
            pat |= c2 << 7 | (c2 & 7) << 11;
        } else {
            pat = scanline[pos];
            pat |= (c1 & 12) << 12 | c1 << 16 | (c1 & 1) << 20;
            pat |= (c2 & 12) << 19 | c2 << 23 | (c2 & 1) << 27;
        }
        scanline[pos] = pat;
    }

    @Override
    public void hblankStart(WritableImage screen, int y, boolean isDirty) {
        if (isDirty) {
            renderScanline(screen, y);
        }
    }
    // Offset is based on location in graphics buffer that corresponds with the row and
    // a number (0-20) that represents how much of the scanline was rendered
    // This is based off the xyOffset but is different because of P
    static int[][] pyOffset;

    static {
        pyOffset = new int[192][21];
        for (int y = 0; y < 192; y++) {
            for (int p = 0; p < 21; p++) {
                pyOffset[y][p] = (y * 560) + (p * 28);
            }
        }
    }

    boolean monochomeMode = false;

    private void renderScanline(WritableImage screen, int y) {
        int p = 0;
        if (rowStart != 0) {
//            getCurrentWriter().markDirty(y);
            p = rowStart * 28;
            if (rowStart < 0) {
                return;
            }
        }
        PixelWriter writer = screen.getPixelWriter();
        // Reset scanline position
        int byteCounter = 0;
        for (int s = rowStart; s < 20; s++) {
            if (s < 0) continue;
            int add = 0;
            int bits;
            if (hiresMode) {
                bits = scanline[s] << 2;
                if (s > 0) {
                    bits |= (scanline[s - 1] >> 26) & 3;
                }
            } else {
                bits = scanline[s] << 3;
                if (s > 0) {
                    bits |= (scanline[s - 1] >> 25) & 7;
                }
            }
            if (s < 19) {
                add = (scanline[s + 1] & 7);
            }
            boolean isBW = false;
            boolean mixed = enableVideo7 && dhgrMode && graphicsMode == rgbMode.MIX;
            for (int i = 0; i < 28; i++) {
                if (i % 7 == 0) {
                    isBW = monochomeMode || !colorActive[byteCounter] || (mixed && !hiresMode && !useColor[byteCounter]);
                    byteCounter++;
                }
                if (isBW) {
                    writer.setColor(p++, y, ((bits & 0x8) == 0) ? BLACK : WHITE);
                } else {
                    writer.setArgb(p++, y, activePalette[i % 4][bits & 0x07f]);
                }
                bits >>= 1;
                if (i == 20) {
                    bits |= add << (hiresMode ? 9 : 10);
                }
            }
//                    } else {
//                        for (int i = 0; i < 28; i++) {
//                            writer.setArgb(p++, y, activePalette[i % 4][bits & 0x07f]);
//                            bits >>= 1;
//                            if (i == 20) {
//                                bits |= add << (hiresMode ? 9 : 10);
//                            }
//                        }
//                    }
        }
        Arrays.fill(scanline, 0);
        rowStart = -1;
    }
    // y Range [0,1]
    public static final double MIN_Y = 0;
    public static final double MAX_Y = 1;
    // i Range [-0.5957, 0.5957]
    public static final double MAX_I = 0.5957;
    // q Range [-0.5226, 0.5226]
    public static final double MAX_Q = 0.5226;
    static final double[][] YIQ_VALUES = {
        {0.0, 0.0, 0.0}, //0000 0
        {0.25, 0.5, 0.5}, //0001 1
        {0.25, -0.5, 0.5}, //0010 2
        {0.5, 0.0, 1.0}, //0011 3 +Q
        {0.25, -0.5, -0.5}, //0100 4
        {0.5, 0.0, 0.0}, //0101 5
        {0.5, -1.0, 0.0}, //0110 6 +I
        {0.75, -0.5, 0.5}, //0111 7
        {0.25, 0.5, -0.5}, //1000 8
        {0.5, 1.0, 0.0}, //1001 9 -I
        {0.5, 0.0, 0.0}, //1010 a
        {0.75, 0.5, 0.5}, //1011 b
        {0.5, 0.0, -1.0}, //1100 c -Q
        {0.75, 0.5, -0.5}, //1101 d
        {0.75, -0.5, -0.5}, //1110 e
        {1.0, 0.0, 0.0}, //1111 f
    };

    public static void CHANGE_BLACK_COLOR(int r, int g, int b) {
        Emulator.withVideo(v->{
            VideoNTSC vntsc = (VideoNTSC) v;
            BLACK = Color.rgb(r, g, b);
            int c = colorToInt(BLACK);
            for (int i1 = 0; i1 < 4; i1++) {                
                vntsc.SOLID_PALETTE[i1][0] = c;
                vntsc.TEXT_PALETTE[i1][0] = c;
            }
        });
        Video.forceRefresh();
    }

    private void initDivideTables() {
        for (int i = 0; i < 560; i++) {
            divBy28[i] = i / 28;
        }
    }
    
    private void initNtscPalette() {
        int maxLevel = 10;
        for (int offset = 0; offset < 4; offset++) {
            for (int pattern = 0; pattern < 128; pattern++) {
                int level = (pattern & 1)
                        + ((pattern >> 1) & 1) * 1
                        + ((pattern >> 2) & 1) * 2
                        + ((pattern >> 3) & 1) * 4
                        + ((pattern >> 4) & 1) * 2
                        + ((pattern >> 5) & 1) * 1;

                int col = (pattern >> 2) & 0x0f;
                for (int rot = 0; rot < offset; rot++) {
                    col = ((col & 8) >> 3) | ((col << 1) & 0x0f);
                }
                double y1 = YIQ_VALUES[col][0];
                double y2 = (level / (double) maxLevel);
                SOLID_PALETTE[offset][pattern] = yiqToRgb(y1, YIQ_VALUES[col][1] * MAX_I, YIQ_VALUES[col][2] * MAX_Q);
                TEXT_PALETTE[offset][pattern] = yiqToRgb(y2, YIQ_VALUES[col][1] * MAX_I, YIQ_VALUES[col][2] * MAX_Q);
            }
        }
    }

    static public int yiqToRgb(double y, double i, double q) {
        return colorToInt(yiqToRgbColor(y, i, q));
    }

    static public Color yiqToRgbColor(double y, double i, double q) {
        int r = (int) (normalize((y + 0.956 * i + 0.621 * q), 0, 1) * 255);
        int g = (int) (normalize((y - 0.272 * i - 0.647 * q), 0, 1) * 255);
        int b = (int) (normalize((y - 1.105 * i + 1.702 * q), 0, 1) * 255);
        return Color.rgb(r, g, b);
    }

    static public int colorToInt(Color c) {
        return (int) (255 << 24) | (int) (c.getRed() * 255) << 16 | (int) (c.getGreen() * 255) << 8 | (int) (c.getBlue() * 255);
    }

    public static double normalize(double x, double minX, double maxX) {
        if (x < minX) {
            return minX;
        }
        return Math.min(x, maxX);
    }

    @Override
    public void reconfigure() {
        activePalette = useTextPalette ? TEXT_PALETTE : SOLID_PALETTE;
        super.reconfigure();
    }
    // The following section captures changes to the RGB mode
    // The details of this are in Brodener's patent application #4631692
    // http://www.freepatentsonline.com/4631692.pdf
    // as well as the AppleColor adapter card manual
    // http://apple2.info/download/Ext80ColumnAppleColorCardHR.pdf
    rgbMode graphicsMode = rgbMode.MIX;

    public enum rgbMode {

        COLOR(true), MIX(true), BW(false), COL_160(false);
        boolean colorMode = false;

        rgbMode(boolean c) {
            this.colorMode = c;
        }

        public boolean isColor() {
            return colorMode;
        }
    }

    boolean f1 = true;
    boolean f2 = true;
    boolean an3 = false;

    public void rgbStateChange() {

// This is the more technically correct implementation except for two issues:
// 1) 160-column mode isn't implemented so it's not worth bothering to capture that state
// 2) A lot of programs are clueless about RGB modes so it's good to default to normal color mode
//        graphicsMode = f1 ? (f2 ? rgbMode.color : rgbMode.mix) : (f2 ? rgbMode._160col : rgbMode.bw);
        graphicsMode = f1 ? (f2 ? rgbMode.COLOR : rgbMode.MIX) : (f2 ? rgbMode.COLOR : rgbMode.BW);
    }
    // These catch changes to the RGB mode to toggle between color, BW and mixed
    Set<RAMListener> rgbStateListeners = new HashSet<>();

    private void registerStateListeners() {
        if (!rgbStateListeners.isEmpty() || Emulator.withComputer(Computer::getVideo, null) != this) {
            return;
        }
        rgbStateListeners.add(getMemory().observe("NTSC: AN3 state change", RAMEvent.TYPE.ANY, 0x0c05e, (e) -> {
            an3 = false;
            rgbStateChange();
        }));
        rgbStateListeners.add(getMemory().observe("NTSC: 80COL state change", RAMEvent.TYPE.ANY, 0x0c05f, (e) -> {
            if (!an3) {
                f2 = f1;
                f1 = SoftSwitches._80COL.getState();
            }
            an3 = true;
            rgbStateChange();
        }));
        rgbStateListeners.add(getMemory().observe("NTSC: Reset hook for reverting RGB mode", RAMEvent.TYPE.EXECUTE, 0x0fa62, (e) -> {
            // When reset hook is called, reset the graphics mode
            // This is useful in case a program is running that
            // is totally clueless how to set the RGB state correctly.
            f1 = true;
            f2 = true;
            an3 = false;
            graphicsMode = rgbMode.COLOR;
            rgbStateChange();
        }));
    }

    @Override

    public void detach() {
        rgbStateListeners.stream().forEach((l) -> {
            getMemory().removeListener(l);
        });
        rgbStateListeners.clear();
        super.detach();
    }

    @Override
    public void attach() {
        super.attach();
        registerStateListeners();
    }
}
