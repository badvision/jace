package jace.ide;

import jace.core.Keyboard;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author blurry
 */
class TextHandler implements LanguageHandler<String> {
    public TextHandler() {
    }

    @Override
    public String getNewDocumentContent() {
        return "\n";
    }    

    @Override
    public CompileResult<String> compile(Program program) {
        return new CompileResult<String>() {
            @Override
            public boolean isSuccessful() {
                return true;
            }

            @Override
            public String getCompiledAsset() {
                return program.getValue();
            }

            @Override
            public Map<Integer, String> getErrorsAndWarnings() {
                return Collections.EMPTY_MAP;
            }

            @Override
            public List<String> getOtherMessages() {
                return Collections.EMPTY_LIST;
            }

            @Override
            public List<String> getRawOutput() {
                return Collections.EMPTY_LIST;
            }
        };
    }

    @Override
    public void execute(CompileResult<String> lastResult) {
        Keyboard.pasteFromString(lastResult.getCompiledAsset());
    }
}
