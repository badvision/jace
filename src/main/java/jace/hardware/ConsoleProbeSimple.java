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

import jace.apple2e.MOS65C02;
import jace.core.Computer;
import jace.core.Keyboard;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Attempt to simplify what ConsoleProbe was attempting. Still not ready for any
 * real use.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class ConsoleProbeSimple {

    RAMListener cout;
    public static int COUT = 0xFDED;

    public void init(final Computer c) {
        Thread t = new Thread(new KeyReader());
        t.start();

        cout = new RAMListener(RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(COUT);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                MOS65C02 cpu = (MOS65C02) c.getCpu();
                int ch = cpu.A & 0x07f;
                if (ch == 13) {
                    System.out.println();
                } else if (ch < ' ') {
                    if (ch == 7) {
                        Toolkit.getDefaultToolkit().beep();
                    } else {
                        System.out.println("CHR" + ch);
                    }
                } else {
                    System.out.print((char) ch);
                }
            }
        };
        c.getMemory().addListener(cout);
    }

    public static class KeyReader implements Runnable {

        public Computer c;

        public void run() {
            while (true) {
                try {
                    while (System.in.available() == 0 || Keyboard.readState() < 0) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ex) {
                            System.out.println(ex.getMessage());
                            Logger.getLogger(ConsoleProbeSimple.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    int ch = System.in.read();
                    if (ch == 10) {
                        ch = 13;
                    }
                    Keyboard.pressKey((byte) ch);
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                    Logger.getLogger(ConsoleProbeSimple.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
}
