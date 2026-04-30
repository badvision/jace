package jace.ipc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe accumulator for per-instruction CyreneOperation records.
 *
 * Called from the CPU thread at ~1 MHz; all mutations are synchronized.
 * When the buffer exceeds OPERATION_FLUSH_THRESHOLD, the oldest entries
 * are dropped to prevent unbounded growth.
 */
class OperationBuffer {

    private final ArrayList<CyreneOperation> buffer =
            new ArrayList<>(IpcConstants.OPERATION_FLUSH_THRESHOLD);

    /**
     * Record one operation. Drops the oldest entries if the buffer exceeds
     * OPERATION_FLUSH_THRESHOLD.
     */
    synchronized void record(CyreneOperation op) {
        buffer.add(op);
        if (buffer.size() > IpcConstants.OPERATION_FLUSH_THRESHOLD) {
            int excess = buffer.size() - IpcConstants.OPERATION_FLUSH_THRESHOLD;
            buffer.subList(0, excess).clear();
        }
    }

    /**
     * Returns a snapshot of the current buffer contents and clears the buffer.
     */
    synchronized List<CyreneOperation> drainAndFlush() {
        List<CyreneOperation> snapshot = new ArrayList<>(buffer);
        buffer.clear();
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * Returns the current number of buffered operations.
     */
    synchronized int size() {
        return buffer.size();
    }

    /**
     * Checks whether op matches any registered breakpoint.
     * Not synchronized — read-only check on the BreakpointBridge.
     *
     * @return STOP_BREAKPOINT as a Byte if a breakpoint matches, null otherwise
     */
    Byte checkStopCondition(CyreneOperation op, BreakpointBridge breakpoints) {
        if (breakpoints.checkBreakpoint(op) >= 0) {
            return IpcConstants.STOP_BREAKPOINT;
        }
        return null;
    }
}
