package jace.terminal;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for NumberParser utility class.
 * Validates $ prefix support for hexadecimal parsing across all terminal commands.
 */
public class NumberParserTest {

    // ========== parseNumber() tests ==========

    @Test
    public void testParseNumber_DecimalDefault() {
        assertEquals(255, NumberParser.parseNumber("255"));
        assertEquals(100, NumberParser.parseNumber("100"));
        assertEquals(0, NumberParser.parseNumber("0"));
    }

    @Test
    public void testParseNumber_HexWithDollarPrefix() {
        assertEquals(255, NumberParser.parseNumber("$FF"));
        assertEquals(255, NumberParser.parseNumber("$ff"));
        assertEquals(2048, NumberParser.parseNumber("$800"));
        assertEquals(0, NumberParser.parseNumber("$0"));
        assertEquals(65535, NumberParser.parseNumber("$FFFF"));
    }

    @Test
    public void testParseNumber_HexWithExplicitRadix() {
        assertEquals(255, NumberParser.parseNumber("FF", 16));
        assertEquals(2048, NumberParser.parseNumber("800", 16));
    }

    @Test
    public void testParseNumber_DecimalWithExplicitRadix() {
        assertEquals(255, NumberParser.parseNumber("255", 10));
        assertEquals(800, NumberParser.parseNumber("800", 10));
    }

    @Test
    public void testParseNumber_NegativeValues() {
        assertEquals(-255, NumberParser.parseNumber("-255"));
        assertEquals(-255, NumberParser.parseNumber("-$FF"));
        assertEquals(-2048, NumberParser.parseNumber("-$800"));
    }

    @Test
    public void testParseNumber_Whitespace() {
        assertEquals(255, NumberParser.parseNumber("  255  "));
        assertEquals(255, NumberParser.parseNumber("  $FF  "));
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_Empty() {
        NumberParser.parseNumber("");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_Null() {
        NumberParser.parseNumber(null);
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_OnlyWhitespace() {
        NumberParser.parseNumber("   ");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_DollarAlone() {
        NumberParser.parseNumber("$");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_DoubleDollar() {
        NumberParser.parseNumber("$$100");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_InvalidHex() {
        NumberParser.parseNumber("$zz");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_InvalidDecimal() {
        NumberParser.parseNumber("12x34");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNumber_NegativeDollarAlone() {
        NumberParser.parseNumber("-$");
    }

    // ========== parseByteValue() tests ==========

    @Test
    public void testParseByteValue_DecimalDefault() {
        assertEquals(255, NumberParser.parseByteValue("255"));
        assertEquals(100, NumberParser.parseByteValue("100"));
        assertEquals(0, NumberParser.parseByteValue("0"));
    }

    @Test
    public void testParseByteValue_HexWithDollarPrefix() {
        assertEquals(0xFF, NumberParser.parseByteValue("$FF"));
        assertEquals(0xff, NumberParser.parseByteValue("$ff"));
        assertEquals(0x80, NumberParser.parseByteValue("$80"));
        assertEquals(0, NumberParser.parseByteValue("$0"));
    }

    @Test
    public void testParseByteValue_HexWith0xPrefix() {
        // Preserve existing 0x support
        assertEquals(0xFF, NumberParser.parseByteValue("0xFF"));
        assertEquals(0xff, NumberParser.parseByteValue("0xff"));
        assertEquals(0x80, NumberParser.parseByteValue("0x80"));
    }

    @Test
    public void testParseByteValue_Overflow() {
        // Values > 255 should be masked to byte range
        assertEquals(0xFF, NumberParser.parseByteValue("$1FF"));
        assertEquals(0x00, NumberParser.parseByteValue("$100"));
        assertEquals(0x34, NumberParser.parseByteValue("$1234"));
    }

    @Test
    public void testParseByteValue_Whitespace() {
        assertEquals(255, NumberParser.parseByteValue("  255  "));
        assertEquals(255, NumberParser.parseByteValue("  $FF  "));
    }

    @Test(expected = NumberFormatException.class)
    public void testParseByteValue_Empty() {
        NumberParser.parseByteValue("");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseByteValue_Null() {
        NumberParser.parseByteValue(null);
    }

    @Test(expected = NumberFormatException.class)
    public void testParseByteValue_DollarAlone() {
        NumberParser.parseByteValue("$");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseByteValue_0xAlone() {
        NumberParser.parseByteValue("0x");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseByteValue_InvalidHex() {
        NumberParser.parseByteValue("$zz");
    }

    // ========== parseWordValue() tests ==========

    @Test
    public void testParseWordValue_DecimalDefault() {
        assertEquals(65535, NumberParser.parseWordValue("65535"));
        assertEquals(2048, NumberParser.parseWordValue("2048"));
        assertEquals(0, NumberParser.parseWordValue("0"));
    }

    @Test
    public void testParseWordValue_HexWithDollarPrefix() {
        assertEquals(0xFFFF, NumberParser.parseWordValue("$FFFF"));
        assertEquals(0xffff, NumberParser.parseWordValue("$ffff"));
        assertEquals(0x800, NumberParser.parseWordValue("$800"));
        assertEquals(0, NumberParser.parseWordValue("$0"));
    }

    @Test
    public void testParseWordValue_HexWith0xPrefix() {
        // Preserve existing 0x support
        assertEquals(0xFFFF, NumberParser.parseWordValue("0xFFFF"));
        assertEquals(0xffff, NumberParser.parseWordValue("0xffff"));
        assertEquals(0x800, NumberParser.parseWordValue("0x800"));
    }

    @Test
    public void testParseWordValue_Overflow() {
        // Values > 65535 should be masked to word range
        assertEquals(0xFFFF, NumberParser.parseWordValue("$1FFFF"));
        assertEquals(0x0000, NumberParser.parseWordValue("$10000"));
        assertEquals(0x2345, NumberParser.parseWordValue("$12345"));
    }

    @Test
    public void testParseWordValue_Whitespace() {
        assertEquals(2048, NumberParser.parseWordValue("  2048  "));
        assertEquals(2048, NumberParser.parseWordValue("  $800  "));
    }

    @Test(expected = NumberFormatException.class)
    public void testParseWordValue_Empty() {
        NumberParser.parseWordValue("");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseWordValue_Null() {
        NumberParser.parseWordValue(null);
    }

    @Test(expected = NumberFormatException.class)
    public void testParseWordValue_DollarAlone() {
        NumberParser.parseWordValue("$");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseWordValue_InvalidHex() {
        NumberParser.parseWordValue("$zzz");
    }

    // ========== Case insensitivity tests ==========

    @Test
    public void testCaseInsensitivity() {
        // $ prefix with mixed case hex
        assertEquals(0xABCD, NumberParser.parseNumber("$ABCD"));
        assertEquals(0xABCD, NumberParser.parseNumber("$abcd"));
        assertEquals(0xABCD, NumberParser.parseNumber("$AbCd"));

        // 0x prefix with mixed case
        assertEquals(0xAB, NumberParser.parseByteValue("0xAB"));
        assertEquals(0xAB, NumberParser.parseByteValue("0xab"));
        assertEquals(0xAB, NumberParser.parseByteValue("0xAb"));

        // 0X (capital X) prefix
        assertEquals(0xCD, NumberParser.parseByteValue("0XCD"));
        assertEquals(0xCD, NumberParser.parseByteValue("0Xcd"));
    }
}
