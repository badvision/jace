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
 *
 * Label forms (ACME):
 *   global:    identifier ':'            (anywhere on line)
 *              identifier at column 0    (no colon — non-mnemonic ident at col 0)
 *              identifier '=' value      (assignment / equate at col 0)
 *   local:     '.identifier' at col 0    (accessible within zone/macro)
 *   anonymous: sequences of '-' or '+'  (already handled)
 *
 * Number formats: $hex  0xhex  &octal  %binary  decimal
 *   Binary may use '.' and '#' as bit substitutes (%..##..##)
 *
 * String formats: "..." and '.'  (single char literal)
 *
 * Pseudo-opcodes: '!' prefix  (ACME directives)
 * Comments:       ';' to EOL
 * Statement sep:  ':'  (multiple statements per line — each sub-statement re-scanned)
 */
public class MOS65C02AssemblySyntax implements SyntaxDefinition {

    private static final Set<String> MNEMONICS;

    static {
        Set<String> set = new TreeSet<>();
        for (MOS65C02.COMMAND cmd : MOS65C02.COMMAND.values()) {
            String name = cmd.name();
            if (name.endsWith("_A")) {
                set.add(name.substring(0, name.length() - 2));
            } else if (name.endsWith("_SPECIAL")) {
                set.add(name.substring(0, name.length() - 8));
            } else {
                set.add(name);
            }
        }
        MNEMONICS = Collections.unmodifiableSet(set);
    }

    @Override
    public String getName() { return "65C02 Assembly"; }

    @Override
    public List<StyleSpan> tokenize(String line, int lineNumber) {
        List<StyleSpan> spans = new ArrayList<>();
        if (line == null || line.isEmpty()) return spans;
        tokenizeStatement(line, 0, line.length(), true, spans);
        return spans;
    }

    /**
     * Tokenize one statement within [start, end). isFirstStatement controls whether
     * column-0 label detection applies. ACME allows ':' to separate multiple
     * statements on one line; we recurse for each.
     */
    private void tokenizeStatement(String line, int start, int end,
                                   boolean isFirstStatement, List<StyleSpan> spans) {
        if (start >= end) return;
        int len = end;

        // Skip leading whitespace
        int pos = start;
        while (pos < len && Character.isWhitespace(line.charAt(pos))) pos++;

        if (pos >= len) return;

        char first = line.charAt(pos);

        // Comment: ';' to end
        if (first == ';') {
            spans.add(new StyleSpan(pos, len, TokenStyle.COMMENT));
            return;
        }

        // ACME directive: '!'
        if (first == '!') {
            int dirEnd = pos + 1;
            while (dirEnd < len && !Character.isWhitespace(line.charAt(dirEnd))
                    && line.charAt(dirEnd) != ';') {
                dirEnd++;
            }
            if (start < pos) spans.add(new StyleSpan(start, pos, TokenStyle.DEFAULT));
            spans.add(new StyleSpan(pos, dirEnd, TokenStyle.DIRECTIVE));
            int rest = scanOperandsAndComment(line, dirEnd, len, spans);
            maybeRecurse(line, rest, len, spans);
            return;
        }

        // '*=' origin
        if (first == '*' && pos + 1 < len && line.charAt(pos + 1) == '=') {
            if (start < pos) spans.add(new StyleSpan(start, pos, TokenStyle.DEFAULT));
            spans.add(new StyleSpan(pos, pos + 2, TokenStyle.DIRECTIVE));
            int rest = scanOperandsAndComment(line, pos + 2, len, spans);
            maybeRecurse(line, rest, len, spans);
            return;
        }

        // Local label starting with '.' (e.g. ".loop" or ".string")
        if (first == '.') {
            int identEnd = pos + 1;
            while (identEnd < len && isLabelChar(line.charAt(identEnd))) identEnd++;
            if (identEnd > pos + 1) {
                // It's a local label definition (at col-0 style) or reference
                boolean isLabelDef = isFirstStatement && (start == pos) /* col 0 area */
                        || (identEnd < len && line.charAt(identEnd) == ':');
                boolean hasColon = identEnd < len && line.charAt(identEnd) == ':';
                if (isLabelDef || hasColon) {
                    if (start < pos) spans.add(new StyleSpan(start, pos, TokenStyle.DEFAULT));
                    int labelEnd = hasColon ? identEnd + 1 : identEnd;
                    spans.add(new StyleSpan(pos, labelEnd, TokenStyle.LABEL));
                    pos = labelEnd;
                    while (pos < len && Character.isWhitespace(line.charAt(pos))) pos++;
                    tokenizeStatement(line, pos, len, false, spans);
                    return;
                }
                // Otherwise it's a local label reference in an operand — fall through
            }
        }

        // Anonymous labels: sequences of '+' or '-'
        if (first == '+' || first == '-') {
            int anonEnd = pos;
            while (anonEnd < len && line.charAt(anonEnd) == first) anonEnd++;
            // Only treat as label if followed by whitespace, EOL, or comment
            if (anonEnd < len && (Character.isWhitespace(line.charAt(anonEnd))
                    || line.charAt(anonEnd) == ';') || anonEnd == len) {
                if (start < pos) spans.add(new StyleSpan(start, pos, TokenStyle.DEFAULT));
                spans.add(new StyleSpan(pos, anonEnd, TokenStyle.LABEL));
                pos = anonEnd;
                while (pos < len && Character.isWhitespace(line.charAt(pos))) pos++;
                tokenizeStatement(line, pos, len, false, spans);
                return;
            }
            // Otherwise it's an operator in an expression — fall through to operands
        }

        // Try to read an identifier
        int identStart = pos;
        int identEnd = pos;
        while (identEnd < len && isLabelChar(line.charAt(identEnd))) identEnd++;

        if (identEnd > identStart) {
            String word = line.substring(identStart, identEnd);

            // Check for label with colon
            if (identEnd < len && line.charAt(identEnd) == ':') {
                if (start < pos) spans.add(new StyleSpan(start, pos, TokenStyle.DEFAULT));
                spans.add(new StyleSpan(identStart, identEnd + 1, TokenStyle.LABEL));
                pos = identEnd + 1;
                while (pos < len && Character.isWhitespace(line.charAt(pos))) pos++;
                tokenizeStatement(line, pos, len, false, spans);
                return;
            }

            // Check for label assignment: identifier '=' value (at col 0 in first stmt)
            int afterIdent = identEnd;
            while (afterIdent < len && line.charAt(afterIdent) == ' ') afterIdent++;
            if (isFirstStatement && identStart == (start == pos ? start : pos)
                    && afterIdent < len && line.charAt(afterIdent) == '='
                    && (afterIdent + 1 >= len || line.charAt(afterIdent + 1) != '=')) {
                if (start < identStart) spans.add(new StyleSpan(start, identStart, TokenStyle.DEFAULT));
                spans.add(new StyleSpan(identStart, identEnd, TokenStyle.LABEL));
                int rest = scanOperandsAndComment(line, identEnd, len, spans);
                maybeRecurse(line, rest, len, spans);
                return;
            }

            // Column-0 non-mnemonic identifier is a label (ACME implicit label)
            boolean col0 = isFirstStatement && (identStart == start);
            if (col0 && !MNEMONICS.contains(word.toUpperCase())) {
                if (start < identStart) spans.add(new StyleSpan(start, identStart, TokenStyle.DEFAULT));
                spans.add(new StyleSpan(identStart, identEnd, TokenStyle.LABEL));
                pos = identEnd;
                while (pos < len && Character.isWhitespace(line.charAt(pos))) pos++;
                tokenizeStatement(line, pos, len, false, spans);
                return;
            }

            // Check if it's a mnemonic
            if (MNEMONICS.contains(word.toUpperCase())) {
                if (start < identStart) spans.add(new StyleSpan(start, identStart, TokenStyle.DEFAULT));
                spans.add(new StyleSpan(identStart, identEnd, TokenStyle.KEYWORD));
                int rest = scanOperandsAndComment(line, identEnd, len, spans);
                maybeRecurse(line, rest, len, spans);
                return;
            }
        }

        // Nothing special — scan as operands/comment
        int rest = scanOperandsAndComment(line, start, len, spans);
        maybeRecurse(line, rest, len, spans);
    }

    /** If the scan stopped at a ':' statement separator, recurse for the next statement. */
    private void maybeRecurse(String line, int pos, int len, List<StyleSpan> spans) {
        if (pos < len && line.charAt(pos) == ':') {
            spans.add(new StyleSpan(pos, pos + 1, TokenStyle.DEFAULT));
            tokenizeStatement(line, pos + 1, len, false, spans);
        }
    }

    /**
     * Scans the operand portion of a line, emitting NUMBER, STRING, COMMENT, LABEL
     * (for label references starting with '.'), and DEFAULT spans.
     * Stops and returns position at a ':' statement separator (does not consume it).
     */
    private static int scanOperandsAndComment(
            String line, int pos, int len, List<StyleSpan> spans) {
        int defaultStart = pos;

        while (pos < len) {
            char c = line.charAt(pos);

            // Statement separator — caller decides whether to recurse
            if (c == ':') {
                flushDefault(spans, defaultStart, pos);
                return pos;
            }

            // Comment
            if (c == ';') {
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, len, TokenStyle.COMMENT));
                return len;
            }

            // Local label reference: '.identifier'
            if (c == '.' && pos + 1 < len && isLabelChar(line.charAt(pos + 1))) {
                int refEnd = pos + 1;
                while (refEnd < len && isLabelChar(line.charAt(refEnd))) refEnd++;
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, refEnd, TokenStyle.LABEL));
                pos = refEnd;
                defaultStart = pos;
                continue;
            }

            // Hex: $[0-9A-Fa-f]+
            if (c == '$') {
                int hexEnd = pos + 1;
                while (hexEnd < len && isHexDigit(line.charAt(hexEnd))) hexEnd++;
                if (hexEnd > pos + 1) {
                    flushDefault(spans, defaultStart, pos);
                    spans.add(new StyleSpan(pos, hexEnd, TokenStyle.NUMBER));
                    pos = hexEnd; defaultStart = pos;
                    continue;
                }
            }

            // Hex: 0x[0-9A-Fa-f]+
            if (c == '0' && pos + 1 < len && line.charAt(pos + 1) == 'x') {
                int hexEnd = pos + 2;
                while (hexEnd < len && isHexDigit(line.charAt(hexEnd))) hexEnd++;
                if (hexEnd > pos + 2) {
                    flushDefault(spans, defaultStart, pos);
                    spans.add(new StyleSpan(pos, hexEnd, TokenStyle.NUMBER));
                    pos = hexEnd; defaultStart = pos;
                    continue;
                }
            }

            // Octal: &[0-7]+
            if (c == '&') {
                int octEnd = pos + 1;
                while (octEnd < len && line.charAt(octEnd) >= '0' && line.charAt(octEnd) <= '7') octEnd++;
                if (octEnd > pos + 1) {
                    flushDefault(spans, defaultStart, pos);
                    spans.add(new StyleSpan(pos, octEnd, TokenStyle.NUMBER));
                    pos = octEnd; defaultStart = pos;
                    continue;
                }
            }

            // Binary: %[01.#]+  (ACME allows '.' and '#' as bit substitutes)
            if (c == '%') {
                int binEnd = pos + 1;
                while (binEnd < len && isBinaryDigit(line.charAt(binEnd))) binEnd++;
                if (binEnd > pos + 1) {
                    flushDefault(spans, defaultStart, pos);
                    spans.add(new StyleSpan(pos, binEnd, TokenStyle.NUMBER));
                    pos = binEnd; defaultStart = pos;
                    continue;
                }
            }

            // Decimal
            if (Character.isDigit(c)) {
                int numEnd = pos;
                while (numEnd < len && Character.isDigit(line.charAt(numEnd))) numEnd++;
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, numEnd, TokenStyle.NUMBER));
                pos = numEnd; defaultStart = pos;
                continue;
            }

            // Double-quoted string
            if (c == '"') {
                int strEnd = pos + 1;
                while (strEnd < len && line.charAt(strEnd) != '"') strEnd++;
                if (strEnd < len) strEnd++;
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, strEnd, TokenStyle.STRING));
                pos = strEnd; defaultStart = pos;
                continue;
            }

            // Single-quoted character literal: 'x'
            if (c == '\'' && pos + 2 < len && line.charAt(pos + 2) == '\'') {
                flushDefault(spans, defaultStart, pos);
                spans.add(new StyleSpan(pos, pos + 3, TokenStyle.STRING));
                pos += 3; defaultStart = pos;
                continue;
            }

            pos++;
        }

        flushDefault(spans, defaultStart, len);
        return len;
    }

    private static boolean isLabelChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || (int) c > 127;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    private static boolean isBinaryDigit(char c) {
        return c == '0' || c == '1' || c == '.' || c == '#';
    }

    private static void flushDefault(List<StyleSpan> spans, int start, int end) {
        if (start < end) spans.add(new StyleSpan(start, end, TokenStyle.DEFAULT));
    }
}
