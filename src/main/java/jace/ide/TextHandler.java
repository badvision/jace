package jace.ide;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jace.core.Keyboard;

/**
 *
 * @author blurry
 */
public class TextHandler implements LanguageHandler<String> {
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
            public Map<Integer, String> getErrors() {
                return Collections.emptyMap();
            }
            
            @Override
            public Map<Integer, String> getWarnings() {
                return Collections.emptyMap();
            }

            @Override
            public List<String> getOtherMessages() {
                return Collections.emptyList();
            }

            @Override
            public List<String> getRawOutput() {
                return Collections.emptyList();
            }
        };
    }

    @Override
    public void execute(CompileResult<String> lastResult) {
        Keyboard.pasteFromString(lastResult.getCompiledAsset());
    }

    @Override
    public void clean(CompileResult<String> lastResult) {
    }
}
