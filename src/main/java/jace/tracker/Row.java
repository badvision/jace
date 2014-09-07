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
package jace.tracker;

import jace.core.Utility;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import javax.swing.ImageIcon;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Row {

    public static enum Note {
        C0(3901),
        CS0(3682),
        D0(3476),
        DS0(3279),
        E0(3096),
        F0(2922),
        FS0(2759),
        G0(2603),
        GS0(2457),
        A0(2319),
        AS0(2189),
        B0(2066),
        C1(1950),
        CS1(1841),
        D1(1737),
        DS1(1640),
        E1(1548),
        F1(1461),
        FS1(1379),
        G1(1302),
        GS1(1229),
        A1(1160),
        AS1(1095),
        B1(1033),
        C2(975),
        CS2(920),
        D2(869),
        DS2(820),
        E2(774),
        F2(731),
        FS2(690),
        G2(651),
        GS2(614),
        A2(580),
        AS2(547),
        B2(517),
        C3(488),
        CS3(460),
        D3(434),
        DS3(410),
        E3(387),
        F3(365),
        FS3(345),
        G3(325),
        GS3(307),
        A3(290),
        AS3(274),
        B3(258),
        C4(244),
        CS4(230),
        D4(217),
        DS4(205),
        E4(193),
        F4(183),
        FS4(172),
        G4(163),
        GS4(154),
        A4(145),
        AS4(137),
        B4(129),
        C5(122),
        CS5(115),
        D5(109),
        DS5(102),
        E5(97),
        F5(91),
        FS5(86),
        G5(81),
        GS5(77),
        A5(72),
        AS5(68),
        B5(65),
        C6(61),
        CS6(58),
        D6(54),
        DS6(51),
        E6(48),
        F6(46),
        FS6(43),
        G6(41),
        GS6(38),
        A6(36),
        AS6(34),
        B6(32),
        C7(30),
        CS7(29),
        D7(27),
        DS7(26),
        E7(24),
        F7(23),
        FS7(22),
        G7(20),
        GS7(19),
        A7(18),
        AS7(17),
        B7(16),
        C8(15);
        int freq;

        Note(int f) {
            freq = f;
        }

        @Override
        public String toString() {
            return super.toString().replace("S", "#");
        }
    }

    static ImageIcon[] ENVELOPE_ICONS;
    static {
        ENVELOPE_ICONS = new ImageIcon[EnvelopeShape.values().length];
        int i = 0;
        for (EnvelopeShape shape : EnvelopeShape.values()) {
            ENVELOPE_ICONS[i++] = shape.getIcon();
        }
    }
    public static enum EnvelopeShape {
        unspecified(-1, ""),
        pulse(0, "|\\____"),
        pulseinv(4, "/|____"),
        saw(8, "|\\|\\|\\"),
        triangle(10,"\\/\\/\\/"),
        triangleinv(14,"/\\/\\/\\"),
        holdinv(11,"|\\|^^^"),
        hold(13,"/^^^^^");
        int value;
        String pattern;
        ImageIcon icon;
        EnvelopeShape(int v, String p) {
            value = v;
            pattern = p;
        }
        ImageIcon getIcon() {
            if (icon == null) {
                if (value >= 0) {
                    icon = Utility.loadIcon("ayenvelope"+value+".png");
                } else {
                    icon = new ImageIcon(new BufferedImage(64, 12,BufferedImage.TYPE_4BYTE_ABGR));
                }
                icon.setDescription(toString());
            }
            return icon;
        }
    }    
    
    public static enum Channel {A1, B1, C1, A2, B2, C2}
    
    public static class ChannelData {
        public Note tone;
        public Integer volume;         // Range 0-F
        public Boolean toneActive;
        public Boolean noiseActive;
        public Boolean envelopeActive; // Results in volume = 0x010
        public Set<Command> commands = new HashSet<Command>();
        public boolean isEmpty() {
            if (!commands.isEmpty()) return false;
            if (tone != null || volume != null || toneActive != null || noiseActive != null || envelopeActive != null) return false;
            return true;
        }
    }
    
    public EnumMap<Channel, ChannelData> channels = new EnumMap<Channel, ChannelData>(Channel.class);

    public Row() {
        for (Channel c : Channel.values()) {
            channels.put(c, new ChannelData());
        }
    }
    
    public Integer ay1noisePeriod, ay2noisePeriod;
    public Integer ay1envelopePeriod, ay2envelopePeriod;
    public EnvelopeShape ay1envelopeShape, ay2envelopeShape;
    public Set<Command> globalCommands = new HashSet<Command>();
    
    public boolean isEmpty() {
        for (ChannelData d : channels.values()) {
            if (d != null && !d.isEmpty()) return false;
        }
        if (ay1envelopePeriod != null || ay2envelopePeriod != null) return false;
        if (ay1envelopeShape != null || ay2envelopeShape != null) return false;
        if (ay1noisePeriod != null || ay2noisePeriod != null) return false;
        return globalCommands.isEmpty();            
    }
}