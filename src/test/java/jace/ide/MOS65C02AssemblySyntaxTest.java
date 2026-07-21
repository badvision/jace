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

    private String spanText(String line, StyleSpan span) {
        return line.substring(span.start(), span.end());
    }

    // ── Basic ────────────────────────────────────────────────────────────────

    @Test public void emptyLineReturnsEmptyList() {
        assertTrue(syntax.tokenize("", 0).isEmpty());
    }

    @Test public void nullLineReturnsEmptyList() {
        assertTrue(syntax.tokenize(null, 0).isEmpty());
    }

    @Test public void pureCommentLine() {
        String line = "; this is a comment";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals(1, spans.size());
        assertEquals(TokenStyle.COMMENT, spans.get(0).style());
        assertEquals(0, spans.get(0).start());
        assertEquals(line.length(), spans.get(0).end());
    }

    @Test public void commentWithLeadingWhitespace() {
        String line = "  ; indented comment";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan c = firstWithStyle(spans, TokenStyle.COMMENT);
        assertNotNull(c);
        assertEquals(line.length(), c.end());
    }

    // ── Labels with colon ────────────────────────────────────────────────────

    @Test public void labelWithColon() {
        String line = "loop: LDA #$FF";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull(label);
        assertEquals("loop:", spanText(line, label));
        boolean foundLda = allWithStyle(spans, TokenStyle.KEYWORD).stream()
                .anyMatch(s -> spanText(line, s).equalsIgnoreCase("LDA"));
        assertTrue("Expected KEYWORD for LDA", foundLda);
    }

    // ── Column-0 label without colon (ACME implicit label) ──────────────────

    @Test public void col0LabelNoColon() {
        String line = "loop LDA #$FF";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull("Expected LABEL for col-0 identifier", label);
        assertEquals("loop", spanText(line, label));
        boolean foundLda = allWithStyle(spans, TokenStyle.KEYWORD).stream()
                .anyMatch(s -> spanText(line, s).equalsIgnoreCase("LDA"));
        assertTrue("Expected KEYWORD for LDA after col-0 label", foundLda);
    }

    @Test public void col0MnemonicIsNotLabel() {
        // A mnemonic at col 0 must NOT be treated as a label
        String line = "NOP";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertTrue("No LABEL expected for NOP at col 0", allWithStyle(spans, TokenStyle.LABEL).isEmpty());
        assertFalse("Expected KEYWORD for NOP", allWithStyle(spans, TokenStyle.KEYWORD).isEmpty());
    }

    // ── Label assignment (equate) ─────────────────────────────────────────────

    @Test public void labelAssignment() {
        String line = "basout = $ffd2";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull("Expected LABEL for assignment target", label);
        assertEquals("basout", spanText(line, label));
        StyleSpan number = firstWithStyle(spans, TokenStyle.NUMBER);
        assertNotNull("Expected NUMBER for $ffd2", number);
        assertEquals("$ffd2", spanText(line, number));
    }

    // ── Local labels ─────────────────────────────────────────────────────────

    @Test public void localLabelDefinition() {
        // ".string" at col 0 without colon — ACME local label
        String line = ".string !pet \"hello\"";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull("Expected LABEL for .string", label);
        assertEquals(".string", spanText(line, label));
    }

    @Test public void localLabelWithColon() {
        String line = ".loop: INX";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull(label);
        assertEquals(".loop:", spanText(line, label));
    }

    @Test public void localLabelReferenceInOperand() {
        String line = "  BNE .loop";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        boolean hasBne = allWithStyle(spans, TokenStyle.KEYWORD).stream()
                .anyMatch(s -> spanText(line, s).equalsIgnoreCase("BNE"));
        assertTrue("Expected KEYWORD for BNE", hasBne);
        boolean hasRef = allWithStyle(spans, TokenStyle.LABEL).stream()
                .anyMatch(s -> spanText(line, s).equals(".loop"));
        assertTrue("Expected LABEL for .loop reference", hasRef);
    }

    // ── Anonymous labels ─────────────────────────────────────────────────────

    @Test public void anonymousLabelMinus() {
        String line = "-  jsr basout";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull(label);
        assertEquals("-", spanText(line, label));
    }

    @Test public void anonymousLabelPlus() {
        String line = "+  lda .string,x";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan label = firstWithStyle(spans, TokenStyle.LABEL);
        assertNotNull(label);
        assertEquals("+", spanText(line, label));
    }

    @Test public void branchToAnonymousForward() {
        String line = "  beq +";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertTrue("Expected KEYWORD for beq", allWithStyle(spans, TokenStyle.KEYWORD).stream()
                .anyMatch(s -> spanText(line, s).equalsIgnoreCase("beq")));
    }

    // ── Mnemonics ─────────────────────────────────────────────────────────────

    @Test public void mnemonicWithLeadingWhitespace() {
        String line = "  BNE loop";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertTrue(allWithStyle(spans, TokenStyle.KEYWORD).stream()
                .anyMatch(s -> spanText(line, s).equalsIgnoreCase("BNE")));
    }

    @Test public void staKeyword() {
        String line = "  STA $C000";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertTrue(allWithStyle(spans, TokenStyle.KEYWORD).stream()
                .anyMatch(s -> spanText(line, s).equalsIgnoreCase("STA")));
    }

    // ── Number formats ────────────────────────────────────────────────────────

    @Test public void hexDollar() {
        String line = "  LDA $1234";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals("$1234", spanText(line, firstWithStyle(spans, TokenStyle.NUMBER)));
    }

    @Test public void hex0x() {
        String line = "  LDA 0xffd2";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals("0xffd2", spanText(line, firstWithStyle(spans, TokenStyle.NUMBER)));
    }

    @Test public void octal() {
        String line = "  LDA &1701";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals("&1701", spanText(line, firstWithStyle(spans, TokenStyle.NUMBER)));
    }

    @Test public void binaryZeroOne() {
        String line = "  LDA #%10110001";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals("%10110001", spanText(line, firstWithStyle(spans, TokenStyle.NUMBER)));
    }

    @Test public void binaryDotHash() {
        // ACME allows '.' and '#' as bit substitutes in binary literals
        String line = "  LDA #%..##..##";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan num = firstWithStyle(spans, TokenStyle.NUMBER);
        assertNotNull("Expected NUMBER for binary with . and #", num);
        assertEquals("%..##..##", spanText(line, num));
    }

    @Test public void decimal() {
        String line = "  LDA #255";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals("255", spanText(line, firstWithStyle(spans, TokenStyle.NUMBER)));
    }

    // ── String literals ───────────────────────────────────────────────────────

    @Test public void doubleQuotedString() {
        String line = "!text \"HELLO\"";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals("\"HELLO\"", spanText(line, firstWithStyle(spans, TokenStyle.STRING)));
    }

    @Test public void singleQuotedChar() {
        String line = "  LDA 'A'";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan str = firstWithStyle(spans, TokenStyle.STRING);
        assertNotNull("Expected STRING for single-quoted char", str);
        assertEquals("'A'", spanText(line, str));
    }

    // ── Directives ────────────────────────────────────────────────────────────

    @Test public void acmeDirective() {
        String line = "!byte $00, $01";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan dir = firstWithStyle(spans, TokenStyle.DIRECTIVE);
        assertNotNull(dir);
        assertEquals("!byte", spanText(line, dir));
        assertEquals(2, allWithStyle(spans, TokenStyle.NUMBER).size());
    }

    @Test public void originDirective() {
        String line = "*= $0800";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertEquals("*=", spanText(line, firstWithStyle(spans, TokenStyle.DIRECTIVE)));
        assertEquals("$0800", spanText(line, firstWithStyle(spans, TokenStyle.NUMBER)));
    }

    // ── Inline comments ───────────────────────────────────────────────────────

    @Test public void inlineComment() {
        String line = "  LDA #$FF ; load value";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        StyleSpan comment = firstWithStyle(spans, TokenStyle.COMMENT);
        assertNotNull(comment);
        assertEquals("; load value", spanText(line, comment));
        assertEquals(line.length(), comment.end());
    }

    // ── Complex lines ─────────────────────────────────────────────────────────

    @Test public void complexLineNonOverlapping() {
        String line = "start: LDA #$FF ; initialize accumulator";
        assertNonOverlapping(syntax.tokenize(line, 0));
    }

    @Test public void exampleFragmentLoop() {
        // From the QuickRef example: "-  jsr basout  ; output character"
        String line = "-\t\t\tjsr basout\t; output character";
        List<StyleSpan> spans = syntax.tokenize(line, 0);
        assertNonOverlapping(spans);
        assertTrue(allWithStyle(spans, TokenStyle.LABEL).stream()
                .anyMatch(s -> spanText(line, s).equals("-")));
        assertTrue(allWithStyle(spans, TokenStyle.KEYWORD).stream()
                .anyMatch(s -> spanText(line, s).equalsIgnoreCase("jsr")));
        assertNotNull(firstWithStyle(spans, TokenStyle.COMMENT));
    }

    @Test public void getNameReturnsNonNull() {
        assertNotNull(syntax.getName());
        assertFalse(syntax.getName().isEmpty());
    }
}
