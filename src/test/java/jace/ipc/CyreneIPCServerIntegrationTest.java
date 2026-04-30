package jace.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jace.AbstractJaceTest;

/**
 * Integration tests for CyreneIPCServer: connect with a real TCP socket,
 * send protocol frames, and verify responses.
 */
public class CyreneIPCServerIntegrationTest extends AbstractJaceTest {

    private CyreneIPCServer server;
    private int testPort;

    @Before
    public void startServer() throws Exception {
        server = CyreneIPCServer.getInstance();
        // Find a free ephemeral port
        try (ServerSocket probe = new ServerSocket(0)) {
            testPort = probe.getLocalPort();
        }
        server.port = testPort;
        server.enabled = true;
        server.start();

        // Give the acceptor thread a moment to bind
        Thread.sleep(100);
    }

    @After
    public void stopServer() {
        server.stop();
        server.enabled = false;
        server.port = IpcConstants.DEFAULT_PORT;
    }

    // -----------------------------------------------------------------------
    // Helper: write a frame to an OutputStream
    // -----------------------------------------------------------------------

    private static void sendFrame(OutputStream out, int type, byte[] payload) throws IOException {
        CyreneSession.writeInt(out, type);
        CyreneSession.writeInt(out, payload.length);
        out.write(payload);
        out.flush();
    }

    // -----------------------------------------------------------------------
    // Helper: read response type and payload from InputStream
    // -----------------------------------------------------------------------

    private static int[] readResponseHeader(InputStream in) throws IOException {
        int type = CyreneSession.readInt(in);
        int length = CyreneSession.readInt(in);
        return new int[]{type, length};
    }

    private static byte[] readResponsePayload(InputStream in, int length) throws IOException {
        byte[] buf = new byte[Math.max(0, length)];
        int remaining = buf.length;
        int offset = 0;
        while (remaining > 0) {
            int n = in.read(buf, offset, remaining);
            if (n < 0) {
                throw new IOException("EOF reading response payload");
            }
            offset += n;
            remaining -= n;
        }
        return buf;
    }

    // -----------------------------------------------------------------------
    // Test 1: server accepts connection and responds to C2K_OPEN_CONNECTION
    // -----------------------------------------------------------------------

    @Test
    public void testServerStartsAndAcceptsConnection() throws Exception {
        AtomicBoolean connected = new AtomicBoolean(false);

        try (Socket client = new Socket("127.0.0.1", testPort)) {
            connected.set(true);

            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // Send C2K_OPEN_CONNECTION (no payload)
            sendFrame(out, IpcConstants.C2K_OPEN_CONNECTION, new byte[0]);

            // Read ACK — server replies with K2C_SEND_SNAPSHOT (possibly empty payload)
            int[] header = readResponseHeader(in);

            assertEquals("Open-connection ACK must be K2C_SEND_SNAPSHOT",
                    IpcConstants.K2C_SEND_SNAPSHOT, header[0]);
        }

        assertTrue("Client must have connected", connected.get());

        // Wait briefly for session to be registered
        long deadline = System.currentTimeMillis() + 2000;
        while (!server.isActive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        // Note: isActive() may be false here since connection was closed —
        // the important assertion is that the server accepted and responded.
    }

    // -----------------------------------------------------------------------
    // Test 2: C2K_GET_SNAPSHOT returns correct size payload
    // -----------------------------------------------------------------------

    @Test
    public void testSnapshotResponseSize() throws Exception {
        try (Socket client = new Socket("127.0.0.1", testPort)) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // Open connection first
            sendFrame(out, IpcConstants.C2K_OPEN_CONNECTION, new byte[0]);
            int[] openHeader = readResponseHeader(in);
            // Drain the open-connection ACK payload (may be 0 or non-zero bytes)
            readResponsePayload(in, openHeader[1]);

            // Request snapshot
            sendFrame(out, IpcConstants.C2K_GET_SNAPSHOT, new byte[0]);

            int[] header = readResponseHeader(in);
            assertEquals("Snapshot response type must be K2C_SEND_SNAPSHOT",
                    IpcConstants.K2C_SEND_SNAPSHOT, header[0]);
            assertEquals("Snapshot payload must be exactly SNAP_TOTAL_SIZE bytes",
                    IpcConstants.SNAP_TOTAL_SIZE, header[1]);
        }
    }

    // -----------------------------------------------------------------------
    // Test 3: snapshot payload contains ROM_VERSION_APPLE_IIE discriminator
    // -----------------------------------------------------------------------

    @Test
    public void testSnapshotHasCorrectDiscriminators() throws Exception {
        try (Socket client = new Socket("127.0.0.1", testPort)) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // Open connection
            sendFrame(out, IpcConstants.C2K_OPEN_CONNECTION, new byte[0]);
            int[] openHeader = readResponseHeader(in);
            readResponsePayload(in, openHeader[1]);

            // Get snapshot
            sendFrame(out, IpcConstants.C2K_GET_SNAPSHOT, new byte[0]);
            int[] snapHeader = readResponseHeader(in);
            assertEquals(IpcConstants.K2C_SEND_SNAPSHOT, snapHeader[0]);

            byte[] payload = readResponsePayload(in, snapHeader[1]);

            // Check ROM version discriminator at OP_OFF_ROM_VERSION within the op block
            int romVersionOffset = IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_ROM_VERSION;
            assertEquals("ROM version must be ROM_VERSION_APPLE_IIE (0x00)",
                    IpcConstants.ROM_VERSION_APPLE_IIE, payload[romVersionOffset]);

            // Check RAM banks discriminator
            int ramBanksOffset = IpcConstants.SNAP_OFF_NEXT_OP + IpcConstants.OP_OFF_RAM_BANKS;
            assertEquals("RAM banks must be RAM_BANKS_APPLE_IIE (2)",
                    IpcConstants.RAM_BANKS_APPLE_IIE, payload[ramBanksOffset]);
        }
    }
}
