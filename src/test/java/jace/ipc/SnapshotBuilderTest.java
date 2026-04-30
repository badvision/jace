package jace.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.AbstractJaceTest;

/**
 * Tests for SnapshotBuilder.
 *
 * Verifies the size, field layout, and fixed-value bytes of the snapshot blob
 * produced for a K2C_SEND_SNAPSHOT response.
 */
public class SnapshotBuilderTest extends AbstractJaceTest {

    private static SnapshotBuilder builder;

    @BeforeClass
    public static void setupBuilder() {
        commonSetupClass();
        builder = new SnapshotBuilder();
    }

    // -------------------------------------------------------------------------
    // Helper: create a minimal CyreneOperation with all fields zero except those
    // explicitly set by callers.
    // -------------------------------------------------------------------------
    private static CyreneOperation makeOp(int a, int x, int y, int pc, int stack,
                                          byte flags,
                                          byte opcode, byte op1, byte op2, byte op3) {
        return new CyreneOperation(
                a, x, y, pc, stack,
                flags,
                opcode, op1, op2, op3,
                0, 0, 0,
                0, 0,
                (byte) 0,
                0L, 0L,
                (byte) 0, (byte) 0);
    }

    // -------------------------------------------------------------------------
    // Test 1: snapshot size
    // -------------------------------------------------------------------------
    @Test
    public void testSnapshotSize() {
        CyreneOperation op = makeOp(0, 0, 0, 0, 0xFF, (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0);
        byte[] snap = builder.build(op, IpcConstants.STOP_NONE, 0);
        assertNotNull(snap);
        assertEquals(IpcConstants.SNAP_TOTAL_SIZE, snap.length);
    }

    // -------------------------------------------------------------------------
    // Test 2: CPU register encoding in the operation data block
    // -------------------------------------------------------------------------
    @Test
    public void testOperationDataRegisters() {
        // A = 0x42, PC = 0x1234, everything else minimal
        CyreneOperation op = makeOp(0x42, 0, 0, 0x1234, 0xFF, (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0);
        byte[] snap = builder.build(op, IpcConstants.STOP_NONE, 0);

        // A: 16-bit LE at OP_OFF_A (offset 0) — low byte == 0x42, high byte == 0x00
        assertEquals((byte) 0x42, snap[IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_A]);
        assertEquals((byte) 0x00, snap[IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_A + 1]);

        // PC: 16-bit LE at OP_OFF_PC (offset 6) — low byte == 0x34, high byte == 0x12
        assertEquals((byte) 0x34, snap[IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_PC]);
        assertEquals((byte) 0x12, snap[IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_PC + 1]);
    }

    // -------------------------------------------------------------------------
    // Test 3: ROM version discriminator
    // -------------------------------------------------------------------------
    @Test
    public void testRomVersionByte() {
        CyreneOperation op = makeOp(0, 0, 0, 0, 0xFF, (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0);
        byte[] snap = builder.build(op, IpcConstants.STOP_NONE, 0);
        assertEquals(IpcConstants.ROM_VERSION_APPLE_IIE,
                snap[IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_ROM_VERSION]);
    }

    // -------------------------------------------------------------------------
    // Test 4: RAM banks discriminator
    // -------------------------------------------------------------------------
    @Test
    public void testRamBanksByte() {
        CyreneOperation op = makeOp(0, 0, 0, 0, 0xFF, (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0);
        byte[] snap = builder.build(op, IpcConstants.STOP_NONE, 0);
        assertEquals(IpcConstants.RAM_BANKS_APPLE_IIE,
                snap[IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_RAM_BANKS]);
    }

    // -------------------------------------------------------------------------
    // Test 5: DOC silence byte
    // -------------------------------------------------------------------------
    @Test
    public void testDocSilenceByte() {
        CyreneOperation op = makeOp(0, 0, 0, 0, 0xFF, (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0);
        byte[] snap = builder.build(op, IpcConstants.STOP_NONE, 0);
        assertEquals(IpcConstants.DOC_SILENCE_BYTE, snap[IpcConstants.SNAP_OFF_DOC_REG + 2]);
    }

    // -------------------------------------------------------------------------
    // Test 6: stop condition and breakpoint id fields
    // -------------------------------------------------------------------------
    @Test
    public void testStopConditionField() {
        CyreneOperation op = makeOp(0, 0, 0, 0, 0xFF, (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0);
        byte[] snap = builder.build(op, IpcConstants.STOP_BREAKPOINT, 42);

        // Byte 100: stop condition
        assertEquals(IpcConstants.STOP_BREAKPOINT, snap[IpcConstants.SNAP_OFF_STOP_COND]);

        // Bytes 101..104: bpId as little-endian int32 == 42
        int bpId = (snap[IpcConstants.SNAP_OFF_BREAKPOINT] & 0xFF)
                | ((snap[IpcConstants.SNAP_OFF_BREAKPOINT + 1] & 0xFF) << 8)
                | ((snap[IpcConstants.SNAP_OFF_BREAKPOINT + 2] & 0xFF) << 16)
                | ((snap[IpcConstants.SNAP_OFF_BREAKPOINT + 3] & 0xFF) << 24);
        assertEquals(42, bpId);
    }

    // -------------------------------------------------------------------------
    // Test 7: Bank 00 region is accessible (no exception, correct size)
    // -------------------------------------------------------------------------
    @Test
    public void testBank00ContentsAccessible() {
        CyreneOperation op = makeOp(0, 0, 0, 0, 0xFF, (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0);
        // Should not throw; snapshot must be complete
        byte[] snap = builder.build(op, IpcConstants.STOP_NONE, 0);
        assertNotNull(snap);
        assertEquals(IpcConstants.SNAP_TOTAL_SIZE, snap.length);
        // Verify the Bank 00 region is within bounds — access first four bytes
        int b0 = snap[IpcConstants.SNAP_OFF_BANK00] & 0xFF;
        int b1 = snap[IpcConstants.SNAP_OFF_BANK00 + 1] & 0xFF;
        int b2 = snap[IpcConstants.SNAP_OFF_BANK00 + 2] & 0xFF;
        int b3 = snap[IpcConstants.SNAP_OFF_BANK00 + 3] & 0xFF;
        // Values are implementation-defined; we only require the reads don't throw.
        assertNotNull(new int[]{b0, b1, b2, b3});
    }
}
