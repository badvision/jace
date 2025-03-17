package jace.core;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the Keyboard class functions
 */
public class KeyboardTest {

    /**
     * Test class for accessing protected methods in Keyboard
     */
    static class TestableKeyboard extends Keyboard {
        public char testFixShiftedChar(char c) {
            return fixShiftedChar(c);
        }
    }
    
    /**
     * Test of fixShiftedChar method.
     * Tests character shifts with various inputs.
     */
    @Test
    public void testFixShiftedChar() {
        TestableKeyboard keyboard = new TestableKeyboard();
        
        // Test lowercase to uppercase conversion
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String expectedUppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        
        for (int i = 0; i < lowercase.length(); i++) {
            char input = lowercase.charAt(i);
            char expected = expectedUppercase.charAt(i);
            char result = keyboard.testFixShiftedChar(input);
            assertEquals("Lowercase '" + input + "' should convert to uppercase '" + expected + "'", 
                    expected, result);
        }
        
        // Test special character shifts
        String unshifted = "0123456789-=[]\\;',./`";
        String expected = ")!@#$%^&*(_+{}|:\"<>?~";
        
        for (int i = 0; i < unshifted.length(); i++) {
            char input = unshifted.charAt(i);
            char expectedChar = expected.charAt(i);
            char result = keyboard.testFixShiftedChar(input);
            assertEquals("Character '" + input + "' should shift to '" + expectedChar + "'", 
                    expectedChar, result);
        }
        
        // Test characters that don't change when shifted
        String unchanged = "ABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*()_+{}|:\"<>?~ ";
        
        for (int i = 0; i < unchanged.length(); i++) {
            char input = unchanged.charAt(i);
            char result = keyboard.testFixShiftedChar(input);
            assertEquals("Character '" + input + "' should remain unchanged when shifted", 
                    input, result);
        }
        
        // Test numeric keypad and arrow keys
        char[] specialInputs = {8, 9, 10, 11, 13, 21, 27, 127};
        
        for (char input : specialInputs) {
            char result = keyboard.testFixShiftedChar(input);
            assertEquals("Special character code " + (int)input + " should remain unchanged", 
                    input, result);
        }
    }

    /**
     * Test boundary conditions for fixShiftedChar
     */
    @Test
    public void testFixShiftedCharBoundary() {
        TestableKeyboard keyboard = new TestableKeyboard();
        
        // Test boundary values
        assertEquals("Char before 'a' should shift to tilde", 
                '~', keyboard.testFixShiftedChar('`'));
        assertEquals("Char after 'z' should remain unchanged", 
                '{', keyboard.testFixShiftedChar('{'));
                
        // Test extreme values
        assertEquals("Char 0 should remain unchanged", 
                (char)0, keyboard.testFixShiftedChar((char)0));
        assertEquals("Max char should remain unchanged", 
                Character.MAX_VALUE, keyboard.testFixShiftedChar(Character.MAX_VALUE));
                
        // Test extended ASCII values outside the normal shift range
        for (char c = 128; c < 256; c++) {
            assertEquals("Extended ASCII char " + (int)c + " should remain unchanged", 
                    c, keyboard.testFixShiftedChar(c));
        }
    }
} 