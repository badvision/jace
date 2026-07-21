package jace.ide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javafx.application.Platform;

/**
 * Unit tests for NativeEditorControl.
 * Verifies core editing contract: getText/setText, dirty tracking, markers.
 */
public class NativeEditorControlTest {

    private static boolean fxInitialized = false;

    @BeforeClass
    public static void initJfxRuntime() {
        if (!fxInitialized) {
            fxInitialized = true;
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException e) {
                // Platform already initialized by another test class — that's fine
            }
        }
    }

    @AfterClass
    public static void tearDown() {
        // No-op — keep FX alive for other tests in the suite
    }

    /** Run a block on the FX thread and wait for it to complete. */
    private static void runOnFxAndWait(Runnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw new Exception("FX thread threw: " + error.get().getMessage(), error.get());
        }
    }

    @Test
    public void setTextGetTextRoundTrip() throws Exception {
        AtomicReference<NativeEditorControl> ref = new AtomicReference<>();
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("hello");
            ref.set(ctrl);
        });
        // getText can be called from any thread once constructed
        runOnFxAndWait(() ->
            assertEquals("getText should return what was set", "hello", ref.get().getText())
        );
    }

    @Test
    public void setTextClearsDirtyFlag() throws Exception {
        AtomicReference<NativeEditorControl> ref = new AtomicReference<>();
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("hello");
            ref.set(ctrl);
        });
        runOnFxAndWait(() ->
            assertFalse("isDirty() must be false after setText()", ref.get().isDirty())
        );
    }

    @Test
    public void textChangeSetsDirtyFlag() throws Exception {
        AtomicReference<NativeEditorControl> ref = new AtomicReference<>();
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("initial");
            ref.set(ctrl);
        });
        // After setText(), dirty must be false
        runOnFxAndWait(() -> assertFalse("dirty should be false after setText", ref.get().isDirty()));
        // Simulate user editing (appendText bypasses the loadingText guard)
        runOnFxAndWait(() -> ref.get().appendTextForTest(" more text"));
        // After a user edit, dirty must be true
        runOnFxAndWait(() -> assertTrue("dirty should be true after user edit", ref.get().isDirty()));
    }

    @Test
    public void clearDirtyResetsDirtyFlag() throws Exception {
        AtomicReference<NativeEditorControl> ref = new AtomicReference<>();
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("content");
            ref.set(ctrl);
        });
        // Make dirty by simulating a user edit
        runOnFxAndWait(() -> ref.get().appendTextForTest(" extra"));
        runOnFxAndWait(() -> assertTrue("dirty should be true after user edit", ref.get().isDirty()));
        // clearDirty should reset it
        runOnFxAndWait(() -> ref.get().clearDirty());
        runOnFxAndWait(() -> assertFalse("dirty should be false after clearDirty()", ref.get().isDirty()));
    }

    @Test
    public void dirtyPropertyIsObservable() throws Exception {
        AtomicReference<NativeEditorControl> ref = new AtomicReference<>();
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            assertNotNull("dirtyProperty() must not return null", ctrl.dirtyProperty());
            ref.set(ctrl);
        });
    }

    @Test
    public void addMarkerDoesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("line1\nline2\nline3\n");
            // addMarker should not throw even when no rendering is active
            ctrl.addMarker(5, MarkerType.ERROR, "bad line");
            ctrl.addMarker(0, MarkerType.WARNING, "warning at start");
            ctrl.addMarker(100, MarkerType.INFO, "info beyond file end");
        });
    }

    @Test
    public void clearMarkersDoesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("content");
            ctrl.addMarker(1, MarkerType.ERROR, "error");
            ctrl.addMarker(2, MarkerType.WARNING, "warning");
            // clearMarkers should remove all markers without throwing
            ctrl.clearMarkers();
            // Re-adding after clear should also work
            ctrl.addMarker(1, MarkerType.ERROR, "new error");
        });
    }

    @Test
    public void setSyntaxDefinitionAcceptsNull() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            // null should fall back to PlainTextSyntax without throwing
            ctrl.setSyntaxDefinition(null);
            assertNotNull("syntaxDef must not be null after setSyntaxDefinition(null)",
                    ctrl.getSyntaxDefinition());
        });
    }

    @Test
    public void setTextNullHandledGracefully() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText(null);
            assertEquals("null setText should produce empty string", "", ctrl.getText());
        });
    }

    @Test
    public void initialStateIsClean() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            assertFalse("freshly constructed control should not be dirty", ctrl.isDirty());
        });
    }

    // ── Phase 4: MarkerRenderer integration tests ───────────────────────────

    @Test
    public void addMarkerDelegatedToMarkerRenderer() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("line1\nline2\nline3\n");
            ctrl.addMarker(3, MarkerType.ERROR, "oops");
            assertTrue("markerRenderer must contain entry for line 3",
                    ctrl.getMarkerRenderer().getMarkers().containsKey(3));
        });
    }

    @Test
    public void clearMarkersDelegatedToMarkerRenderer() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.addMarker(3, MarkerType.ERROR, "oops");
            ctrl.clearMarkers();
            assertTrue("markerRenderer must be empty after clearMarkers()",
                    ctrl.getMarkerRenderer().getMarkers().isEmpty());
        });
    }

    @Test
    public void addClearAddLeavesOnlySecondMarker() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            ctrl.setText("line1\nline2\nline3\n");
            ctrl.addMarker(1, MarkerType.ERROR, "first");
            ctrl.clearMarkers();
            ctrl.addMarker(2, MarkerType.WARNING, "second");

            java.util.Map<Integer, java.util.List<MarkerRenderer.MarkerInfo>> markers =
                    ctrl.getMarkerRenderer().getMarkers();
            assertEquals("Only the second marker should survive", 1, markers.size());
            assertTrue("Line 2 must be present", markers.containsKey(2));
            assertFalse("Line 1 must be absent after clear", markers.containsKey(1));
        });
    }

    @Test
    public void goToLinePositionsCaretAtCorrectOffset() throws Exception {
        runOnFxAndWait(() -> {
            NativeEditorControl ctrl = new NativeEditorControl();
            // "abc\ndef\nghi\n" — line 2 starts at offset 4
            ctrl.setText("abc\ndef\nghi\n");
            ctrl.goToLine(2);
            // After goToLine(2), caret should be at the start of "def" (offset 4)
            assertEquals("Caret must be at start of line 2 (offset 4)", 4, ctrl.getCaretPositionForTest());
        });
    }
}
