package jace.ide;

import java.util.List;
import java.util.Map;

/**
 *
 * @author blurry
 */
public interface CompileResult<T> {

    boolean isSuccessful();
    
    T getCompiledAsset();
    
    Map<Integer, String> getErrors();

    Map<Integer, String> getWarnings();

    List<String> getOtherMessages();

    List<String> getRawOutput();
}
