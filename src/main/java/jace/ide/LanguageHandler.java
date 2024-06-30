package jace.ide;

public interface LanguageHandler<T> {

    String getNewDocumentContent();
    
    CompileResult<T> compile(Program program);

    void execute(CompileResult<T> lastResult) throws Exception;
    
    void clean(CompileResult<T> lastResult);
}