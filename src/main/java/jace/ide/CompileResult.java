package jace.ide;

import java.util.List;
import java.util.Map;

/**
 *
 * @author blurry
 */
public interface CompileResult<T> {

    public boolean isSuccessful();
    
    public T getCompiledAsset();
    
    public Map<Integer, String> getErrorsAndWarnings();

    public List<String> getOtherMessages();

    public List<String> getRawOutput();    
}
