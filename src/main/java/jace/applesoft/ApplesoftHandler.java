package jace.applesoft;

import jace.Emulator;
import jace.ide.Program;
import jace.ide.CompileResult;
import jace.ide.LanguageHandler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author blurry
 */
public class ApplesoftHandler implements LanguageHandler<ApplesoftProgram> {

    @Override
    public String getNewDocumentContent() {
        return ApplesoftProgram.fromMemory(Emulator.computer.getMemory()).toString();
    }

    @Override
    public CompileResult<ApplesoftProgram> compile(Program program) {
        final ApplesoftProgram result = ApplesoftProgram.fromString(program.getValue());
        final Map<Integer, String> warnings = new LinkedHashMap<>();
//        int lineNumber = 1;
//        for (Line l : result.lines) {
//            warnings.put(lineNumber++, l.toString());
//        }
        return new CompileResult<ApplesoftProgram>() {
            @Override
            public boolean isSuccessful() {
                return result != null;
            }

            @Override
            public ApplesoftProgram getCompiledAsset() {
                return result;
            }

            @Override
            public Map<Integer, String> getErrors() {
                return Collections.EMPTY_MAP;
            }

            @Override
            public Map<Integer, String> getWarnings() {
                return warnings;
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
    public void execute(CompileResult<ApplesoftProgram> lastResult) {
        lastResult.getCompiledAsset().run();
    }
    
}
