package jace.ide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for Program.manageCompileResult() marker integration.
 *
 * <p>Verifies that manageCompileResult() correctly calls EditorControl.addMarker()
 * with the 1-based line numbers delivered by CompileResult (no caller-side offset
 * adjustment — the raw map keys are passed as-is).
 *
 * <p>Uses a stub EditorControl that records every addMarker() call, allowing
 * assertions without any JavaFX scene graph dependency.
 */
public class ProgramMarkerTest {

    // -------------------------------------------------------------------------
    // Stub EditorControl — records addMarker() calls for inspection
    // -------------------------------------------------------------------------

    static class RecordingEditorControl implements EditorControl {

        record MarkerCall(int line, MarkerType type, String message) {}

        final List<MarkerCall> addMarkerCalls = new ArrayList<>();
        int clearMarkersCallCount = 0;
        private String text = "";

        @Override public String getText() { return text; }
        @Override public void setText(String t) { text = t != null ? t : ""; }
        @Override public void setSyntaxDefinition(SyntaxDefinition def) {}
        @Override public boolean isDirty() { return false; }
        @Override public void clearDirty() {}
        @Override public void requestFocus() {}
        @Override public void cut() {}
        @Override public void copy() {}
        @Override public void paste() {}
        @Override public void undo() {}
        @Override public void redo() {}
        @Override public javafx.beans.property.ReadOnlyBooleanProperty dirtyProperty() { return null; }
        @Override public javafx.beans.property.ReadOnlyStringProperty textProperty() { return null; }
        @Override public void showFindReplace() {}
        @Override public void goToLine(int lineNumber) {}
        @Override public void setShowLineNumbers(boolean show) {}

        @Override
        public void clearMarkers() {
            clearMarkersCallCount++;
            addMarkerCalls.clear();
        }

        @Override
        public void addMarker(int line, MarkerType type, String message) {
            addMarkerCalls.add(new MarkerCall(line, type, message));
        }
    }

    // -------------------------------------------------------------------------
    // Stub CompileResult helper
    // -------------------------------------------------------------------------

    static CompileResult<Void> buildResult(Map<Integer, String> errors, Map<Integer, String> warnings) {
        return new CompileResult<>() {
            @Override public boolean isSuccessful() { return errors.isEmpty(); }
            @Override public Void getCompiledAsset() { return null; }
            @Override public Map<Integer, String> getErrors() { return errors; }
            @Override public Map<Integer, String> getWarnings() { return warnings; }
            @Override public List<String> getOtherMessages() { return Collections.emptyList(); }
            @Override public List<String> getRawOutput() { return Collections.emptyList(); }
        };
    }

    /** Convenience: create a Program with a RecordingEditorControl attached. */
    static Program programWithRecorder(RecordingEditorControl recorder) {
        Program p = new Program(Program.DocumentType.plain);
        p.editorControl = recorder;
        return p;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void manageCompileResultCallsClearMarkersFirst() {
        RecordingEditorControl recorder = new RecordingEditorControl();
        // Pre-populate the recorder so we can verify clear happens
        recorder.addMarkerCalls.add(new RecordingEditorControl.MarkerCall(99, MarkerType.ERROR, "stale"));

        Program p = programWithRecorder(recorder);
        p.manageCompileResult(buildResult(Map.of(5, "syntax error"), Map.of()));

        assertEquals("clearMarkers() must be called exactly once", 1, recorder.clearMarkersCallCount);
    }

    @Test
    public void manageCompileResultPassesErrorLine1Based() {
        RecordingEditorControl recorder = new RecordingEditorControl();
        Program p = programWithRecorder(recorder);

        // AcmeCompiler stores 1-based line numbers directly from ACME output
        Map<Integer, String> errors = Map.of(3, "undefined symbol");
        p.manageCompileResult(buildResult(errors, Map.of()));

        assertEquals("One addMarker call for one error", 1, recorder.addMarkerCalls.size());
        RecordingEditorControl.MarkerCall call = recorder.addMarkerCalls.get(0);
        assertEquals("Line number must be 3 (1-based, no offset)", 3, call.line());
        assertEquals(MarkerType.ERROR, call.type());
        assertEquals("undefined symbol", call.message());
    }

    @Test
    public void manageCompileResultPassesWarningLine1Based() {
        RecordingEditorControl recorder = new RecordingEditorControl();
        Program p = programWithRecorder(recorder);

        Map<Integer, String> warnings = Map.of(7, "deprecated opcode");
        p.manageCompileResult(buildResult(Map.of(), warnings));

        assertEquals("One addMarker call for one warning", 1, recorder.addMarkerCalls.size());
        RecordingEditorControl.MarkerCall call = recorder.addMarkerCalls.get(0);
        assertEquals("Line number must be 7 (1-based, no offset)", 7, call.line());
        assertEquals(MarkerType.WARNING, call.type());
        assertEquals("deprecated opcode", call.message());
    }

    @Test
    public void manageCompileResultHandlesMultipleErrors() {
        RecordingEditorControl recorder = new RecordingEditorControl();
        Program p = programWithRecorder(recorder);

        Map<Integer, String> errors = new LinkedHashMap<>();
        errors.put(1, "error on line 1");
        errors.put(10, "error on line 10");
        errors.put(20, "error on line 20");

        p.manageCompileResult(buildResult(errors, Map.of()));

        assertEquals("Three addMarker calls for three errors", 3, recorder.addMarkerCalls.size());
        List<Integer> lines = recorder.addMarkerCalls.stream()
                .map(RecordingEditorControl.MarkerCall::line)
                .toList();
        assertTrue("Line 1 must be present", lines.contains(1));
        assertTrue("Line 10 must be present", lines.contains(10));
        assertTrue("Line 20 must be present", lines.contains(20));
    }

    @Test
    public void manageCompileResultHandlesBothErrorsAndWarnings() {
        RecordingEditorControl recorder = new RecordingEditorControl();
        Program p = programWithRecorder(recorder);

        Map<Integer, String> errors = Map.of(2, "error");
        Map<Integer, String> warnings = Map.of(5, "warning");

        p.manageCompileResult(buildResult(errors, warnings));

        assertEquals("Two addMarker calls total (one error, one warning)",
                2, recorder.addMarkerCalls.size());

        long errorCount = recorder.addMarkerCalls.stream()
                .filter(c -> c.type() == MarkerType.ERROR).count();
        long warningCount = recorder.addMarkerCalls.stream()
                .filter(c -> c.type() == MarkerType.WARNING).count();
        assertEquals(1, errorCount);
        assertEquals(1, warningCount);
    }

    @Test
    public void manageCompileResultNoOpWhenEditorControlNull() {
        // Program with no editor (headless mode) must not throw
        Program p = new Program(Program.DocumentType.plain);
        // editorControl is null — manageCompileResult must guard against this
        p.manageCompileResult(buildResult(Map.of(1, "error"), Map.of()));
        // If we reach here without NullPointerException, the test passes
    }

    @Test
    public void manageCompileResultEmptyResultCallsClearAndNoAdd() {
        RecordingEditorControl recorder = new RecordingEditorControl();
        Program p = programWithRecorder(recorder);

        p.manageCompileResult(buildResult(Map.of(), Map.of()));

        assertEquals("clearMarkers() must be called even when result is empty",
                1, recorder.clearMarkersCallCount);
        assertEquals("No addMarker calls for empty result",
                0, recorder.addMarkerCalls.size());
    }

    @Test
    public void manageCompileResultLine1BasedNotZeroBased() {
        // Sanity check: line 1 means first line (1-based), NOT second line (0-based)
        RecordingEditorControl recorder = new RecordingEditorControl();
        Program p = programWithRecorder(recorder);

        // ACME reports line 1 for errors on the first source line
        p.manageCompileResult(buildResult(Map.of(1, "first line error"), Map.of()));

        assertEquals(1, recorder.addMarkerCalls.size());
        RecordingEditorControl.MarkerCall call = recorder.addMarkerCalls.get(0);
        assertEquals("Line number 1 must pass through unchanged (1-based convention)", 1, call.line());
    }
}
