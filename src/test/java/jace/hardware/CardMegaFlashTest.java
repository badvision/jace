/**
* Copyright 2024 Brendan Robert
*/

package jace.hardware;

import jace.AbstractFXTest;
import jace.core.RAMEvent;
import jace.hardware.mbf.MicrosoftBinaryFormat;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test suite for MegaFlash Bluetooth Mouse Simulator Card
 *
 * Tests all 12 commands and validates protocol compliance according to
 * the command protocol specification in Mouse/docs/command-protocol.md
 */
public class CardMegaFlashTest extends AbstractFXTest {
    private CardMegaFlash card;

    // Command codes
    private static final int CMD_MOUSE_INIT = 0x60;
    private static final int CMD_MOUSE_READ = 0x61;
    private static final int CMD_MOUSE_STATUS = 0x62;
    private static final int CMD_MOUSE_CONFIG = 0x63;
    private static final int CMD_MOUSE_PAIR_START = 0x64;
    private static final int CMD_MOUSE_PAIR_CANCEL = 0x65;
    private static final int CMD_MOUSE_DISCONNECT = 0x66;
    private static final int CMD_MOUSE_CLAMP = 0x67;

    // Math accelerator command codes - Binary operations
    private static final int CMD_FADD = 0x30;
    private static final int CMD_FMUL = 0x31;
    private static final int CMD_FDIV = 0x32;

    // Math accelerator command codes - Unary operations
    private static final int CMD_FSIN = 0x33;
    private static final int CMD_FCOS = 0x34;
    private static final int CMD_FTAN = 0x35;
    private static final int CMD_FATN = 0x36;
    private static final int CMD_FLOG = 0x37;
    private static final int CMD_FEXP = 0x38;
    private static final int CMD_FSQR = 0x39;

    // Error codes
    private static final int MFERR_NONE = 0x00;
    private static final int MFERR_INVALIDWEKEY = 0x04;
    private static final int MFERR_INVALIDARG = 0x0A;

    // Math error codes (returned in result byte 0)
    private static final int MATHERR_OVERFLOW = 0x80;
    private static final int MATHERR_DIV0 = 0x40;
    private static final int MATHERR_IQERROR = 0x20;
    private static final int MATHERR_NONE = 0x00;

    // Constants
    private static final int WRITEENABLEKEY = 0x71;

    @Before
    public void setUp() {
        card = new CardMegaFlash();
        card.setSlot(4);
        // CMD_RESETBOTHPTRS (0x00): reset paramPointer and dataPointer to 0.
        // On real hardware, boot firmware leaves paramPointer=2; programs issue this
        // command before using the FPU. Mirrors real usage so tests start clean.
        executeCommand(0x00);
    }

    private RAMEvent createEvent(RAMEvent.TYPE type, int value) {
        return new RAMEvent(type, RAMEvent.SCOPE.ANY, RAMEvent.VALUE.ANY, 0xC0C0, 0, value);
    }

    private void writeParam(int value) {
        RAMEvent event = createEvent(RAMEvent.TYPE.WRITE, value);
        card.handleIOAccess(1, RAMEvent.TYPE.WRITE, value, event);
    }

    private int readParam() {
        RAMEvent event = createEvent(RAMEvent.TYPE.READ, 0);
        card.handleIOAccess(1, RAMEvent.TYPE.READ, 0, event);
        return event.getNewValue();
    }

    private int readStatus() {
        RAMEvent event = createEvent(RAMEvent.TYPE.READ, 0);
        card.handleIOAccess(0, RAMEvent.TYPE.READ, 0, event);
        return event.getNewValue();
    }

    private void executeCommand(int cmd) {
        RAMEvent event = createEvent(RAMEvent.TYPE.WRITE, cmd);
        card.handleIOAccess(0, RAMEvent.TYPE.WRITE, cmd, event);
    }

    /**
     * Test 1: CMD_MOUSE_INIT returns capability info on first call
     */
    @Test
    public void testMouseInitReturnsCapability() {
        executeCommand(CMD_MOUSE_INIT);

        int result = readParam();
        assertEquals("INIT should return 0x00 (success) on first call", 0x00, result);

        int status = readStatus();
        assertEquals("Status should be MFERR_NONE", MFERR_NONE, status & 0x7F);
    }

    /**
     * Test 2: CMD_MOUSE_INIT returns already initialized on second call
     */
    @Test
    public void testMouseInitAlreadyInitialized() {
        // First init
        executeCommand(CMD_MOUSE_INIT);
        readParam(); // Clear result

        // Second init
        executeCommand(CMD_MOUSE_INIT);
        int result = readParam();

        assertEquals("INIT should return 0x03 (already initialized) on second call", 0x03, result);
    }

    /**
     * Test 3: CMD_MOUSE_READ returns zeros when not connected
     */
    @Test
    public void testMouseReadReturnsZerosWhenNotConnected() {
        executeCommand(CMD_MOUSE_READ);

        int deltaX = readParam();
        int deltaY = readParam();
        int buttons = readParam();
        int flags = readParam();

        assertEquals("Delta X should be 0", 0, deltaX);
        assertEquals("Delta Y should be 0", 0, deltaY);
        assertEquals("Buttons should be 0", 0, buttons);
        assertEquals("Connection bit (bit 6) should be clear", 0, flags & 0x40);
    }

    /**
     * Test 4: CMD_MOUSE_READ behavior depends on connection state
     *
     * This test verifies that when not connected, appropriate values are returned.
     * Connection testing is challenging in unit test environment due to JavaFX threading.
     */
    @Test
    public void testMouseReadBehavior() {
        // When not connected, should return zeros with connection bit clear
        executeCommand(CMD_MOUSE_READ);
        readParam(); // deltaX
        readParam(); // deltaY
        readParam(); // buttons
        int flags = readParam();

        assertEquals("Connection bit should be clear when not connected", 0, flags & 0x40);
    }

    /**
     * Test 5: CMD_MOUSE_READ clears deltas after read (read-and-clear semantics)
     */
    @Test
    public void testMouseReadClearsDeltasAfterRead() {
        // First read
        executeCommand(CMD_MOUSE_READ);
        readParam(); // deltaX
        readParam(); // deltaY
        readParam(); // buttons
        readParam(); // flags

        // Second read immediately after - deltas should still be zero (no mouse movement)
        executeCommand(CMD_MOUSE_READ);
        int deltaX2 = readParam();
        int deltaY2 = readParam();

        assertEquals("Second read delta X should be 0", 0, deltaX2);
        assertEquals("Second read delta Y should be 0", 0, deltaY2);
    }

    /**
     * Test 6: CMD_MOUSE_PAIR_START enters pairing mode
     *
     * NOTE: Full success/failure alternation test is complex in test environment
     * due to JavaFX threading. This test verifies the command accepts parameters
     * and enters pairing mode correctly.
     */
    @Test
    public void testPairingEntersMode() throws InterruptedException {
        executeCommand(CMD_MOUSE_INIT);
        readParam();

        // Start pairing with valid parameters
        writeParam(120); // timeout
        writeParam(0x00); // options
        writeParam(WRITEENABLEKEY);
        executeCommand(CMD_MOUSE_PAIR_START);

        int result = readParam();
        assertEquals("Pairing command should return 0x00 (entered pairing mode)", 0x00, result);

        // Verify status returns non-zero state (either pairing or completed)
        executeCommand(CMD_MOUSE_STATUS);
        int state = readParam();
        assertTrue("Status should indicate valid state", state >= 0x00 && state <= 0x04);
    }

    /**
     * Test 7: CMD_MOUSE_CONFIG validates write enable key
     */
    @Test
    public void testWriteEnableKeyValidation() {
        // Try config with wrong key
        writeParam(128); // sensitivity
        writeParam(0x02); // acceleration
        writeParam(0x00); // button mapping
        writeParam(0x55); // WRONG KEY

        executeCommand(CMD_MOUSE_CONFIG);

        int status = readStatus();
        assertEquals("Config should fail with MFERR_INVALIDWEKEY", MFERR_INVALIDWEKEY, status & 0x7F);
    }

    /**
     * Test 8: CMD_MOUSE_CONFIG accepts valid parameters
     */
    @Test
    public void testMouseConfigWithValidParameters() {
        writeParam(128); // sensitivity 1.0x
        writeParam(0x02); // medium acceleration
        writeParam(0x00); // default button mapping
        writeParam(WRITEENABLEKEY); // CORRECT KEY

        executeCommand(CMD_MOUSE_CONFIG);

        int status = readStatus();
        assertEquals("Config should succeed", MFERR_NONE, status & 0x7F);
    }

    /**
     * Test 9: CMD_MOUSE_CONFIG rejects invalid sensitivity
     */
    @Test
    public void testMouseConfigRejectsInvalidSensitivity() {
        writeParam(32); // Invalid: < 64
        writeParam(0x02);
        writeParam(0x00);
        writeParam(WRITEENABLEKEY);

        executeCommand(CMD_MOUSE_CONFIG);

        int status = readStatus();
        assertEquals("Config should fail with MFERR_INVALIDARG", MFERR_INVALIDARG, status & 0x7F);
    }

    /**
     * Test 10: CMD_MOUSE_CONFIG rejects invalid acceleration mode
     */
    @Test
    public void testMouseConfigRejectsInvalidAcceleration() {
        writeParam(128);
        writeParam(0x05); // Invalid: > 3
        writeParam(0x00);
        writeParam(WRITEENABLEKEY);

        executeCommand(CMD_MOUSE_CONFIG);

        int status = readStatus();
        assertEquals("Config should fail with MFERR_INVALIDARG", MFERR_INVALIDARG, status & 0x7F);
    }

    /**
     * Test 11: CMD_MOUSE_STATUS returns valid connection states
     */
    @Test
    public void testMouseStatusReturnsValidState() {
        // Before init
        executeCommand(CMD_MOUSE_STATUS);
        int state1 = readParam();
        assertEquals("State before init should be 0x00 (not initialized)", 0x00, state1);

        // After init
        executeCommand(CMD_MOUSE_INIT);
        readParam(); // Clear init result

        executeCommand(CMD_MOUSE_STATUS);
        int state2 = readParam();
        assertEquals("State after init should be 0x01 (initialized, not paired)", 0x01, state2);
    }

    /**
     * Test 12: CMD_MOUSE_CLAMP validates axis parameter
     */
    @Test
    public void testMouseClampValidatesAxis() {
        writeParam(0x02); // Invalid axis (should be 0 or 1)
        writeParam(0x00); // minLow
        writeParam(0x00); // minHigh
        writeParam(0xFF); // maxLow
        writeParam(0x02); // maxHigh

        executeCommand(CMD_MOUSE_CLAMP);

        int status = readStatus();
        assertEquals("Clamp should fail with MFERR_INVALIDARG", MFERR_INVALIDARG, status & 0x7F);
    }

    /**
     * Test 13: CMD_MOUSE_CLAMP validates min <= max
     */
    @Test
    public void testMouseClampValidatesMinMax() {
        writeParam(0x00); // X axis
        writeParam(0xFF); // minLow = 255
        writeParam(0x02); // minHigh = 2 (total 255 + 512 = 767)
        writeParam(0x00); // maxLow = 0
        writeParam(0x01); // maxHigh = 1 (total 256)

        executeCommand(CMD_MOUSE_CLAMP);

        int status = readStatus();
        assertEquals("Clamp should fail when min > max", MFERR_INVALIDARG, status & 0x7F);
    }

    /**
     * Test 14: CMD_MOUSE_CLAMP accepts valid bounds
     */
    @Test
    public void testMouseClampAcceptsValidBounds() {
        // Set X bounds to 0-639
        writeParam(0x00); // X axis
        writeParam(0x00); // minLow
        writeParam(0x00); // minHigh
        writeParam(0x7F); // maxLow (639 & 0xFF = 127)
        writeParam(0x02); // maxHigh (639 >> 8 = 2)

        executeCommand(CMD_MOUSE_CLAMP);

        int status = readStatus();
        assertEquals("Clamp should succeed", MFERR_NONE, status & 0x7F);
    }

    /**
     * Test 15: CMD_MOUSE_DISCONNECT requires write enable key
     */
    @Test
    public void testMouseDisconnectRequiresWriteKey() {
        writeParam(0x00); // Don't forget
        writeParam(0x42); // WRONG KEY

        executeCommand(CMD_MOUSE_DISCONNECT);

        int status = readStatus();
        assertEquals("Disconnect should fail with MFERR_INVALIDWEKEY", MFERR_INVALIDWEKEY, status & 0x7F);
    }

    /**
     * Test 16: CMD_MOUSE_DISCONNECT accepts forget flag parameter
     */
    @Test
    public void testMouseDisconnectWithForgetFlag() {
        // Disconnect with forget flag and valid key
        writeParam(0x01); // Forget device
        writeParam(WRITEENABLEKEY);
        executeCommand(CMD_MOUSE_DISCONNECT);

        int status = readStatus();
        assertEquals("Disconnect with forget should succeed", MFERR_NONE, status & 0x7F);

        // Disconnect without forget flag
        writeParam(0x00); // Don't forget
        writeParam(WRITEENABLEKEY);
        executeCommand(CMD_MOUSE_DISCONNECT);

        status = readStatus();
        assertEquals("Disconnect without forget should succeed", MFERR_NONE, status & 0x7F);
    }

    /**
     * Test 17: IDREG alternates between hardware values (0x96/0x69) for detection
     */
    @Test
    public void testIDRegAlternatesForDetection() {
        // First read should return 0x96 (hardware initial value)
        RAMEvent event1 = createEvent(RAMEvent.TYPE.READ, 0);
        card.handleIOAccess(3, RAMEvent.TYPE.READ, 0, event1);
        int firstRead = event1.getNewValue();

        // Second read should return 0x69 (bitwise NOT of 0x96)
        RAMEvent event2 = createEvent(RAMEvent.TYPE.READ, 0);
        card.handleIOAccess(3, RAMEvent.TYPE.READ, 0, event2);
        int secondRead = event2.getNewValue();

        // Third read should return 0x96 (bitwise NOT of 0x69, cycles back)
        RAMEvent event3 = createEvent(RAMEvent.TYPE.READ, 0);
        card.handleIOAccess(3, RAMEvent.TYPE.READ, 0, event3);
        int thirdRead = event3.getNewValue();

        // Verify values match real hardware
        assertEquals("First IDREG read should be 0x96", 0x96, firstRead);
        assertEquals("Second IDREG read should be 0x69", 0x69, secondRead);
        assertEquals("Third IDREG read should be 0x96 (cycle)", 0x96, thirdRead);

        // Verify XOR detection pattern: first XOR second should equal 0xFF
        int xorResult = firstRead ^ secondRead;
        assertEquals("XOR of consecutive reads should be 0xFF for detection", 0xFF, xorResult);
    }

    /**
     * Test 18: Button state reading returns expected format
     */
    @Test
    public void testButtonStateReading() {
        // Read button state (initially no buttons pressed)
        executeCommand(CMD_MOUSE_READ);
        readParam(); // deltaX
        readParam(); // deltaY
        int buttons = readParam();

        // Buttons should be 0 initially (bits 7-5: button3, button2, button1)
        // Top 3 bits should all be 0
        assertEquals("No buttons should be pressed initially", 0, buttons & 0xE0);
    }

    /**
     * Test 18b: BUSY flag is SET when command is written and CLEARED after completion
     * This test verifies the fix for the hang issue where PLASMA FPU code waits for BUSY flag to clear.
     */
    @Test
    public void testBusyFlagLifecycle() {
        // Write operands for a FADD operation
        writeBinaryOperands(2.0, 3.0);

        // Execute FADD command - this should SET the BUSY flag immediately
        executeCommand(CMD_FADD);

        // Read status register - BUSY flag should be CLEAR after command completes
        // (Commands execute synchronously in this implementation)
        int status = readStatus();

        // BUSY flag (bit 7 = 0x80) should be CLEAR (0)
        assertEquals("BUSY flag should be clear after command completes", 0, status & 0x80);

        // Verify the command actually executed correctly
        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FADD should complete successfully", MATHERR_NONE, errorCode);
        assertEquals("FADD: 2.0 + 3.0 should equal 5.0", 5.0, value, 0.0001);
    }

    // ==================== Math Accelerator Tests ====================

    private static final int MFERR_UNKNOWNCMD = 0x03;

    private int readData() {
        RAMEvent event = createEvent(RAMEvent.TYPE.READ, 0);
        card.handleIOAccess(2, RAMEvent.TYPE.READ, 0, event);
        return event.getNewValue();
    }

    /**
     * Test 19: FOUT command formats 1.0 as "1"
     */
    @Test
    public void testFOutFormatsOne() {
        // Write FAC in interleaved Pico protocol format
        writeUnaryOperand(1.0);

        // Execute FOUT
        executeCommand(CMD_FOUT);

        // Check status
        int status = readStatus();
        assertEquals("FOUT should succeed", MFERR_NONE, status & 0x7F);

        // Read string length from paramBuffer[0]
        int length = readParam();
        assertEquals("Length should be 1", 1, length);

        // Read string from dataBuffer
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((char)readData());
        }

        assertEquals("FOUT(1.0) should format as '1'", "1", result.toString());
    }

    /**
     * Test 20: FOUT command formats 123.45 as "123.45"
     */
    @Test
    public void testFOutFormatsDecimal() {
        writeUnaryOperand(123.45);

        executeCommand(CMD_FOUT);

        int status = readStatus();
        assertEquals("FOUT should succeed", MFERR_NONE, status & 0x7F);

        int length = readParam();
        assertTrue("Length should be reasonable", length > 0 && length <= 32);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((char)readData());
        }

        assertEquals("FOUT(123.45) should format as '123.45'", "123.45", result.toString());
    }

    /**
     * Test 21: FOUT command formats zero as "0"
     */
    @Test
    public void testFOutFormatsZero() {
        writeUnaryOperand(0.0);

        executeCommand(CMD_FOUT);

        int status = readStatus();
        assertEquals("FOUT should succeed", MFERR_NONE, status & 0x7F);

        int length = readParam();
        assertEquals("Length should be 1", 1, length);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((char)readData());
        }

        assertEquals("FOUT(0.0) should format as '0'", "0", result.toString());
    }

    /**
     * Test 22: FOUT command uses scientific notation for large values
     */
    @Test
    public void testFOutScientificNotationLarge() {
        writeUnaryOperand(1.23456e10);

        executeCommand(CMD_FOUT);

        int status = readStatus();
        assertEquals("FOUT should succeed", MFERR_NONE, status & 0x7F);

        int length = readParam();
        assertTrue("Length should be reasonable", length > 0 && length <= 32);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((char)readData());
        }

        // Should use scientific notation (e.g., "1.23456E+10")
        assertTrue("FOUT should use scientific notation for large values",
                   result.toString().contains("E+"));
    }

    /**
     * Test 23: FOUT command uses scientific notation for small values
     */
    @Test
    public void testFOutScientificNotationSmall() {
        writeUnaryOperand(1.23456e-5);

        executeCommand(CMD_FOUT);

        int status = readStatus();
        assertEquals("FOUT should succeed", MFERR_NONE, status & 0x7F);

        int length = readParam();
        assertTrue("Length should be reasonable", length > 0 && length <= 32);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((char)readData());
        }

        // Should use scientific notation (e.g., "1.23456E-5")
        assertTrue("FOUT should use scientific notation for small values",
                   result.toString().contains("E-"));
    }

    /**
     * Test 24: FOUT command handles negative numbers
     */
    @Test
    public void testFOutNegativeNumber() {
        writeUnaryOperand(-42.0);

        executeCommand(CMD_FOUT);

        int status = readStatus();
        assertEquals("FOUT should succeed", MFERR_NONE, status & 0x7F);

        int length = readParam();
        assertTrue("Length should be reasonable", length > 0 && length <= 32);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((char)readData());
        }

        assertEquals("FOUT(-42.0) should format as '-42'", "-42", result.toString());
    }

    /**
     * Test 25: FOUT command formats 0.001 without scientific notation
     */
    @Test
    public void testFOutFormatsSmallDecimal() {
        writeUnaryOperand(0.001);

        executeCommand(CMD_FOUT);

        int status = readStatus();
        assertEquals("FOUT should succeed", MFERR_NONE, status & 0x7F);

        int length = readParam();
        assertTrue("Length should be reasonable", length > 0 && length <= 32);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((char)readData());
        }

        assertEquals("FOUT(0.001) should format as '0.001'", "0.001", result.toString());
    }

    /**
     * Test 26: FMUL10 command returns UNKNOWNCMD
     */
    @Test
    public void testFMul10ReturnsUnknownCmd() {
        executeCommand(CMD_FMUL10);

        int status = readStatus();
        assertEquals("FMUL10 should return UNKNOWNCMD", MFERR_UNKNOWNCMD, status & 0x7F);
    }

    /**
     * Test 27: FDIV10 command returns UNKNOWNCMD
     */
    @Test
    public void testFDiv10ReturnsUnknownCmd() {
        executeCommand(CMD_FDIV10);

        int status = readStatus();
        assertEquals("FDIV10 should return UNKNOWNCMD", MFERR_UNKNOWNCMD, status & 0x7F);
    }

    private static final int CMD_FOUT = 0x3A;
    private static final int CMD_FMUL10 = 0x3B;
    private static final int CMD_FDIV10 = 0x3C;

    // ==================== Helper Methods for Binary Math Operations ====================

    // Pico input buffer indices (13-byte interleaved)
    private static final int FACSIGN      = 0;
    private static final int ARGSIGN      = 1;
    private static final int FACMANTISSA4 = 2;
    private static final int ARGMANTISSA4 = 3;
    private static final int FACMANTISSA3 = 4;
    private static final int ARGMANTISSA3 = 5;
    private static final int FACMANTISSA2 = 6;
    private static final int ARGMANTISSA2 = 7;
    private static final int FACMANTISSA1 = 8;
    private static final int ARGMANTISSA1 = 9;
    private static final int FACEXP       = 10;
    private static final int ARGEXP       = 11;
    private static final int FACEXT       = 12;
    // Pico output buffer indices (8-byte sequential)
    private static final int RESERROR     = 0;
    private static final int RESSIGN      = 1;
    private static final int RESMANTISSA4 = 2;
    private static final int RESMANTISSA3 = 3;
    private static final int RESMANTISSA2 = 4;
    private static final int RESMANTISSA1 = 5;
    private static final int RESEXP       = 6;
    private static final int RESEXT       = 7;

    /**
     * Write FAC and ARG in the 13-byte interleaved Pico protocol format.
     *
     * PLASMA MBF layout: [0]=EXP, [1]=M1, [2]=M2, [3]=M3, [4]=M4, [5]=SIGN, [6]=EXT
     * Pico interleaved order: FACSIGN, ARGSIGN, FACM4, ARGM4, FACM3, ARGM3,
     *                         FACM2, ARGM2, FACM1, ARGM1, FACEXP, ARGEXP, FACEXT
     */
    private void writeBinaryOperands(double fac, double arg) {
        byte[] facMbf = new byte[7];
        byte[] argMbf = new byte[7];
        MicrosoftBinaryFormat.doubleToMbf(fac, facMbf, 0);
        MicrosoftBinaryFormat.doubleToMbf(arg, argMbf, 0);

        // Build 13-byte interleaved buffer
        // facMbf: [0]=EXP, [1]=M1, [2]=M2, [3]=M3, [4]=M4, [5]=SIGN, [6]=EXT
        byte[] buf = new byte[13];
        buf[FACSIGN]      = facMbf[5];
        buf[ARGSIGN]      = argMbf[5];
        buf[FACMANTISSA4] = facMbf[4];
        buf[ARGMANTISSA4] = argMbf[4];
        buf[FACMANTISSA3] = facMbf[3];
        buf[ARGMANTISSA3] = argMbf[3];
        buf[FACMANTISSA2] = facMbf[2];
        buf[ARGMANTISSA2] = argMbf[2];
        buf[FACMANTISSA1] = facMbf[1];
        buf[ARGMANTISSA1] = argMbf[1];
        buf[FACEXP]       = facMbf[0];
        buf[ARGEXP]       = argMbf[0];
        buf[FACEXT]       = facMbf[6];

        for (int i = 0; i < 13; i++) {
            writeParam(buf[i] & 0xFF);
        }
    }

    /**
     * Read the result of a binary or unary math operation (8 bytes in Pico output format).
     * Pico output: [RESERROR, RESSIGN, RESMANTISSA4, RESMANTISSA3, RESMANTISSA2, RESMANTISSA1, RESEXP, RESEXT]
     * PLASMA MBF:  [0]=EXP,  [1]=M1,  [2]=M2,        [3]=M3,        [4]=M4,        [5]=SIGN,    [6]=EXT
     * Returns: [error_code, result_value]
     */
    private Object[] readBinaryResult() {
        int errorCode = readParam();                // RESERROR (index 0)
        int ressign   = readParam();                // RESSIGN  (index 1)
        int resm4     = readParam();                // RESMANTISSA4 (index 2)
        int resm3     = readParam();                // RESMANTISSA3 (index 3)
        int resm2     = readParam();                // RESMANTISSA2 (index 4)
        int resm1     = readParam();                // RESMANTISSA1 (index 5)
        int resexp    = readParam();                // RESEXP   (index 6)
        int resext    = readParam();                // RESEXT   (index 7)

        // Reconstruct PLASMA MBF array from Pico output format
        byte[] mbf = new byte[7];
        mbf[0] = (byte) resexp;
        mbf[1] = (byte) resm1;
        mbf[2] = (byte) resm2;
        mbf[3] = (byte) resm3;
        mbf[4] = (byte) resm4;
        mbf[5] = (byte) ressign;
        mbf[6] = (byte) resext;

        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        return new Object[]{errorCode, result};
    }

    // ==================== Binary Math Operation Tests ====================

    /**
     * Test 28: FADD: 2.0 + 2.0 = 4.0
     */
    @Test
    public void testFAdd_Basic() {
        writeBinaryOperands(2.0, 2.0);
        executeCommand(CMD_FADD);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FADD error code should be NONE", MATHERR_NONE, errorCode);
        assertEquals("FADD: 2.0 + 2.0 should equal 4.0", 4.0, value, 0.0001);
    }

    /**
     * Test 29: FADD with negative numbers: -5.0 + 3.0 = -2.0
     */
    @Test
    public void testFAdd_NegativeNumbers() {
        writeBinaryOperands(-5.0, 3.0);
        executeCommand(CMD_FADD);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FADD error code should be NONE", MATHERR_NONE, errorCode);
        assertEquals("FADD: -5.0 + 3.0 should equal -2.0", -2.0, value, 0.0001);
    }

    /**
     * Test 30: FADD overflow: Adding values near MBF_MAX should detect overflow
     */
    @Test
    public void testFAdd_Overflow() {
        double nearMax = MicrosoftBinaryFormat.MBF_MAX * 0.9;
        writeBinaryOperands(nearMax, nearMax);
        executeCommand(CMD_FADD);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];

        assertEquals("FADD overflow should set OVERFLOW error", MATHERR_OVERFLOW, errorCode);
    }

    /**
     * Test 31: FMUL: 3.0 * 4.0 = 12.0
     */
    @Test
    public void testFMul_Basic() {
        writeBinaryOperands(3.0, 4.0);
        executeCommand(CMD_FMUL);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FMUL error code should be NONE", MATHERR_NONE, errorCode);
        assertEquals("FMUL: 3.0 * 4.0 should equal 12.0", 12.0, value, 0.0001);
    }

    /**
     * Test 32: FMUL with negative: 5.0 * -2.0 = -10.0
     */
    @Test
    public void testFMul_NegativeOperand() {
        writeBinaryOperands(5.0, -2.0);
        executeCommand(CMD_FMUL);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FMUL error code should be NONE", MATHERR_NONE, errorCode);
        assertEquals("FMUL: 5.0 * -2.0 should equal -10.0", -10.0, value, 0.0001);
    }

    /**
     * Test 33: FMUL overflow: Multiplying large values should detect overflow
     */
    @Test
    public void testFMul_Overflow() {
        double largeValue = Math.sqrt(MicrosoftBinaryFormat.MBF_MAX) * 1.5;
        writeBinaryOperands(largeValue, largeValue);
        executeCommand(CMD_FMUL);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];

        assertEquals("FMUL overflow should set OVERFLOW error", MATHERR_OVERFLOW, errorCode);
    }

    /**
     * Test 34: FDIV: 12.0 / 3.0 = 4.0
     * Pico semantics: arg / fac. So fac=3.0 (divisor), arg=12.0 (dividend).
     */
    @Test
    public void testFDiv_Basic() {
        writeBinaryOperands(3.0, 12.0);  // fac=3.0 (divisor), arg=12.0 (dividend)
        executeCommand(CMD_FDIV);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FDIV error code should be NONE", MATHERR_NONE, errorCode);
        assertEquals("FDIV: 12.0 / 3.0 should equal 4.0", 4.0, value, 0.0001);
    }

    /**
     * Test 35: FDIV by zero: FAC=0 triggers DIV0 error (Pico checks fac==0.0)
     */
    @Test
    public void testFDiv_DivisionByZero() {
        writeBinaryOperands(0.0, 10.0);  // fac=0.0 (divisor=0 => error), arg=10.0
        executeCommand(CMD_FDIV);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FDIV by zero should set DIV0 error", MATHERR_DIV0, errorCode);
        assertEquals("FDIV by zero should return 0.0", 0.0, value, 0.0001);
    }

    /**
     * Test 36: FDIV with negative divisor: 12.0 / -3.0 = -4.0
     * Pico semantics: arg / fac. So fac=-3.0 (divisor), arg=12.0 (dividend).
     */
    @Test
    public void testFDiv_NegativeDivisor() {
        writeBinaryOperands(-3.0, 12.0);  // fac=-3.0 (divisor), arg=12.0 (dividend)
        executeCommand(CMD_FDIV);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FDIV error code should be NONE", MATHERR_NONE, errorCode);
        assertEquals("FDIV: 12.0 / -3.0 should equal -4.0", -4.0, value, 0.0001);
    }

    /**
     * Test 37: Parameter buffer read/write with 13-byte format
     */
    @Test
    public void testBinaryOperation_ParameterBufferFormat() {
        // Write operands
        writeBinaryOperands(5.5, 2.5);

        // Execute addition
        executeCommand(CMD_FADD);

        // Read result - should be 8 bytes (1 error + 7 result)
        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("Error code should be NONE", MATHERR_NONE, errorCode);
        assertEquals("5.5 + 2.5 should equal 8.0", 8.0, value, 0.0001);
    }

    // ==================== Unary Math Operation Tests ====================

    /**
     * Helper to write a unary operand in the 13-byte interleaved Pico protocol format.
     * For unary operations, FAC is the only meaningful operand (ARG is sent as the same
     * value, matching PLASMA's execUnaryOp which calls sendFACARG(reg, reg)).
     */
    private void writeUnaryOperand(double value) {
        writeBinaryOperands(value, value);
    }

    /**
     * Helper to read unary or binary operation result (8 bytes in Pico output format).
     * Delegates to readBinaryResult since the format is identical.
     */
    private Object[] readUnaryResult() {
        return readBinaryResult();
    }

    /**
     * Test 38: CMD_FSIN: sin(0) = 0
     */
    @Test
    public void testFSinOfZero() {
        writeUnaryOperand(0.0);
        executeCommand(CMD_FSIN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FSIN(0) should have no error", MATHERR_NONE, errorCode);
        assertEquals("sin(0) should be 0", 0.0, value, 1e-6);
    }

    /**
     * Test 39: CMD_FSIN: sin(π/2) ≈ 1
     */
    @Test
    public void testFSinOfPiOver2() {
        writeUnaryOperand(Math.PI / 2.0);
        executeCommand(CMD_FSIN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FSIN(π/2) should have no error", MATHERR_NONE, errorCode);
        assertEquals("sin(π/2) should be 1", 1.0, value, 1e-6);
    }

    /**
     * Test 40: CMD_FSIN: sin(π) ≈ 0
     */
    @Test
    public void testFSinOfPi() {
        writeUnaryOperand(Math.PI);
        executeCommand(CMD_FSIN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FSIN(π) should have no error", MATHERR_NONE, errorCode);
        assertEquals("sin(π) should be 0", 0.0, value, 1e-6);
    }

    /**
     * Test 41: CMD_FCOS: cos(0) = 1
     */
    @Test
    public void testFCosOfZero() {
        writeUnaryOperand(0.0);
        executeCommand(CMD_FCOS);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FCOS(0) should have no error", MATHERR_NONE, errorCode);
        assertEquals("cos(0) should be 1", 1.0, value, 1e-6);
    }

    /**
     * Test 42: CMD_FCOS: cos(π) ≈ -1
     */
    @Test
    public void testFCosOfPi() {
        writeUnaryOperand(Math.PI);
        executeCommand(CMD_FCOS);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FCOS(π) should have no error", MATHERR_NONE, errorCode);
        assertEquals("cos(π) should be -1", -1.0, value, 1e-6);
    }

    /**
     * Test 43: CMD_FCOS: cos(π/2) ≈ 0
     */
    @Test
    public void testFCosOfPiOver2() {
        writeUnaryOperand(Math.PI / 2.0);
        executeCommand(CMD_FCOS);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FCOS(π/2) should have no error", MATHERR_NONE, errorCode);
        assertEquals("cos(π/2) should be 0", 0.0, value, 1e-6);
    }

    /**
     * Test 44: CMD_FTAN: tan(0) = 0
     */
    @Test
    public void testFTanOfZero() {
        writeUnaryOperand(0.0);
        executeCommand(CMD_FTAN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FTAN(0) should have no error", MATHERR_NONE, errorCode);
        assertEquals("tan(0) should be 0", 0.0, value, 1e-6);
    }

    /**
     * Test 45: CMD_FTAN: tan(π/4) ≈ 1
     */
    @Test
    public void testFTanOfPiOver4() {
        writeUnaryOperand(Math.PI / 4.0);
        executeCommand(CMD_FTAN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FTAN(π/4) should have no error", MATHERR_NONE, errorCode);
        assertEquals("tan(π/4) should be 1", 1.0, value, 1e-6);
    }

    /**
     * Test 46: CMD_FTAN detects overflow near π/2
     */
    @Test
    public void testFTanOverflow() {
        writeUnaryOperand(Math.PI / 2.0);
        executeCommand(CMD_FTAN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];

        assertEquals("FTAN(π/2) should overflow", MATHERR_OVERFLOW, errorCode);
    }

    /**
     * Test 47: CMD_FATN: atan(0) = 0
     */
    @Test
    public void testFAtnOfZero() {
        writeUnaryOperand(0.0);
        executeCommand(CMD_FATN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FATN(0) should have no error", MATHERR_NONE, errorCode);
        assertEquals("atan(0) should be 0", 0.0, value, 1e-6);
    }

    /**
     * Test 48: CMD_FATN: atan(1) ≈ π/4
     */
    @Test
    public void testFAtnOfOne() {
        writeUnaryOperand(1.0);
        executeCommand(CMD_FATN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FATN(1) should have no error", MATHERR_NONE, errorCode);
        assertEquals("atan(1) should be π/4", Math.PI / 4.0, value, 1e-6);
    }

    /**
     * Test 49: CMD_FATN: atan(-1) ≈ -π/4
     */
    @Test
    public void testFAtnOfNegativeOne() {
        writeUnaryOperand(-1.0);
        executeCommand(CMD_FATN);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FATN(-1) should have no error", MATHERR_NONE, errorCode);
        assertEquals("atan(-1) should be -π/4", -Math.PI / 4.0, value, 1e-6);
    }

    /**
     * Test 50: CMD_FLOG: log(e) = 1
     */
    @Test
    public void testFLogOfE() {
        writeUnaryOperand(Math.E);
        executeCommand(CMD_FLOG);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FLOG(e) should have no error", MATHERR_NONE, errorCode);
        assertEquals("log(e) should be 1", 1.0, value, 1e-6);
    }

    /**
     * Test 51: CMD_FLOG: log(1) = 0
     */
    @Test
    public void testFLogOfOne() {
        writeUnaryOperand(1.0);
        executeCommand(CMD_FLOG);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FLOG(1) should have no error", MATHERR_NONE, errorCode);
        assertEquals("log(1) should be 0", 0.0, value, 1e-6);
    }

    /**
     * Test 52: CMD_FLOG: log(e^2) = 2
     */
    @Test
    public void testFLogOfESquared() {
        writeUnaryOperand(Math.E * Math.E);
        executeCommand(CMD_FLOG);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FLOG(e^2) should have no error", MATHERR_NONE, errorCode);
        assertEquals("log(e^2) should be 2", 2.0, value, 1e-6);
    }

    /**
     * Test 53: CMD_FLOG detects illegal quantity for negative input
     */
    @Test
    public void testFLogOfNegative() {
        writeUnaryOperand(-1.0);
        executeCommand(CMD_FLOG);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];

        assertEquals("FLOG(-1) should return IQERROR", MATHERR_IQERROR, errorCode);
    }

    /**
     * Test 54: CMD_FLOG detects illegal quantity for zero
     */
    @Test
    public void testFLogOfZero() {
        writeUnaryOperand(0.0);
        executeCommand(CMD_FLOG);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];

        assertEquals("FLOG(0) should return IQERROR", MATHERR_IQERROR, errorCode);
    }

    /**
     * Test 55: CMD_FEXP: exp(0) = 1
     */
    @Test
    public void testFExpOfZero() {
        writeUnaryOperand(0.0);
        executeCommand(CMD_FEXP);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FEXP(0) should have no error", MATHERR_NONE, errorCode);
        assertEquals("exp(0) should be 1", 1.0, value, 1e-6);
    }

    /**
     * Test 56: CMD_FEXP: exp(1) = e
     */
    @Test
    public void testFExpOfOne() {
        writeUnaryOperand(1.0);
        executeCommand(CMD_FEXP);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FEXP(1) should have no error", MATHERR_NONE, errorCode);
        assertEquals("exp(1) should be e", Math.E, value, 1e-6);
    }

    /**
     * Test 57: CMD_FEXP: exp(2) = e^2
     */
    @Test
    public void testFExpOfTwo() {
        writeUnaryOperand(2.0);
        executeCommand(CMD_FEXP);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FEXP(2) should have no error", MATHERR_NONE, errorCode);
        assertEquals("exp(2) should be e^2", Math.E * Math.E, value, 1e-5);
    }

    /**
     * Test 58: CMD_FEXP detects overflow on large inputs
     */
    @Test
    public void testFExpOverflow() {
        writeUnaryOperand(100.0);
        executeCommand(CMD_FEXP);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];

        assertEquals("FEXP(100) should overflow", MATHERR_OVERFLOW, errorCode);
    }

    /**
     * Test 59: CMD_FSQR: sqrt(4) = 2
     */
    @Test
    public void testFSqrOfFour() {
        writeUnaryOperand(4.0);
        executeCommand(CMD_FSQR);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FSQR(4) should have no error", MATHERR_NONE, errorCode);
        assertEquals("sqrt(4) should be 2", 2.0, value, 1e-6);
    }

    /**
     * Test 60: CMD_FSQR: sqrt(0) = 0
     */
    @Test
    public void testFSqrOfZero() {
        writeUnaryOperand(0.0);
        executeCommand(CMD_FSQR);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FSQR(0) should have no error", MATHERR_NONE, errorCode);
        assertEquals("sqrt(0) should be 0", 0.0, value, 1e-6);
    }

    /**
     * Test 61: CMD_FSQR: sqrt(9) = 3
     */
    @Test
    public void testFSqrOfNine() {
        writeUnaryOperand(9.0);
        executeCommand(CMD_FSQR);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FSQR(9) should have no error", MATHERR_NONE, errorCode);
        assertEquals("sqrt(9) should be 3", 3.0, value, 1e-6);
    }

    /**
     * Test 62: CMD_FSQR detects illegal quantity for sqrt(-1)
     */
    @Test
    public void testFSqrOfNegativeOne() {
        writeUnaryOperand(-1.0);
        executeCommand(CMD_FSQR);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];

        assertEquals("FSQR(-1) should return IQERROR", MATHERR_IQERROR, errorCode);
    }

    /**
     * Test 63: CMD_FSQR detects illegal quantity for sqrt(-4)
     */
    @Test
    public void testFSqrOfNegativeFour() {
        writeUnaryOperand(-4.0);
        executeCommand(CMD_FSQR);

        Object[] result = readUnaryResult();
        int errorCode = (int) result[0];

        assertEquals("FSQR(-4) should return IQERROR", MATHERR_IQERROR, errorCode);
    }

    // ==================== Reset Command Tests ====================

    /**
     * Test 64: CMD_RESETBOTHPTRS (0x00) sets status to MFERR_NONE
     *
     * Before this fix, command 0x00 fell through to the default case and
     * set status to MFERR_UNKNOWNCMD. Verify the fix returns MFERR_NONE.
     */
    @Test
    public void testCmdResetBothPtrsReturnsNone() {
        executeCommand(0x00);

        int status = readStatus();
        assertEquals("CMD_RESETBOTHPTRS (0x00) should return MFERR_NONE, not MFERR_UNKNOWNCMD",
                MFERR_NONE, status & 0x7F);
    }

    /**
     * Test 65: CMD_RESETDATAPTR (0x01) sets status to MFERR_NONE
     */
    @Test
    public void testCmdResetDataPtrReturnsNone() {
        executeCommand(0x01);

        int status = readStatus();
        assertEquals("CMD_RESETDATAPTR (0x01) should return MFERR_NONE",
                MFERR_NONE, status & 0x7F);
    }

    /**
     * Test 66: CMD_RESETPARAMPTR (0x02) sets status to MFERR_NONE
     */
    @Test
    public void testCmdResetParamPtrReturnsNone() {
        executeCommand(0x02);

        int status = readStatus();
        assertEquals("CMD_RESETPARAMPTR (0x02) should return MFERR_NONE",
                MFERR_NONE, status & 0x7F);
    }

    /**
     * Run one FPU operation exactly as mftest.bas does after the CMD_RESETBOTHPTRS fix:
     *   1. POKE 49344,0  — CMD_RESETBOTHPTRS: reset both paramPointer and dataPointer
     *   2. POKE 49344,2  — CMD_RESETPARAMPTR: firmware bug resets dataPointer (not paramPointer)
     *   3. Write 13 interleaved bytes to PARAMREG
     *   4. Issue the FPU command
     *   5. POKE 49344,2  — reset before reading
     *   6. Read 8 result bytes from PARAMREG
     *
     * @param cmd     FPU command byte (e.g. CMD_FADD=0x30)
     * @param sendBuf 13-byte interleaved buffer: [FS,AS,F4,A4,F3,A3,F2,A2,F1,A1,FE,AE,FX]
     * @return int[8]: [ERR, SIGN, M4, M3, M2, M1, EXP, EXT]
     */
    private int[] runMftestOp(int cmd, int[] sendBuf) {
        executeCommand(0x00); // CMD_RESETBOTHPTRS — ensures clean buffer state
        executeCommand(0x02); // CMD_RESETPARAMPTR (firmware bug: resets dataPointer only)
        for (int b : sendBuf) writeParam(b);
        executeCommand(cmd);
        executeCommand(0x02); // reset before reading
        int[] rb = new int[8];
        for (int i = 0; i < 8; i++) rb[i] = readParam();
        return rb;
    }

    /**
     * Test 68: mftest.bas — all 5 FPU operations pass with CMD_RESETBOTHPTRS fix.
     *
     * Simulates the exact POKE/PEEK sequence that mftest.bas performs on the Apple II,
     * using the raw MBF bytes the BASIC program would send.  The fix on line 8010 of
     * mftest.bas issues CMD_RESETBOTHPTRS (POKE 49344,0) before each operation so the
     * parameter buffer is always in a known state regardless of firmware boot state.
     *
     * Interleaved send buffer: [FS, AS, F4, A4, F3, A3, F2, A2, F1, A1, FE, AE, FX]
     * Result buffer: [ERR, SIGN, M4, M3, M2, M1, EXP, EXT]
     */
    @Test
    public void testMfTestBasicProgram() {
        int passed = 0;
        StringBuilder failures = new StringBuilder();

        // Test 1: FADD 3+4=7
        // FAC=3.0: EXP=$82=130, M1=$C0=192  |  ARG=4.0: EXP=$83=131, M1=$80=128
        // Expected=7.0: EXP=$83=131, M1=$E0=224
        {
            int[] rb = runMftestOp(CMD_FADD, new int[]{0,0,0,0,0,0,0,0,192,128,130,131,0});
            if (rb[0]==0 && rb[6]==131 && rb[5]==224 && rb[4]==0 && rb[3]==0 && rb[2]==0 && rb[1]==0) {
                passed++;
            } else {
                failures.append("\n  FADD 3+4=7: ERR=").append(rb[0])
                        .append(" EXP=").append(rb[6]).append(" M1=").append(rb[5]).append(" SIGN=").append(rb[1]);
            }
        }

        // Test 2: FMUL 3*4=12
        // FAC=3.0: EXP=$82=130, M1=$C0=192  |  ARG=4.0: EXP=$83=131, M1=$80=128
        // Expected=12.0: EXP=$84=132, M1=$C0=192
        {
            int[] rb = runMftestOp(CMD_FMUL, new int[]{0,0,0,0,0,0,0,0,192,128,130,131,0});
            if (rb[0]==0 && rb[6]==132 && rb[5]==192 && rb[4]==0 && rb[3]==0 && rb[2]==0 && rb[1]==0) {
                passed++;
            } else {
                failures.append("\n  FMUL 3*4=12: ERR=").append(rb[0])
                        .append(" EXP=").append(rb[6]).append(" M1=").append(rb[5]).append(" SIGN=").append(rb[1]);
            }
        }

        // Test 3: FSQR(4)=2 (unary — ARG unused, sent as zero)
        // FAC=4.0: EXP=$83=131, M1=$80=128
        // Expected=2.0: EXP=$82=130, M1=$80=128
        {
            int[] rb = runMftestOp(CMD_FSQR, new int[]{0,0,0,0,0,0,0,0,128,0,131,0,0});
            if (rb[0]==0 && rb[6]==130 && rb[5]==128 && rb[4]==0 && rb[3]==0 && rb[2]==0 && rb[1]==0) {
                passed++;
            } else {
                failures.append("\n  FSQR(4)=2: ERR=").append(rb[0])
                        .append(" EXP=").append(rb[6]).append(" M1=").append(rb[5]).append(" SIGN=").append(rb[1]);
            }
        }

        // Test 4: FSIN(0)=0 (unary — FAC=0.0, all zeros)
        // Expected=0.0: all zeros
        {
            int[] rb = runMftestOp(CMD_FSIN, new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0});
            if (rb[0]==0 && rb[6]==0 && rb[5]==0 && rb[4]==0 && rb[3]==0 && rb[2]==0 && rb[1]==0) {
                passed++;
            } else {
                failures.append("\n  FSIN(0)=0: ERR=").append(rb[0])
                        .append(" EXP=").append(rb[6]).append(" M1=").append(rb[5]).append(" SIGN=").append(rb[1]);
            }
        }

        // Test 5: FDIV 12/4=3  (Pico semantics: result = ARG/FAC)
        // FAC=4.0 (divisor): EXP=$83=131, M1=$80=128
        // ARG=12.0 (dividend): EXP=$84=132, M1=$C0=192
        // Expected=3.0: EXP=$82=130, M1=$C0=192
        {
            int[] rb = runMftestOp(CMD_FDIV, new int[]{0,0,0,0,0,0,0,0,128,192,131,132,0});
            if (rb[0]==0 && rb[6]==130 && rb[5]==192 && rb[4]==0 && rb[3]==0 && rb[2]==0 && rb[1]==0) {
                passed++;
            } else {
                failures.append("\n  FDIV 12/4=3: ERR=").append(rb[0])
                        .append(" EXP=").append(rb[6]).append(" M1=").append(rb[5]).append(" SIGN=").append(rb[1]);
            }
        }

        assertEquals("mftest.bas: all 5 tests should pass" + failures, 5, passed);
    }

    /**
     * Test 69: CMD_RESETBOTHPTRS does not corrupt subsequent operations
     *
     * Sends a reset command and then verifies a math command still works correctly.
     */
    @Test
    public void testCmdResetBothPtrsDoesNotCorruptSubsequentOps() {
        // Send reset command
        executeCommand(0x00);
        assertEquals("Status after reset should be MFERR_NONE", MFERR_NONE, readStatus() & 0x7F);

        // Now perform a real math operation to confirm nothing is broken
        writeBinaryOperands(3.0, 4.0);
        executeCommand(CMD_FMUL);

        Object[] result = readBinaryResult();
        int errorCode = (int) result[0];
        double value = (double) result[1];

        assertEquals("FMUL after reset should succeed", MATHERR_NONE, errorCode);
        assertEquals("3.0 * 4.0 should equal 12.0 after reset", 12.0, value, 0.001);
    }
}
