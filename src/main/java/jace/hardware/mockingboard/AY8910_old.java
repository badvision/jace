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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jace.hardware.CardMockingboard;

// Port of AY code from AppleWin -- not used buy kept for reference.

/***************************************************************************
 *
 * ay8910.c
 *
 *
 * Emulation of the AY-3-8910 / YM2149 sound chip.
 *
 * Based on various code snippets by Ville Hallik, Michael Cuddy,
 * Tatsuyuki Satoh, Fabrice Frances, Nicola Salmoria.
 *
 ***************************************************************************/

//
// From mame.txt (http://www.mame.net/readme.html)
//
// VI. Reuse of Source Code
// --------------------------
//    This chapter might not apply to specific portions of MAME (e.g. CPU
//    emulators) which bear different copyright notices.
//    The source code cannot be used in a commercial product without the written
//    authorization of the authors. Use in non-commercial products is allowed, and
//    indeed encouraged.  If you use portions of the MAME source code in your
//    program, however, you must make the full source code freely available as
//    well.
//    Usage of the _information_ contained in the source code is free for any use.
//    However, given the amount of time and energy it took to collect this
//    information, if you find new information we would appreciate if you made it
//    freely available as well.
//

public class AY8910_old {
    static final int MAX_OUTPUT = 0x007fff;
    static final int MAX_AY8910 = 2;
    static final int CLOCK = 1789770;
    static final int SAMPLE_RATE = 44100;
    
// See AY8910_set_clock() for definition of STEP
    static final int STEP = 0x008000;
    static int num = 0, ym_num = 0;
    int SampleRate = 0;
    
    /* register id's */
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
            max=maxValue;
        }
        static Reg get(int number) {
            for (Reg r:Reg.values())
                if (r.registerNumber == number) return r;
            return null;
        }
        static public Reg[] preferredOrder = new Reg[]{
            Enable,EnvShape,EnvCoarse,EnvFine,NoisePeriod,AVol,BVol,CVol,
            AFine,ACoarse,BFine,BCoarse,CFine,CCoarse};
    }
    
    public AY8910_old() {
        chips = new ArrayList<PSG>();
        for (int i=0; i < MAX_AY8910; i++) {
            PSG chip = new PSG();
            chips.add(chip);
        }
        initAll(CLOCK, SAMPLE_RATE);
    }
    
///////////////////////////////////////////////////////////
    private List<PSG> chips;
    
    static int[] VolTable;
    static {
        buildMixerTable();
    }
    
    private class PSG {
        int Channel;
        int register_latch;
        Map<Reg, Integer> registers;
        
        int lastEnable;
        int UpdateStep;
        int PeriodA,PeriodB,PeriodC,PeriodN,PeriodE;
        int CountA,CountB,CountC,CountN,CountE;
        int VolA,VolB,VolC,VolE;
        int EnvelopeA,EnvelopeB,EnvelopeC;
        int OutputA,OutputB,OutputC,OutputN;
        int CountEnv;
        int Hold,Alternate,Attack,Holding;
        int RNG;
        
        public PSG() {
            registers = new HashMap<Reg, Integer>();
            for (Reg r: Reg.values())
                setReg(r, 0);
        }
        
        public void reset() {
            register_latch = 0;
            RNG = 1;
            OutputA = 0;
            OutputB = 0;
            OutputC = 0;
            OutputN = 0x00ff;
            lastEnable = -1;	/* force a write */
            for (Reg r: Reg.values())
                writeReg(r, 0);
        }
        
        public void setClock(int clock) {
            /* the step clock for the tone and noise generators is the chip clock    */
            /* divided by 8; for the envelope generator of the AY-3-8910, it is half */
            /* that much (clock/16), but the envelope of the YM2149 goes twice as    */
            /* fast, therefore again clock/8.                                        */
            /* Here we calculate the number of steps which happen during one sample  */
            /* at the given sample rate. No. of events = sample rate / (clock/8).    */
            /* STEP is a multiplier used to turn the fraction into a fixed point     */
            /* number.                                                               */
            double clk = clock;
            double smprate = SampleRate;
            UpdateStep = (int) (((double)STEP * smprate * 8.0 + clk/2.0) / clk);
        }
        
        public void setReg(Reg r, int value) {
            registers.put(r,value);
        }
        public int getReg(Reg r) {
            return registers.get(r);
        }
        
        public void writeReg(Reg r, int value) {
            value &= r.max;
            setReg(r, value);
            int old;
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
            switch(r) {
                case ACoarse:
                case AFine:
                    old = PeriodA;
                    PeriodA = (getReg(Reg.AFine) + 256 * getReg(Reg.ACoarse)) * UpdateStep;
                    if (PeriodA == 0) PeriodA = UpdateStep;
                    CountA += PeriodA - old;
                    if (CountA <= 0) CountA = 1;
                    break;
                case BCoarse:
                case BFine:
                    old = PeriodB;
                    PeriodB = (getReg(Reg.BFine) + 256 * getReg(Reg.BCoarse)) * UpdateStep;
                    if (PeriodB == 0) PeriodB = UpdateStep;
                    CountB += PeriodB - old;
                    if (CountB <= 0) CountB = 1;
                    break;
                case CCoarse:
                case CFine:
                    setReg(Reg.CCoarse, getReg(Reg.CCoarse) & 0x0f);
                    old = PeriodC;
                    PeriodA = (getReg(Reg.CFine) + 256 * getReg(Reg.CCoarse)) * UpdateStep;
                    if (PeriodC == 0) PeriodC = UpdateStep;
                    CountC += PeriodC - old;
                    if (CountC <= 0) CountC = 1;
                    break;
                case NoisePeriod:
                    old = PeriodN;
                    PeriodN = getReg(Reg.NoisePeriod) * UpdateStep;
                    if (PeriodN == 0) PeriodN = UpdateStep;
                    CountN += PeriodN - old;
                    if (CountN <= 0) CountN = 1;
                    break;
                case Enable:
                    lastEnable = value;
                    break;
                case AVol:
                    EnvelopeA = value & 0x10;
                    if (EnvelopeA > 0)
                        VolA = VolE;
                    else {
                        if (value > 0)
                            VolA = CardMockingboard.VolTable[value];
                        else
                            VolA = CardMockingboard.VolTable[0];
                    }
                    break;
                case BVol:
                    EnvelopeB = value & 0x10;
                    if (EnvelopeB > 0)
                        VolB = VolE;
                    else {
                        if (value > 0)
                            VolB = CardMockingboard.VolTable[value];
                        else
                            VolB = CardMockingboard.VolTable[0];
                    }
                    break;
                case CVol:
                    EnvelopeC = value & 0x10;
                    if (EnvelopeC > 0)
                        VolC = VolE;
                    else {
                        if (value > 0)
                            VolC = CardMockingboard.VolTable[value];
                        else
                            VolC = CardMockingboard.VolTable[0];
                    }
                    break;
                case EnvFine:
                case EnvCoarse:
                    old = PeriodE;
                    PeriodE = ((getReg(Reg.EnvFine) + 256 * getReg(Reg.EnvCoarse))) * UpdateStep;
                    if (PeriodE == 0) PeriodE = UpdateStep / 2;
                    CountE += PeriodE - old;
                    if (CountE <= 0) CountE = 1;
                    if (PeriodE <= 0) PeriodE = 1;
                    break;
                case EnvShape:
                /* envelope shapes:
                C AtAlH
                0 0 x x  \___
                0 1 x x  /|__
                1 0 0 0  \\\\
                1 0 0 1  \___
                1 0 1 0  \/\/
                           __
                1 0 1 1  \|
                1 1 0 0  ////
                          ___
                1 1 0 1  /
                1 1 1 0  /\/\
                1 1 1 1  /|__
                 
                The envelope counter on the AY-3-8910 has 16 steps. On the YM2149 it
                has twice the steps, happening twice as fast. Since the end result is
                just a smoother curve, we always use the YM2149 behaviour.
                 */
                    Attack = (value & 0x04) != 0 ? 0x1f : 0x00;
                    if ( (value & 0x08) == 0) {
                        /* if Continue = 0, map the shape to the equivalent one which has Continue = 1 */
                        Hold = 1;
                        Alternate = Attack;
                    } else {
                        Hold = value & 0x01;
                        Alternate = value & 0x02;
                    }
                    CountE = PeriodE;
                    CountEnv = 0x1f;
                    Holding = 0;
                    VolE = CardMockingboard.VolTable[CountEnv ^ Attack];
                    if (EnvelopeA != 0) VolA = VolE;
                    if (EnvelopeB != 0) VolB = VolE;
                    if (EnvelopeC != 0) VolC = VolE;
                    break;
                case PortA:
                case PortB:
                    break;
            }
        }
        
        void update(int[][] buffer, int length) {
            int[] buf1, buf2, buf3;
            int outn;
            
            buf1 = buffer[0];
            buf2 = buffer[1];
            buf3 = buffer[2];
            
            
            /* The 8910 has three outputs, each output is the mix of one of the three */
            /* tone generators and of the (single) noise generator. The two are mixed */
            /* BEFORE going into the DAC. The formula to mix each channel is: */
            /* (ToneOn | ToneDisable) & (NoiseOn | NoiseDisable). */
            /* Note that this means that if both tone and noise are disabled, the output */
            /* is 1, not 0, and can be modulated changing the volume. */
            
            
            /* If the channels are disabled, set their output to 1, and increase the */
            /* counter, if necessary, so they will not be inverted during this update. */
            /* Setting the output to 1 is necessary because a disabled channel is locked */
            /* into the ON state (see above); and it has no effect if the volume is 0. */
            /* If the volume is 0, increase the counter, but don't touch the output. */
            if ( (getReg(Reg.Enable) & 0x01) != 0) {
                if (CountA <= length*STEP) CountA += length*STEP;
                OutputA = 1;
            } else if (getReg(Reg.AVol) == 0) {
                /* note that I do count += length, NOT count = length + 1. You might think */
                /* it's the same since the volume is 0, but doing the latter could cause */
                /* interferencies when the program is rapidly modulating the volume. */
                if (CountA <= length*STEP) CountA += length*STEP;
            }
            
            if ( (getReg(Reg.Enable) & 0x02) != 0) {
                if (CountB <= length*STEP) CountB += length*STEP;
                OutputB = 1;
            } else if (getReg(Reg.BVol) == 0) {
                if (CountB <= length*STEP) CountB += length*STEP;
            }
            
            if ( (getReg(Reg.Enable) & 0x04) != 0) {
                if (CountC <= length*STEP) CountC += length*STEP;
                OutputC = 1;
            } else if (getReg(Reg.CVol) == 0) {
                if (CountC <= length*STEP) CountC += length*STEP;
            }
            
            /* for the noise channel we must not touch OutputN - it's also not necessary */
            /* since we use outn. */
            if ((getReg(Reg.Enable) & 0x38) == 0x38)	/* all off */
                if (CountN <= length*STEP) CountN += length*STEP;
            
            outn = (OutputN | getReg(Reg.Enable));
            int index = 0;
            //System.out.println("Length:"+length);
            /* buffering loop */
            while (length != 0) {
                int vola,volb,volc;
                int left;
                
                /* vola, volb and volc keep track of how long each square wave stays */
                /* in the 1 position during the sample period. */
                vola = volb = volc = 0;
                //System.out.println("STEP:"+STEP);
                
                left = STEP;
                do {
                    int nextevent;
                    
                    if (CountN < left) nextevent = CountN;
                    else nextevent = left;
                        
                    if ( (outn & 0x08) != 0) {
                        if (OutputA != 0) vola += CountA;
                        CountA -= nextevent;
                        /* PeriodA is the half period of the square wave. Here, in each */
                        /* loop I add PeriodA twice, so that at the end of the loop the */
                        /* square wave is in the same status (0 or 1) it was at the start. */
                        /* vola is also incremented by PeriodA, since the wave has been 1 */
                        /* exactly half of the time, regardless of the initial position. */
                        /* If we exit the loop in the middle, OutputA has to be inverted */
                        /* and vola incremented only if the exit status of the square */
                        /* wave is 1. */
                        while (CountA <= 0 && PeriodA > 0) {
                            CountA += PeriodA;
                            if (CountA > 0) {
                                OutputA ^= 1;
                                if (OutputA != 0) vola += PeriodA;
                                break;
                            }
                            CountA += PeriodA;
                            vola += PeriodA;
                        }
                        if (OutputA != 0) vola -= CountA;
                    } else {
                        CountA -= nextevent;
                        while (CountA <= 0 && PeriodA > 0) {
                            CountA += PeriodA;
                            if (CountA > 0) {
                                OutputA ^= 1;
                                break;
                            }
                            CountA += PeriodA;
                        }
                    }
                    
                    if ((outn & 0x10) != 0) {
                        if (OutputB != 0) volb += CountB;
                        CountB -= nextevent;
                        while (CountB <= 0 && PeriodB > 0) {
                            CountB += PeriodB;
                            if (CountB > 0) {
                                OutputB ^= 1;
                                if (OutputB != 0) volb += PeriodB;
                                break;
                            }
                            CountB += PeriodB;
                            volb += PeriodB;
                        }
                        if (OutputB != 0) volb -= CountB;
                    } else {
                        CountB -= nextevent;
                        while (CountB <= 0 && PeriodB > 0) {
                            CountB += PeriodB;
                            if (CountB > 0) {
                                OutputB ^= 1;
                                break;
                            }
                            CountB += PeriodB;
                        }
                    }
                    
                    if ( (outn & 0x20) != 0) {
                        if (OutputC != 0) volc += CountC;
                        CountC -= nextevent;
                        while (CountC <= 0 && PeriodC > 0) {
                            CountC += PeriodC;
                            if (CountC > 0) {
                                OutputC ^= 1;
                                if (OutputC != 0) volc += PeriodC;
                                break;
                            }
                            CountC += PeriodC;
                            volc += PeriodC;
                        }
                        if (OutputC != 0) volc -= CountC;
                    } else {
                        CountC -= nextevent;
                        while (CountC <= 0 && PeriodC > 0) {
                            CountC += PeriodC;
                            if (CountC > 0) {
                                OutputC ^= 1;
                                break;
                            }
                            CountC += PeriodC;
                        }
                    }
                    
                    CountN -= nextevent;
                    if (CountN <= 0 && PeriodN > 0) {
                        /* Is noise output going to change? */
                        /* (bit0^bit1)? */
                        if (((RNG + 1) & 2) != 0) {
                            OutputN ^= 0x0FF;
                            outn = (OutputN | getReg(Reg.Enable));
                        }
                        
                        /* The Random Number Generator of the 8910 is a 17-bit shift */
                        /* register. The input to the shift register is bit0 XOR bit3 */
                        /* (bit0 is the output). This was verified on AY-3-8910 and YM2149 chips. */
                        
                        /* The following is a fast way to compute bit17 = bit0^bit3. */
                        /* Instead of doing all the logic operations, we only check */
                        /* bit0, relying on the fact that after three shifts of the */
                        /* register, what now is bit3 will become bit0, and will */
                        /* invert, if necessary, bit14, which previously was bit17. */
                        if ((RNG & 1) != 0) RNG ^= 0x0024000; /* This version is called the "Galois configuration". */
                        RNG >>= 1;
                        CountN += PeriodN;
                    }
                    
                    left -= nextevent;
                } while (left > 0);
          //      System.out.println("End left loop");
                
                /* update envelope */
                if (Holding == 0) {
                    CountE -= STEP;
                    if (CountE <= 0) {
                        do {
                            CountEnv--;
                            CountE += PeriodE;
                        } while (CountE <= 0);
                        
                        /* check envelope current position */
                        if (CountEnv < 0) {
                            if (Hold != 0) {
                                if (Alternate != 0)
                                    Attack ^= 0x1f;
                                Holding = 1;
                                CountEnv = 0;
                            } else {
                                /* if CountEnv has looped an odd number of times (usually 1), */
                                /* invert the output. */
                                if ( (Alternate != 0) && ((CountEnv & 0x20) != 0))
                                    Attack ^= 0x1f;
                                CountEnv &= 0x1f;
                            }
                        }
                        
                        VolE = VolTable[CountEnv ^ Attack];
                        /* reload volume */
                        if (EnvelopeA != 0) VolA = VolE;
                        if (EnvelopeB != 0) VolB = VolE;
                        if (EnvelopeC != 0) VolC = VolE;
                    }
                }
                
                // Output PCM wave [-32768...32767] instead of MAME's voltage level [0...32767]
                // - This allows for better s/w mixing
buf1[index] = (vola * VolA) / STEP;
buf2[index] = (volb * VolB) / STEP;
buf3[index] = (volc * VolC) / STEP;
/*                
                if(VolA != 0) {
                    if (vola != 0) buf1[index] = (vola * VolA) / STEP;
                    else buf1[index] = -VolA;
                } else {
                    buf1[index] = 0;
                }
                
                //
                
                if(VolB != 0) {
                    if (volb != 0) buf2[index] = (volb * VolB) / STEP;
                    else buf2[index] = -VolB;
                } else
                    buf2[index] = 0;
                
                //
                
                if(VolC != 0) {
                    if (volc != 0) buf3[index] = (volc * VolC) / STEP;
                    else buf3[index] = -VolC;
                } else
                    buf3[index] = 0;
   */             
                index++;
                length--;
            }
        }
    };
    
    public void writeReg(int chipNumber, int register, int value) {
        Reg r = Reg.get(register);
        writeReg(chipNumber, r, value);
    }
    
    public void writeReg(int chipNumber, Reg register, int value) {
        chips.get(chipNumber).writeReg(register, value);
    }
    
    // /length/ is the number of samples we require
    // NB. This should be called at twice the 6522 IRQ rate or (eg) 60Hz if no IRQ.
    public void update(int chipNumber,int[][] buffer,int length) {
        chips.get(chipNumber).update(buffer, length);
    }
    
    int[][] buffers;
    int bufferLength = -1;
    public int[][] getBuffers(int length) {
        if (buffers == null || bufferLength != length) {
            buffers = new int[3][length];
            bufferLength = length;
        }
        return buffers;
    }
    
    public void playSound(int size, int[] left, int[] right) {
        int[][] buffers = getBuffers(left.length);
        update(0, buffers, size);
        mixDown(left, buffers, size);
        update(1, buffers, size);
        mixDown(right, buffers, size);        
    }
    
    public void mixDown(int[] out, int[][] in, int size) {
        for (int i=0; i < size; i++) {
            int sample = (in[0][i] + in[1][i] + in[2][i]) / 3;
            out[i] = sample;
        }
    }
    
    public void setClock(int chipNumber,int clock) {
        chips.get(chipNumber).setClock(clock);
    }
    
    public void reset(int chipNumber) {
        chips.get(chipNumber).reset();
    }
    
    public void initAll(int nClock, int nSampleRate) {
        SampleRate = nSampleRate;
        for (PSG p:chips) {
            p.setClock(nClock);
            p.reset();
        }
    }
    
    public void initClock(int nClock) {
        for (PSG p:chips) p.setClock(nClock);
    }
    
    static void buildMixerTable() {
        VolTable = new int[32];
        int SampleRate;
        
        /* calculate the volume->voltage conversion table */
        /* The AY-3-8910 has 16 levels, in a logarithmic scale (3dB per step) */
        /* The YM2149 still has 16 levels for the tone generators, but 32 for */
        /* the envelope generator (1.5dB per step). */
        double out = MAX_OUTPUT;
        for (int i = 31;i > 0;i--) {
            VolTable[i] = (int) (out + 0.5);	/* round to nearest */	// [TC: unsigned int cast]
            out /= 1.188502227;	/* = 10 ^ (1.5/20) = 1.5dB */
        }
        VolTable[0] = 0;
    }
}