package jace.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link AcmeLabelParser}.
 *
 * The VICE-format cases exist because vt3's apple2/Makefile invokes
 * {@code acme --vicelabels}, which emits "al C:xxxx .name" — a format the
 * original Pt3PlayerRegisterTest parser silently ignored, producing an
 * obscure "test_entry not found" failure.
 */
public class AcmeLabelParserTest {

    // ------------------------------------------------------------------
    // VICE format: "al C:xxxx .name" — what acme --vicelabels emits
    // ------------------------------------------------------------------

    @Test
    public void viceFormat_parsesLabelNameAndAddress() {
        Map<String, Integer> labels = AcmeLabelParser.parse(List.of("al C:2298 .test_entry"));
        assertEquals(Integer.valueOf(0x2298), labels.get("test_entry"));
    }

    @Test
    public void viceFormat_parsesZeroPageAndSlotIoAddresses() {
        Map<String, Integer> labels = AcmeLabelParser.parse(List.of(
            "al C:0007 .AY_LATCH",
            "al C:c407 .VIA1_T1LH",
            "al C:19c1 .irq_exit"
        ));
        assertEquals(Integer.valueOf(0x0007), labels.get("AY_LATCH"));
        assertEquals(Integer.valueOf(0xC407), labels.get("VIA1_T1LH"));
        assertEquals(Integer.valueOf(0x19C1), labels.get("irq_exit"));
    }

    @Test
    public void viceFormat_doesNotRetainLeadingDotInName() {
        Map<String, Integer> labels = AcmeLabelParser.parse(List.of("al C:08b1 .PLR_FLAGS"));
        assertFalse("Label name must not keep the VICE '.' prefix", labels.containsKey(".PLR_FLAGS"));
        assertEquals(Integer.valueOf(0x08B1), labels.get("PLR_FLAGS"));
    }

    // ------------------------------------------------------------------
    // Plain format: "name = $XXXX" — acme --labeldump / --symbollist
    // ------------------------------------------------------------------

    @Test
    public void plainFormat_stillParses() {
        Map<String, Integer> labels = AcmeLabelParser.parse(List.of(
            "\ttest_entry\t= $2298",
            "\tPLR_FLAGS\t= $08b1\t; player state flags"
        ));
        assertEquals(Integer.valueOf(0x2298), labels.get("test_entry"));
        assertEquals(Integer.valueOf(0x08B1), labels.get("PLR_FLAGS"));
    }

    // ------------------------------------------------------------------
    // Robustness
    // ------------------------------------------------------------------

    @Test
    public void ignoresBlankAndCommentAndUnrecognizedLines() {
        Map<String, Integer> labels = AcmeLabelParser.parse(List.of(
            "",
            "   ",
            "; a whole-line comment",
            "some random text with no address",
            "al C:2298 .test_entry"
        ));
        assertEquals("Only the one real label should be parsed", 1, labels.size());
        assertEquals(Integer.valueOf(0x2298), labels.get("test_entry"));
    }

    @Test
    public void unknownLabel_isAbsentRatherThanZero() {
        Map<String, Integer> labels = AcmeLabelParser.parse(List.of("al C:2298 .test_entry"));
        assertNull("Missing labels must be null, not 0 — 0 is a valid address",
                   labels.get("no_such_label"));
    }
}
