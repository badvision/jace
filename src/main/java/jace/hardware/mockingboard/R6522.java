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

import jace.Emulator;
import jace.core.Device;

/**
 * Implementation of 6522 VIA chip
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class R6522 extends Device {
    public static long SPEED = 1020484L; // (NTSC)
    
    public R6522() {
        super();
        timer1freerun = true;
        timer1running = true;
        timer1latch = 0x1fff;
        timer1interruptEnabled = false;
    }

    // 6522 VIA
    // http://www.applevault.com/twiki/Main/Mockingboard/6522.pdf
    // I/O registers
    public enum Register {
        ORB(0), // Output Register B
        ORA(1), // Output Register A
        DDRB(2),// Data direction reg B
        DDRA(3),// Data direction reg A
        T1CL(4),// T1 low-order latches (low-order counter for read operations)
        T1CH(5),// T1 high-order counter
        T1LL(6),// T1 low-order latches
        T1LH(7),// T1 high-order latches
        T2CL(8),// T2 low-order latches (low-order counter for read operations)
        T2CH(9),// T2 high-order counter
        SR(10),// Shift register
        ACR(11),// Aux control register
        PCR(12),// Perripheral control register
        IFR(13),// Interrupt flag register
        IER(14),// Interrupt enable register
        ORAH(15);// Output Register A (no handshake)
        
        int val;

        Register(int v) {
            val = v;
        }
        
        static public Register fromInt(int i) {
            for (Register r : Register.values()) {
                if (r.val == i) {
                    return r;
                }
            }
            return null;
        }
    }
    // state variables
    public int oraReg = 0;
    public int iraReg = 0;
    public int orbReg = 0;
    public int irbReg = 0;
    // DDRA and DDRB must be set to output for mockingboard to do anything
    // Common values for this are FF for DDRA and 7 for DDRB
    // DDRB bits 0-2 are used to control AY chips but bits 3-7 are not connected.
    // that's why it is common to see mockingboard drivers init the port with a 7
    public int dataDirectionA = 0;
    public int dataDirectionB = 0;

    // Though this is necessary for a complete emulation of the 6522, it isn't needed by the mockingboard
    // set by bit 0 of ACR
//    public boolean latchEnabledA = false;
    // set by bit 1 of ACR
//    public boolean latchEnabledB = false;
    //Bits 2,3,4 of ACR
//    static public enum ShiftRegisterControl {
//        interruptDisabled(0),
//        shiftInT2(4),
//        shiftIn02(8),
//        shiftInExt(12),
//        shiftOutFree(16),
//        shiftOutT2(20),
//        shiftOut02(24),
//        shiftOutExt(28);
//        
//        int val;
//        private ShiftRegisterControl(int v) {
//            val = v;
//        }
//
//        public static ShiftRegisterControl fromBits(int b) {
//            b=b&28;
//            for (ShiftRegisterControl s : values()) {
//                if (s.val == b) return s;
//            }
//            return null;
//        }
//    }
//    public ShiftRegisterControl shiftMode = ShiftRegisterControl.interruptDisabled;
//    //Bit 5 of ACR (false = timed interrupt, true = count down pulses on PB6)
//    public boolean t2countPulses = false;
//    //Bit 6 of ACR (true = continuous, false = one-shot)
//    public boolean t1continuous = false;
//    //Bit 7 of ACR (true = enable PB7, false = interruptDisabled)
//    public boolean t1enablePB7 = false;
//    // NOTE: Mockingboard did not use PB6 or PB7, they are not connected to anything
    public boolean timer1interruptEnabled = true;
    public boolean timer1IRQ = false; // True if timer interrupt flag is set
    public int timer1latch = 0;
    public int timer1counter = 0;
    public boolean timer1freerun = false;
    public boolean timer1running = false;
    public boolean timer2interruptEnabled = true;
    public boolean timer2IRQ = false; // True if timer interrupt flag is set
    public int timer2latch = 0;
    public int timer2counter = 0;
    public boolean timer2running = false;
    public boolean unclocked = false;
    public boolean debug = false;    

    @Override
    protected String getDeviceName() {
        return "6522 VIA Chip";
    }
    
    @Override
    public void tick() {
        if (timer1running) {
            timer1counter--;
            if (debug && timer1counter % 1000 == 0)
                System.out.println(getShortName() + " Timer 1 counter: "+timer1counter+" Timer 1 interrupt enabled: "+timer1interruptEnabled);
            if (timer1counter < 0) {
                timer1counter = timer1latch;
                if (!timer1freerun) {
                    timer1running = false;
                }
                if (timer1interruptEnabled) {
                    if (debug) System.out.println("Timer 1 generated interrupt");
                    timer1IRQ = true;
                    Emulator.withComputer(c->c.getCpu().generateInterrupt());
                }
            }
        }
        if (timer2running) {
            timer2counter--;
            if (debug && timer2counter % 1000 == 0)
                System.out.println(getShortName() + " Timer 2 counter: "+timer2counter+" Timer 2 interrupt enabled: "+timer2interruptEnabled);
            if (timer2counter < 0) {
                timer2running = false;
                timer2counter = timer2latch;
                if (timer2interruptEnabled) {
                    if (debug) System.out.println("Timer 2 generated interrupt");
                    timer2IRQ = true;
                    Emulator.withComputer(c->c.getCpu().generateInterrupt());
                }
            }
        }
        if (!timer1running && !timer2running) {
            if (debug) System.out.println("No timers active, suspending");
            suspend();
        }
    }
    
    public void setUnclocked(boolean unclocked) {
        this.unclocked = unclocked;
    }
    
    @Override
    public void reconfigure() {
        // Nothing to do
    }
    
    public void writeRegister(int reg, int val) {
        int value = val & 0x0ff;
        Register r = Register.fromInt(reg);
        if (debug) System.out.println(getShortName() + " Writing "+Integer.toHexString(value&0x0ff)+" to register "+r.toString());
        switch (r) {
            case ORB -> {
                if (dataDirectionB == 0) {
                    break;
                }
                sendOutputB(value & dataDirectionB);
            }
            case ORA -> {
                //            case ORAH:
                if (dataDirectionA == 0) {
                    break;
                }
                sendOutputA(value & dataDirectionA);
            }
            case DDRB -> dataDirectionB = value;
            case DDRA -> dataDirectionA = value;
            case T1CL, T1LL -> timer1latch = (timer1latch & 0x0ff00) | value;
            case T1CH -> {
                timer1latch = (timer1latch & 0x0ff) | (value << 8);
                timer1IRQ = false;
                timer1counter = timer1latch;
                timer1running = true;
            }
            case T1LH -> {
                timer1latch = (timer1latch & 0x0ff) | (value << 8);
                timer1IRQ = false;
            }
            case T2CL -> timer2latch = (timer2latch & 0x0ff00) | value;
            case T2CH -> {
                timer2latch = (timer2latch & 0x0ff) | (value << 8);
                timer2IRQ = false;
                timer2counter = timer2latch;
                timer2running = true;
            }
            case SR -> {
            }
            case ACR -> {
                // SHIFT REGISTER NOT IMPLEMENTED
                timer1freerun = (value & 64) != 0;
                if (timer1freerun) {
                    timer1running = true;
                }
            }
            case PCR -> {
            }
            case IFR -> {
                if ((value & 64) != 0) {
                    timer1IRQ = false;
                }
                if ((value & 32) != 0) {
                    timer2IRQ = false;
                }
            }
            case IER -> {
                boolean enable = (value & 128) != 0;
                if ((value & 64) != 0) {
                    timer1interruptEnabled = enable;
                }
                if ((value & 32) != 0) {
                    timer2interruptEnabled = enable;
                }
            }
            default -> {
            }
        }
        // SHIFT REGISTER NOT IMPLEMENTED
        // TODO: Implement if Votrax (SSI) is to be supported
        if (timer1running || timer2running) {
            if (debug) System.out.println("One or more timers active, resuming");
            resume();
        }
    }

    // Whatever uses 6522 will want to know when it is outputting values
    // So to hook that in, these abstract methods will be defined as appropriate
    public abstract void sendOutputA(int value);

    public abstract void sendOutputB(int value);
    
    public int readRegister(int reg) {
        Register r = Register.fromInt(reg);
        if (debug) System.out.println(getShortName() + " Reading register "+r.toString());
        switch (r) {
            case ORB -> {
                if (dataDirectionB == 0x0ff) {
                    break;
                }
                return receiveOutputB() & (dataDirectionB ^ 0x0ff);
            }
            case ORA, ORAH -> {
                if (dataDirectionA == 0x0ff) {
                    break;
                }
                return receiveOutputA() & (dataDirectionA ^ 0x0ff);
            }
            case DDRB -> {
                return dataDirectionB;
            }
            case DDRA -> {
                return dataDirectionA;
            }
            case T1CL -> {
                timer1IRQ = false;
                return timer1counter & 0x0ff;
            }
            case T1CH -> {
                return (timer1counter & 0x0ff00) >> 8;
            }
            case T1LL -> {
                return timer1latch & 0x0ff;
            }
            case T1LH -> {
                return (timer1latch & 0x0ff00) >> 8;
            }
            case T2CL -> {
                timer2IRQ = false;
                return timer2counter & 0x0ff;
            }
            case T2CH -> {
                return (timer2counter & 0x0ff00) >> 8;
            }
            case SR -> {
                // SHIFT REGISTER NOT IMPLEMENTED
                return 0;
            }
            case ACR -> {
                // SHIFT REGISTER NOT IMPLEMENTED
                if (timer1freerun) {
                    return 64;
                }
                return 0;
            }
            case PCR -> {
            }
            case IFR -> {
                int val = 0;
                if (timer1IRQ) {
                    val |= 64;
                }
                if (timer2IRQ) {
                    val |= 32;
                }
                if (val != 0) {
                    val |= 128;
                }
                return val;
            }
            case IER -> {
                int val = 128;
                if (timer1interruptEnabled) {
                    val |= 64;
                }
                if (timer2interruptEnabled) {
                    val |= 32;
                }
                return val;
            }
        }
        return 0;
    }

    public abstract int receiveOutputA();

    public abstract int receiveOutputB();    
}
