package jace.ide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jace.apple2e.MOS65C02;

/**
 * Syntax definition for ACME 65C02 assembly source lines.
 * Mnemonic set is derived from MOS65C02.COMMAND enum names.
 * Pure function — no JavaFX dependencies, safe to call from any thread.
 *
 * Syntax rules:
 *  - ';' to EOL → COMMENT
 *  - Lines beginning with '!' or '*=' → DIRECTIVE
 *  - label (identifier ending ':') at start → LABEL
 *  - 3–5 letter mnemonic matching COMMAND → KEYWORD
 *  - '$hex' or '%binary' or decimal digits → NUMBER
 *  - "strings" → STRING
 *  - Everything else → DEFAULT
 */
public class MOS65C02AssemblySyntax implements SyntaxDefinition {

    /**
     * Canonical set of mnemonic strings (upper-case) derived from COMMAND enum.
     * Internal variants like ASL_A, NOP_SPECIAL are normalised to their 3-letter bases.
     * Bit-manipulation variants BBR0–BBR7 etc. are kept as-is (e.g. "BBR0").
     */
    private static final Set<String> MNEMONICS;

    static {
        Set<String> set = new TreeSet<>();
        for (MOS65C02.COMMAND cmd : MOS65C02.COMMAND.values()) {
            String name = cmd.name();
            // Strip "_A" suffix used for accumulator-mode variants (ASL_A → ASL)
            if (name.endsWith("_A")) {
                set.add(name.substring(0, name.length() - 2));
            // Strip "_SPECIAL" suffix (NOP_SPECIAL → NOP)
            } else if (name.endsWith("_SPECIAL")) {
                set.add(name.substring(0, name.length() - 8));
            } else {
                set.add(name);
            }
        }
        MNEMONICS = Collections.unmodifiableSet(set);
    }

    @Override
    public String getName() {
        return "65C02 Assembly";
    }

    /**
     * Tokenizes a single assembly source line.
     *
     * @param line       source line text (may have leading whitespace)
     * @param lineNumber 0-based line number (unused for highlighting, included for interface)
     * @return list of non-overlapping StyleSpan in ascending start order
     */
    @Override
    public List<StyleSpan> tokenize(String line, int lineNumber) {
        List<StyleSpan> spans = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return spans;
        }

        int len = line.length();
        int pos = 0;

        // Skip leading whitespace (track offset)
        int stripped = 0;
        while (stripped < len && Character.isWhitespace(line.charAt(stripped))) {
            stripped++;
        }

        // Whole-line comment: first non-whitespace is ';'
        if (stripped < len && line.charAt(stripped) == ';') {
            spans.add(new StyleSpan(0, len, TokenStyle.COMMENT));
            return spans;
        }

        // ACME directive: line (after whitespace) starts with '!' or is '*='
        if (stripped < len && line.charAt(stripped) == '!') {
            // Directive token extends to first whitespace or end of line
            int dirEnd = stripped + 1;
            while (dirEnd < len && !Character.isWhitespace(line.charAt(dirEnd))) {
                dirEnd++;
            }
            if (stripped > 0) {
                spans.add(new StyleSpan(0, stripped, TokenStyle.DEFAULT));
            }
            spans.add(new StyleSpan(stripped, dirEnd, TokenStyle.DIRECTIVE));
            pos = dirEnd;
            // Fall through to handle operands after directive
            pos = scanOperandsAndComment(line, pos, len, spans);
            return spans;
        }

        // '*=' origin directive
        if (stripped < len && line.charAt(stripped) == '*'
                && stripped + 1 < len && line.charAt(stripped + 1) == '=') {
            if (stripped > 0) {
                spans.add(new StyleSpan(0, stripped, TokenStyle.DEFAULT));
            }
            spans.add(new StyleSpan(stripped, stripped + 2, TokenStyle.DIRECTIVE));
            pos = stripped + 2;
            pos = scanOperandsAndComment(line, pos, len, spans);
            return spans;
        }

        // Potential label at line start (after optional whitespace)
        // A label is an identifier followed immediately by ':'
        pos = stripped;
        int identEnd = pos;
        while (identEnd < len
                && (Character.isLetterOrDigit(line.charAt(identEnd))
                    || line.charAt(identEnd) == '_')) {
            identEnd++;
        }
        if (identEnd > pos && identEnd < len && line.charAt(identEnd) == ':') {
            // Emit leading whitespace as DEFAULT if any
            if (stripped > 0) {
                spans.add(new StyleSpan(0, stripped, TokenStyle.DEFAULT));
            }
            spans.add(new StyleSpan(pos, identEnd + 1, TokenStyle.LABEL));
            pos = identEnd + 1;
            // Skip whitespace after label
            while (pos < len && Character.isWhitespace(line.charAt(pos))) {
                pos++;
            }
        } else {
            // No label — emit leading whitespace as DEFAULT if any
            if (stripped > 0) {
                spans.add(new StyleSpan(0, stripped, TokenStyle.DEFAULT));
            }
            pos = stripped;
        }

        // ACME local labels: '+' or '-' sequences (e.g. '++', '-')
        if (pos < len && (line.charAt(pos) == '+' || line.charAt(pos) == '-')) {
            int localEnd = pos;
            char localChar = line.charAt(pos);
            while (localEnd < len && line.charAt(localEnd) == localChar) {
                localEnd++;
            }
            spans.add(new StyleSpan(pos, localEnd, TokenStyle.LABEL));
            pos = localEnd;
            while (pos < len && Character.isWhitespace(line.charAt(pos))) {
                pos++;
            }
        }

        // Mnemonic: 1–6 uppercase/lowercase letters+digits matching a known COMMAND
        int mnemonicStart = pos;
        int mnemonicEnd = pos;
        while (mnemonicEnd < len
                && (Character.isLetter(line.charAt(mnemonicEnd))
                    || Character.isDigit(line.charAt(mnemonicEnd)))) {
            mnemonicEnd++;
        }
        if (mnemonicEnd > mnemonicStart) {
            String candidate = line.substring(mnemonicStart, mnemonicEnd).toUpperCase();
            if (MNEMONICS.contains(candidate)) {
                if (mnemonicStart > pos) {
                    spans.add(new StyleSpan(pos, mnemonicStart, TokenStyle.DEFAULT));
                }
                spans.add(new StyleSpan(mnemonicStart, mnemonicEnd, TokenStyle.KEYWORD));
                pos = mnemonicEnd;
            }
            // If not a mnemonic, fall through and let scanOperandsAndComment handle it
        }

        // Operands, numbers, strings, inline comments
        pos = scanOperandsAndComment(line, pos, len, spans);

        return spans;
    }

    /**
     * Scans the operand portion of a line after the mnemonic has been consumed.
     * Handles: hex numbers ($xx), binary numbers (%bb), decimal numbers, string
     * literals, inline comments (;), and DEFAULT text for everything else.
     *
     * @return position after all spans have been emitted (== len)
     */
    private static int scanOperandsAndComment(
            String line, int pos, int len, List<StyleSpan> spans) {
        int defaultStart = pos;

        while (pos < len) {
            char c = line.charAt(pos);

            // Inline comment
            if (c == ';') {
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, len, TokenStyle.COMMENT));
                return len;
            }

            // Hex number: $[0-9A-Fa-f]+
            if (c == '$') {
                int hexEnd = pos + 1;
                while (hexEnd < len && isHexDigit(line.charAt(hexEnd))) {
                    hexEnd++;
                }
                if (hexEnd > pos + 1) {
                    flushDefault(spans, defaultStart, pos);
                    spans.add(new StyleSpan(pos, hexEnd, TokenStyle.NUMBER));
                    pos = hexEnd;
                    defaultStart = pos;
                    continue;
                }
            }

            // Binary number: %[01]+
            if (c == '%') {
                int binEnd = pos + 1;
                while (binEnd < len
                        && (line.charAt(binEnd) == '0' || line.charAt(binEnd) == '1')) {
                    binEnd++;
                }
                if (binEnd > pos + 1) {
                    flushDefault(spans, defaultStart, pos);
                    spans.add(new StyleSpan(pos, binEnd, TokenStyle.NUMBER));
                    pos = binEnd;
                    defaultStart = pos;
                    continue;
                }
            }

            // Decimal number (stand-alone digit sequence)
            if (Character.isDigit(c)) {
                int numEnd = pos;
                while (numEnd < len && Character.isDigit(line.charAt(numEnd))) {
                    numEnd++;
                }
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, numEnd, TokenStyle.NUMBER));
                pos = numEnd;
                defaultStart = pos;
                continue;
            }

            // String literal
            if (c == '"') {
                int strEnd = pos + 1;
                while (strEnd < len && line.charAt(strEnd) != '"') {
                    strEnd++;
                }
                if (strEnd < len) {
                    strEnd++; // include closing '"'
                }
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, strEnd, TokenStyle.STRING));
                pos = strEnd;
                defaultStart = pos;
                continue;
            }

            pos++;
        }

        flushDefault(spans, defaultStart, len);
        return len;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'A' && c <= 'F')
                || (c >= 'a' && c <= 'f');
    }

    private static void flushDefault(List<StyleSpan> spans, int start, int end) {
        if (start < end) {
            spans.add(new StyleSpan(start, end, TokenStyle.DEFAULT));
        }
    }
}
