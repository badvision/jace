package jace.ipc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.config.ConfigurableField;
import jace.config.Reconfigurable;
import jace.core.RAMEvent;
import jace.core.RAMListener;

/**
 * Singleton TCP server that accepts connections from JaceIPCShim.exe and
 * bridges them to the running Apple IIe emulator state.
 */
public class CyreneIPCServer implements Reconfigurable {

    private static final Logger LOG = Logger.getLogger(CyreneIPCServer.class.getName());

    private static final CyreneIPCServer INSTANCE = new CyreneIPCServer();

    @ConfigurableField(name = "IPC Port", shortName = "ipcPort",
            description = "TCP port for Cyrene IPC bridge", category = "IPC",
            defaultValue = "57867")
    public int port = IpcConstants.DEFAULT_PORT;

    @ConfigurableField(name = "IPC Enabled", shortName = "ipcEnabled",
            description = "Enable Cyrene IPC bridge server", category = "IPC",
            defaultValue = "false")
    public boolean enabled = false;

    private ServerSocket serverSocket;
    private volatile CyreneSession activeSession;
    private Thread acceptorThread;
    private RAMListener executeListener;

    private CyreneIPCServer() {}

    public static CyreneIPCServer getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (!enabled) {
            return;
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            LOG.info("Cyrene IPC server started on port " + port);
            acceptorThread = new Thread(this::acceptLoop, "cyrene-ipc-acceptor");
            acceptorThread.setDaemon(true);
            acceptorThread.start();
            registerExecuteListener();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to start Cyrene IPC server on port " + port, e);
        }
    }

    private void registerExecuteListener() {
        try {
            Emulator.withMemory(mem -> {
                executeListener = new RAMListener("cyrene-execute",
                        RAMEvent.TYPE.EXECUTE,
                        RAMEvent.SCOPE.RANGE,
                        RAMEvent.VALUE.ANY) {
                    @Override
                    protected void doConfig() {
                        setScopeStart(0x0000);
                        setScopeEnd(0xFFFF);
                    }

                    @Override
                    protected void doEvent(RAMEvent e) {
                        if (!isActive()) {
                            return;
                        }
                        CyreneSession session = activeSession;
                        if (session == null || !session.isTracingOperations()) {
                            return;
                        }
                        try {
                            Emulator.withComputer(c -> {
                                MOS65C02 cpu = (MOS65C02) c.getCpu();
                                if (cpu == null) {
                                    return;
                                }
                                int pc = cpu.getProgramCounter();
                                byte opcode = readMemByte(c.getMemory(), pc);
                                byte op1 = readMemByte(c.getMemory(), pc + 1);
                                byte op2 = readMemByte(c.getMemory(), pc + 2);
                                byte op3 = readMemByte(c.getMemory(), pc + 3);
                                byte flags = CyreneOperation.packFlags(
                                        cpu.N, cpu.V, cpu.B, cpu.D, cpu.I, cpu.Z, cpu.C);
                                CyreneOperation op = new CyreneOperation(
                                        cpu.A, cpu.X, cpu.Y, pc, cpu.STACK,
                                        flags,
                                        opcode, op1, op2, op3,
                                        0, 0, 0, 0, 0, (byte) 0,
                                        session.goid.get(), session.gcc.get(),
                                        (byte) 0, (byte) 0);
                                session.onInstruction(op);
                            });
                        } catch (Exception ex) {
                            LOG.log(Level.FINE, "cyrene-execute: error building operation", ex);
                        }
                    }
                };
                mem.addListener(executeListener);
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not register execute listener — emulator not ready", e);
        }
    }

    private static byte readMemByte(jace.core.RAM memory, int address) {
        try {
            return (byte) (memory.read(address & 0xFFFF, RAMEvent.TYPE.READ_DATA, false, false) & 0xFF);
        } catch (Exception e) {
            return 0;
        }
    }

    public void stop() {
        if (executeListener != null) {
            try {
                Emulator.withMemory(mem -> mem.removeListener(executeListener));
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error removing execute listener", e);
            }
            executeListener = null;
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing server socket", e);
            }
            serverSocket = null;
        }
        CyreneSession session = activeSession;
        if (session != null) {
            session.close();
            activeSession = null;
        }
    }

    private void acceptLoop() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                LOG.info("Cyrene client connected from " + socket.getRemoteSocketAddress());
                CyreneSession old = activeSession;
                if (old != null) {
                    old.close();
                }
                CyreneSession session = new CyreneSession(socket);
                activeSession = session;
                Thread sessionThread = new Thread(session, "cyrene-ipc-session");
                sessionThread.setDaemon(true);
                sessionThread.start();
            } catch (IOException e) {
                if (serverSocket == null || serverSocket.isClosed()) {
                    break;
                }
                LOG.log(Level.WARNING, "Error accepting Cyrene connection", e);
            }
        }
    }

    public void onVBL() {
        CyreneSession session = activeSession;
        if (session != null) {
            session.onVBL();
        }
    }

    public void onInstruction(CyreneOperation op) {
        CyreneSession session = activeSession;
        if (session == null) {
            return;
        }
        session.onInstruction(op);
    }

    public boolean isActive() {
        CyreneSession session = activeSession;
        return session != null && !session.isClosed();
    }

    public CyreneSession getActiveSession() {
        return activeSession;
    }

    @Override
    public void reconfigure() {
        stop();
        if (enabled) {
            start();
        }
    }

    @Override
    public String getName() {
        return "Cyrene IPC Server";
    }

    @Override
    public String getShortName() {
        return "cyreneIPC";
    }
}
