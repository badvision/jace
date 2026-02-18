package jace.hardware.mbf;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Comprehensive unit tests for Microsoft Binary Format (MBF) conversion.
 *
 * Tests cover:
 * - Known value conversions (1.0, -1.0, 0.0, π, e)
 * - Edge cases (MBF_MAX, MBF_MIN_POSITIVE)
 * - Round-trip conversion accuracy
 * - Overflow/underflow detection
 * - Special values (zero, near-zero)
 */
public class MicrosoftBinaryFormatTest {

    private static final double EPSILON = 1e-6; // Tolerance for floating point comparison

    @Test
    public void testZeroConversion() {
        byte[] mbf = new byte[7];

        // Convert 0.0 to MBF
        MicrosoftBinaryFormat.doubleToMbf(0.0, mbf, 0);

        // All bytes should be zero for 0.0
        for (int i = 0; i < 7; i++) {
            assertEquals("Byte " + i + " should be 0 for zero value", 0, mbf[i]);
        }

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("MBF zero should convert to 0.0", 0.0, result, 0.0);
    }

    @Test
    public void testPositiveOneConversion() {
        byte[] mbf = new byte[7];

        // Convert 1.0 to MBF
        MicrosoftBinaryFormat.doubleToMbf(1.0, mbf, 0);

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("1.0 should round-trip accurately", 1.0, result, EPSILON);
    }

    @Test
    public void testNegativeOneConversion() {
        byte[] mbf = new byte[7];

        // Convert -1.0 to MBF
        MicrosoftBinaryFormat.doubleToMbf(-1.0, mbf, 0);

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("-1.0 should round-trip accurately", -1.0, result, EPSILON);
    }

    @Test
    public void testPiConversion() {
        byte[] mbf = new byte[7];

        // Convert π to MBF
        MicrosoftBinaryFormat.doubleToMbf(Math.PI, mbf, 0);

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("π should round-trip accurately", Math.PI, result, EPSILON);
    }

    @Test
    public void testEulerConversion() {
        byte[] mbf = new byte[7];

        // Convert e to MBF
        MicrosoftBinaryFormat.doubleToMbf(Math.E, mbf, 0);

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("e should round-trip accurately", Math.E, result, EPSILON);
    }

    @Test
    public void testMaxValueConversion() {
        byte[] mbf = new byte[7];

        // Convert MBF_MAX to MBF
        MicrosoftBinaryFormat.doubleToMbf(MicrosoftBinaryFormat.MBF_MAX, mbf, 0);

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("MBF_MAX should round-trip accurately",
                    MicrosoftBinaryFormat.MBF_MAX, result,
                    MicrosoftBinaryFormat.MBF_MAX * EPSILON);
    }

    @Test
    public void testMinPositiveValueConversion() {
        byte[] mbf = new byte[7];

        // Convert MBF_MIN_POSITIVE to MBF
        MicrosoftBinaryFormat.doubleToMbf(MicrosoftBinaryFormat.MBF_MIN_POSITIVE, mbf, 0);

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("MBF_MIN_POSITIVE should round-trip accurately",
                    MicrosoftBinaryFormat.MBF_MIN_POSITIVE, result,
                    MicrosoftBinaryFormat.MBF_MIN_POSITIVE * 0.1);
    }

    @Test
    public void testOverflowDetection() {
        // Test value exceeding MBF_MAX
        assertTrue("Value exceeding MBF_MAX should be detected as overflow",
                  MicrosoftBinaryFormat.isOverflow(MicrosoftBinaryFormat.MBF_MAX * 2));

        assertTrue("Double.MAX_VALUE should overflow MBF",
                  MicrosoftBinaryFormat.isOverflow(Double.MAX_VALUE));

        assertFalse("MBF_MAX itself should not overflow",
                   MicrosoftBinaryFormat.isOverflow(MicrosoftBinaryFormat.MBF_MAX));

        assertFalse("1.0 should not overflow",
                   MicrosoftBinaryFormat.isOverflow(1.0));
    }

    @Test
    public void testUnderflowDetection() {
        // Test value smaller than MBF_MIN_POSITIVE
        assertTrue("Value below MBF_MIN_POSITIVE should be detected as underflow",
                  MicrosoftBinaryFormat.isUnderflow(MicrosoftBinaryFormat.MBF_MIN_POSITIVE / 2));

        assertTrue("Double.MIN_VALUE should underflow in MBF",
                  MicrosoftBinaryFormat.isUnderflow(Double.MIN_VALUE));

        assertFalse("MBF_MIN_POSITIVE itself should not underflow",
                   MicrosoftBinaryFormat.isUnderflow(MicrosoftBinaryFormat.MBF_MIN_POSITIVE));

        assertFalse("1.0 should not underflow",
                   MicrosoftBinaryFormat.isUnderflow(1.0));

        assertFalse("0.0 should not be considered underflow",
                   MicrosoftBinaryFormat.isUnderflow(0.0));
    }

    @Test
    public void testRoundTripConversion() {
        // Test a variety of values for round-trip accuracy
        double[] testValues = {
            0.0, 1.0, -1.0,
            2.0, -2.0,
            0.5, -0.5,
            10.0, -10.0,
            100.0, -100.0,
            1000.0, -1000.0,
            0.1, -0.1,
            0.01, -0.01,
            Math.PI, -Math.PI,
            Math.E, -Math.E,
            1234.5678, -1234.5678
        };

        byte[] mbf = new byte[7];

        for (double original : testValues) {
            MicrosoftBinaryFormat.doubleToMbf(original, mbf, 0);
            double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);

            // Use relative error for comparison (within 1 ULP tolerance)
            double tolerance = Math.abs(original) < 1.0 ? EPSILON : Math.abs(original) * EPSILON;
            assertEquals("Value " + original + " should round-trip accurately",
                        original, result, tolerance);
        }
    }

    @Test
    public void testNearZeroValues() {
        byte[] mbf = new byte[7];

        // Test values near zero but not underflowing
        double nearZero = MicrosoftBinaryFormat.MBF_MIN_POSITIVE * 10;
        MicrosoftBinaryFormat.doubleToMbf(nearZero, mbf, 0);
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);

        assertEquals("Near-zero value should convert accurately",
                    nearZero, result, nearZero * 0.1);
    }

    @Test
    public void testLargeValues() {
        byte[] mbf = new byte[7];

        // Test large values near MBF_MAX
        double largeValue = MicrosoftBinaryFormat.MBF_MAX / 10;
        MicrosoftBinaryFormat.doubleToMbf(largeValue, mbf, 0);
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);

        assertEquals("Large value should convert accurately",
                    largeValue, result, largeValue * EPSILON);
    }

    @Test
    public void testOffsetConversion() {
        byte[] mbf = new byte[14]; // Buffer with offset

        // Convert at offset 7
        MicrosoftBinaryFormat.doubleToMbf(Math.PI, mbf, 7);

        // First 7 bytes should remain zero
        for (int i = 0; i < 7; i++) {
            assertEquals("Byte " + i + " should remain unchanged", 0, mbf[i]);
        }

        // Convert back from offset 7
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 7);
        assertEquals("Offset conversion should work correctly", Math.PI, result, EPSILON);
    }

    @Test
    public void testNegativeZero() {
        byte[] mbf = new byte[7];

        // Convert -0.0 to MBF
        MicrosoftBinaryFormat.doubleToMbf(-0.0, mbf, 0);

        // Convert back to double
        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("Negative zero should convert to positive zero", 0.0, result, 0.0);
    }

    @Test
    public void testSignBit() {
        byte[] posMbf = new byte[7];
        byte[] negMbf = new byte[7];

        // Convert positive and negative value
        MicrosoftBinaryFormat.doubleToMbf(42.0, posMbf, 0);
        MicrosoftBinaryFormat.doubleToMbf(-42.0, negMbf, 0);

        // Sign should be stored in bit 7 of byte 5
        assertEquals("Positive value should have sign bit clear", 0, posMbf[5] & 0x80);
        assertEquals("Negative value should have sign bit set", 0x80, negMbf[5] & 0x80);

        // Other bytes should be identical
        for (int i = 0; i < 5; i++) {
            assertEquals("Byte " + i + " should be identical for +/- values",
                        posMbf[i], negMbf[i]);
        }
    }

    @Test
    public void testExponentSpecialCase() {
        byte[] mbf = new byte[7];
        // Manually set exponent to 0 (should represent 0.0)
        mbf[0] = 0;
        mbf[1] = (byte) 0xFF; // Non-zero mantissa
        mbf[2] = (byte) 0xFF;
        mbf[3] = (byte) 0xFF;
        mbf[4] = (byte) 0xFF;
        mbf[5] = 0;
        mbf[6] = 0;

        double result = MicrosoftBinaryFormat.mbfToDouble(mbf, 0);
        assertEquals("Exponent=0 should always represent 0.0 regardless of mantissa",
                    0.0, result, 0.0);
    }
}
