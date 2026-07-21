package jace.ide;

public interface LanguageHandler<T> {

    String getNewDocumentContent();

    CompileResult<T> compile(Program program);

    default void writeToMemory(CompileResult<T> lastResult) throws Exception {}

    void execute(CompileResult<T> lastResult) throws Exception;

    void clean(CompileResult<T> lastResult);
}