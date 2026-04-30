package jace.ipc;

/**
 * Immutable value object representing one executed CPU instruction as reported to Cyrene.
 * All fields are public and final.
 */
public final class CyreneOperation {

    // 16-bit register values stored as int (upper bits zeroed)
    public final int a;
    public final int x;
    public final int y;
    public final int pc;
    public final int stack;

    // Packed status register
    public final byte flags;

    // Instruction bytes
    public final byte opcode;
    public final byte op1;
    public final byte op2;
    public final byte op3;

    // 24-bit addresses stored as int
    public final int readAddr;
    public final int writeAddr;
    public final int jumpAddr;

    // Memory access values
    public final int readVal;
    public final int writeVal;

    // Operation flags (0x80 = interrupt)
    public final byte opFlags;

    // Global operation counters
    public final long goid;
    public final long gcc;

    // Soft switch state at instruction time
    public final byte vertcnt;
    public final byte horizcnt;

    public CyreneOperation(
            int a, int x, int y, int pc, int stack,
            byte flags,
            byte opcode, byte op1, byte op2, byte op3,
            int readAddr, int writeAddr, int jumpAddr,
            int readVal, int writeVal,
            byte opFlags,
            long goid, long gcc,
            byte vertcnt, byte horizcnt) {
        this.a = a & 0xFFFF;
        this.x = x & 0xFFFF;
        this.y = y & 0xFFFF;
        this.pc = pc & 0xFFFF;
        this.stack = stack & 0xFFFF;
        this.flags = flags;
        this.opcode = opcode;
        this.op1 = op1;
        this.op2 = op2;
        this.op3 = op3;
        this.readAddr = readAddr & 0xFFFFFF;
        this.writeAddr = writeAddr & 0xFFFFFF;
        this.jumpAddr = jumpAddr & 0xFFFFFF;
        this.readVal = readVal;
        this.writeVal = writeVal;
        this.opFlags = opFlags;
        this.goid = goid;
        this.gcc = gcc;
        this.vertcnt = vertcnt;
        this.horizcnt = horizcnt;
    }

    /**
     * Pack CPU status flags into a single byte.
     * Bit layout: N=7, V=6, bit5 always 1, B=4, D=3, I=2, Z=1, C=0
     *
     * @param n  Negative flag
     * @param v  Overflow flag
     * @param b  Break flag
     * @param d  Decimal flag
     * @param i  Interrupt-disable flag
     * @param z  Zero flag
     * @param c  Carry (0 or 1)
     * @return   Packed status byte
     */
    public static byte packFlags(boolean n, boolean v, boolean b, boolean d, boolean i, boolean z, int c) {
        int result = 0x20; // bit 5 always set
        if (n) result |= 0x80;
        if (v) result |= 0x40;
        if (b) result |= 0x10;
        if (d) result |= 0x08;
        if (i) result |= 0x04;
        if (z) result |= 0x02;
        if ((c & 1) != 0) result |= 0x01;
        return (byte) result;
    }
}
