package jace.ide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jace.applesoft.Command;

/**
 * Syntax definition for Applesoft BASIC source lines.
 * Tokenizes using Command.TOKEN enum values (sorted longest-first for greedy match).
 * Pure function — no JavaFX dependencies, safe to call from any thread.
 */
public class AppleSoftBasicSyntax implements SyntaxDefinition {

    /**
     * Keyword strings sorted descending by length so longer tokens take priority
     * (e.g. "NOTRACE" before "NOT", "HIMEM:" before "HIMEM", "PRINT" before "PR#").
     * Single-character operator tokens (+, -, *, /, ^, >, =, <, &) are excluded
     * because they conflict with operand characters and are not keyword-highlighted.
     */
    private static final List<String> SORTED_KEYWORDS;

    static {
        List<String> kws = new ArrayList<>();
        for (Command.TOKEN t : Command.TOKEN.values()) {
            String s = t.toString();
            if (s.length() > 1) {
                kws.add(s);
            }
        }
        kws.sort((a, b) -> Integer.compare(b.length(), a.length()));
        SORTED_KEYWORDS = Collections.unmodifiableList(kws);
    }

    @Override
    public String getName() {
        return "Applesoft BASIC";
    }

    /**
     * Tokenizes a single Applesoft BASIC source line.
     *
     * Rules applied in order:
     * 1. Leading digits → LINE_NUMBER span
     * 2. Scan remaining characters:
     *    - '"' → STRING span (to closing '"' or EOL)
     *    - "REM" → COMMENT span covering REM through EOL
     *    - Longest matching TOKEN keyword → KEYWORD span
     *    - Everything else accumulated into DEFAULT span
     *
     * @param line       the source line text
     * @param lineNumber 0-based line number (unused for Applesoft, included for interface)
     * @return list of non-overlapping StyleSpan in ascending start order
     */
    @Override
    public List<StyleSpan> tokenize(String line, int lineNumber) {
        List<StyleSpan> spans = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return spans;
        }

        String upper = line.toUpperCase();
        int pos = 0;
        int len = line.length();

        // Step 1: leading line number
        if (pos < len && Character.isDigit(upper.charAt(pos))) {
            int lineNumEnd = pos;
            while (lineNumEnd < len && Character.isDigit(upper.charAt(lineNumEnd))) {
                lineNumEnd++;
            }
            spans.add(new StyleSpan(pos, lineNumEnd, TokenStyle.LINE_NUMBER));
            pos = lineNumEnd;
        }

        // Step 2: scan rest of line
        int defaultStart = pos; // start of the current un-classified region

        while (pos < len) {
            char c = upper.charAt(pos);

            // String literal starting with '"'
            if (c == '"') {
                flushDefault(spans, defaultStart, pos);
                int strEnd = pos + 1;
                while (strEnd < len && upper.charAt(strEnd) != '"') {
                    strEnd++;
                }
                if (strEnd < len) {
                    strEnd++; // include closing '"'
                }
                spans.add(new StyleSpan(pos, strEnd, TokenStyle.STRING));
                pos = strEnd;
                defaultStart = pos;
                continue;
            }

            // Try keyword match (longest first)
            boolean matched = false;
            for (String kw : SORTED_KEYWORDS) {
                if (upper.startsWith(kw, pos)) {
                    flushDefault(spans, defaultStart, pos);

                    if ("REM".equals(kw)) {
                        // REM and everything after it is a comment
                        spans.add(new StyleSpan(pos, len, TokenStyle.COMMENT));
                        pos = len;
                        defaultStart = pos;
                        matched = true;
                        break;
                    }

                    int kwEnd = pos + kw.length();
                    spans.add(new StyleSpan(pos, kwEnd, TokenStyle.KEYWORD));
                    pos = kwEnd;
                    defaultStart = pos;
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                pos++;
            }
        }

        // Flush any remaining un-classified text as DEFAULT
        flushDefault(spans, defaultStart, len);

        return spans;
    }

    /**
     * Resolves a BASIC line number to its 1-based physical line index.
     * Scans each physical line for a leading integer; returns the first line whose
     * BASIC line number >= logicalLine (nearest-match, like a real GOTO).
     * Falls back to physical line behavior if no match is found.
     */
    @Override
    public int resolvePhysicalLine(String fullText, int logicalLine) {
        if (fullText == null || fullText.isEmpty()) {
            return logicalLine;
        }
        String[] lines = fullText.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].stripLeading();
            if (!trimmed.isEmpty() && Character.isDigit(trimmed.charAt(0))) {
                int end = 0;
                while (end < trimmed.length() && Character.isDigit(trimmed.charAt(end))) {
                    end++;
                }
                try {
                    int basicLineNum = Integer.parseInt(trimmed.substring(0, end));
                    if (basicLineNum >= logicalLine) {
                        return i + 1; // 1-based physical line
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return logicalLine;
    }

    /** Emits a DEFAULT span if there is pending un-classified text. */
    private static void flushDefault(List<StyleSpan> spans, int start, int end) {
        if (start < end) {
            spans.add(new StyleSpan(start, end, TokenStyle.DEFAULT));
        }
    }
}
