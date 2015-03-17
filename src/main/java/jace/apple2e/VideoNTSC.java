/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package jace.apple2e;

import jace.config.ConfigurableField;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.Video;
import java.util.HashSet;
import java.util.Set;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

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
    public Boolean useTextPalette = true;
    int activePalette[][] = textPalette;
    @ConfigurableField(name = "Video 7", shortName = "video7", defaultValue = "true", description = "Enable Video 7 RGB rendering support")
    public Boolean enableVideo7 = true;
    // Scanline represents 560 bits, divided up into 28-bit words
    int[] scanline = new int[20];
    static int[] divBy28 = new int[560];

    static {
        for (int i = 0; i < 560; i++) {
            divBy28[i] = i / 28;
        }
    }
    int pos = 0;
    int lastKnownY = -1;
    boolean colorActive = false;
    int rowStart = 0;

    public VideoNTSC(Computer computer) {
        super(computer);
        createStateListeners();
    }

    @Override
    protected void showBW(WritableImage screen, int xOffset, int y, int dhgrWord) {
        if (lastKnownY != y) {
            lastKnownY = y;
            pos = rowStart = divBy28[xOffset];
            colorActive = false;
        } else {
            if (pos > 20) {
                pos -= 20;
            }
        }
        doDisplay(screen, xOffset, y, dhgrWord);
    }

    @Override
    protected void showDhgr(WritableImage screen, int xOffset, int y, int dhgrWord) {
        if (lastKnownY != y) {
            lastKnownY = y;
            pos = rowStart = divBy28[xOffset];
            colorActive = true;
        }
        doDisplay(screen, xOffset, y, dhgrWord);
    }

    @Override
    protected void displayLores(WritableImage screen, int xOffset, int y, int rowAddress) {
        // Skip odd columns since this does two at once
        if ((xOffset & 0x01) == 1) {
            return;
        }

        if (lastKnownY != y) {
            lastKnownY = y;
            pos = rowStart = divBy28[xOffset];
            colorActive = true;
        }
        int c1 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset) & 0x0FF;
        if ((y & 7) < 4) {
            c1 &= 15;
        } else {
            c1 >>= 4;
        }
        int c2 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset + 1) & 0x0FF;
        if ((y & 7) < 4) {
            c2 &= 15;
        } else {
            c2 >>= 4;
        }
        int pat = c1 | c1 << 4 | c1 << 8 | (c1 & 3) << 12;
        pat |= (c2 & 12) << 12 | c2 << 16 | c2 << 20 | c2 << 24;
        scanline[pos++] = pat;
    }

    @Override
    protected void displayDoubleLores(WritableImage screen, int xOffset, int y, int rowAddress) {
        if (lastKnownY != y) {
            lastKnownY = y;
            pos = rowStart = divBy28[xOffset];
            colorActive = true;
        }
        int c1 = ((RAM128k) computer.getMemory()).getAuxVideoMemory().readByte(rowAddress + xOffset) & 0x0FF;
        if ((y & 7) < 4) {
            c1 &= 15;
        } else {
            c1 >>= 4;
        }
        int c2 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset) & 0x0FF;
        if ((y & 7) < 4) {
            c2 &= 15;
        } else {
            c2 >>= 4;
        }
        if ((xOffset & 0x01) == 0) {
            int pat = c1 | (c1 & 7) << 4;
            pat |= c2 << 7 | (c2 & 7) << 11;
            scanline[pos] = pat;
        } else {
            int pat = scanline[pos];
            pat |= (c1 & 12) << 12 | c1 << 16 | (c1 & 1) << 20;
            pat |= (c2 & 12) << 19 | c2 << 23 | (c2 & 1) << 27;
            scanline[pos] = pat;
            pos++;
        }
    }

    private void doDisplay(WritableImage screen, int xOffset, int y, int dhgrWord) {
        if (pos >= 20) {
            pos -= 20;
        }
        scanline[pos] = dhgrWord;
        pos++;
    }

    @Override
    public void hblankStart(WritableImage screen, int y, boolean isDirty) {
        if (isDirty) {
            renderScanline(screen, y);
        }
        lastKnownY = -1;
    }
    // Offset is based on location in graphics buffer that corresponds with the row and
    // a number (0-20) that represents how much of the scanline was rendered
    // This is based off the xyOffset but is different because of P
    static int pyOffset[][];

    static {
        pyOffset = new int[192][21];
        for (int y = 0; y < 192; y++) {
            for (int p = 0; p < 21; p++) {
                pyOffset[y][p] = (y * 560) + (p * 28);
            }
        }
    }

    private void renderScanline(WritableImage screen, int y) {
        PixelWriter writer = screen.getPixelWriter();
        try {
            // This is equivilant to y*560 but is 5% faster
            //int yOffset = ((y << 4) + (y << 5) + (y << 9))+xOffset;

            // For some reason this jumps up to 40 in the wayout title screen (?)
            int p = 0;
            if (rowStart > 0) {
                getCurrentWriter().markDirty(y);
            }
            // Reset scanline position
            if (colorActive && (!dhgrMode || !enableVideo7 || graphicsMode.isColor())) {
                int byteCounter = 0;
                for (int s = rowStart; s < 20; s++) {
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
                    if (enableVideo7 && dhgrMode && graphicsMode == rgbMode.mix) {
                        for (int i = 0; i < 28; i++) {
                            if (i % 7 == 0) {
                                isBW = !hiresMode && !useColor[byteCounter];
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
                    } else {
                        for (int i = 0; i < 28; i++) {
                            writer.setArgb(p++, y, activePalette[i % 4][bits & 0x07f]);
                            bits >>= 1;
                            if (i == 20) {
                                bits |= add << (hiresMode ? 9 : 10);
                            }
                        }
                    }
                }
            } else {
                for (int s = rowStart; s < 20; s++) {
                    int bits = scanline[s];
                    for (int i = 0; i < 28; i++) {
                        writer.setColor(p++, y, ((bits & 1) == 0) ? BLACK : WHITE);
                        bits >>= 1;
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            // Flag this scanline to be written again, something screwed up!
            // This only happens during a race condition when the video
            // mode changes at just the wrong time.
            getCurrentWriter().markDirty(y);
        }
    }
    // y Range [0,1]
    public static final double MIN_Y = 0;
    public static final double MAX_Y = 1;
    // i Range [-0.5957, 0.5957]
    public static final double MAX_I = 0.5957;
    // q Range [-0.5226, 0.5226]
    public static final double MAX_Q = 0.5226;
    static final int solidPalette[][] = new int[4][128];
    static final int textPalette[][] = new int[4][128];
    static final double[][] yiq = {
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

    static {
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
                double y1 = yiq[col][0];
                double y2 = ((double) level / (double) maxLevel);
                solidPalette[offset][pattern] = yiqToRgb(y1, yiq[col][1] * MAX_I, yiq[col][2] * MAX_Q);
                textPalette[offset][pattern] = yiqToRgb(y2, yiq[col][1] * MAX_I, yiq[col][2] * MAX_Q);
            }
        }
    }

    static public int yiqToRgb(double y, double i, double q) {
        int r = (int) (normalize((y + 0.956 * i + 0.621 * q), 0, 1) * 255);
        int g = (int) (normalize((y - 0.272 * i - 0.647 * q), 0, 1) * 255);
        int b = (int) (normalize((y - 1.105 * i + 1.702 * q), 0, 1) * 255);
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    public static double normalize(double x, double minX, double maxX) {
        if (x < minX) {
            return minX;
        }
        if (x > maxX) {
            return maxX;
        }
        return x;
    }

    @Override
    public void reconfigure() {
        activePalette = useTextPalette ? textPalette : solidPalette;
        super.reconfigure();
    }
    // The following section captures changes to the RGB mode
    // The details of this are in Brodener's patent application #4631692
    // http://www.freepatentsonline.com/4631692.pdf    
    // as well as the AppleColor adapter card manual
    // http://apple2.info/download/Ext80ColumnAppleColorCardHR.pdf
    rgbMode graphicsMode = rgbMode.mix;

    public static enum rgbMode {

        color(true), mix(true), bw(false), _160col(false);
        boolean colorMode = false;

        rgbMode(boolean c) {
            this.colorMode = c;
        }

        public boolean isColor() {
            return colorMode;
        }
    }

    public static enum ModeStateChanges {

        SET_AN3, CLEAR_AN3, SET_80, CLEAR_80;
    }
    boolean f1 = true;
    boolean f2 = true;
    boolean an3 = true;

    public void rgbStateChange(ModeStateChanges state) {
        switch (state) {
            case CLEAR_80:
                break;
            case CLEAR_AN3:
                an3 = false;
                break;
            case SET_80:
                break;
            case SET_AN3:
                if (!an3) {
                    f2 = f1;
                    f1 = SoftSwitches._80COL.getState();
                }
                an3 = true;
                break;
        }
// This is the more technically correct implementation except for two issues:
// 1) 160-column mode isn't implemented so it's not worth bothering to capture that state
// 2) A lot of programs are clueless about RGB modes so it's good to default to normal color mode
//        graphicsMode = f1 ? (f2 ? rgbMode.color : rgbMode.mix) : (f2 ? rgbMode._160col : rgbMode.bw);
        graphicsMode = f1 ? (f2 ? rgbMode.color : rgbMode.mix) : (f2 ? rgbMode.color : rgbMode.bw);
//        System.out.println(state + ": "+ graphicsMode);
    }
    // These catch changes to the RGB mode to toggle between color, BW and mixed
    Set<RAMListener> rgbStateListeners = new HashSet<>();

    private void createStateListeners() {
        rgbStateListeners.add(new RAMListener(RAMEvent.TYPE.ANY, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0c05e);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                Video v = computer.getVideo();
                if (v instanceof VideoNTSC) {
                    ((VideoNTSC) v).rgbStateChange(ModeStateChanges.CLEAR_AN3);
                }
            }
        });
        rgbStateListeners.add(new RAMListener(RAMEvent.TYPE.ANY, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0c05f);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                Video v = computer.getVideo();
                if (v instanceof VideoNTSC) {
                    ((VideoNTSC) v).rgbStateChange(ModeStateChanges.SET_AN3);
                }
            }
        });
        rgbStateListeners.add(new RAMListener(RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0fa62);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                Video v = computer.getVideo();
                if (v instanceof VideoNTSC) {
                    // When reset hook is called, reset the graphics mode
                    // This is useful in case a program is running that 
                    // is totally clueless how to set the RGB state correctly.
                    ((VideoNTSC) v).f1 = true;
                    ((VideoNTSC) v).f2 = true;
                    ((VideoNTSC) v).an3 = false;
                    ((VideoNTSC) v).graphicsMode = rgbMode.color;
                }
            }
        });
        rgbStateListeners.add(new RAMListener(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0c00d);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                Video v = computer.getVideo();
                if (v instanceof VideoNTSC) {
                    ((VideoNTSC) v).rgbStateChange(ModeStateChanges.SET_80);
                }
            }
        });
    }

    @Override
    public void detach() {
        rgbStateListeners.stream().forEach((l) -> {
            computer.getMemory().removeListener(l);
        });
        super.detach();
    }

    @Override
    public void attach() {
        super.attach();
        rgbStateListeners.stream().forEach((l) -> {
            computer.getMemory().addListener(l);
        });
    }
}
