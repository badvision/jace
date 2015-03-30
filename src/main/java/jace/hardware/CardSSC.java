/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package jace.hardware;

import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.config.Reconfigurable;
import jace.core.Card;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Label;

/**
 * Super Serial Card with serial-over-tcp/ip support. This is fully compatible
 * with the SSC ROM and supported applications.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("Super Serial Card")
public class CardSSC extends Card implements Reconfigurable {

    @ConfigurableField(name = "TCP/IP Port", shortName = "port")
    public short IP_PORT = 1977;
    protected ServerSocket socket;
    protected Socket clientSocket;
    protected BufferedReader socketInput;
    protected Thread listenThread;
    private int lastInputByte = 0;
    private boolean FULL_ECHO = true;
    private boolean RECV_ACTIVE = true;
    private boolean TRANS_ACTIVE = true;
//    private boolean RECV_STRIP_LF = true;
//    private boolean TRANS_ADD_LF = true;
    @ConfigurableField(name = "Strip LF (recv)", shortName = "stripLF", defaultValue = "false", description = "Strip incoming linefeeds")
    public boolean RECV_STRIP_LF = false;
    @ConfigurableField(name = "Add LF (send)", shortName = "addLF", defaultValue = "false", description = "Append linefeeds after outgoing carriage returns")
    public boolean TRANS_ADD_LF = false;
    private boolean DTR = true;
    public int SW1 = 0x01;              // Read = Jumper block SW1
    //Bit 0 = !SW1-6
    //Bit 1 = !SW1-5
    //Bit 4 = !SW1-4
    //Bit 5 = !SW1-3
    //Bit 6 = !SW1-2
    //Bit 7 = !SW1-1
    // 19200 baud (SW1-1,2,3,4 off)
    // Communications mode (SW1-5,6 on)
    public int SW1_SETTING = 0x0F0;
    public int SW2_CTS = 0x02;          // Read = Jumper block SW2 and CTS
    //Bit 0 = !CTS
    //SW2-6 = Allow interrupts (disable in ][, ][+)
    //Bit 1 = !SW2-5  -- Generate LF after CR
    //Bit 2 = !SW2-4
    //Bit 3 = !SW2-3
    //Bit 5 = !SW2-2
    //Bit 7 = !SW2-1
    // 1 stop bit (SW2-1 on)
    // 8 data bits (SW2-2 on)
    // No parity (SW2-3 don't care, SW2-4 off)
    private int SW2_SETTING = 0x04;
    public int ACIA_Data = 0x08;        // Read=Receive / Write=transmit
    public int ACIA_Status = 0x09;     // Read=Status / Write=Reset
    public int ACIA_Command = 0x0A;
    public int ACIA_Control = 0x0B;
    public boolean PORT_CONNECTED = false;
    public boolean RECV_IRQ_ENABLED = false;
    public boolean TRANS_IRQ_ENABLED = false;
    public boolean IRQ_TRIGGERED = false;
    // Bitmask for stop bits (FF = 8, 7F = 7, etc)
    private int DATA_BITS = 0x07F;

    public CardSSC(Computer computer) {
        super(computer);
    }

    @Override
    public String getDeviceName() {
        return "Super Serial Card";
    }

    Label activityIndicator;

    @Override
    public void setSlot(int slot) {
        try {
            loadRom("jace/data/SSC.rom");
        } catch (IOException ex) {
            Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
        }
        super.setSlot(slot);
        activityIndicator = Utility.loadIconLabel("network-wired.png");
        activityIndicator.setText("Slot " + slot);
    }

    boolean newInputAvailable = false;
    public void socketMonitor() {
        while (socket != null && !socket.isClosed()) {
            try {
                Logger.getLogger(CardSSC.class.getName()).log(Level.INFO, "Slot " + getSlot() + " listening on port " + IP_PORT, (Throwable) null);
                while ((clientSocket = socket.accept()) != null) {
                    socketInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    clientConnected();
                    clientSocket.setTcpNoDelay(true);
                    while (isConnected()) {
                        try {
                            Thread.sleep(10);
                            if (socketInput.ready()) {
                                newInputAvailable = true;
                            }
                        } catch (InterruptedException ex) {
                            // Do nothing
                        }
                    }
                    clientDisconnected();
                    hangUp();
                    socketInput = null;
                }
            } catch (SocketTimeoutException ex) {
                // Do nothing
            } catch (IOException ex) {
                Logger.getLogger(CardSSC.class.getName()).log(Level.FINE, null, ex);
            }
        }
        socket = null;
    }

    // Called when a client first connects via telnet
    public void clientConnected() {
        System.err.println("Client connected");

    }

    // Called when a client disconnects
    public void clientDisconnected() {
        System.out.println("Client disconnected");
    }

    public void loadRom(String path) throws IOException {
        // Load rom file, first 0x0700 bytes are C8 rom, last 0x0100 bytes are CX rom
        // CF00-CFFF are unused by the SSC
        InputStream romFile = CardSSC.class.getClassLoader().getResourceAsStream(path);
        final int cxRomLength = 0x0100;
        final int c8RomLength = 0x0700;
        byte[] romxData = new byte[cxRomLength];
        byte[] rom8Data = new byte[c8RomLength];
        try {
            if (romFile.read(rom8Data) != c8RomLength) {
                throw new IOException("Bad SSC rom size");
            }
            getC8Rom().loadData(rom8Data);
            if (romFile.read(romxData) != cxRomLength) {
                throw new IOException("Bad SSC rom size");
            }
            getCxRom().loadData(romxData);
        } catch (IOException ex) {
            throw ex;
        }
    }

    @Override
    public void reset() {
        Thread resetThread = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
            }
            suspend();
            resume();
        });
        resetThread.start();
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        try {
            int newValue = -1;
            switch (type) {
                case EXECUTE:
                case READ_OPERAND:
                case READ_DATA:
                case READ:
                    if (register == SW1) {
                        newValue = SW1_SETTING;
                    }
                    if (register == SW2_CTS) {
                        newValue = SW2_SETTING & 0x0FE;
                        // if port is connected and ready to send another byte, set CTS bit on
                        newValue |= (PORT_CONNECTED && inputAvailable()) ? 0x00 : 0x01;
                    }
                    if (register == ACIA_Data) {
                        EmulatorUILogic.addIndicator(this, activityIndicator);
                        newValue = getInputByte();
                    }
                    if (register == ACIA_Status) {
                        newValue = 0;
                        // 0 = Parity error (1)
                        // 1 = Framing error (1)
                        // 2 = Overrun error (1)
                        // 3 = ACIA Receive Register full (1)
                        if (newInputAvailable || inputAvailable()) {
                            newValue |= 0x08;
                        }
                        // 4 = ACIA Transmit Register empty (1)
                        newValue |= 0x010;
                        // 5 = Data Carrier Detect (DCD) true (0)
                        // 6 = Data Set Ready (DSR) true (0)
                        // 7 = Interrupt (IRQ) has occurred
                        if (IRQ_TRIGGERED) {
                            newValue |= 0x080;
                        }
                        IRQ_TRIGGERED = false;
                    }
                    if (register == ACIA_Command) {
                        newValue = DTR ? 1 : 0;
                        // 0 = DTR Enable (1) / Disable (0) receiver and IRQ
                        // 1 = Allow IRQ (1) when status bit 3 is true
                        if (RECV_IRQ_ENABLED) {
                            newValue |= 2;
                        }
                        // 2,3 = Control transmit IRQ, RTS level and transmitter
                        newValue |= 12;
                        // 4 = Normal mode 0, or Echo mode 1 (bits 2 and 3 must be 0)
                        if (FULL_ECHO) {
                            newValue |= 16;
                        }
                        // 5 = Control parity
                    }
                    if (register == ACIA_Control) {
                        // 0-3 = Baud Rate
                        // 4 = Use baud rate generator (1) / Use external clock (0)
                        // 5-6 = Number of data bits (00 = 8, 10 = 7, 01 = 6, 11 = 5)
                        // 7 = Number of stop bits (0 = 1 stop bit, 1 = 1-1/2 (with 5 data bits no parity), 1 (8 data plus parity) or 2)
                        newValue = 0;
                    }
                    break;
                case WRITE:
                    if (register == ACIA_Data) {
                        EmulatorUILogic.addIndicator(this, activityIndicator);
                        sendOutputByte(value & 0x0FF);
                        if (TRANS_IRQ_ENABLED) {
                            triggerIRQ();
                        }
                    }
                    if (register == ACIA_Command) {
                        // 0 = DTR Enable (1) / Disable (0) receiver and IRQ
                        DTR = ((value & 1) == 0);
                        // 0 = Allow IRQ (0) when status bit 3 is true
                        if ((value & 2) == 0) {
                            RECV_IRQ_ENABLED = !DTR;
                        } else {
                            RECV_IRQ_ENABLED = false;
                        }
                        // 2,3 = Control transmit IRQ, RTS level and transmitter
                        // 0 0 = Transmit interrupt off, RTS high, Transmitter off
                        // 1 0 = Transmit interrupt ON, RTS low, Transmitter on
                        // 0 1 = Transmit interrupt off, RTS low, Transmitter on
                        // 1 1 = Transmit interrupt off, RTS low, Transmit BRK
                        switch ((value >> 2) & 3) {
                            case 0:
                                TRANS_IRQ_ENABLED = false;
                                TRANS_ACTIVE = false;
                                break;
                            case 1:
                                TRANS_IRQ_ENABLED = true;
                                TRANS_ACTIVE = true;
                                break;
                            case 2:
                                TRANS_IRQ_ENABLED = false;
                                TRANS_ACTIVE = true;
                                break;
                            case 3:
                                TRANS_IRQ_ENABLED = false;
                                TRANS_ACTIVE = true;
                                break;
                        }
                        // 4 = Normal mode 0, or Echo mode 1 (bits 2 and 3 must be 0)
                        FULL_ECHO = ((value & 16) > 0);
//                        System.out.println("Echo set to " + FULL_ECHO);
                        // 5 = Control parity
                    }
                    if (register == ACIA_Control) {
                        // 0-3 = Baud Rate
                        // 4 = Use baud rate generator (1) / Use external clock (0)
                        // 5-6 = Number of data bits (00 = 8, 01 = 7, 10 = 6, 11 = 5)
                        // 7 = Number of stop bits (0 = 1 stop bit, 1 = 1-1/2 (with 5 data bits no parity), 1 (8 data plus parity) or 2)
                        int bits = (value & 127) >> 5;
                        System.out.println("Data bits set to " + (8 - bits));
                        switch (bits) {
                            case 0:
                                DATA_BITS = 0x0FF;
                                break;
                            case 1:
                                DATA_BITS = 0x07F;
                                break;
                            case 2:
                                DATA_BITS = 0x03F;
                                break;
                            case 3:
                                DATA_BITS = 0x01F;
                                break;
                        }
                    }
                    break;
            }
            if (newValue > -1) {
                e.setNewValue(newValue);
                value = newValue;
            }
        } catch (IOException ex) {
            Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void tick() {
        if (RECV_IRQ_ENABLED && newInputAvailable) {
            newInputAvailable = false;
            triggerIRQ();
        }
    }

    public boolean inputAvailable() throws IOException {
        if (isConnected() && clientSocket != null && socketInput != null) {
//            return socketInput.available() > 0;
            return socketInput.ready();
        } else {
            return false;
        }
    }

    private int getInputByte() throws IOException {
        if (inputAvailable()) {
            int in = socketInput.read() & DATA_BITS;
            if (RECV_STRIP_LF && in == 10 && lastInputByte == 13) {
                in = socketInput.read() & DATA_BITS;
            }
            lastInputByte = in;
        }
        return lastInputByte;
    }
    long lastSuccessfulWrite = -1L;

    private void sendOutputByte(int i) throws IOException {
        if (clientSocket != null && clientSocket.isConnected()) {
            try {
                clientSocket.getOutputStream().write(i & DATA_BITS);
                if (TRANS_ADD_LF && (i & DATA_BITS) == 13) {
                    clientSocket.getOutputStream().write(10);
                }
                clientSocket.getOutputStream().flush();
                lastSuccessfulWrite = System.currentTimeMillis();
            } catch (IOException e) {
                lastSuccessfulWrite = -1L;
                hangUp();
            }
        } else {
            lastSuccessfulWrite = -1L;
        }
    }

    private void setCTS(boolean b) throws InterruptedException {
        PORT_CONNECTED = b;
        if (b == false) {
            reset();
        }
    }

    private boolean getCTS() throws InterruptedException {
        return PORT_CONNECTED;
    }

    private void triggerIRQ() {
        IRQ_TRIGGERED = true;
        computer.getCpu().generateInterrupt();            
    }

    public void hangUp() {
        lastInputByte = 0;
        lastSuccessfulWrite = -1L;
        if (clientSocket != null && clientSocket.isConnected()) {
            try {
                clientSocket.shutdownInput();
                clientSocket.shutdownOutput();
                clientSocket.close();
            } catch (IOException ex) {
                Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        clientSocket = null;
    }

    /**
     * Detach from server socket port and ensure that the card's resources are
     * no longer in use
     *
     * @return
     */
    @Override
    public boolean suspend() {
        synchronized (this) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            hangUp();
            if (listenThread != null && listenThread.isAlive()) {
                try {
                    listenThread.join();
                } catch (InterruptedException ex) {
                    Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            listenThread = null;
            socket = null;
            return super.suspend();
        }
    }

    @Override
    public void resume() {
        synchronized (this) {

            if (!isRunning()) {
                super.resume();
                RECV_IRQ_ENABLED = false;
                TRANS_IRQ_ENABLED = false;
                IRQ_TRIGGERED = false;

                try {
                    socket = new ServerSocket(IP_PORT);
                    socket.setReuseAddress(true);
                    socket.setSoTimeout(0);
                    //socket.setReuseAddress(true);
                    listenThread = new Thread(this::socketMonitor);
                    listenThread.setDaemon(false);
                    listenThread.setName("SSC port listener, slot" + getSlot());
                    listenThread.start();
                } catch (IOException ex) {
                    suspend();
                    Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    @ConfigurableField(category = "Advanced", name = "Liveness check interval", description = "How often the connection is polled for signs of life (in milliseconds)")
    public int livenessCheck = 10000;

    public boolean isConnected() {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return false;
        }
        if (lastSuccessfulWrite == -1 || System.currentTimeMillis() > (lastSuccessfulWrite + livenessCheck)) {
            try {
                sendOutputByte(0);
                return true;
            } catch (IOException e) {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // Do nothing -- the card rom does everything
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // There is no special c8 rom behavior for this card
    }
}
