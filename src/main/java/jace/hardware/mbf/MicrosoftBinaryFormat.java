package jace.hardware.mbf;

/**
 * Microsoft Binary Format (MBF) conversion utilities.
 *
 * Provides conversion between Java double-precision floating-point values
 * and the 7-byte Microsoft Binary Format used by Applesoft BASIC and
 * compatible systems.
 *
 * MBF Format:
 * - Byte 0: Exponent (0-255, bias 129)
 * - Bytes 1-4: Mantissa (32-bit with implicit leading 1)
 * - Byte 5: Sign bit (bit 7) + mantissa extension
 * - Byte 6: Extension byte for additional precision
 *
 * Special Cases:
 * - Exponent = 0 represents 0.0 (regardless of mantissa)
 * - Mantissa has implicit leading 1 bit (normalized form)
 * - Sign stored separately in byte 5, bit 7
 */
public class MicrosoftBinaryFormat {

    /** Maximum representable value in MBF format */
    public static final double MBF_MAX = 1.701411733192644e+38;

    /** Minimum positive representable value in MBF format */
    public static final double MBF_MIN_POSITIVE = 2.938735877055719e-39;

    /** Exponent bias for MBF format */
    private static final int MBF_EXPONENT_BIAS = 129;

    /** Exponent bias for IEEE 754 double format */
    private static final int IEEE_EXPONENT_BIAS = 1023;

    /** Mantissa bit width for MBF format */
    private static final int MBF_MANTISSA_BITS = 32;

    /**
     * Convert 7-byte Microsoft Binary Format to Java double.
     *
     * @param mbf byte array containing MBF data
     * @param offset starting offset in the array
     * @return converted double value
     */
    public static double mbfToDouble(byte[] mbf, int offset) {
        // Extract exponent (byte 0)
        int exponent = mbf[offset] & 0xFF;

        // Special case: exponent = 0 means value is 0.0
        if (exponent == 0) {
            return 0.0;
        }

        // Extract mantissa bytes (bytes 1-4) with implicit leading 1
        // The mantissa is stored in normalized form with the leading 1 bit implicit
        long mantissaRaw = ((long)(mbf[offset + 1] | 0x80) & 0xFF) << 24  // Restore implicit 1
                         | ((long)(mbf[offset + 2] & 0xFF) << 16)
                         | ((long)(mbf[offset + 3] & 0xFF) << 8)
                         | ((long)(mbf[offset + 4] & 0xFF));

        // Extract sign from byte 5, bit 7
        boolean negative = (mbf[offset + 5] & 0x80) != 0;

        // Convert mantissa to double
        // MBF mantissa is 32-bit with implicit leading 1, representing [1.0, 2.0)
        // So we divide by 2^31 to get the mantissa in the range [1.0, 2.0)
        double mantissa = mantissaRaw / (double)(1L << (MBF_MANTISSA_BITS - 1));

        // Calculate actual exponent (unbias)
        int actualExponent = exponent - MBF_EXPONENT_BIAS;

        // Compute final value: mantissa * 2^actualExponent
        double value = mantissa * Math.pow(2, actualExponent);

        return negative ? -value : value;
    }

    /**
     * Convert Java double to 7-byte Microsoft Binary Format.
     *
     * @param value double value to convert
     * @param mbf byte array to store MBF data
     * @param offset starting offset in the array
     */
    public static void doubleToMbf(double value, byte[] mbf, int offset) {
        // Handle zero special case (including -0.0)
        if (value == 0.0) {
            for (int i = 0; i < 7; i++) {
                mbf[offset + i] = 0;
            }
            return;
        }

        // Extract sign and work with absolute value
        boolean negative = value < 0;
        double absValue = Math.abs(value);

        // Get IEEE 754 components using bit manipulation
        long bits = Double.doubleToLongBits(absValue);
        int ieeeExponent = (int)((bits >>> 52) & 0x7FF);
        long ieeeMantissa = bits & 0xFFFFFFFFFFFFFL;

        // Convert IEEE exponent to MBF exponent
        // IEEE bias is 1023, MBF bias is 129
        // IEEE mantissa is 52 bits, MBF mantissa is 32 bits
        int mbfExponent = ieeeExponent - IEEE_EXPONENT_BIAS + MBF_EXPONENT_BIAS;

        // Check for overflow/underflow
        if (mbfExponent > 255) {
            // Overflow - set to max value
            mbfExponent = 255;
            ieeeMantissa = 0xFFFFFFFFFFFFFL;
        } else if (mbfExponent < 1) {
            // Underflow - set to zero
            for (int i = 0; i < 7; i++) {
                mbf[offset + i] = 0;
            }
            return;
        }

        // Convert 52-bit IEEE mantissa to 31-bit MBF mantissa
        // Take the top 31 bits from the 52-bit IEEE mantissa
        long mbfMantissa = ieeeMantissa >>> 21;

        // Write exponent (byte 0)
        mbf[offset] = (byte)(mbfExponent & 0xFF);

        // Write mantissa (bytes 1-4)
        // The mantissa is stored in bytes 1-4, with the implicit 1 removed
        // Byte 1: top 7 bits (bit 7 is 0 because implicit 1 is removed)
        // Bytes 2-4: remaining 24 bits
        mbf[offset + 1] = (byte)((mbfMantissa >>> 24) & 0xFF);
        mbf[offset + 2] = (byte)((mbfMantissa >>> 16) & 0xFF);
        mbf[offset + 3] = (byte)((mbfMantissa >>> 8) & 0xFF);
        mbf[offset + 4] = (byte)(mbfMantissa & 0xFF);

        // Write sign bit in byte 5, bit 7
        mbf[offset + 5] = (byte)(negative ? 0x80 : 0x00);

        // Extension byte (byte 6) - not used in standard conversion
        mbf[offset + 6] = 0;
    }

    /**
     * Check if a double value exceeds the maximum representable MBF value.
     *
     * @param value value to check
     * @return true if value would overflow in MBF format
     */
    public static boolean isOverflow(double value) {
        double absValue = Math.abs(value);
        return absValue > MBF_MAX;
    }

    /**
     * Check if a double value is smaller than the minimum positive MBF value
     * and would underflow to zero.
     *
     * @param value value to check
     * @return true if value would underflow to zero in MBF format
     */
    public static boolean isUnderflow(double value) {
        if (value == 0.0) {
            return false; // Zero is not underflow
        }
        double absValue = Math.abs(value);
        return absValue < MBF_MIN_POSITIVE && absValue != 0.0;
    }
}
