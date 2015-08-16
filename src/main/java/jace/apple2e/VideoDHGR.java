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

import jace.core.Computer;
import jace.core.Font;
import jace.core.Palette;
import jace.core.RAMEvent;
import jace.core.Video;
import static jace.core.Video.hiresOffset;
import static jace.core.Video.hiresRowLookup;
import static jace.core.Video.textRowLookup;
import jace.core.VideoWriter;
import java.util.logging.Logger;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * This is the primary video rendering class, which provides all necessary video
 * writers for every display mode as well as managing the display mode (via
 * configureVideoMode). The quality of the color rendering is sub-par compared
 * to VideoNTSC.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class VideoDHGR extends Video {
    // Reorder bits 3,2,1,0 -> 0,3,2,1
    // Fixes double-hires color palette

    public final static int[] FLIP_NYBBLE = {
        0, 2, 4, 6,
        8, 10, 12, 14,
        1, 3, 5, 7,
        9, 11, 13, 15
    };
    private static final boolean USE_GS_MOUSETEXT = false;
    private VideoWriter textPage1;
    private VideoWriter textPage2;
    private VideoWriter loresPage1;
    private VideoWriter loresPage2;
    private VideoWriter hiresPage1;
    private VideoWriter hiresPage2;
    // Special 80-column modes
    private VideoWriter text80Page1;
    private VideoWriter text80Page2;
    private VideoWriter dloresPage1;
    private VideoWriter dloresPage2;
    private VideoWriter dhiresPage1;
    private VideoWriter dhiresPage2;
    // Mixed mode
    private final VideoWriter mixed;
    private VideoWriter currentGraphicsWriter = null;
    private VideoWriter currentTextWriter = null;

    /**
     * Creates a new instance of VideoDHGR
     *
     * @param computer
     */
    public VideoDHGR(Computer computer) {
        super(computer);
        hiresPage1 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (hiresOffset[y] + 0x02000);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayHires(screen, xOffset, y, yGraphicsOffset + 0x02000);
            }
        };
        hiresPage2 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (hiresOffset[y] + 0x04000);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayHires(screen, xOffset, y, yGraphicsOffset + 0x04000);
            }
        };
        dhiresPage1 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (hiresOffset[y] + 0x02000);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayDoubleHires(screen, xOffset, y, yGraphicsOffset + 0x02000);
            }

            @Override
            public VideoWriter actualWriter() {
                return hiresPage1;
            }
        };
        dhiresPage2 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (hiresOffset[y] + 0x04000);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayDoubleHires(screen, xOffset, y, yGraphicsOffset + 0x04000);
            }

            @Override
            public VideoWriter actualWriter() {
                return hiresPage2;
            }
        };
        textPage1 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0400);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayText(screen, xOffset, y, yTextOffset + 0x0400);
            }
        };
        textPage2 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0800);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayText(screen, xOffset, y, yTextOffset + 0x0800);
            }
        };
        text80Page1 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0400);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayText80(screen, xOffset, y, yTextOffset + 0x0400);
            }

            @Override
            public VideoWriter actualWriter() {
                return textPage1;
            }
        };
        text80Page2 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0800);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayText80(screen, xOffset, y, yTextOffset + 0x0800);
            }

            @Override
            public VideoWriter actualWriter() {
                return textPage2;
            }
        };
        loresPage1 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0400);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayLores(screen, xOffset, y, yTextOffset + 0x0400);
            }

            @Override
            public VideoWriter actualWriter() {
                return textPage1;
            }
        };
        loresPage2 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0800);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayLores(screen, xOffset, y, yTextOffset + 0x0800);
            }

            @Override
            public VideoWriter actualWriter() {
                return textPage2;
            }
        };
        dloresPage1 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0400);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayDoubleLores(screen, xOffset, y, yTextOffset + 0x0400);
            }

            @Override
            public VideoWriter actualWriter() {
                return textPage1;
            }
        };
        dloresPage2 = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return (textOffset[y] + 0x0800);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayDoubleLores(screen, xOffset, y, yTextOffset + 0x0800);
            }

            @Override
            public VideoWriter actualWriter() {
                return textPage2;
            }
        };
        mixed = new VideoWriter() {
            @Override
            public int getYOffset(int y) {
                return actualWriter().getYOffset(y);
            }

            @Override
            public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
                displayMixed(screen, xOffset, y, yTextOffset, yGraphicsOffset);
            }

            @Override
            public void markDirty(int y) {
                actualWriter().actualWriter().markDirty(y);
            }

            @Override
            public void clearDirty(int y) {
                actualWriter().actualWriter().clearDirty(y);
            }

            @Override
            public boolean isRowDirty(int y) {
                return actualWriter().actualWriter().isRowDirty(y);
            }

            @Override
            public VideoWriter actualWriter() {
                if (y < 160) {
                    return currentGraphicsWriter;
                }
                return currentTextWriter;
            }

            @Override
            public boolean isMixed() {
                return true;
            }
        };
        registerDirtyFlagChecks();
    }
    // color burst per byte (chat mauve compatibility)
    boolean[] useColor = new boolean[80];

    protected void displayDoubleHires(WritableImage screen, int xOffset, int y, int rowAddress) {
        // Skip odd columns since this does two at once
        if ((xOffset & 0x01) == 1) {
            return;
        }
        int b1 = ((RAM128k) computer.getMemory()).getAuxVideoMemory().readByte(rowAddress + xOffset);
        int b2 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset);
        int b3 = ((RAM128k) computer.getMemory()).getAuxVideoMemory().readByte(rowAddress + xOffset + 1);
        int b4 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset + 1);
        int useColOffset = xOffset << 1;
        // This shouldn't be necessary but prevents an index bounds exception when graphics modes are flipped (Race condition?)
        if (useColOffset >= 77) {
            useColOffset = 76;
        }
        useColor[useColOffset] = (b1 & 0x80) != 0;
        useColor[useColOffset + 1] = (b2 & 0x80) != 0;
        useColor[useColOffset + 2] = (b3 & 0x80) != 0;
        useColor[useColOffset + 3] = (b4 & 0x80) != 0;
        int dhgrWord = 0x07f & b1;
        dhgrWord |= (0x07f & b2) << 7;
        dhgrWord |= (0x07f & b3) << 14;
        dhgrWord |= (0x07f & b4) << 21;
        showDhgr(screen, TIMES_14[xOffset], y, dhgrWord);
    }
    boolean extraHalfBit = false;

    protected void displayHires(WritableImage screen, int xOffset, int y, int rowAddress) {
        // Skip odd columns since this does two at once
        if ((xOffset & 0x01) == 1) {
            return;
        }
        int b1 = 0x0ff & ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset);
        int b2 = 0x0ff & ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset + 1);
        int dhgrWord = HGR_TO_DHGR[(extraHalfBit && xOffset > 0) ? b1 | 0x0100 : b1][b2];
        extraHalfBit = (dhgrWord & 0x10000000) != 0;
        showDhgr(screen, TIMES_14[xOffset], y, dhgrWord & 0xfffffff);
// If you want monochrome, use this instead...
//            showBW(screen, times14[xOffset], y, dhgrWord);
    }
    // Take two consecutive bytes and double them, taking hi-bit into account
    // This should yield a 28-bit word of 7 color dhgr pixels
    // This looks like crap on text...
    static final int[][] HGR_TO_DHGR;
    // Take two consecutive bytes and double them, disregarding hi-bit
    // Useful for text mode
    static final int[][] HGR_TO_DHGR_BW;
    static final int[] TIMES_14;
    static final int[] FLIP_BITS;

    static {
        // complete reverse of 8 bits
        FLIP_BITS = new int[256];
        for (int i = 0; i < 256; i++) {
            FLIP_BITS[i] = (((i * 0x0802 & 0x22110) | (i * 0x8020 & 0x88440)) * 0x10101 >> 16) & 0x0ff;
        }

        TIMES_14 = new int[40];
        for (int i = 0; i < 40; i++) {
            TIMES_14[i] = i * 14;
        }
        HGR_TO_DHGR = new int[512][256];
        HGR_TO_DHGR_BW = new int[256][256];
        for (int bb1 = 0; bb1 < 512; bb1++) {
            for (int bb2 = 0; bb2 < 256; bb2++) {
                int value = ((bb1 & 0x0181) >= 0x0101) ? 1 : 0;
                int b1 = byteDoubler((byte) (bb1 & 0x07f));
                if ((bb1 & 0x080) != 0) {
                    b1 <<= 1;
                }
                int b2 = byteDoubler((byte) (bb2 & 0x07f));
                if ((bb2 & 0x080) != 0) {
                    b2 <<= 1;
                }
                if ((bb1 & 0x040) == 0x040 && (bb2 & 1) != 0) {
                    b2 |= 1;
                }
                value |= b1 | (b2 << 14);
                if ((bb2 & 0x040) != 0) {
                    value |= 0x10000000;
                }
                HGR_TO_DHGR[bb1][bb2] = value;
                HGR_TO_DHGR_BW[bb1 & 0x0ff][bb2]
                        = byteDoubler((byte) bb1) | (byteDoubler((byte) bb2) << 14);
            }
        }
    }

    protected void displayLores(WritableImage screen, int xOffset, int y, int rowAddress) {
        int c1 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset) & 0x0FF;
        if ((y & 7) < 4) {
            c1 &= 15;
        } else {
            c1 >>= 4;
        }
        Color color = Palette.color[c1];
        // Unrolled loop, faster
        PixelWriter writer = screen.getPixelWriter();
        int xx = xOffset * 7;
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
    }

    protected void displayDoubleLores(WritableImage screen, int xOffset, int y, int rowAddress) {
        int c1 = ((RAM128k) computer.getMemory()).getAuxVideoMemory().readByte(rowAddress + xOffset) & 0x0FF;
        int c2 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset) & 0x0FF;
        if ((y & 7) < 4) {
            c1 &= 15;
            c2 &= 15;
        } else {
            c1 >>= 4;
            c2 >>= 4;
        }
        PixelWriter writer = screen.getPixelWriter();
//        int yOffset = xyOffset[y][times14[xOffset]];
        Color color = Palette.color[c1];
        // Unrolled loop, faster
        int xx = xOffset * 7;
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        color = Palette.color[c2];
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
        writer.setColor(xx++, y, color);
    }
    boolean flashInverse = false;
    int flashTimer = 0;
    int FLASH_SPEED = 16; // UTAIIe:8-13,P7 - FLASH toggles every 16 scans
    int[] currentCharMap = CHAR_MAP1;
    static final int[] CHAR_MAP1;
    static final int[] CHAR_MAP2;
    static final int[] CHAR_MAP3;

    static {
        // Generate screen text lookup maps ahead of time
        // ALTCHR clear
        // 00-3F - Inverse characters (uppercase only) "@P 0"
        // 40-7F - Flashing characters (uppercase only) "@P 0"
        // 80-BF - Normal characters (uppercase only) "@P 0"
        // C0-DF - Normal characters (repeat 80-9F) "@P"
        // E0-FF - Normal characters (lowercase) "`p"

        // ALTCHR set
        // 00-3f - Inverse characters (uppercase only) "@P 0"
        // 40-5f - Mousetext (//gs alts are at 0x46 and 0x47, swap with 0x11 and 0x12 for //e and //c)
        // 60-7f - Inverse characters (lowercase only)
        // 80-BF - Normal characters (uppercase only)
        // C0-DF - Normal characters (repeat 80-9F)
        // E0-FF - Normal characters (lowercase)
        // MAP1: Normal map, flash inverse = false
        CHAR_MAP1 = new int[256];
        // MAP2: Normal map, flash inverse = true
        CHAR_MAP2 = new int[256];
        // MAP3: Alt map, mousetext mode
        CHAR_MAP3 = new int[256];
        for (int b = 0; b < 256; b++) {
            int mod = b % 0x020;
            // Inverse
            if (b < 0x020) {
                CHAR_MAP1[b] = mod + 0x0c0;
                CHAR_MAP2[b] = mod + 0x0c0;
                CHAR_MAP3[b] = mod + 0x0c0;
            } else if (b < 0x040) {
                CHAR_MAP1[b] = mod + 0x0a0;
                CHAR_MAP2[b] = mod + 0x0a0;
                CHAR_MAP3[b] = mod + 0x0a0;
            } else if (b < 0x060) {
                // Flash/Mouse
                CHAR_MAP1[b] = mod + 0x0c0;
                CHAR_MAP2[b] = mod + 0x040;
                if (!USE_GS_MOUSETEXT && mod == 6) {
                    CHAR_MAP3[b] = 0x011;
                } else if (!USE_GS_MOUSETEXT && mod == 7) {
                    CHAR_MAP3[b] = 0x012;
                } else {
                    CHAR_MAP3[b] = mod + 0x080;
                }
            } else if (b < 0x080) {
                // Flash/Inverse lowercase
                CHAR_MAP1[b] = mod + 0x0a0;
                CHAR_MAP2[b] = mod + 0x020;
                CHAR_MAP3[b] = mod + 0x0e0;
            } else if (b < 0x0a0) {
                // Normal uppercase
                CHAR_MAP1[b] = mod + 0x040;
                CHAR_MAP2[b] = mod + 0x040;
                CHAR_MAP3[b] = mod + 0x040;
            } else if (b < 0x0c0) {
                // Normal uppercase
                CHAR_MAP1[b] = mod + 0x020;
                CHAR_MAP2[b] = mod + 0x020;
                CHAR_MAP3[b] = mod + 0x020;
            } else if (b < 0x0e0) {
                // Normal uppercase (repeat)
                CHAR_MAP1[b] = mod + 0x040;
                CHAR_MAP2[b] = mod + 0x040;
                CHAR_MAP3[b] = mod + 0x040;
            } else {
                // Normal lowercase
                CHAR_MAP1[b] = mod + 0x060;
                CHAR_MAP2[b] = mod + 0x060;
                CHAR_MAP3[b] = mod + 0x060;
            }
        }
    }

    @Override
    public void vblankStart() {
        // ALTCHR set only affects character mapping and disables FLASH.
        if (SoftSwitches.ALTCH.isOn()) {
            currentCharMap = CHAR_MAP3;
        } else {
            flashTimer--;
            if (flashTimer <= 0) {
                markFlashDirtyBits();
                flashTimer = FLASH_SPEED;
                flashInverse = !flashInverse;
                if (flashInverse) {
                    currentCharMap = CHAR_MAP2;
                } else {
                    currentCharMap = CHAR_MAP1;
                }
            }
        }
        super.vblankStart();
    }

    @Override
    public void vblankEnd() {
    }

    private int getFontChar(byte b) {
        return currentCharMap[b & 0x0ff];
    }

    protected void displayText(WritableImage screen, int xOffset, int y, int rowAddress) {
        // Skip odd columns since this does two at once
        if ((xOffset & 0x01) == 1) {
            return;
        }
        int yOffset = y & 7;
        byte byte2 = ((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset + 1);
        int c1 = getFontChar(((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset));
        int c2 = getFontChar(byte2);
        int b1 = Font.getByte(c1, yOffset);
        int b2 = Font.getByte(c2, yOffset);
        // Why is this getting inversed now?  Bug in hgrToDhgrBW?
        // Nick says: are you getting confused because the //e video ROM is inverted? (1=black)
        int out = HGR_TO_DHGR_BW[b1][b2];
        showBW(screen, TIMES_14[xOffset], y, out);
    }

    protected void displayText80(WritableImage screen, int xOffset, int y, int rowAddress) {
        // Skip odd columns since this does two at once
        if ((xOffset & 0x01) == 1) {
            return;
        }
        int yOffset = y & 7;
        int c1 = getFontChar(((RAM128k) computer.getMemory()).getAuxVideoMemory().readByte(rowAddress + xOffset));
        int c2 = getFontChar(((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset));
        int c3 = getFontChar(((RAM128k) computer.getMemory()).getAuxVideoMemory().readByte(rowAddress + xOffset + 1));
        int c4 = getFontChar(((RAM128k) computer.getMemory()).getMainMemory().readByte(rowAddress + xOffset + 1));
        int bits = Font.getByte(c1, yOffset) | (Font.getByte(c2, yOffset) << 7)
                | (Font.getByte(c3, yOffset) << 14) | (Font.getByte(c4, yOffset) << 21);
        showBW(screen, TIMES_14[xOffset], y, bits);
    }

    private void displayMixed(WritableImage screen, int xOffset, int y, int textOffset, int graphicsOffset) {
        mixed.actualWriter().displayByte(screen, xOffset, y, textOffset, graphicsOffset);
    }
    protected boolean hiresMode = false;
    public boolean dhgrMode = false;

    @Override
    public void configureVideoMode() {
        boolean page2 = SoftSwitches.PAGE2.isOn() && SoftSwitches._80STORE.isOff();
        dhgrMode = SoftSwitches._80COL.getState() && SoftSwitches.DHIRES.getState() && SoftSwitches.HIRES.getState();
        currentTextWriter
                = SoftSwitches._80COL.getState()
                        ? page2
                                ? text80Page2 : text80Page1
                        : page2
                                ? textPage2 : textPage1;
        currentGraphicsWriter
                = SoftSwitches._80COL.getState() && SoftSwitches.DHIRES.getState()
                        ? SoftSwitches.HIRES.getState()
                                ? page2
                                        ? dhiresPage2 : dhiresPage1
                                : page2
                                        ? dloresPage2 : dloresPage1
                        : SoftSwitches.HIRES.getState()
                                ? page2
                                        ? hiresPage2 : hiresPage1
                                : page2
                                        ? loresPage2 : loresPage1;
        setCurrentWriter(
                SoftSwitches.TEXT.getState() ? currentTextWriter
                        : SoftSwitches.MIXED.getState() ? mixed
                                : currentGraphicsWriter);
        hiresMode = !SoftSwitches.DHIRES.getState();
    }

    protected void showDhgr(WritableImage screen, int xOffset, int y, int dhgrWord) {
        //Graphics2D g = (Graphics2D) screen.getGraphics();
        int xx = xOffset * 7;
        PixelWriter writer = screen.getPixelWriter();
        try {
            for (int i = 0; i < 7; i++) {
                Color color = Palette.color[FLIP_NYBBLE[dhgrWord & 15]];
                writer.setColor(xx++, y, color);
                writer.setColor(xx++, y, color);
                writer.setColor(xx++, y, color);
                writer.setColor(xx++, y, color);
                dhgrWord >>= 4;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            Logger.getLogger(getClass().getName()).warning("Went out of bounds in video display");
        }
    }
    static final Color BLACK = Color.BLACK;
    static final Color WHITE = Color.WHITE;
    static final int[][] XY_OFFSET;

    static {
        XY_OFFSET = new int[192][560];
        for (int y = 0; y < 192; y++) {
            for (int x = 0; x < 560; x++) {
                XY_OFFSET[y][x] = y * 560 + x;
            }
        }
    }

    protected void showBW(WritableImage screen, int xOffset, int y, int dhgrWord) {
        // Using the data buffer directly is about 15 times faster than setRGB
        // This is because setRGB does extra (useless) color model logic
        // For that matter even Graphics.drawLine is faster than setRGB!
        // This is equivilant to y*560 but is 5% faster
        // Also, adding xOffset now makes it additionally 5% faster
        //int yOffset = ((y << 4) + (y << 5) + (y << 9))+xOffset;

        int xx = xOffset * 7;
        PixelWriter writer = screen.getPixelWriter();
        for (int i = 0; i < 28; i++) {
            // yOffset++ is used instead of yOffset+i, because it is faster
            writer.setColor(xx++, y, (dhgrWord & 1) == 1 ? WHITE : BLACK);
            dhgrWord >>= 1;
        }
    }

    /**
     *
     */
    @Override
    public void doPostDraw() {
    }

    /**
     *
     * @return
     */
    @Override
    protected String getDeviceName() {
        return "DHGR-Capable Video";
    }

    private void markFlashDirtyBits() {
        // TODO: Be smarter about detecting where flash is used... one day...
        for (int row = 0; row < 192; row++) {
            currentTextWriter.markDirty(row);
        }
    }

    private void registerTextDirtyFlag(RAMEvent e) {
        int row = textRowLookup[e.getAddress() & 0x03ff];
        if (row > 23) {
            return;
        }
        VideoWriter tmark = (e.getAddress() < 0x0800) ? textPage1 : textPage2;
        row <<= 3;
        int yy = row + 8;
        for (int y = row; y < yy; y++) {
            tmark.markDirty(y);
        }
    }

    private void registerHiresDirtyFlag(RAMEvent e) {
        int row = hiresRowLookup[e.getAddress() & 0x01fff];
        if (row < 0 || row >= 192) {
            return;
        }
        VideoWriter mark = (e.getAddress() < 0x04000) ? hiresPage1 : hiresPage2;
        mark.markDirty(row);
    }

    private void registerDirtyFlagChecks() {
        computer.getMemory().observe(RAMEvent.TYPE.WRITE, 0x0400, 0x0bff, this::registerTextDirtyFlag);
        computer.getMemory().observe(RAMEvent.TYPE.WRITE, 0x02000, 0x05fff, this::registerHiresDirtyFlag);
    }

    @Override
    public void reconfigure() {
        // Do nothing (for now)
    }

    @Override
    public void attach() {
        // Do nothing
    }

    @Override
    public void hblankStart(WritableImage screen, int y, boolean isDirty) {
        // Do nothing
    }
}
