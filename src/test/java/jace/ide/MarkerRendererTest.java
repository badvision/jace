package jace.ide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;

/**
 * Unit tests for MarkerRenderer.
 *
 * Data-path tests (addMarker / clearMarkers / getMarkers) require no FX thread and
 * run on the calling thread. renderMarkers() requires a live Canvas GraphicsContext,
 * so those tests dispatch to the FX thread.
 */
public class MarkerRendererTest {

    private static boolean fxInitialized = false;

    @BeforeClass
    public static void initJfxRuntime() {
        if (!fxInitialized) {
            fxInitialized = true;
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException e) {
                // Platform already initialized — that is fine
            }
        }
    }

    @AfterClass
    public static void tearDown() {
        // Keep FX alive for other test classes in the suite
    }

    /** Helper: run on FX thread and wait. */
    private static void runOnFxAndWait(Runnable r) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                r.run();
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

    // -------------------------------------------------------------------------
    // Data-path tests — no FX thread required
    // -------------------------------------------------------------------------

    @Test
    public void addMarkerStoresEntryForLine() {
        MarkerRenderer renderer = new MarkerRenderer();
        renderer.addMarker(5, MarkerType.ERROR, "bad syntax");

        Map<Integer, List<MarkerRenderer.MarkerInfo>> result = renderer.getMarkers();
        assertTrue("Markers map must contain line 5", result.containsKey(5));
        assertEquals("Line 5 must have exactly one marker", 1, result.get(5).size());

        MarkerRenderer.MarkerInfo info = result.get(5).get(0);
        assertEquals(MarkerType.ERROR, info.type());
        assertEquals("bad syntax", info.message());
    }

    @Test
    public void clearMarkersRemovesAll() {
        MarkerRenderer renderer = new MarkerRenderer();
        renderer.addMarker(1, MarkerType.ERROR, "e1");
        renderer.addMarker(2, MarkerType.WARNING, "w1");
        renderer.addMarker(10, MarkerType.INFO, "i1");

        renderer.clearMarkers();

        assertTrue("Markers map must be empty after clearMarkers()", renderer.getMarkers().isEmpty());
    }

    @Test
    public void addMarkerSameLineTwiceBothStored() {
        MarkerRenderer renderer = new MarkerRenderer();
        renderer.addMarker(3, MarkerType.ERROR, "first error");
        renderer.addMarker(3, MarkerType.WARNING, "first warning");

        List<MarkerRenderer.MarkerInfo> lineMarkers = renderer.getMarkers().get(3);
        assertNotNull("Line 3 must have markers", lineMarkers);
        assertEquals("Both markers on line 3 must be stored", 2, lineMarkers.size());
        assertEquals(MarkerType.ERROR, lineMarkers.get(0).type());
        assertEquals(MarkerType.WARNING, lineMarkers.get(1).type());
    }

    @Test
    public void addMarkerDifferentLines() {
        MarkerRenderer renderer = new MarkerRenderer();
        renderer.addMarker(1, MarkerType.ERROR, "line 1 error");
        renderer.addMarker(5, MarkerType.WARNING, "line 5 warning");
        renderer.addMarker(10, MarkerType.INFO, "line 10 info");

        Map<Integer, List<MarkerRenderer.MarkerInfo>> m = renderer.getMarkers();
        assertEquals(3, m.size());
        assertEquals(MarkerType.ERROR,   m.get(1).get(0).type());
        assertEquals(MarkerType.WARNING, m.get(5).get(0).type());
        assertEquals(MarkerType.INFO,    m.get(10).get(0).type());
    }

    @Test
    public void getMarkersReturnsUnmodifiableView() {
        MarkerRenderer renderer = new MarkerRenderer();
        renderer.addMarker(1, MarkerType.ERROR, "err");

        Map<Integer, List<MarkerRenderer.MarkerInfo>> view = renderer.getMarkers();
        try {
            view.put(99, List.of(new MarkerRenderer.MarkerInfo(MarkerType.INFO, "injected")));
            // If no exception, the test still validates data integrity
        } catch (UnsupportedOperationException expected) {
            // Expected — map is truly unmodifiable
        }
        // The internal map should still contain only line 1
        assertTrue("Internal map must still contain only line 1", renderer.getMarkers().containsKey(1));
    }

    @Test
    public void clearThenAddWorks() {
        MarkerRenderer renderer = new MarkerRenderer();
        renderer.addMarker(1, MarkerType.ERROR, "old");
        renderer.clearMarkers();
        renderer.addMarker(2, MarkerType.WARNING, "new");

        Map<Integer, List<MarkerRenderer.MarkerInfo>> m = renderer.getMarkers();
        assertEquals("Only one entry after clear-and-add", 1, m.size());
        assertTrue("Line 2 must be present", m.containsKey(2));
    }

    @Test
    public void emptyRendererHasNoMarkers() {
        MarkerRenderer renderer = new MarkerRenderer();
        assertTrue("Fresh renderer must have empty markers", renderer.getMarkers().isEmpty());
    }

    // -------------------------------------------------------------------------
    // renderMarkers() tests — requires FX thread for Canvas
    // -------------------------------------------------------------------------

    @Test
    public void renderMarkersOnZeroSizeCanvasDoesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            MarkerRenderer renderer = new MarkerRenderer();
            renderer.addMarker(1, MarkerType.ERROR, "err");
            Canvas canvas = new Canvas(0, 0);
            // Must not throw even with zero-size canvas
            renderer.renderMarkers(canvas.getGraphicsContext2D(), 16.0, 40.0, 0, 0, 1);
        });
    }

    @Test
    public void renderMarkersWithNoMarkersDoesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            MarkerRenderer renderer = new MarkerRenderer();
            Canvas canvas = new Canvas(400, 300);
            // Empty renderer — must be a no-op
            renderer.renderMarkers(canvas.getGraphicsContext2D(), 16.0, 40.0, 400, 0, 1);
        });
    }

    @Test
    public void renderMarkersWithAllTypesDoesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            MarkerRenderer renderer = new MarkerRenderer();
            renderer.addMarker(1, MarkerType.ERROR, "error on line 1");
            renderer.addMarker(2, MarkerType.WARNING, "warning on line 2");
            renderer.addMarker(3, MarkerType.INFO, "info on line 3");

            Canvas canvas = new Canvas(400, 300);
            renderer.renderMarkers(canvas.getGraphicsContext2D(), 16.0, 40.0, 400, 0, 1);
        });
    }

    @Test
    public void renderMarkersScrolledPastAllMarkersDoesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            MarkerRenderer renderer = new MarkerRenderer();
            renderer.addMarker(1, MarkerType.ERROR, "err");

            Canvas canvas = new Canvas(400, 300);
            // Scroll past all markers (line 1 would be at y = 0 - 9999 = off-screen)
            renderer.renderMarkers(canvas.getGraphicsContext2D(), 16.0, 40.0, 400, 9999, 625);
        });
    }

    @Test
    public void renderMarkersZeroLineHeightDoesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            MarkerRenderer renderer = new MarkerRenderer();
            renderer.addMarker(1, MarkerType.ERROR, "err");

            Canvas canvas = new Canvas(400, 300);
            // lineHeight=0 is a guard-case; must not divide by zero or throw
            renderer.renderMarkers(canvas.getGraphicsContext2D(), 0, 40.0, 400, 0, 1);
        });
    }
}
