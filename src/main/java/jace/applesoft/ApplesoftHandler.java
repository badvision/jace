package jace.applesoft;

import jace.Emulator;
import jace.ide.Program;
import jace.ide.CompileResult;
import jace.ide.LanguageHandler;

/**
 *
 * @author blurry
 */
public class ApplesoftHandler implements LanguageHandler<Program> {

    @Override
    public String getNewDocumentContent() {
        return ApplesoftProgram.fromMemory(Emulator.computer.getMemory()).toString();
    }

    @Override
    public CompileResult<Program> compile(Program program) {
        return null;
    }

    @Override
    public void execute(CompileResult<Program> lastResult) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
