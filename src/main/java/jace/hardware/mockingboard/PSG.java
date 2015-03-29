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
package jace.hardware.mockingboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the AY sound PSG chip. This class manages register values
 * and mixes the channels together (in the update method) The work of
 * maintaining the sound, envelope and noise generator states is provided by the
 * respective generator classes.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class PSG {

    int baseReg;
    /* register ids */

    public enum Reg {

        AFine(0, 255),
        ACoarse(1, 15),
        BFine(2, 255),
        BCoarse(3, 15),
        CFine(4, 255),
        CCoarse(5, 15),
        NoisePeriod(6, 31),
        Enable(7, 255),
        AVol(8, 31),
        BVol(9, 31),
        CVol(10, 31),
        EnvFine(11, 255),
        EnvCoarse(12, 255),
        EnvShape(13, 15),
        PortA(14, 255),
        PortB(15, 255);
        public final int registerNumber;
        public final int max;

        Reg(int number, int maxValue) {
            registerNumber = number;
            max = maxValue;
        }

        static Reg get(int number) {
            for (Reg r : Reg.values()) {
                if (r.registerNumber == number) {
                    return r;
                }
            }
            return null;
        }
        static public Reg[] preferredOrder = new Reg[]{
            Enable, EnvShape, EnvCoarse, EnvFine, NoisePeriod, AVol, BVol, CVol,
            AFine, ACoarse, BFine, BCoarse, CFine, CCoarse};
    }

    public enum BusControl {

        inactive(4),
        read(5),
        write(6),
        latch(7);
        int val;

        BusControl(int v) {
            val = v;
        }

        public static BusControl fromInt(int i) {
            for (BusControl b : BusControl.values()) {
                if (b.val == i) {
                    return b;
                }
            }
            return null;
        }
    }
    List<SoundGenerator> channels;
    EnvelopeGenerator envelopeGenerator;
    NoiseGenerator noiseGenerator;
    int CLOCK;
    int SAMPLE_RATE;
    public int bus;
    int selectedReg;
    String name;
    Map<Reg, Integer> regValues;
    public int mask;

    public PSG(int base, int clock, int sample_rate, String name, int DDR_Mask) {
        this.name = name;
        this.mask = DDR_Mask;
        baseReg = base;
        channels = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            channels.add(new SoundGenerator(clock, sample_rate));
        }
        envelopeGenerator = new EnvelopeGenerator(clock, sample_rate);
        noiseGenerator = new NoiseGenerator(clock, sample_rate);
        regValues = Collections.synchronizedMap(new EnumMap<Reg, Integer>(Reg.class));
        reset();
    }

    public void setBus(int b) {
        bus = b;
    }

    public void setControl(int c) {
        BusControl cmd = BusControl.fromInt(c);
        if (cmd == null) {
//            System.out.println("Bad control param "+c);
            return;
        }
        switch (cmd) {
            case inactive:
                break;
            case latch:
//                System.out.println("PSG latched register "+selectedReg);
                selectedReg = bus & 0x0f;
                break;
            case read:
                bus = getReg(Reg.get(selectedReg));
//                System.out.println("PSG read register "+selectedReg + " == "+bus);
                break;
            case write:
//                System.out.println("PSG wrote register "+selectedReg + " == "+bus);
                setReg(Reg.get(selectedReg), bus);
                break;
        }
    }

    public int getBaseReg() {
        return baseReg;
    }

    public void setRate(int clock, int sample_rate) {
        CLOCK = clock;
        SAMPLE_RATE = sample_rate;
        channels.stream().forEach((c) -> {
            c.setRate(clock, sample_rate);
        });
        envelopeGenerator.setRate(clock, sample_rate);
        noiseGenerator.setRate(clock, sample_rate);
        reset();
    }

    public final void reset() {
        for (Reg r : Reg.values()) {
            // Don't reset the enable register with 0, that will turn everything on!
            setReg(r, r == Reg.Enable ? 255 : 0);
        }
        envelopeGenerator.reset();
        noiseGenerator.reset();
        channels.parallelStream().forEach((c) -> {
            c.reset();
        });
    }

    public void setReg(Reg r, int value) {
        if (r != null) {
            value &= r.max;
            regValues.put(r, value);
            writeReg(r, value & 0x0ff);
        }
    }

    public int getReg(Reg r) {
        if (r == null) {
            return -1;
        }
        Integer value = regValues.get(r);
        if (value == null) {
            return -1;
        }
        return value & 0x0ff;
    }

    public void writeReg(Reg r, int value) {
        /* A note about the period of tones, noise and envelope: for speed reasons,*/
        /* we count down from the period to 0, but careful studies of the chip     */
        /* output prove that it instead counts up from 0 until the counter becomes */
        /* greater or equal to the period. This is an important difference when the*/
        /* program is rapidly changing the period to modulate the sound.           */
        /* To compensate for the difference, when the period is changed we adjust  */
        /* our internal counter.                                                   */
        /* Also, note that period = 0 is the same as period = 1. This is mentioned */
        /* in the YM2203 data sheets. However, this does NOT apply to the Envelope */
        /* period. In that case, period = 0 is half as period = 1. */
        switch (r) {
            case ACoarse:
            case AFine:
                channels.get(0).setPeriod(getReg(Reg.AFine) + (getReg(Reg.ACoarse) << 8));
                break;
            case BCoarse:
            case BFine:
                channels.get(1).setPeriod(getReg(Reg.BFine) + (getReg(Reg.BCoarse) << 8));
                break;
            case CCoarse:
            case CFine:
                channels.get(2).setPeriod(getReg(Reg.CFine) + (getReg(Reg.CCoarse) << 8));
                break;
            case NoisePeriod:
                noiseGenerator.setPeriod(value);
                noiseGenerator.counter = 0;
                break;
            case Enable:
                channels.get(0).setActive((value & 1) == 0);
                channels.get(0).setNoiseActive((value & 8) == 0);
                channels.get(1).setActive((value & 2) == 0);
                channels.get(1).setNoiseActive((value & 16) == 0);
                channels.get(2).setActive((value & 4) == 0);
                channels.get(2).setNoiseActive((value & 32) == 0);
                break;
            case AVol:
                channels.get(0).setAmplitude(value);
                break;
            case BVol:
                channels.get(1).setAmplitude(value);
                break;
            case CVol:
                channels.get(2).setAmplitude(value);
                break;
            case EnvFine:
            case EnvCoarse:
                envelopeGenerator.setPeriod(getReg(Reg.EnvFine) + 256 * getReg(Reg.EnvCoarse));
                break;
            case EnvShape:
                envelopeGenerator.setShape(value);
                break;
            case PortA:
            case PortB:
                break;
        }
    }

    public void update(int[] bufA, boolean clearA, int[] bufB, boolean clearB, int[] bufC, boolean clearC, int length) {
        for (int i = 0; i < length; i++) {
            noiseGenerator.step();
            envelopeGenerator.step();
            if (clearA) {
                bufA[i] = channels.get(0).step(noiseGenerator, envelopeGenerator);
            } else {
                bufA[i] += channels.get(0).step(noiseGenerator, envelopeGenerator);
            }
            if (clearB) {
                bufB[i] = channels.get(1).step(noiseGenerator, envelopeGenerator);
            } else {
                bufB[i] += channels.get(1).step(noiseGenerator, envelopeGenerator);
            }
            if (clearC) {
                bufC[i] = channels.get(2).step(noiseGenerator, envelopeGenerator);
            } else {
                bufC[i] += channels.get(2).step(noiseGenerator, envelopeGenerator);
            }
        }
    }
};
