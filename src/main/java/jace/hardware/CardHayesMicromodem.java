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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.config.Name;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;

/**
 * Partial Hayes Micromodem II implementation, acting more as a bridge to
 * provide something similar to the Super Serial support for applications which
 * do not support the SSC card but do support Hayes, such as DiversiDial.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Name("Hayes Micromodem II")
public class CardHayesMicromodem extends CardSSC {

    @Override
    public String getDeviceName() {
        return "Hayes Micromodem";
    }
    public int RING_INDICATOR_REG = 5;
    private boolean ringIndicator = false;

    public CardHayesMicromodem() {
        super();
        ACIA_Data = 7;
        ACIA_Status = 6;
        ACIA_Control = 5;
        ACIA_Command = 6;
        // set these to high values will essentially NO-OP them.
        SW1 = 255;
        SW1_SETTING = 255;
        SW2_CTS = 255;
        RECV_IRQ_ENABLED = false;
        TRANS_IRQ_ENABLED = false;
    }

    @Override
    public void loadRom() throws IOException {
        String path = "/jace/data/hayes-micromodem-8308a271.rom";
        // Load rom file, first 0x0FF bytes are CX rom, next 0x0800 bytes are C8 rom
        try (InputStream romFile = CardSSC.class.getResourceAsStream(path)) {
            final int cxRomLength = 0x0100;
            final int c8RomLength = 0x0800;
            byte[] rom8Data = new byte[c8RomLength];
            getC8Rom().loadData(rom8Data);
            getCxRom().loadData(Arrays.copyOf(rom8Data, cxRomLength));
        }
    }

    @Override
    public void clientConnected() {
        setRingIndicator(true);
        super.clientConnected();
    }

    @Override
    public void clientDisconnected() {
        setRingIndicator(false);
        super.clientDisconnected();
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        if (register == ACIA_Data) {
            super.handleIOAccess(register, type, value, e);
            return;
        }
        if (type.isRead() && register == RING_INDICATOR_REG) {
            e.setNewValue(isRingIndicator() ? 0 : 255);
        } else if (type.isRead() && register == ACIA_Status) {
            e.setNewValue(getStatusValue());
        } else if (type == TYPE.WRITE && register == ACIA_Control) {
            if ((value & 0x080) == 0) {
                System.out.println("Software triggered disconnect");
                try {
                    if (clientSocket != null) {
                        clientSocket.getOutputStream().write("Disconnected by host\n".getBytes());
                    }
                } catch (IOException ex) {
                    System.out.println("Client disconnected before host");
                    // If there's an error, ignore it.  That means the client disconnected first.
                }
                // Hang up
                hangUp();
                setRingIndicator(false);
            } else {
                System.out.println("Software answered connect request");
                try {
                    if (clientSocket != null) {
                        clientSocket.getOutputStream().write("Connected to emulated Apple\n".getBytes());
                    }
                } catch (IOException ex) {
                    Logger.getLogger(CardHayesMicromodem.class.getName()).log(Level.SEVERE, null, ex);
                }
                setRingIndicator(false);
            }
        }
    }

    /**
     * @return the ringIndicator
     */
    public boolean isRingIndicator() {
        return ringIndicator;
    }

    /**
     * @param ringIndicator the ringIndicator to set
     */
    public void setRingIndicator(boolean ringIndicator) {
        this.ringIndicator = ringIndicator;
    }

    private int getStatusValue() {
        int status = 0;
        try {
            // 0 = receive register full
            if (inputAvailable()) {
                status |= 0x01;
            }
            // 1 = transmit register empty -- always :-)
            status |= 0x02;
            // 2 = No Carrier
            if (isRingIndicator() || !isConnected()) {
                status |= 0x04;
            }
        } catch (Throwable ex) {
            Logger.getLogger(CardHayesMicromodem.class.getName()).log(Level.SEVERE, null, ex);
        }
        return status;
    }
}
