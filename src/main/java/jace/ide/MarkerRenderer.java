package jace.ide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Renders error/warning/info markers on a JavaFX Canvas.
 *
 * <p>Markers are stored by 1-based line number (matching CompileResult.getErrors() /
 * getWarnings() key convention from AcmeCompiler). The renderMarkers() method converts
 * to pixel Y-positions using: y = (line - 1) * lineHeight - scrollOffset.
 *
 * <p>This class is deliberately standalone — no JavaFX scene graph dependency on the
 * data-path methods (addMarker, clearMarkers, getMarkers). Only renderMarkers() requires
 * a live GraphicsContext, allowing unit tests to verify marker storage without an FX thread.
 *
 * <p>NativeEditorControl calls renderMarkers() during its canvas repaint cycle, after
 * syntax tints are painted and before any text overlay.
 */
public class MarkerRenderer {

    /**
     * Immutable value type for a single marker entry.
     */
    public record MarkerInfo(MarkerType type, String message) {}

    /** Line number (1-based) → ordered list of markers on that line. */
    private final Map<Integer, List<MarkerInfo>> markers = new LinkedHashMap<>();

    /**
     * Adds a marker at the given 1-based line number. Multiple markers on the same
     * line accumulate in insertion order.
     *
     * @param line    1-based line number (as delivered by CompileResult.getErrors() /
     *                getWarnings() — no caller-side offset adjustment is needed)
     * @param type    marker severity
     * @param message human-readable description shown in the gutter / tooltip
     */
    public void addMarker(int line, MarkerType type, String message) {
        markers.computeIfAbsent(line, k -> new ArrayList<>())
               .add(new MarkerInfo(type, message));
    }

    /** Removes all markers. */
    public void clearMarkers() {
        markers.clear();
    }

    /**
     * Returns an unmodifiable view of the marker map for tooltip lookup.
     * Key = 1-based line number, value = ordered list of markers on that line.
     */
    public Map<Integer, List<MarkerInfo>> getMarkers() {
        return Collections.unmodifiableMap(markers);
    }

    /**
     * Paints all visible markers onto the supplied GraphicsContext.
     *
     * <p>Called by NativeEditorControl during its canvas repaint cycle. The gc should
     * cover the full editor area (gutter + text area). Rendering is additive — callers
     * are responsible for clearing the canvas before the full repaint cycle.
     *
     * @param gc              graphics context to paint onto
     * @param lineHeight      height of one text line in pixels (must be &gt; 0)
     * @param gutterWidth     width of the line-number gutter in pixels
     * @param width           total canvas width in pixels
     * @param scrollOffset    vertical scroll offset in pixels (0 = top)
     * @param firstVisibleLine 1-based index of the first line visible in the viewport
     */
    public void renderMarkers(GraphicsContext gc, double lineHeight, double gutterWidth,
                              double width, double scrollOffset, int firstVisibleLine) {
        if (markers.isEmpty() || lineHeight <= 0 || width <= 0) {
            return;
        }

        double canvasHeight = gc.getCanvas().getHeight();
        // Add 2 extra lines to account for partial visibility at viewport edges
        int lastVisibleLine = firstVisibleLine + (int) Math.ceil(canvasHeight / lineHeight) + 2;

        for (Map.Entry<Integer, List<MarkerInfo>> entry : markers.entrySet()) {
            int line = entry.getKey();  // 1-based
            if (line < firstVisibleLine || line > lastVisibleLine) {
                continue;
            }
            List<MarkerInfo> lineMarkers = entry.getValue();
            if (lineMarkers.isEmpty()) {
                continue;
            }

            // Use first (highest-priority by insertion order) marker for coloring.
            // ERROR takes precedence over WARNING takes precedence over INFO.
            MarkerInfo primary = selectPrimary(lineMarkers);

            // Convert 1-based line to pixel Y coordinate
            double y = (line - 1) * lineHeight - scrollOffset;

            // Skip if entirely outside the visible area
            if (y + lineHeight < 0 || y > canvasHeight) {
                continue;
            }

            Color bgColor = backgroundColorFor(primary.type());
            Color stripeColor = stripeColorFor(primary.type());

            // 1. Full-width background tint (text area only, not gutter)
            gc.setFill(bgColor);
            gc.fillRect(gutterWidth, y, Math.max(0, width - gutterWidth), lineHeight);

            // 2. Left-border stripe (4 px) in the gutter
            gc.setFill(stripeColor);
            gc.fillRect(0, y, 4, lineHeight);

            // 3. Underline at the bottom of the line (text area portion)
            gc.setStroke(stripeColor);
            gc.setLineWidth(1.0);
            double underlineY = y + lineHeight - 1;
            gc.strokeLine(gutterWidth, underlineY, width, underlineY);
        }
    }

    // --- helpers ---

    private static MarkerInfo selectPrimary(List<MarkerInfo> lineMarkers) {
        for (MarkerInfo m : lineMarkers) {
            if (m.type() == MarkerType.ERROR) return m;
        }
        for (MarkerInfo m : lineMarkers) {
            if (m.type() == MarkerType.WARNING) return m;
        }
        return lineMarkers.get(0);
    }

    private static Color backgroundColorFor(MarkerType type) {
        return switch (type) {
            case ERROR   -> Color.color(1.0, 0.2, 0.2, 0.15);
            case WARNING -> Color.color(1.0, 0.8, 0.0, 0.15);
            case INFO    -> Color.color(0.2, 0.5, 1.0, 0.10);
        };
    }

    private static Color stripeColorFor(MarkerType type) {
        return switch (type) {
            case ERROR   -> Color.RED;
            case WARNING -> Color.YELLOW;
            case INFO    -> Color.CORNFLOWERBLUE;
        };
    }
}
