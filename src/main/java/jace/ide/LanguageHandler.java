package jace.ide;

public interface LanguageHandler<T> {

    public String getNewDocumentContent();
    
    public CompileResult<T> compile(Program program);

    public void execute(CompileResult<T> lastResult);
    
    public void clean(CompileResult<T> lastResult);
}