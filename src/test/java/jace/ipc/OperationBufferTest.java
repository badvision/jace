package jace.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import jace.core.Debugger;

public class OperationBufferTest {

    private OperationBuffer buffer;

    /** Minimal concrete Debugger for testing — no emulator required. */
    private static final Debugger TEST_DEBUGGER = new Debugger() {
        @Override
        public void updateStatus() {
            // no-op
        }
    };

    @Before
    public void setUp() {
        buffer = new OperationBuffer();
        // Reset static Debugger breakpoint list between tests
        TEST_DEBUGGER.getBreakpoints().clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CyreneOperation opAtPc(int pc) {
        return new CyreneOperation(
                0, 0, 0, pc, 0,
                (byte) 0x20,
                (byte) 0xEA, (byte) 0, (byte) 0, (byte) 0,
                0, 0, 0,
                0, 0,
                (byte) 0,
                0L, 0L,
                (byte) 0, (byte) 0);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testRecordAndDrain() {
        buffer.record(opAtPc(0x1000));
        buffer.record(opAtPc(0x2000));
        buffer.record(opAtPc(0x3000));

        assertEquals(3, buffer.size());

        List<CyreneOperation> drained = buffer.drainAndFlush();

        assertEquals(3, drained.size());
        assertEquals(0x1000, drained.get(0).pc);
        assertEquals(0x2000, drained.get(1).pc);
        assertEquals(0x3000, drained.get(2).pc);

        // Buffer must be empty after drain
        assertEquals(0, buffer.size());
    }

    @Test
    public void testFlushThresholdPreventsUnboundedGrowth() {
        int recordCount = IpcConstants.OPERATION_FLUSH_THRESHOLD + 10;
        for (int i = 0; i < recordCount; i++) {
            buffer.record(opAtPc(i));
        }

        assertTrue(
                "Buffer size must not exceed OPERATION_FLUSH_THRESHOLD",
                buffer.size() <= IpcConstants.OPERATION_FLUSH_THRESHOLD);
    }

    @Test
    public void testConcurrentRecordAndDrain() throws InterruptedException {
        int opsPerThread = 500;
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        // Writer thread 1
        Thread writer1 = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < opsPerThread; i++) {
                    buffer.record(opAtPc(i));
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        // Writer thread 2
        Thread writer2 = new Thread(() -> {
            try {
                start.await();
                for (int i = opsPerThread; i < opsPerThread * 2; i++) {
                    buffer.record(opAtPc(i));
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        writer1.start();
        writer2.start();
        start.countDown();
        done.await();

        // No ConcurrentModificationException or other error should have occurred
        assertNull("Concurrent access produced an exception: " + error.get(), error.get());

        // Drain and verify total count is bounded
        int remaining = buffer.size();
        List<CyreneOperation> drained = buffer.drainAndFlush();
        int total = remaining + drained.size();

        // After the drain the buffer is empty; the ops we just got + remaining == total
        // Total recorded (before threshold trimming) was opsPerThread * 2 = 1000.
        // Because of the flush threshold, total must be <= FLUSH_THRESHOLD and >= 0.
        assertTrue("Total ops must be >= 0", total >= 0);
        assertTrue("Total ops must not exceed FLUSH_THRESHOLD",
                total <= IpcConstants.OPERATION_FLUSH_THRESHOLD);
    }

    @Test
    public void testCheckStopConditionWithBreakpointMatch() {
        BreakpointBridge bridge = new BreakpointBridge(TEST_DEBUGGER);
        bridge.updateFromText("1;Address=00/4000;Execute;Action:Stop;Repeat:Always\n");

        CyreneOperation op = opAtPc(0x4000);
        Byte result = buffer.checkStopCondition(op, bridge);

        assertNotNull("Expected a stop condition for matched breakpoint", result);
        assertEquals(IpcConstants.STOP_BREAKPOINT, result.byteValue());
    }

    @Test
    public void testCheckStopConditionNoMatch() {
        BreakpointBridge bridge = new BreakpointBridge(TEST_DEBUGGER);
        bridge.updateFromText("1;Address=00/4000;Execute;Action:Stop;Repeat:Always\n");

        CyreneOperation op = opAtPc(0x5000);
        Byte result = buffer.checkStopCondition(op, bridge);

        assertNull("Expected no stop condition for unmatched PC", result);
    }
}
