package jace.ipc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.core.Debugger;
import jace.core.RAM;
import jace.core.RAMEvent;

/**
 * Manages one connected Cyrene client session over TCP.
 * All ints on the wire are little-endian 4-byte values.
 */
class CyreneSession implements Runnable {

    private static final Logger LOG = Logger.getLogger(CyreneSession.class.getName());

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private volatile boolean closed = false;

    volatile boolean tracingOperations = false;
    final AtomicLong goid = new AtomicLong(0);
    final AtomicLong gcc = new AtomicLong(0);
    volatile byte[] lastPayload;

    private final OperationBuffer operationBuffer;
    private final BreakpointBridge breakpointBridge;
    private final SnapshotBuilder snapshotBuilder;

    /** True once a breakpoint stop has been triggered; cleared on next GET_SNAPSHOT. */
    private volatile boolean pendingBreakpointStop = false;
    private volatile int pendingBreakpointId = -1;

    /** Production constructor — uses the socket's streams. */
    CyreneSession(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.operationBuffer = new OperationBuffer();
        this.breakpointBridge = new BreakpointBridge(buildNullDebugger());
        this.snapshotBuilder = new SnapshotBuilder();
    }

    /** Test constructor — uses caller-supplied streams; socket is null. */
    CyreneSession(InputStream in, OutputStream out) {
        this.socket = null;
        this.in = in;
        this.out = out;
        this.operationBuffer = new OperationBuffer();
        this.breakpointBridge = new BreakpointBridge(buildNullDebugger());
        this.snapshotBuilder = new SnapshotBuilder();
    }

    // -----------------------------------------------------------------------
    // Runnable: I/O loop
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        try {
            int[] header;
            while (!closed && (header = readFrame()) != null) {
                int type = header[0];
                dispatch(type);
            }
        } catch (IOException e) {
            if (!isClosed()) {
                LOG.log(Level.WARNING, "Cyrene session I/O error", e);
            }
        } finally {
            close();
        }
    }

    private void dispatch(int type) throws IOException {
        switch (type) {
            case IpcConstants.C2K_OPEN_CONNECTION:
                handleOpenConnection();
                break;
            case IpcConstants.C2K_CLOSE_CONNECTION:
                handleCloseConnection();
                break;
            case IpcConstants.C2K_GET_SNAPSHOT:
                handleGetSnapshot();
                break;
            case IpcConstants.C2K_GET_OPERATION:
                handleGetOperation();
                break;
            case IpcConstants.C2K_PAUSE:
                handlePause();
                break;
            case IpcConstants.C2K_WRITE_DATA:
                handleWriteData(lastPayload);
                break;
            default:
                LOG.warning("Unknown Cyrene frame type: " + type + " — ignoring");
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Handlers
    // -----------------------------------------------------------------------

    private void handleOpenConnection() throws IOException {
        goid.set(0);
        gcc.set(0);
        tracingOperations = false;
        pendingBreakpointStop = false;
        pendingBreakpointId = -1;
        // ACK with an empty snapshot placeholder; real snapshot comes on C2K_GET_SNAPSHOT
        sendFrame(IpcConstants.K2C_SEND_SNAPSHOT, new byte[0]);
    }

    void handleCloseConnection() throws IOException {
        sendFrame(IpcConstants.K2C_CLOSE_CONNECTION, new byte[0]);
        close();
    }

    void handleGetSnapshot() throws IOException {
        tracingOperations = false;
        CyreneOperation currentOp = buildCurrentOperation();

        byte stopCond = IpcConstants.STOP_NONE;
        int bpId = 0;
        if (pendingBreakpointStop) {
            stopCond = IpcConstants.STOP_BREAKPOINT;
            bpId = pendingBreakpointId;
            pendingBreakpointStop = false;
            pendingBreakpointId = -1;
        }

        byte[] snapshot = snapshotBuilder.build(currentOp, stopCond, bpId);
        sendFrame(IpcConstants.K2C_SEND_SNAPSHOT, snapshot);
    }

    void handleGetOperation() throws IOException {
        tracingOperations = true;
        // Resume emulator so it can generate operations
        try {
            Emulator.withComputer(c -> c.resume());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "handleGetOperation: emulator not available", e);
        }

        // Drain any buffered operations and serialize them
        List<CyreneOperation> ops = operationBuffer.drainAndFlush();
        if (ops.isEmpty()) {
            sendFrame(IpcConstants.K2C_SEND_OPERATION, new byte[0]);
            return;
        }

        byte[] payload = serializeOperations(ops);
        sendFrame(IpcConstants.K2C_SEND_OPERATION, payload);
    }

    void handlePause() {
        if (Emulator.instance == null) {
            return;
        }
        try {
            Emulator.withComputer(c -> c.pause());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "handlePause: emulator not available", e);
        }
    }

    void handleWriteData(byte[] payload) {
        new MemoryWriteHandler().apply(payload);
    }

    // -----------------------------------------------------------------------
    // Notification entry points (called from emulator thread)
    // -----------------------------------------------------------------------

    void onInstruction(CyreneOperation op) {
        if (!tracingOperations) {
            return;
        }
        goid.incrementAndGet();
        gcc.addAndGet(1); // approximate: 1 gcc per instruction

        operationBuffer.record(op);

        int bpMatch = breakpointBridge.checkBreakpoint(op);
        if (bpMatch >= 0) {
            tracingOperations = false;
            pendingBreakpointStop = true;
            pendingBreakpointId = bpMatch;
            try {
                Emulator.withComputer(c -> c.pause());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "onInstruction: could not pause emulator", e);
            }
        }
    }

    void onVBL() {
        // Future: implement VBL-triggered operation flush
    }

    boolean isTracingOperations() {
        return tracingOperations;
    }

    // -----------------------------------------------------------------------
    // Operation building helpers
    // -----------------------------------------------------------------------

    /**
     * Reads current CPU state from the emulator and builds a CyreneOperation.
     * Returns a zero-filled operation if the emulator is not available.
     */
    CyreneOperation buildCurrentOperation() {
        try {
            return Emulator.withComputer(c -> {
                MOS65C02 cpu = (MOS65C02) c.getCpu();
                if (cpu == null) {
                    return zeroOperation();
                }
                int pc = cpu.getProgramCounter();
                byte opcode = readMemByte(c.getMemory(), pc);
                byte op1 = readMemByte(c.getMemory(), pc + 1);
                byte op2 = readMemByte(c.getMemory(), pc + 2);
                byte op3 = readMemByte(c.getMemory(), pc + 3);

                byte flags = CyreneOperation.packFlags(cpu.N, cpu.V, cpu.B, cpu.D, cpu.I, cpu.Z, cpu.C);

                return new CyreneOperation(
                        cpu.A, cpu.X, cpu.Y, pc, cpu.STACK,
                        flags,
                        opcode, op1, op2, op3,
                        0, 0, 0,
                        0, 0,
                        (byte) 0,
                        goid.get(), gcc.get(),
                        (byte) 0, (byte) 0);
            }, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "buildCurrentOperation: emulator not available", e);
            return null;
        }
    }

    private static byte readMemByte(RAM memory, int address) {
        try {
            return (byte) (memory.read(address & 0xFFFF, RAMEvent.TYPE.READ_DATA, false, false) & 0xFF);
        } catch (Exception e) {
            return 0;
        }
    }

    private static CyreneOperation zeroOperation() {
        return new CyreneOperation(
                0, 0, 0, 0, 0, (byte) 0x20,
                (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                0, 0, 0, 0, 0, (byte) 0,
                0L, 0L, (byte) 0, (byte) 0);
    }

    /**
     * Serializes a list of CyreneOperation into a flat byte array.
     * Each operation occupies IpcConstants.OP_BLOCK_SIZE bytes.
     */
    private static byte[] serializeOperations(List<CyreneOperation> ops) {
        ByteBuffer buf = ByteBuffer.allocate(ops.size() * IpcConstants.OP_BLOCK_SIZE);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (CyreneOperation op : ops) {
            writeOperationBlock(buf, op);
        }
        return buf.array();
    }

    /**
     * Writes one operation's fields into the buffer starting at its current position.
     * Uses explicit position-setting to mirror SnapshotBuilder's layout exactly.
     * Advances the buffer by exactly IpcConstants.OP_BLOCK_SIZE bytes.
     */
    static void writeOperationBlock(ByteBuffer buf, CyreneOperation op) {
        int base = buf.position();

        buf.position(base + IpcConstants.OP_OFF_A);
        buf.putShort((short) (op.a & 0xFFFF));

        buf.position(base + IpcConstants.OP_OFF_X);
        buf.putShort((short) (op.x & 0xFFFF));

        buf.position(base + IpcConstants.OP_OFF_Y);
        buf.putShort((short) (op.y & 0xFFFF));

        buf.position(base + IpcConstants.OP_OFF_PC);
        buf.putShort((short) (op.pc & 0xFFFF));

        buf.position(base + IpcConstants.OP_OFF_PBR);
        buf.put((byte) 0x00);  // PBR
        buf.put((byte) 0x00);  // DBR

        buf.position(base + IpcConstants.OP_OFF_STACK);
        buf.put((byte) (op.stack & 0xFF));
        buf.put((byte) 0x01);

        buf.position(base + IpcConstants.OP_OFF_DIRECT_PAGE);
        buf.putShort((short) 0x0000);

        buf.position(base + IpcConstants.OP_OFF_STATUS);
        buf.put(op.flags);

        buf.position(base + IpcConstants.OP_OFF_OPCODE);
        buf.put(op.opcode);
        buf.put(op.op1);
        buf.put(op.op2);
        buf.put(op.op3);

        buf.position(base + IpcConstants.OP_OFF_READ_ADDR);
        put24(buf, op.readAddr);

        buf.position(base + IpcConstants.OP_OFF_WRITE_ADDR);
        put24(buf, op.writeAddr);

        buf.position(base + IpcConstants.OP_OFF_JUMP_ADDR);
        put24(buf, op.jumpAddr);

        buf.position(base + IpcConstants.OP_OFF_READ_VAL);
        buf.putShort((short) (op.readVal & 0xFFFF));

        buf.position(base + IpcConstants.OP_OFF_WRITE_VAL);
        buf.putShort((short) (op.writeVal & 0xFFFF));

        buf.position(base + IpcConstants.OP_OFF_FLAGS);
        buf.put(op.opFlags);

        buf.position(base + IpcConstants.OP_OFF_CALL_NUM);
        buf.putShort((short) 0);

        buf.position(base + IpcConstants.OP_OFF_ROM_VERSION);
        buf.put(IpcConstants.ROM_VERSION_APPLE_IIE);

        buf.position(base + IpcConstants.OP_OFF_RAM_BANKS);
        buf.put(IpcConstants.RAM_BANKS_APPLE_IIE);

        buf.position(base + IpcConstants.OP_OFF_VERTCNT);
        buf.put(op.vertcnt);

        buf.position(base + IpcConstants.OP_OFF_HORIZCNT);
        buf.put(op.horizcnt);

        buf.position(base + IpcConstants.OP_OFF_GOID);
        buf.putLong(op.goid);

        buf.position(base + IpcConstants.OP_OFF_GCC);
        buf.putLong(op.gcc);

        buf.position(base + IpcConstants.OP_BLOCK_SIZE);
    }

    private static void put24(ByteBuffer buf, int value) {
        buf.put((byte) (value & 0xFF));
        buf.put((byte) ((value >> 8) & 0xFF));
        buf.put((byte) ((value >> 16) & 0xFF));
    }

    // -----------------------------------------------------------------------
    // TCP framing helpers
    // -----------------------------------------------------------------------

    /**
     * Reads one frame from the wire.
     * Frame layout: [type:int32-LE][length:int32-LE][payload:length bytes]
     *
     * @return int[]{type, length}, or null on EOF / error.
     */
    int[] readFrame() throws IOException {
        int type;
        try {
            type = readInt(in);
        } catch (IOException e) {
            return null;
        }
        int length = readInt(in);
        byte[] payload = new byte[Math.max(0, length)];
        int remaining = payload.length;
        int offset = 0;
        while (remaining > 0) {
            int n = in.read(payload, offset, remaining);
            if (n < 0) {
                return null;
            }
            offset += n;
            remaining -= n;
        }
        lastPayload = payload;
        return new int[]{type, length};
    }

    void sendFrame(int type, byte[] payload) throws IOException {
        synchronized (out) {
            writeInt(out, type);
            writeInt(out, payload.length);
            out.write(payload);
            out.flush();
        }
    }

    static int readInt(InputStream stream) throws IOException {
        int b0 = stream.read();
        int b1 = stream.read();
        int b2 = stream.read();
        int b3 = stream.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new IOException("EOF while reading int");
        }
        return (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
    }

    static void writeInt(OutputStream stream, int value) throws IOException {
        stream.write(value & 0xFF);
        stream.write((value >> 8) & 0xFF);
        stream.write((value >> 16) & 0xFF);
        stream.write((value >> 24) & 0xFF);
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    boolean isClosed() {
        if (closed) {
            return true;
        }
        if (socket != null) {
            return socket.isClosed();
        }
        return false;
    }

    void close() {
        closed = true;
        try {
            in.close();
        } catch (IOException e) {
            // ignore
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Error closing session socket", e);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Creates a no-op Debugger instance for use when no real debugger is available. */
    private static Debugger buildNullDebugger() {
        return new Debugger() {
            @Override
            public void updateStatus() {
                // no-op
            }
        };
    }
}
