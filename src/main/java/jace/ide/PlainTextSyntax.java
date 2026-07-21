package jace.ide;

import java.util.List;

public class PlainTextSyntax implements SyntaxDefinition {
    @Override
    public String getName() {
        return "Plain Text";
    }

    @Override
    public List<StyleSpan> tokenize(String line, int lineNumber) {
        return List.of();
    }
}
