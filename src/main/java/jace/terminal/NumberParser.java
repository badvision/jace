/**
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.terminal;

/**
 * Utility class for parsing numeric arguments in terminal commands.
 * Supports the Apple II / Wozniak monitor convention of $ prefix for hex.
 */
public class NumberParser {

    /**
     * Parse a number with optional $ prefix for hexadecimal.
     * If no prefix, uses the specified default radix.
     *
     * @param value the string to parse (e.g., "255", "$FF", "ff")
     * @param defaultRadix the radix to use when no prefix is present (10 or 16)
     * @return the parsed integer value
     * @throws NumberFormatException if the string is malformed
     */
    public static int parseNumber(String value, int defaultRadix) throws NumberFormatException {
        if (value == null || value.isEmpty()) {
            throw new NumberFormatException("empty value");
        }

        // Normalize: trim whitespace
        value = value.trim();
        if (value.isEmpty()) {
            throw new NumberFormatException("value is only whitespace");
        }

        // Handle $ prefix (hex)
        if (value.startsWith("$")) {
            if (value.length() == 1) {
                throw new NumberFormatException("'$' with no digits");
            }
            String hexPart = value.substring(1);
            if (hexPart.startsWith("$")) {
                throw new NumberFormatException("double '$' prefix: " + value);
            }
            return Integer.parseInt(hexPart, 16);
        }

        // Handle negative forms
        if (value.startsWith("-$")) {
            if (value.length() == 2) {
                throw new NumberFormatException("'-$' with no digits");
            }
            return -Integer.parseInt(value.substring(2), 16);
        }

        // No prefix: use default radix
        return Integer.parseInt(value, defaultRadix);
    }

    /**
     * Parse a number with default radix of decimal.
     *
     * @param value the string to parse
     * @return the parsed integer value
     * @throws NumberFormatException if the string is malformed
     */
    public static int parseNumber(String value) throws NumberFormatException {
        return parseNumber(value, 10);
    }

    /**
     * Parse a byte value (0-255) with optional $ prefix for hexadecimal.
     * Preserves backward compatibility with existing 0x prefix support.
     *
     * @param value the string to parse
     * @return the parsed byte value (0-255)
     * @throws NumberFormatException if the string is malformed
     */
    public static int parseByteValue(String value) throws NumberFormatException {
        if (value == null || value.isEmpty()) {
            throw new NumberFormatException("empty value");
        }

        value = value.trim();
        if (value.isEmpty()) {
            throw new NumberFormatException("value is only whitespace");
        }

        // Handle $ prefix (hex)
        if (value.startsWith("$")) {
            if (value.length() == 1) {
                throw new NumberFormatException("'$' with no digits");
            }
            return Integer.parseInt(value.substring(1), 16) & 0xFF;
        }

        // Preserve existing 0x support
        if (value.startsWith("0x") || value.startsWith("0X")) {
            if (value.length() == 2) {
                throw new NumberFormatException("'0x' with no digits");
            }
            return Integer.parseInt(value.substring(2), 16) & 0xFF;
        }

        // No prefix: decimal
        return Integer.parseInt(value) & 0xFF;
    }

    /**
     * Parse a word value (0-65535) with optional $ prefix for hexadecimal.
     * Preserves backward compatibility with existing 0x prefix support.
     *
     * @param value the string to parse
     * @return the parsed word value (0-65535)
     * @throws NumberFormatException if the string is malformed
     */
    public static int parseWordValue(String value) throws NumberFormatException {
        if (value == null || value.isEmpty()) {
            throw new NumberFormatException("empty value");
        }

        value = value.trim();
        if (value.isEmpty()) {
            throw new NumberFormatException("value is only whitespace");
        }

        // Handle $ prefix (hex)
        if (value.startsWith("$")) {
            if (value.length() == 1) {
                throw new NumberFormatException("'$' with no digits");
            }
            return Integer.parseInt(value.substring(1), 16) & 0xFFFF;
        }

        // Preserve existing 0x support
        if (value.startsWith("0x") || value.startsWith("0X")) {
            if (value.length() == 2) {
                throw new NumberFormatException("'0x' with no digits");
            }
            return Integer.parseInt(value.substring(2), 16) & 0xFFFF;
        }

        // No prefix: decimal
        return Integer.parseInt(value) & 0xFFFF;
    }
}
