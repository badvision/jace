package jace.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Tests for CyreneSession framing and dispatch logic.
 * Uses in-memory streams — no network, no emulator required.
 */
public class CyreneSessionTest {

    // -----------------------------------------------------------------------
    // Helper: build a raw frame byte array
    // -----------------------------------------------------------------------

    private byte[] buildFrame(int type, byte[] payload) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CyreneSession.writeInt(buf, type);
        CyreneSession.writeInt(buf, payload.length);
        buf.write(payload);
        return buf.toByteArray();
    }

    // -----------------------------------------------------------------------
    // 1. Frame round-trip: write an int, read it back
    // -----------------------------------------------------------------------

    @Test
    public void testFrameRoundTrip() throws IOException {
        int original = 0x12345678;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CyreneSession.writeInt(buf, original);

        InputStream in = new ByteArrayInputStream(buf.toByteArray());
        int read = CyreneSession.readInt(in);

        assertEquals(original, read);
    }

    @Test
    public void testFrameRoundTripZero() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CyreneSession.writeInt(buf, 0);
        int read = CyreneSession.readInt(new ByteArrayInputStream(buf.toByteArray()));
        assertEquals(0, read);
    }

    @Test
    public void testFrameRoundTripNegative() throws IOException {
        // Verify little-endian preservation for all bit patterns
        int original = 0xDEADBEEF;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CyreneSession.writeInt(buf, original);
        int read = CyreneSession.readInt(new ByteArrayInputStream(buf.toByteArray()));
        assertEquals(original, read);
    }

    // -----------------------------------------------------------------------
    // 2. C2K_CLOSE_CONNECTION -> K2C_CLOSE_CONNECTION response
    // -----------------------------------------------------------------------

    @Test
    public void testCloseConnectionResponse() throws Exception {
        // Pipe from test -> session (session reads from pipeIn)
        PipedOutputStream testToSession = new PipedOutputStream();
        PipedInputStream  pipeIn        = new PipedInputStream(testToSession);

        // Pipe from session -> test (test reads from pipeOut)
        PipedInputStream  testFromSession = new PipedInputStream();
        PipedOutputStream pipeOut         = new PipedOutputStream(testFromSession);

        CyreneSession session = new CyreneSession(pipeIn, pipeOut);

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        Thread sessionThread = new Thread(() -> {
            try {
                session.run();
            } catch (Exception e) {
                error.set(e);
            } finally {
                done.countDown();
            }
        }, "test-session");
        sessionThread.setDaemon(true);
        sessionThread.start();

        // Send C2K_CLOSE_CONNECTION frame (no payload)
        testToSession.write(buildFrame(IpcConstants.C2K_CLOSE_CONNECTION, new byte[0]));
        testToSession.flush();

        // Wait for session to finish (it closes after handling close)
        boolean finished = done.await(5, TimeUnit.SECONDS);

        // Read the response type from the session's output
        int responseType = CyreneSession.readInt(testFromSession);
        int responseLength = CyreneSession.readInt(testFromSession);

        assertEquals("Session should finish after close", true, finished);
        assertEquals("No exception expected in session thread: " + error.get(), null, error.get());
        assertEquals("Response type must be K2C_CLOSE_CONNECTION",
                IpcConstants.K2C_CLOSE_CONNECTION, responseType);
        assertEquals("Response payload length must be 0", 0, responseLength);
    }

    // -----------------------------------------------------------------------
    // 3. C2K_PAUSE must not throw even when emulator is not running
    // -----------------------------------------------------------------------

    @Test
    public void testPauseDoesNotCrash() throws IOException {
        // Build input: C2K_PAUSE frame followed by C2K_CLOSE_CONNECTION to exit loop
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(buildFrame(IpcConstants.C2K_PAUSE, new byte[0]));
        frames.write(buildFrame(IpcConstants.C2K_CLOSE_CONNECTION, new byte[0]));

        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        CyreneSession session = new CyreneSession(
                new ByteArrayInputStream(frames.toByteArray()), sink);

        // run() must complete without throwing
        // (emulator may not be available — handlePause swallows the exception)
        session.run();

        // Verify the session processed both frames (close response was written)
        byte[] response = sink.toByteArray();
        // At minimum: 8 bytes for the close-connection response header
        assertEquals("Response must contain at least one frame header",
                true, response.length >= IpcConstants.FRAME_HEADER_SIZE);
    }

    // -----------------------------------------------------------------------
    // 4. Unknown frame type is ignored — session continues
    // -----------------------------------------------------------------------

    @Test
    public void testUnknownTypeIgnored() throws IOException {
        // Unknown type 99, then a close to allow the session to exit cleanly
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(buildFrame(99, new byte[]{1, 2, 3}));
        frames.write(buildFrame(IpcConstants.C2K_CLOSE_CONNECTION, new byte[0]));

        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        CyreneSession session = new CyreneSession(
                new ByteArrayInputStream(frames.toByteArray()), sink);

        // Should not throw; unknown type is logged and skipped
        session.run();

        byte[] response = sink.toByteArray();
        // Close response must still be present
        InputStream respStream = new ByteArrayInputStream(response);
        int type = CyreneSession.readInt(respStream);
        assertEquals("Close response type must be K2C_CLOSE_CONNECTION",
                IpcConstants.K2C_CLOSE_CONNECTION, type);
    }
}
