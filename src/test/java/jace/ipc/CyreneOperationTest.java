package jace.ipc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CyreneOperationTest {

    @Test
    public void testPackFlagsAllSet() {
        // N=1 V=1 bit5=1 B=1 D=1 I=1 Z=1 C=1 => 0xFF
        byte result = CyreneOperation.packFlags(true, true, true, true, true, true, 1);
        assertEquals((byte) 0xFF, result);
    }

    @Test
    public void testPackFlagsAllClear() {
        // All false, c=0 => only bit5 set => 0x20
        byte result = CyreneOperation.packFlags(false, false, false, false, false, false, 0);
        assertEquals((byte) 0x20, result);
    }

    @Test
    public void testPackFlagsCarryOnly() {
        // Only c=1, all booleans false => bit5 + bit0 => 0x21
        byte result = CyreneOperation.packFlags(false, false, false, false, false, false, 1);
        assertEquals((byte) 0x21, result);
    }

    @Test
    public void testPackFlagsNegativeOnly() {
        // Only n=true, c=0 => N=1, bit5=1 => 0xA0
        byte result = CyreneOperation.packFlags(true, false, false, false, false, false, 0);
        assertEquals((byte) 0xA0, result);
    }
}
