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

import jace.core.Computer;
import jace.core.Device;

/**
 * Implementation of 6522 VIA chip
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class R6522 extends Device {

    public R6522(Computer computer) {
        super(computer);
    }
    
    // 6522 VIA
    // http://www.applevault.com/twiki/Main/Mockingboard/6522.pdf
    
    // I/O registers
    public static enum Register {
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
                if (r.val == i) return r;
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
    
    @Override
    protected String getDeviceName() {
        return "6522 VIA Chip";
    }

    @Override
    public void tick() {
        if (timer1running) {
            timer1counter --;
            if (timer1counter < 0) {
                timer1counter = timer1latch;
                if (!timer1freerun) timer1running = false;
                if (timer1interruptEnabled) {
//                    System.out.println("Timer 1 generated interrupt");
                    timer1IRQ = true;
                    computer.getCpu().generateInterrupt();
                }
            }
        }
        if (timer2running) {
            timer2counter --;
            if (timer2counter < 0) {
                timer2running = false;
                timer2counter = timer2latch;
                if (timer2interruptEnabled) {
                    timer2IRQ = true;
                    computer.getCpu().generateInterrupt();
                }
            }
        }
        if (!timer1running && !timer2running)
            setRun(false);
    }

    @Override
    public void attach() {
        // Start chip
    }

    @Override
    public void reconfigure() {
        // Reset
    }
    
    public void writeRegister(int reg, int value) {
        value &= 0x0ff;
        Register r = Register.fromInt(reg);
//        System.out.println("Writing "+(value&0x0ff)+" to register "+r.toString());
        switch (r) {
            case ORB:
                if (dataDirectionB == 0) break;
                value = value & dataDirectionB;
                sendOutputB(value);
                break;
            case ORA:
//            case ORAH:
                if (dataDirectionA == 0) break;
                value = value & dataDirectionA;
                sendOutputA(value);
                break;
            case DDRB:
                dataDirectionB = value;
                break;                
            case DDRA:
                dataDirectionA = value;
                break;
            case T1CL:
            case T1LL:
                timer1latch = (timer1latch & 0x0ff00) | value;
                break;
            case T1CH:
                timer1latch = (timer1latch & 0x0ff) | (value << 8);
                timer1IRQ = false;
                timer1counter = timer1latch;
                timer1running = true;
                setRun(true);
                break;
            case T1LH:
                timer1latch = (timer1latch & 0x0ff) | (value << 8);
                timer1IRQ = false;
                break;
            case T2CL:
                timer2latch = (timer2latch & 0x0ff00) | value;
                break;
            case T2CH:
                timer2latch = (timer2latch & 0x0ff) | (value << 8);
                timer2IRQ = false;
                timer2counter = timer2latch;
                timer2running = true;
                setRun(true);
                break;
            case SR:
                // SHIFT REGISTER NOT IMPLEMENTED
                break;
            case ACR:
                // SHIFT REGISTER NOT IMPLEMENTED
                timer1freerun = (value & 64) != 0;
                if (timer1freerun) timer1running = true;
                break;
            case PCR:
                // TODO: Implement if Votrax (SSI) is to be supported
                break;
            case IFR:
                if ((value & 64) != 0) timer1IRQ = false;
                if ((value & 32) != 0) timer2IRQ = false;                
                break;
            case IER:
                boolean enable = (value & 128) != 0;
                if ((value & 64) != 0) timer1interruptEnabled = enable;
                if ((value & 32) != 0) timer2interruptEnabled = enable;
                break;
            default:
        }
    }
    
    // Whatever uses 6522 will want to know when it is outputting values
    // So to hook that in, these abstract methods will be defined as appropriate
    public abstract void sendOutputA(int value);
    public abstract void sendOutputB(int value);

    public int readRegister(int reg) {
        Register r = Register.fromInt(reg);
//        System.out.println("Reading register "+r.toString());
        switch (r) {
            case ORB:
                if (dataDirectionB ==  0x0ff) break;
                return receiveOutputB() & (dataDirectionB ^ 0x0ff);
            case ORA:
            case ORAH:
                if (dataDirectionA == 0x0ff) break;
                return receiveOutputA() & (dataDirectionA ^ 0x0ff);
            case DDRB:
                return dataDirectionB;
            case DDRA:
                return dataDirectionA;
            case T1CL:
                timer1IRQ = false;
//                int out = timer1counter & 0x0ff;
                // Behavior to get SkyFox to detect mockingboard -- thanks to tom@snsys.com!
                timer1counter -= 8;
//                return out;
               return timer1counter & 0x0ff;
            case T1CH:
                // Behavior to get SkyFox to detect mockingboard -- thanks to tom@snsys.com!
                timer1counter -= 8;
                return (timer1counter & 0x0ff00) >> 8;
            case T1LL:
                return timer1latch & 0x0ff;
            case T1LH:
                return (timer1latch & 0x0ff00) >> 8;
            case T2CL:
                timer2IRQ = false;
                return timer2counter & 0x0ff;
            case T2CH:
                return (timer2counter & 0x0ff00) >> 8;
            case SR:
                // SHIFT REGISTER NOT IMPLEMENTED
                return 0;
            case ACR:
                // SHIFT REGISTER NOT IMPLEMENTED
                if (timer1freerun) return 64;
                return 0;
            case PCR:
                break;
            case IFR:
                int val = 0;
                if (timer1IRQ) val |= 64;
                if (timer2IRQ) val |= 32;
                if (val != 0) val |= 128;
                return val;
            case IER:
                val = 128;
                if (timer1interruptEnabled) val |= 64;
                if (timer2interruptEnabled) val |= 32;
                return val;
        }
        return 0;
    }
    public abstract int receiveOutputA();
    public abstract int receiveOutputB();    
}