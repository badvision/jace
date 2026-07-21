package jace.ide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for AppleSoftBasicSyntax.tokenize().
 * Pure-function tests: no JavaFX required.
 */
public class AppleSoftBasicSyntaxTest {

    private AppleSoftBasicSyntax syntax;

    @Before
    public void setUp() {
        syntax = new AppleSoftBasicSyntax();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Returns the first span with the given style, or null. */
    private static StyleSpan firstWithStyle(List<StyleSpan> spans, TokenStyle style) {
        return spans.stream().filter(s -> s.style() == style).findFirst().orElse(null);
    }

    /** Returns all spans with the given style. */
    private static List<StyleSpan> allWithStyle(List<StyleSpan> spans, TokenStyle style) {
        return spans.stream().filter(s -> s.style() == style).toList();
    }

    /** Asserts spans are in ascending start order with no overlap. */
    private static void assertNonOverlapping(List<StyleSpan> spans) {
        for (int i = 1; i < spans.size(); i++) {
            StyleSpan prev = spans.get(i - 1);
            StyleSpan curr = spans.get(i);
            assertTrue(
                "Spans must be in ascending start order: span[" + (i-1) + "] end=" + prev.end()
                    + " > span[" + i + "] start=" + curr.start(),
                prev.end() <= curr.start());
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    public void emptyLineReturnsEmptyList() {
        List<StyleSpan> spans = syntax.tokenize("", 0);
        assertTrue("Empty line should produce no spans", spans.isEmpty());
    }

    @Test
    public void nullLineReturnsEmptyList() {
        List<StyleSpan> spans = syntax.tokenize(null, 0);
        assertTrue("Null line should produce no spans", spans.isEmpty());
    }

    @Test
    public void lineNumberSpanAtStart() {
        // "100 PRINT" — line number "100" should be LINE_NUMBER span [0,3)
        List<StyleSpan> spans = syntax.tokenize("100 PRINT", 0);
        assertNonOverlapping(spans);

        StyleSpan ln = firstWithStyle(spans, TokenStyle.LINE_NUMBER);
        assertTrue("Expected a LINE_NUMBER span", ln != null);
        assertEquals("LINE_NUMBER start must be 0", 0, ln.start());
        assertEquals("LINE_NUMBER end must be 3", 3, ln.end());
    }

    @Test
    public void printKeywordSpan() {
        // "100 PRINT" — PRINT should be a KEYWORD span
        List<StyleSpan> spans = syntax.tokenize("100 PRINT", 0);
        assertNonOverlapping(spans);

        List<StyleSpan> keywords = allWithStyle(spans, TokenStyle.KEYWORD);
        assertFalse("Expected at least one KEYWORD span", keywords.isEmpty());

        // Find the KEYWORD that covers the text "PRINT"
        String text = "100 PRINT";
        boolean foundPrint = keywords.stream().anyMatch(s ->
            text.substring(s.start(), s.end()).equalsIgnoreCase("PRINT"));
        assertTrue("Expected KEYWORD span covering 'PRINT'", foundPrint);
    }

    @Test
    public void printHelloWorldSpans() {
        // "100 PRINT \"HELLO\"" → LINE_NUMBER at [0,3], KEYWORD for PRINT, STRING for "HELLO"
        String line = "100 PRINT \"HELLO\"";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan ln = firstWithStyle(spans, TokenStyle.LINE_NUMBER);
        assertTrue("Expected LINE_NUMBER span", ln != null);
        assertEquals("LINE_NUMBER start", 0, ln.start());
        assertEquals("LINE_NUMBER end", 3, ln.end());

        List<StyleSpan> keywords = allWithStyle(spans, TokenStyle.KEYWORD);
        assertFalse("Expected KEYWORD spans", keywords.isEmpty());
        boolean foundPrint = keywords.stream().anyMatch(s ->
            line.substring(s.start(), s.end()).equalsIgnoreCase("PRINT"));
        assertTrue("Expected KEYWORD covering PRINT", foundPrint);

        StyleSpan str = firstWithStyle(spans, TokenStyle.STRING);
        assertTrue("Expected STRING span", str != null);
        String strText = line.substring(str.start(), str.end());
        assertTrue("STRING span should include quotes: got '" + strText + "'",
            strText.startsWith("\"") && strText.endsWith("\""));
        assertTrue("STRING should contain HELLO", strText.contains("HELLO"));
    }

    @Test
    public void forToKeywords() {
        // "200 FOR I=1 TO 10" → LINE_NUMBER, KEYWORD(FOR), KEYWORD(TO)
        String line = "200 FOR I=1 TO 10";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan ln = firstWithStyle(spans, TokenStyle.LINE_NUMBER);
        assertTrue("Expected LINE_NUMBER", ln != null);
        assertEquals(0, ln.start());
        assertEquals(3, ln.end());

        List<StyleSpan> kws = allWithStyle(spans, TokenStyle.KEYWORD);
        boolean foundFor = kws.stream().anyMatch(s ->
            line.substring(s.start(), s.end()).equalsIgnoreCase("FOR"));
        boolean foundTo = kws.stream().anyMatch(s ->
            line.substring(s.start(), s.end()).equalsIgnoreCase("TO"));
        assertTrue("Expected KEYWORD covering FOR", foundFor);
        assertTrue("Expected KEYWORD covering TO", foundTo);
    }

    @Test
    public void remBecomesCommentToEndOfLine() {
        // "300 REM this is a comment" → LINE_NUMBER, COMMENT from REM to EOL
        String line = "300 REM this is a comment";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan ln = firstWithStyle(spans, TokenStyle.LINE_NUMBER);
        assertTrue("Expected LINE_NUMBER", ln != null);

        StyleSpan comment = firstWithStyle(spans, TokenStyle.COMMENT);
        assertTrue("Expected COMMENT span", comment != null);
        // COMMENT should start at REM and run to end of line
        String commentText = line.substring(comment.start(), comment.end());
        assertTrue("COMMENT should start with REM", commentText.startsWith("REM"));
        assertEquals("COMMENT must end at line end", line.length(), comment.end());
    }

    @Test
    public void remAloneIsComment() {
        String line = "400 REM";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan comment = firstWithStyle(spans, TokenStyle.COMMENT);
        assertTrue("Expected COMMENT span for bare REM", comment != null);
        assertEquals("COMMENT from REM position to end", line.length(), comment.end());
    }

    @Test
    public void noLineNumberNoKeyword() {
        // No line number, just variables
        String line = "X = 1";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan ln = firstWithStyle(spans, TokenStyle.LINE_NUMBER);
        assertTrue("No LINE_NUMBER expected when line does not start with digit", ln == null);
    }

    @Test
    public void spansAreNonOverlappingForComplexLine() {
        String line = "500 FOR I = 1 TO 10 : PRINT I : NEXT I";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertFalse("Should have spans for a complex line", spans.isEmpty());
    }

    @Test
    public void stringSpanIncludesQuotes() {
        String line = "100 PRINT \"ABC\"";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan str = firstWithStyle(spans, TokenStyle.STRING);
        assertTrue("Expected STRING span", str != null);
        String text = line.substring(str.start(), str.end());
        assertEquals("\"ABC\"", text);
    }

    @Test
    public void gotoKeyword() {
        String line = "600 GOTO 100";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        List<StyleSpan> kws = allWithStyle(spans, TokenStyle.KEYWORD);
        boolean foundGoto = kws.stream().anyMatch(s ->
            line.substring(s.start(), s.end()).equalsIgnoreCase("GOTO"));
        assertTrue("Expected KEYWORD covering GOTO", foundGoto);
    }

    @Test
    public void getNameReturnsNonNull() {
        assertTrue("getName() must not be null or empty",
            syntax.getName() != null && !syntax.getName().isEmpty());
    }

    @Test
    public void resolvePhysicalLine_exactMatch() {
        String text = "10 PRINT \"A\"\n20 GOTO 10\n30 END\n";
        assertEquals("BASIC line 20 is at physical line 2", 2, syntax.resolvePhysicalLine(text, 20));
    }

    @Test
    public void resolvePhysicalLine_nearestMatch() {
        // Lines 10, 30, 50 — asking for 25 should land on line 30 (first >= 25)
        String text = "10 PRINT \"A\"\n30 GOTO 10\n50 END\n";
        assertEquals("BASIC line 25 nearest-match is physical line 2 (line 30)", 2, syntax.resolvePhysicalLine(text, 25));
    }

    @Test
    public void resolvePhysicalLine_noMatch_fallback() {
        // Asking for line 999 when max is 50 — falls back to physical line 999
        String text = "10 PRINT \"A\"\n50 END\n";
        assertEquals("No matching BASIC line falls back to physical line", 999, syntax.resolvePhysicalLine(text, 999));
    }

    @Test
    public void resolvePhysicalLine_emptyText_fallback() {
        assertEquals("Empty text falls back to physical line", 5, syntax.resolvePhysicalLine("", 5));
    }
}
