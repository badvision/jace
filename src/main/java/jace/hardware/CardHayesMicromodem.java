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

import jace.config.Name;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    public CardHayesMicromodem(Computer computer) {
        super(computer);
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

    @Override
    public void loadRom(String path) throws IOException {
        // Do nothing -- there is no rom for this card right now.
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
