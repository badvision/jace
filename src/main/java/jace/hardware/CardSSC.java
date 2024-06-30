/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.hardware;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.Emulator;
import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import javafx.scene.control.Label;

/**
 * Super Serial Card with serial-over-tcp/ip support. This is fully compatible
 * with the SSC ROM and supported applications.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("Super Serial Card")
public class CardSSC extends Card {

    @ConfigurableField(name = "TCP/IP Port", shortName = "port")
    public short IP_PORT = 1977;
    protected ServerSocket socket;
    protected Socket clientSocket;
    protected BufferedReader socketInput;
    protected Thread listenThread;
    private int lastInputByte = 0;
    private boolean FULL_ECHO = true;
//    private boolean RECV_STRIP_LF = true;
//    private boolean TRANS_ADD_LF = true;
    @ConfigurableField(category = "Advanced", name = "Liveness check interval", description = "How often the connection is polled for signs of life when idle (in milliseconds)")
    public int livenessCheck = 5000000;
    @ConfigurableField(name = "Strip LF (recv)", shortName = "stripLF", defaultValue = "false", description = "Strip incoming linefeeds")
    public boolean RECV_STRIP_LF = false;
    @ConfigurableField(name = "Add LF (send)", shortName = "addLF", defaultValue = "false", description = "Append linefeeds after outgoing carriage returns")
    public boolean TRANS_ADD_LF = false;
    private boolean DTR = true;
    public static int SW1 = 0x01;              // Read = Jumper block SW1
    //Bit 0 = !SW1-6
    //Bit 1 = !SW1-5
    //Bit 4 = !SW1-4
    //Bit 5 = !SW1-3
    //Bit 6 = !SW1-2
    //Bit 7 = !SW1-1
    // 19200 baud (SW1-1,2,3,4 off)
    // Communications mode (SW1-5,6 on)
    public int SW1_SETTING = 0x0F0;
    public static int SW2_CTS = 0x02;          // Read = Jumper block SW2 and CTS
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
    private final int SW2_SETTING = 0x04;
    public static int ACIA_Data = 0x08;        // Read=Receive / Write=transmit
    public static int ACIA_Status = 0x09;     // Read=Status / Write=Reset
    public static int ACIA_Command = 0x0A;
    public static int ACIA_Control = 0x0B;
    public boolean PORT_CONNECTED = false;
    public boolean RECV_IRQ_ENABLED = false;
    public boolean TRANS_IRQ_ENABLED = false;
    public boolean IRQ_TRIGGERED = false;
    // Bitmask for stop bits (FF = 8, 7F = 7, etc)
    private int DATA_BITS = 0x07F;

    public CardSSC() {
        super(false);
    }

    @Override
    public String getDeviceName() {
        return "Super Serial Card";
    }

    Label activityIndicator;

    @Override
    public void setSlot(int slot) {
        try {
            loadRom("/jace/data/SSC.rom");
        } catch (IOException ex) {
            Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
        }
        super.setSlot(slot);
        Utility.loadIconLabel("network-wired.png").ifPresent(icon->{
            activityIndicator = icon;
            activityIndicator.setText("Slot " + slot);
        });
    }

    AtomicBoolean newInputAvailable = new AtomicBoolean();
    public void socketMonitor() {
        try {
            socket = new ServerSocket(IP_PORT);
            socket.setReuseAddress(true);
            socket.setSoTimeout(0);
        } catch (IOException ex) {
            Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
            suspend();
            return;
        }
        while (socket != null && !socket.isClosed()) {
            try {
                Logger.getLogger(CardSSC.class.getName()).log(Level.INFO, "Slot " + getSlot() + " listening on port " + IP_PORT, (Throwable) null);
                while ((clientSocket = socket.accept()) != null) {
                    socketInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    clientConnected();
                    clientSocket.setTcpNoDelay(true);
                    while (isConnected()) {
                        Thread.onSpinWait();
                        if (!newInputAvailable.get() && inputAvailable()) {
                            lastTransmission = System.currentTimeMillis();
                            synchronized (newInputAvailable) {
                                newInputAvailable.set(true);
                            }
                        }
                    }
                    clientDisconnected();
                    hangUp();
                    socketInput = null;
                }
                Thread.yield();
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
        InputStream romFile = CardSSC.class.getResourceAsStream(path);
        final int cxRomLength = 0x0100;
        final int c8RomLength = 0x0700;
        byte[] romxData = new byte[cxRomLength];
        byte[] rom8Data = new byte[c8RomLength];
        if (romFile.read(rom8Data) != c8RomLength) {
            throw new IOException("Bad SSC rom size");
        }
        getC8Rom().loadData(rom8Data);
        if (romFile.read(romxData) != cxRomLength) {
            throw new IOException("Bad SSC rom size");
        }
        getCxRom().loadData(romxData);
    }

    @Override
    public void reset() {
        suspend();
        Thread resetThread = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
            }
            resume();
        });
        resetThread.start();
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        try {
            int newValue = -1;
            switch (type) {
                case ANY:
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
                        newValue |= (PORT_CONNECTED && newInputAvailable.get() ? 0x00 : 0x01);
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
                        if (newInputAvailable.get()) {
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
                                break;
                            case 1:
                                TRANS_IRQ_ENABLED = true;
                                break;
                            case 2:
                                TRANS_IRQ_ENABLED = false;
                                break;
                            case 3:
                                TRANS_IRQ_ENABLED = false;
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
            }
        } catch (IOException ex) {
            Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void tick() {
        if (RECV_IRQ_ENABLED && newInputAvailable.get()) {
            // newInputAvailable = false;
            triggerIRQ();
        }
    }

    public boolean inputAvailable() throws IOException {
        if (isConnected() && clientSocket != null && socketInput != null) {
            return socketInput.ready();
        } else {
            return false;
        }
    }

    private int getInputByte() throws IOException {
        if (newInputAvailable.get()) {
            synchronized (newInputAvailable) {
                int in = socketInput.read() & DATA_BITS;
                if (RECV_STRIP_LF && in == 10 && lastInputByte == 13) {
                    in = socketInput.read() & DATA_BITS;
                }
                lastInputByte = in;
                newInputAvailable.set(false);
           }
        }
        return lastInputByte;
    }
    long lastTransmission = -1L;

    private void sendOutputByte(int i) throws IOException {
        if (clientSocket != null && clientSocket.isConnected()) {
            try {
                clientSocket.getOutputStream().write(i & DATA_BITS);
                if (TRANS_ADD_LF && (i & DATA_BITS) == 13) {
                    clientSocket.getOutputStream().write(10);
                }
                clientSocket.getOutputStream().flush();
                lastTransmission = System.currentTimeMillis();
            } catch (IOException e) {
                lastTransmission = -1L;
                hangUp();
            }
        } else {
            lastTransmission = -1L;
        }
    }

    // CTS isn't used here -- it's assumed that we're always clear-to-send
    // private void setCTS(boolean b) throws InterruptedException {
    //     PORT_CONNECTED = b;
    //     if (b == false) {
    //         reset();
    //     }
    // }

    // private boolean getCTS() throws InterruptedException {
    //     return PORT_CONNECTED;
    // }

    private void triggerIRQ() {
        IRQ_TRIGGERED = true;
        Emulator.withComputer(c->c.getCpu().generateInterrupt());
    }

    public void hangUp() {
        lastInputByte = 0;
        lastTransmission = -1L;
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
                listenThread.interrupt();
                listenThread.join(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(CardSSC.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        listenThread = null;
        socket = null;
        return super.suspend();
    }

    @Override
    public void resume() {
        if (!isRunning()) {
            RECV_IRQ_ENABLED = false;
            TRANS_IRQ_ENABLED = false;
            IRQ_TRIGGERED = false;

            //socket.setReuseAddress(true);
            listenThread = new Thread(this::socketMonitor);
            listenThread.setDaemon(false);
            listenThread.setName("SSC port listener, slot" + getSlot());
            listenThread.start();
        }
        super.resume();
    }

    public boolean isConnected() {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return false;
        }
        if (lastTransmission == -1 || System.currentTimeMillis() > (lastTransmission + livenessCheck)) {
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
