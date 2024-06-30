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

package jace.hardware.mockingboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of the AY sound PSG chip. This class manages register values
 * and mixes the channels together (in the update method) The work of
 * maintaining the sound, envelope and noise generator states is provided by the
 * respective generator classes.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class PSG {
    boolean debug = false;
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
    public int bus;
    int selectedReg;
    Map<Reg, Integer> regValues;
    public int mask;

    public PSG(int base, int clock, int sample_rate, String name, int DDR_Mask) {
        this.mask = DDR_Mask;
        baseReg = base;
        channels = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            channels.add(new SoundGenerator(clock, sample_rate));
        }
        envelopeGenerator = new EnvelopeGenerator(clock, sample_rate);
        noiseGenerator = new NoiseGenerator(clock, sample_rate);
        regValues = Collections.synchronizedMap(new EnumMap<>(Reg.class));
        reset();
    }

    public void setBus(int b) {
        bus = b;
    }

    public void setControl(int c) {
        BusControl cmd = BusControl.fromInt(c);
        if (cmd == null) {
            if (debug) System.out.println("Bad control param "+c);
            return;
        }
        switch (cmd) {
            case inactive -> {
            }
            case latch -> {
                if (debug) System.out.println("PSG latched register "+selectedReg);
                selectedReg = bus & 0x0f;
            }
            case read -> {
                bus = getReg(Reg.get(selectedReg));
                if (debug) System.out.println("PSG read register "+selectedReg + " == "+bus);
            }
            case write -> {
                if (debug) System.out.println("PSG wrote register "+selectedReg + " == "+bus);
                setReg(Reg.get(selectedReg), bus);
            }
        }
    }

    public int getBaseReg() {
        return baseReg;
    }

    public void setRate(int clock, int sample_rate) {
        channels.stream().forEach((c) -> {
            c.setRate(clock, sample_rate);
        });
        envelopeGenerator.setRate(clock, sample_rate);
        noiseGenerator.setRate(clock, sample_rate);
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
        value = value & 0x0ff;
        switch (r) {
            case ACoarse, AFine -> channels.get(0).setPeriod(getReg(Reg.AFine) + (getReg(Reg.ACoarse) << 8));
            case BCoarse, BFine -> channels.get(1).setPeriod(getReg(Reg.BFine) + (getReg(Reg.BCoarse) << 8));
            case CCoarse, CFine -> channels.get(2).setPeriod(getReg(Reg.CFine) + (getReg(Reg.CCoarse) << 8));
            case NoisePeriod -> {
                if (value == 0) value = 32;
                noiseGenerator.setPeriod(value+16);
                noiseGenerator.counter = 0;
            }
            case Enable -> {
                channels.get(0).setActive((value & 1) == 0);
                channels.get(0).setNoiseActive((value & 8) == 0);
                channels.get(1).setActive((value & 2) == 0);
                channels.get(1).setNoiseActive((value & 16) == 0);
                channels.get(2).setActive((value & 4) == 0);
                channels.get(2).setNoiseActive((value & 32) == 0);
            }
            case AVol -> channels.get(0).setAmplitude(value);
            case BVol -> channels.get(1).setAmplitude(value);
            case CVol -> channels.get(2).setAmplitude(value);
            case EnvFine, EnvCoarse -> envelopeGenerator.setPeriod(getReg(Reg.EnvFine) + 256 * getReg(Reg.EnvCoarse));
            case EnvShape -> envelopeGenerator.setShape(value);
            case PortA, PortB -> {
            }
        }
    }

    public void update(AtomicInteger bufA, boolean clearA, AtomicInteger bufB, boolean clearB, AtomicInteger bufC, boolean clearC) {
        noiseGenerator.step();
        envelopeGenerator.step();
        if (clearA) {
            bufA.set(channels.get(0).step(noiseGenerator, envelopeGenerator));
        } else {
            bufA.addAndGet(channels.get(0).step(noiseGenerator, envelopeGenerator));
        }
        if (clearB) {
            bufB.set(channels.get(1).step(noiseGenerator, envelopeGenerator));
        } else {
            bufB.addAndGet(channels.get(1).step(noiseGenerator, envelopeGenerator));
        }
        if (clearC) {
            bufC.set(channels.get(2).step(noiseGenerator, envelopeGenerator));
        } else {
            bufC.addAndGet(channels.get(2).step(noiseGenerator, envelopeGenerator));
        }
    }
}
