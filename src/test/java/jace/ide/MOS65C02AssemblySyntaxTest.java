package jace.ide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for MOS65C02AssemblySyntax.tokenize().
 * Pure-function tests: no JavaFX required.
 */
public class MOS65C02AssemblySyntaxTest {

    private MOS65C02AssemblySyntax syntax;

    @Before
    public void setUp() {
        syntax = new MOS65C02AssemblySyntax();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static StyleSpan firstWithStyle(List<StyleSpan> spans, TokenStyle style) {
        return spans.stream().filter(s -> s.style() == style).findFirst().orElse(null);
    }

    private static List<StyleSpan> allWithStyle(List<StyleSpan> spans, TokenStyle style) {
        return spans.stream().filter(s -> s.style() == style).toList();
    }

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
    public void pureCommentLine() {
        // "; this is a comment" → single COMMENT span covering whole line
        String line = "; this is a comment";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        assertEquals("Whole-line comment should produce exactly one span", 1, spans.size());
        StyleSpan comment = spans.get(0);
        assertEquals("Span style should be COMMENT", TokenStyle.COMMENT, comment.style());
        assertEquals("COMMENT start must be 0", 0, comment.start());
        assertEquals("COMMENT end must cover whole line", line.length(), comment.end());
    }

    @Test
    public void commentWithLeadingWhitespace() {
        // "  ; indented comment" → COMMENT span
        String line = "  ; indented comment";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan comment = firstWithStyle(spans, TokenStyle.COMMENT);
        assertNotNull("Expected COMMENT span", comment);
        assertEquals("COMMENT must run to end of line", line.length(), comment.end());
    }

    @Test
    public void labelWithMnemonic() {
        // "loop: LDA #$FF" → LABEL for "loop:", KEYWORD for "LDA", NUMBER for "$FF"
        String line = "loop: LDA #$FF";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull("Expected LABEL span", label);
        assertEquals("loop:", line.substring(label.start(), label.end()));

        List<StyleSpan> kws = allWithStyle(spans, TokenStyle.KEYWORD);
        assertFalse("Expected KEYWORD spans", kws.isEmpty());
        boolean foundLda = kws.stream().anyMatch(s ->
            line.substring(s.start(), s.end()).equalsIgnoreCase("LDA"));
        assertTrue("Expected KEYWORD covering LDA", foundLda);

        StyleSpan number = firstWithStyle(spans, TokenStyle.NUMBER);
        assertNotNull("Expected NUMBER span for $FF", number);
        String numText = line.substring(number.start(), number.end());
        assertEquals("$FF", numText);
    }

    @Test
    public void mnemonicWithNoLabel() {
        // "  BNE loop" → KEYWORD for "BNE"
        String line = "  BNE loop";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        List<StyleSpan> kws = allWithStyle(spans, TokenStyle.KEYWORD);
        assertFalse("Expected KEYWORD spans", kws.isEmpty());
        boolean foundBne = kws.stream().anyMatch(s ->
            line.substring(s.start(), s.end()).equalsIgnoreCase("BNE"));
        assertTrue("Expected KEYWORD covering BNE", foundBne);
    }

    @Test
    public void directiveLineWithByte() {
        // ".byte $00, $01" → DIRECTIVE span (note: ACME uses '!' but we also handle '.')
        // Actually ACME uses '!' — let's test the real ACME syntax
        String line = "!byte $00, $01";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan directive = firstWithStyle(spans, TokenStyle.DIRECTIVE);
        assertNotNull("Expected DIRECTIVE span for !byte", directive);
        String dirText = line.substring(directive.start(), directive.end());
        assertEquals("!byte", dirText);
    }

    @Test
    public void directiveWithNumbers() {
        // "!byte $00, $01" → DIRECTIVE, NUMBER($00), NUMBER($01)
        String line = "!byte $00, $01";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        List<StyleSpan> numbers = allWithStyle(spans, TokenStyle.NUMBER);
        assertEquals("Expected two NUMBER spans", 2, numbers.size());
        assertEquals("$00", line.substring(numbers.get(0).start(), numbers.get(0).end()));
        assertEquals("$01", line.substring(numbers.get(1).start(), numbers.get(1).end()));
    }

    @Test
    public void originDirective() {
        // "*= $0800" → DIRECTIVE for "*="
        String line = "*= $0800";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan directive = firstWithStyle(spans, TokenStyle.DIRECTIVE);
        assertNotNull("Expected DIRECTIVE span for *=", directive);
        assertEquals("*=", line.substring(directive.start(), directive.end()));

        StyleSpan number = firstWithStyle(spans, TokenStyle.NUMBER);
        assertNotNull("Expected NUMBER for $0800", number);
        assertEquals("$0800", line.substring(number.start(), number.end()));
    }

    @Test
    public void hexNumberRecognized() {
        String line = "  LDA $1234";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan number = firstWithStyle(spans, TokenStyle.NUMBER);
        assertNotNull("Expected NUMBER span for $1234", number);
        assertEquals("$1234", line.substring(number.start(), number.end()));
    }

    @Test
    public void binaryNumberRecognized() {
        String line = "  LDA #%10110001";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan number = firstWithStyle(spans, TokenStyle.NUMBER);
        assertNotNull("Expected NUMBER span for binary literal", number);
        String numText = line.substring(number.start(), number.end());
        assertEquals("%10110001", numText);
    }

    @Test
    public void decimalNumberRecognized() {
        String line = "  LDA #255";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan number = firstWithStyle(spans, TokenStyle.NUMBER);
        assertNotNull("Expected NUMBER span for decimal 255", number);
        assertEquals("255", line.substring(number.start(), number.end()));
    }

    @Test
    public void inlineComment() {
        String line = "  LDA #$FF ; load value";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan comment = firstWithStyle(spans, TokenStyle.COMMENT);
        assertNotNull("Expected inline COMMENT span", comment);
        assertEquals("; load value", line.substring(comment.start(), comment.end()));
        assertEquals("COMMENT must end at line end", line.length(), comment.end());
    }

    @Test
    public void stringLiteralInDirective() {
        String line = "!text \"HELLO\"";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        StyleSpan str = firstWithStyle(spans, TokenStyle.STRING);
        assertNotNull("Expected STRING span", str);
        assertEquals("\"HELLO\"", line.substring(str.start(), str.end()));
    }

    @Test
    public void staKeywordRecognized() {
        String line = "  STA $C000";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);

        List<StyleSpan> kws = allWithStyle(spans, TokenStyle.KEYWORD);
        boolean foundSta = kws.stream().anyMatch(s ->
            line.substring(s.start(), s.end()).equalsIgnoreCase("STA"));
        assertTrue("Expected KEYWORD covering STA", foundSta);
    }

    @Test
    public void spansNonOverlappingForComplexLine() {
        String line = "start: LDA #$FF ; initialize accumulator";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertFalse("Should have spans for a complex line", spans.isEmpty());
    }

    @Test
    public void getNameReturnsNonNull() {
        assertNotNull("getName() must not return null", syntax.getName());
        assertFalse("getName() must not be empty", syntax.getName().isEmpty());
    }
}
