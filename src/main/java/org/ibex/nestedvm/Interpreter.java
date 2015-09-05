// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache Public Source License 2.0 ("the License").
// You may not use this file except in compliance with the License.

// Copyright 2003 Brian Alliet
// Based on org.xwt.imp.MIPS by Adam Megacz
// Portions Copyright 2003 Adam Megacz

package org.ibex.nestedvm;

import org.ibex.nestedvm.util.*;
import java.io.*;

public class Interpreter extends UnixRuntime implements Cloneable {
    // Registers
    private int[] registers = new int[32];
    private int hi,lo;
    
    // Floating Point Registers
    private int[] fpregs = new int[32];
    // 24-31 - unused
    // 23 - conditional bit
    // 18-22 - unused
    // 12-17 - cause bits (unimplemented)
    // 7-11  - enables bits (unimplemented)
    // 2-6   - flags (unimplemented)
    // 0-1   - rounding mode (only implemented for fixed point conversions)
    private int fcsr;
    
    private int pc;
    
    // The filename if the binary we're running
    public String image;
    private ELF.Symtab symtab;
    
    // Register Operations
    private final void setFC(boolean b) { fcsr = (fcsr&~0x800000) | (b ? 0x800000 : 0x000000); }
    private final int roundingMode() { return fcsr & 3; /* bits 0-1 */ }
    private final double getDouble(int r) {
        return Double.longBitsToDouble(((fpregs[r+1]&0xffffffffL) << 32) | (fpregs[r]&0xffffffffL));
    }
    private final void setDouble(int r, double d) {
        long l = Double.doubleToLongBits(d);
        fpregs[r+1] = (int)(l >>> 32); fpregs[r] = (int)l;
    }
    private final float getFloat(int r) { return Float.intBitsToFloat(fpregs[r]); }
    private final void setFloat(int r, float f) { fpregs[r] = Float.floatToRawIntBits(f); }
    
    protected void _execute() throws ExecutionException {
        try {
            runSome();
        } catch(ExecutionException e) {
            e.setLocation(toHex(pc) + ": " + sourceLine(pc));
            throw e;
        }
    }
    
    protected Object clone() throws CloneNotSupportedException {
        Interpreter r = (Interpreter) super.clone();
        r.registers = (int[]) registers.clone();
        r.fpregs = (int[]) fpregs.clone();
        return r;
    }
    
    // Main interpretor
    // the return value is meaningless, its just to catch people typing "return" by accident
    private final int runSome() throws FaultException,ExecutionException {
        final int PAGE_WORDS = (1<<pageShift)>>2;
        int[] r = registers;
        int[] f = fpregs;
        int pc = this.pc;
        int nextPC = pc + 4;
    try {
    OUTER: for(;;) {
        int insn;
        try {
            insn = readPages[pc>>>pageShift][(pc>>>2)&PAGE_WORDS-1];
        } catch (RuntimeException e) {
            if(pc == 0xdeadbeef) throw new Error("fell off cpu: r2: " + r[2]);
            insn = memRead(pc);
        }

        int op = (insn >>> 26) & 0xff;                 // bits 26-31
        int rs = (insn >>> 21) & 0x1f;                 // bits 21-25
        int rt = (insn >>> 16) & 0x1f;                 // bits 16-20 
        int ft = (insn >>> 16) & 0x1f;
        int rd = (insn >>> 11) & 0x1f;                 // bits 11-15
        int fs = (insn >>> 11) & 0x1f;
        int shamt = (insn >>> 6) & 0x1f;               // bits 6-10
        int fd = (insn >>> 6) & 0x1f;
        int subcode = insn & 0x3f;                     // bits 0-5  

        int jumpTarget = (insn & 0x03ffffff);          // bits 0-25
        int unsignedImmediate = insn & 0xffff;
        int signedImmediate = (insn << 16) >> 16;
        int branchTarget = signedImmediate;

        int tmp, addr; // temporaries
        
        r[ZERO] = 0;
        
        switch(op) {
            case 0: {
                switch(subcode) {
                    case 0: // SLL
                        if(insn == 0) break;
                        r[rd] = r[rt] << shamt;
                        break;
                    case 2: // SRL
                        r[rd] = r[rt] >>> shamt;
                        break;
                    case 3: // SRA
                        r[rd] = r[rt] >> shamt;
                        break;
                    case 4: // SLLV
                        r[rd] = r[rt] << (r[rs]&0x1f);
                        break;
                    case 6: // SRLV
                        r[rd] = r[rt] >>> (r[rs]&0x1f);
                        break;
                    case 7: // SRAV
                        r[rd] = r[rt] >> (r[rs]&0x1f);
                        break;
                    case 8: // JR
                        tmp = r[rs]; pc += 4; nextPC = tmp;
                        continue OUTER;
                    case 9: // JALR
                        tmp = r[rs]; pc += 4; r[rd] = pc+4; nextPC = tmp;
                        continue OUTER;
                    case 12: // SYSCALL
                        this.pc = pc;
                        r[V0] = syscall(r[V0],r[A0],r[A1],r[A2],r[A3],r[T0],r[T1]);
                        if(state != RUNNING) { this.pc = nextPC; break OUTER; }
                        break;
                    case 13: // BREAK
                        throw new ExecutionException("Break");
                    case 16: // MFHI
                        r[rd] = hi;
                        break;
                    case 17: // MTHI
                        hi = r[rs];
                        break;
                    case 18: // MFLO
                        r[rd] = lo;
                        break;
                    case 19: // MTLO
                        lo = r[rs];
                        break;
                    case 24: { // MULT
                        long hilo = ((long)r[rs]) * ((long)r[rt]);
                        hi = (int) (hilo >>> 32);
                        lo = (int) hilo;
                        break;
                    }
                    case 25: { // MULTU
                        long hilo = (r[rs] & 0xffffffffL) * (r[rt] & 0xffffffffL);
                        hi = (int) (hilo >>> 32);
                        lo = (int) hilo;
                        break;
                    }
                    case 26: // DIV
                        hi = r[rs]%r[rt];
                        lo = r[rs]/r[rt];
                        break;
                    case 27: // DIVU
                        if(rt != 0) {
                            hi = (int)((r[rs] & 0xffffffffL) % (r[rt] & 0xffffffffL));
                            lo = (int)((r[rs] & 0xffffffffL) / (r[rt] & 0xffffffffL));
                        }
                        break;
                    case 32: // ADD
                        throw new ExecutionException("ADD (add with oveflow trap) not suported");
                        /*This must trap on overflow
                        r[rd] = r[rs] + r[rt];
                        break;*/
                    case 33: // ADDU
                        r[rd] = r[rs] + r[rt];
                        break;
                    case 34: // SUB
                        throw new ExecutionException("SUB (sub with oveflow trap) not suported");
                        /*This must trap on overflow
                        r[rd] = r[rs] - r[rt];
                        break;*/
                    case 35: // SUBU
                        r[rd] = r[rs] - r[rt];
                        break;
                    case 36: // AND
                        r[rd] = r[rs] & r[rt];
                        break;
                    case 37: // OR
                        r[rd] = r[rs] | r[rt];
                        break;
                    case 38: // XOR
                        r[rd] = r[rs] ^ r[rt];
                        break;
                    case 39: // NOR
                        r[rd] = ~(r[rs] | r[rt]);
                        break;
                    case 42: // SLT
                        r[rd] = r[rs] < r[rt] ? 1 : 0;
                        break;
                    case 43: // SLTU
                        r[rd] = ((r[rs] & 0xffffffffL) < (r[rt] & 0xffffffffL)) ? 1 : 0;
                        break;
                    default:
                        throw new ExecutionException("Illegal instruction 0/" + subcode);
                }
                break;
            }
            case 1: {
                switch(rt) {
                    case 0: // BLTZ
                        if(r[rs] < 0) {
                            pc += 4; tmp = pc + branchTarget*4; nextPC = tmp;                   
                            continue OUTER;
                        }
                        break;
                    case 1: // BGEZ
                        if(r[rs] >= 0) {
                            pc += 4; tmp = pc + branchTarget*4; nextPC = tmp;
                            continue OUTER;
                        }
                        break;
                    case 16: // BLTZAL
                        if(r[rs] < 0) {
                            pc += 4; r[RA] = pc+4; tmp = pc + branchTarget*4; nextPC = tmp;
                            continue OUTER;
                        }
                        break;
                    case 17: // BGEZAL
                        if(r[rs] >= 0) {
                            pc += 4; r[RA] = pc+4; tmp = pc + branchTarget*4; nextPC = tmp;  
                            continue OUTER;
                        }
                        break;
                    default:
                        throw new ExecutionException("Illegal Instruction");
                }
                break;
            }
            case 2: { // J
                tmp = (pc&0xf0000000) | (jumpTarget << 2);
                pc+=4; nextPC = tmp;
                continue OUTER;
            }
            case 3: { // JAL
                tmp = (pc&0xf0000000) | (jumpTarget << 2);
                pc+=4; r[RA] = pc+4; nextPC = tmp;
                continue OUTER;
            }
            case 4: // BEQ
                if(r[rs] == r[rt]) {
                    pc += 4; tmp = pc + branchTarget*4; nextPC = tmp;
                    continue OUTER;
                }
                break;
            case 5: // BNE                
                if(r[rs] != r[rt]) {
                    pc += 4; tmp = pc + branchTarget*4; nextPC = tmp; 
                    continue OUTER;
                }
                break;
            case 6: //BLEZ
                if(r[rs] <= 0) {
                    pc += 4; tmp = pc + branchTarget*4; nextPC = tmp;
                    continue OUTER;
                }
                break;
            case 7: //BGTZ
                if(r[rs] > 0) {
                    pc += 4; tmp = pc + branchTarget*4; nextPC = tmp;
                    continue OUTER;
                }
                break;
            case 8: // ADDI
                r[rt] = r[rs] + signedImmediate;
                break;
            case 9: // ADDIU
                r[rt] = r[rs] + signedImmediate;
                break;
            case 10: // SLTI
                r[rt] = r[rs] < signedImmediate ? 1 : 0;
                break;
            case 11: // SLTIU
                r[rt] = (r[rs]&0xffffffffL) < (signedImmediate&0xffffffffL) ? 1 : 0;
                break;
            case 12: // ANDI
                r[rt] = r[rs] & unsignedImmediate;
                break;
            case 13: // ORI
                r[rt] = r[rs] | unsignedImmediate;
                break;
            case 14: // XORI
                r[rt] = r[rs] ^ unsignedImmediate;
                break;
            case 15: // LUI
                r[rt] = unsignedImmediate << 16;
                break;
            case 16:
                throw new ExecutionException("TLB/Exception support not implemented");
            case 17: { // FPU
                boolean debug = false;
                String line = debug ? sourceLine(pc) : "";
                boolean debugon = debug && (line.indexOf("dtoa.c:51") >= 0 || line.indexOf("dtoa.c:52") >= 0 || line.indexOf("test.c") >= 0);
                if(rs > 8 && debugon)
                    System.out.println("               FP Op: " + op + "/" + rs + "/" + subcode + " " + line);
                if(roundingMode() != 0 && rs != 6 /*CTC.1*/ && !((rs==16 || rs==17) && subcode == 36 /* CVT.W.Z */))
                    throw new ExecutionException("Non-cvt.w.z operation attempted with roundingMode != round to nearest");
                switch(rs) {
                    case 0: // MFC.1
                        r[rt] = f[rd];
                        break;
                    case 2: // CFC.1
                        if(fs != 31) throw new ExecutionException("FCR " + fs + " unavailable");
                        r[rt] = fcsr;
                        break;
                    case 4: // MTC.1
                        f[rd] = r[rt];
                        break;
                    case 6: // CTC.1
                        if(fs != 31) throw new ExecutionException("FCR " + fs + " unavailable");
                        fcsr = r[rt];   
                        break;
                    case 8: // BC1F, BC1T
                        if(((fcsr&0x800000)!=0) == (((insn>>>16)&1)!=0)) {
                            pc += 4; tmp = pc + branchTarget*4; nextPC = tmp;
                            continue OUTER;
                        }
                        break;
                    case 16: {  // Single
                        switch(subcode) {
                            case 0: // ADD.S
                                setFloat(fd,getFloat(fs)+getFloat(ft));
                                break;
                            case 1: // SUB.S
                                setFloat(fd,getFloat(fs)-getFloat(ft));
                                break;
                            case 2: // MUL.S
                                setFloat(fd,getFloat(fs)*getFloat(ft));
                                break;
                            case 3: // DIV.S
                                setFloat(fd,getFloat(fs)/getFloat(ft));
                                break;
                            case 5: // ABS.S
                                setFloat(fd,Math.abs(getFloat(fs)));
                                break;
                            case 6: // MOV.S
                                f[fd] = f[fs];
                                break;
                            case 7: // NEG.S
                                setFloat(fd,-getFloat(fs));
                                break;
                            case 33: // CVT.D.S
                                setDouble(fd,getFloat(fs));
                                break;
                            case 36: // CVT.W.S
                                switch(roundingMode()) {
                                    case 0: f[fd] = (int)Math.floor(getFloat(fs)+0.5f); break; // Round to nearest
                                    case 1: f[fd] = (int)getFloat(fs); break; // Round towards zero
                                    case 2: f[fd] = (int)Math.ceil(getFloat(fs)); break; // Round towards plus infinity
                                    case 3: f[fd] = (int)Math.floor(getFloat(fs)); break; // Round towards minus infinity
                                }
                                break;
                            case 50: // C.EQ.S
                                setFC(getFloat(fs) == getFloat(ft));
                                break;
                            case 60: // C.LT.S
                                setFC(getFloat(fs) < getFloat(ft));
                                break;
                            case 62: // C.LE.S
                                setFC(getFloat(fs) <= getFloat(ft));
                                break;   
                            default: throw new ExecutionException("Invalid Instruction 17/" + rs + "/" + subcode + " at " + sourceLine(pc));
                        }
                        break;
                    }
                    case 17: { // Double
                        switch(subcode) {
                            case 0: // ADD.D
                                setDouble(fd,getDouble(fs)+getDouble(ft));
                                break;
                            case 1: // SUB.D
                                if(debugon) System.out.println("f" + fd + " = f" + fs + " (" + getDouble(fs) + ") - f" + ft + " (" + getDouble(ft) + ")");
                                setDouble(fd,getDouble(fs)-getDouble(ft));
                                break;
                            case 2: // MUL.D
                                if(debugon) System.out.println("f" + fd + " = f" + fs + " (" + getDouble(fs) + ") * f" + ft + " (" + getDouble(ft) + ")");
                                setDouble(fd,getDouble(fs)*getDouble(ft));
                                if(debugon) System.out.println("f" + fd + " = " + getDouble(fd));
                                break;
                            case 3: // DIV.D
                                setDouble(fd,getDouble(fs)/getDouble(ft));
                                break;
                            case 5: // ABS.D
                                setDouble(fd,Math.abs(getDouble(fs)));
                                break;
                            case 6: // MOV.D
                                f[fd] = f[fs];
                                f[fd+1] = f[fs+1];
                                break;
                            case 7: // NEG.D
                                setDouble(fd,-getDouble(fs));
                                break;
                            case 32: // CVT.S.D
                                setFloat(fd,(float)getDouble(fs));
                                break;
                            case 36: // CVT.W.D
                                if(debugon) System.out.println("CVT.W.D rm: " + roundingMode() + " f" + fs + ":" + getDouble(fs));
                                switch(roundingMode()) {
                                    case 0: f[fd] = (int)Math.floor(getDouble(fs)+0.5); break; // Round to nearest
                                    case 1: f[fd] = (int)getDouble(fs); break; // Round towards zero
                                    case 2: f[fd] = (int)Math.ceil(getDouble(fs)); break; // Round towards plus infinity
                                    case 3: f[fd] = (int)Math.floor(getDouble(fs)); break; // Round towards minus infinity
                                }
                                if(debugon) System.out.println("CVT.W.D: f" + fd + ":" + f[fd]);
                                break;
                            case 50: // C.EQ.D
                                setFC(getDouble(fs) == getDouble(ft));
                                break;
                            case 60: // C.LT.D
                                setFC(getDouble(fs) < getDouble(ft));
                                break;
                            case 62: // C.LE.D
                                setFC(getDouble(fs) <= getDouble(ft));
                                break;                                
                            default: throw new ExecutionException("Invalid Instruction 17/" + rs + "/" + subcode + " at " + sourceLine(pc));
                        }
                        break;
                    }
                    case 20: { // Integer
                        switch(subcode) {
                            case 32: // CVT.S.W
                                setFloat(fd,f[fs]);
                                break;
                            case 33: // CVT.D.W
                                setDouble(fd,f[fs]);
                                break;
                            default: throw new ExecutionException("Invalid Instruction 17/" + rs + "/" + subcode + " at " + sourceLine(pc));
                        }
                        break;
                    }
                    default:
                        throw new ExecutionException("Invalid Instruction 17/" + rs);
                }
                break;
            }
            case 18: case 19:
                throw new ExecutionException("No coprocessor installed");
            case 32: { // LB
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr&~3);
                }
                switch(addr&3) {
                    case 0: tmp = (tmp>>>24)&0xff; break;
                    case 1: tmp = (tmp>>>16)&0xff; break;
                    case 2: tmp = (tmp>>> 8)&0xff; break;
                    case 3: tmp = (tmp>>> 0)&0xff; break;
                }
                if((tmp&0x80)!=0) tmp |= 0xffffff00; // sign extend
                r[rt] = tmp;
                break;
            }
            case 33: { // LH
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr&~3);
                }
                switch(addr&3) {
                    case 0: tmp = (tmp>>>16)&0xffff; break;
                    case 2: tmp = (tmp>>> 0)&0xffff; break;
                    default: throw new ReadFaultException(addr);
                }
                if((tmp&0x8000)!=0) tmp |= 0xffff0000; // sign extend
                r[rt] = tmp;
                break;              
            }
            case 34: { // LWL;
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr&~3);
                }
                switch(addr&3) {
                    case 0: r[rt] = (r[rt]&0x00000000)|(tmp<< 0); break;
                    case 1: r[rt] = (r[rt]&0x000000ff)|(tmp<< 8); break;
                    case 2: r[rt] = (r[rt]&0x0000ffff)|(tmp<<16); break;
                    case 3: r[rt] = (r[rt]&0x00ffffff)|(tmp<<24); break;
                }
                break;
            }
            case 35: // LW
                addr = r[rs] + signedImmediate;
                try {
                    r[rt] = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    r[rt] = memRead(addr);
                }
                break;
            case 36: { // LBU
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr);
                }
                switch(addr&3) {
                    case 0: r[rt] = (tmp>>>24)&0xff; break;
                    case 1: r[rt] = (tmp>>>16)&0xff; break;
                    case 2: r[rt] = (tmp>>> 8)&0xff; break;
                    case 3: r[rt] = (tmp>>> 0)&0xff; break;
                }
                break;
            }
            case 37: { // LHU
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr&~3);
                }
                switch(addr&3) {
                    case 0: r[rt] = (tmp>>>16)&0xffff; break;
                    case 2: r[rt] = (tmp>>> 0)&0xffff; break;
                    default: throw new ReadFaultException(addr);
                }
                break;
            }
            case 38: { // LWR
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr&~3);
                }
                switch(addr&3) {
                    case 0: r[rt] = (r[rt]&0xffffff00)|(tmp>>>24); break;
                    case 1: r[rt] = (r[rt]&0xffff0000)|(tmp>>>16); break;
                    case 2: r[rt] = (r[rt]&0xff000000)|(tmp>>> 8); break;
                    case 3: r[rt] = (r[rt]&0x00000000)|(tmp>>> 0); break;
                }
                break;
            }
            case 40: { // SB
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr&~3);
                }
                switch(addr&3) {
                    case 0: tmp = (tmp&0x00ffffff) | ((r[rt]&0xff)<<24); break;
                    case 1: tmp = (tmp&0xff00ffff) | ((r[rt]&0xff)<<16); break;
                    case 2: tmp = (tmp&0xffff00ff) | ((r[rt]&0xff)<< 8); break;
                    case 3: tmp = (tmp&0xffffff00) | ((r[rt]&0xff)<< 0); break;
                }
                try {
                    writePages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)] = tmp;
                } catch(RuntimeException e) {
                    memWrite(addr&~3,tmp);
                }
                break;
            }
            case 41: { // SH
                addr = r[rs] + signedImmediate;
                try {
                    tmp = readPages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)];
                } catch(RuntimeException e) {
                    tmp = memRead(addr&~3);
                }
                switch(addr&3) {
                    case 0: tmp = (tmp&0x0000ffff) | ((r[rt]&0xffff)<<16); break;
                    case 2: tmp = (tmp&0xffff0000) | ((r[rt]&0xffff)<< 0); break;
                    default: throw new WriteFaultException(addr);
                }
                try {
                    writePages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)] = tmp;
                } catch(RuntimeException e) {
                    memWrite(addr&~3,tmp);
                }
                break;
            }
            case 42: { // SWL
                addr = r[rs] + signedImmediate;
                tmp = memRead(addr&~3);
                switch(addr&3) {
                    case 0: tmp=(tmp&0x00000000)|(r[rt]>>> 0); break;
                    case 1: tmp=(tmp&0xff000000)|(r[rt]>>> 8); break;
                    case 2: tmp=(tmp&0xffff0000)|(r[rt]>>>16); break;
                    case 3: tmp=(tmp&0xffffff00)|(r[rt]>>>24); break;
                }
                try {
                    writePages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)] = tmp;
                } catch(RuntimeException e) {
                    memWrite(addr&~3,tmp);
                }
                break;
            }
            case 43: // SW
                addr = r[rs] + signedImmediate;
                try {
                    writePages[addr>>>pageShift][(addr>>>2)&(PAGE_WORDS-1)] = r[rt];
                } catch(RuntimeException e) {
                    memWrite(addr&~3,r[rt]);
                }
                break;
            case 46: { // SWR
                addr = r[rs] + signedImmediate;
                tmp = memRead(addr&~3);
                switch(addr&3) {
                    case 0: tmp=(tmp&0x00ffffff)|(r[rt]<<24); break;
                    case 1: tmp=(tmp&0x0000ffff)|(r[rt]<<16); break;
                    case 2: tmp=(tmp&0x000000ff)|(r[rt]<< 8); break;
                    case 3: tmp=(tmp&0x00000000)|(r[rt]<< 0); break;
                }
                memWrite(addr&~3,tmp);
                break;
            }
            // Needs to be atomic w/ threads
            case 48: // LWC0/LL
                r[rt] = memRead(r[rs] + signedImmediate);
                break;
            case 49: // LWC1
                f[rt] = memRead(r[rs] + signedImmediate);
                break;
            // Needs to be atomic w/ threads
            case 56:
                memWrite(r[rs] + signedImmediate,r[rt]);
                r[rt] = 1;
                break;
            case 57: // SWC1
                memWrite(r[rs] + signedImmediate,f[rt]);
                break;
            default:
                throw new ExecutionException("Invalid Instruction: " + op);
        }
        pc = nextPC;
        nextPC = pc + 4;
    } // for(;;)
    } catch(ExecutionException e) {
        this.pc = pc;
        throw e;
    }
        return 0;
    }
    
    public int lookupSymbol(String name) {
        ELF.Symbol sym = symtab.getSymbol(name);
        return sym == null ? -1 : sym.addr;
    }
    
    private int gp;
    protected int gp() { return gp; }
    
    private ELF.Symbol userInfo;
    protected int userInfoBae() { return userInfo == null ? 0 : userInfo.addr; }
    protected int userInfoSize() { return userInfo == null ? 0 : userInfo.size; }
    
    private int entryPoint;
    protected int entryPoint() { return entryPoint; }
    
    private int heapStart;
    protected int heapStart() { return heapStart; }
    
    // Image loading function
    private void loadImage(Seekable data) throws IOException {
        ELF elf = new ELF(data);
        symtab = elf.getSymtab();
        
        if(elf.header.type != ELF.ET_EXEC) throw new IOException("Binary is not an executable");
        if(elf.header.machine != ELF.EM_MIPS) throw new IOException("Binary is not for the MIPS I Architecture");
        if(elf.ident.data != ELF.ELFDATA2MSB) throw new IOException("Binary is not big endian");
        
        entryPoint = elf.header.entry;
        
        ELF.Symtab symtab = elf.getSymtab();
        if(symtab == null) throw new IOException("No symtab in binary (did you strip it?)");
        userInfo = symtab.getSymbol("user_info");
        ELF.Symbol gpsym = symtab.getSymbol("_gp");
        
        if(gpsym == null) throw new IOException("NO _gp symbol!");
        gp = gpsym.addr;
        
        entryPoint = elf.header.entry;
        
        ELF.PHeader[] pheaders = elf.pheaders;
        int brk = 0;
        int pageSize = (1<<pageShift);
        int pageWords = (1<<pageShift) >> 2;
        for(int i=0;i<pheaders.length;i++) {
            ELF.PHeader ph = pheaders[i];
            if(ph.type != ELF.PT_LOAD) continue;
            int memsize = ph.memsz;
            int filesize = ph.filesz;
            if(memsize == 0) continue;
            if(memsize < 0) throw new IOException("pheader size too large");
            int addr = ph.vaddr;
            if(addr == 0x0) throw new IOException("pheader vaddr == 0x0");
            brk = max(addr+memsize,brk);
            
            for(int j=0;j<memsize+pageSize-1;j+=pageSize) {
                int page = (j+addr) >>> pageShift;
                if(readPages[page] == null)
                    readPages[page] = new int[pageWords];
                if(ph.writable()) writePages[page] = readPages[page];
            }
            if(filesize != 0) {
                filesize = filesize & ~3;
                DataInputStream dis = new DataInputStream(ph.getInputStream());
                do {
                    readPages[addr >>> pageShift][(addr >>> 2)&(pageWords-1)] = dis.readInt();
                    addr+=4;
                    filesize-=4;
                } while(filesize > 0);
                dis.close();
            }
        }
        heapStart = (brk+pageSize-1)&~(pageSize-1);
    }
    
    protected void setCPUState(CPUState state) {
        for(int i=1;i<32;i++) registers[i] = state.r[i];
        for(int i=0;i<32;i++) fpregs[i] = state.f[i];
        hi=state.hi; lo=state.lo; fcsr=state.fcsr;
        pc=state.pc;
    }
    
    protected void getCPUState(CPUState state) {
        for(int i=1;i<32;i++) state.r[i] = registers[i];
        for(int i=0;i<32;i++) state.f[i] = fpregs[i];
        state.hi=hi; state.lo=lo; state.fcsr=fcsr;
        state.pc=pc;
    }
    
    public Interpreter(Seekable data) throws IOException {
        super(4096,65536);
        loadImage(data);
    }
    public Interpreter(String filename) throws IOException {
        this(new Seekable.File(filename,false));
        image = filename;
    }
    public Interpreter(InputStream is) throws IOException { this(new Seekable.InputStream(is)); }
    
    // Debug functions
    // NOTE: This probably requires a jdk > 1.1, however, it is only used for debugging
    private java.util.HashMap<Integer,String> sourceLineCache;
    public String sourceLine(int pc) {
        final String addr2line = "mips-unknown-elf-addr2line";
        String line = (String) (sourceLineCache == null ? null : sourceLineCache.get(new Integer(pc)));
        if(line != null) return line;
        if(image==null) return null;
        try {
            Process p = java.lang.Runtime.getRuntime().exec(new String[]{addr2line,"-e",image,toHex(pc)});
            line = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
            if(line == null) return null;
            while(line.startsWith("../")) line = line.substring(3);
            if(sourceLineCache == null) sourceLineCache = new java.util.HashMap<Integer,String>();
            sourceLineCache.put(new Integer(pc),line);
            return line;
        } catch(IOException e) {
            return null;
        }
    }
    
    public class DebugShutdownHook implements Runnable {
        public void run() {
            int pc = Interpreter.this.pc;
            if(getState() == RUNNING)
                System.err.print("\nCPU Executing " + toHex(pc) + ": " + sourceLine(pc) + "\n");
        }
    }

    public static void main(String[] argv) throws Exception {
        String image = argv[0];
        Interpreter emu = new Interpreter(image);
        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(emu.new DebugShutdownHook()));
        int status = emu.run(argv);
        System.err.println("Exit status: " + status);
        System.exit(status);
    }
}
