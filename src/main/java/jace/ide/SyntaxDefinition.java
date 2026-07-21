package jace.ide;

import java.util.List;

public interface SyntaxDefinition {
    String getName();
    List<StyleSpan> tokenize(String line, int lineNumber);

    /**
     * Resolves a logical line identifier to a 1-based physical line index.
     * Override when the language uses its own line numbering (e.g. Applesoft BASIC).
     * Default: treat logicalLine as a 1-based physical index (passthrough).
     */
    default int resolvePhysicalLine(String fullText, int logicalLine) {
        return logicalLine;
    }
}
