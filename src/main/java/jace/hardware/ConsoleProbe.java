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

import jace.apple2e.SoftSwitches;
import jace.core.Computer;
import jace.core.Keyboard;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Attempt at adding accessibility by redirecting screen/keyboard traffic to
 * stdout/stdin of the console. Doesn't work well, unfortunately.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class ConsoleProbe {

    public static boolean enabled = true;
    public String[] lastScreen = new String[24];
    public List<Rectangle> regions = new ArrayList<Rectangle>();
    private RAMListener textListener;
    public static long lastChange;
    public static long updateDelay = 100L;
    public static boolean readerActive = false;
    public Computer computer;
    private Thread keyReaderThread;

    public void init(final Computer c) {
        computer = c;
        enabled = true;
        keyReaderThread = new Thread(new KeyReader());
        keyReaderThread.setName("Console probe key reader");
        keyReaderThread.start();
        textListener = new RAMListener(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0400);
                setScopeEnd(0x0BFF);
            }

            @Override
            protected void doEvent(RAMEvent e) {
                if (e.getAddress() < 0x0800 && SoftSwitches.PAGE2.isOn()) {
                    return;
                }
                if (SoftSwitches.TEXT.isOff()) {
                    if (SoftSwitches.MIXED.isOn()) {
                        handleMixedMode();
                    }
                } else {
                    handleTextMode();
                }
            }

            private void handleMixedMode() {
                handleTextMode();
            }

            private void handleTextMode() {
                lastChange = System.currentTimeMillis();
                if (readerActive) {
                    return;
                }
                Thread t = new Thread(new ScreenReader());
                t.start();
            }
        };
        c.getMemory().addListener(textListener);
    }

    public static synchronized void performRead() {
    }

    public void shutdown() {
        enabled = false;
        if (textListener != null) {
            computer.getMemory().removeListener(textListener);
        }

        if (keyReaderThread != null && keyReaderThread.isAlive()) {
            try {
                keyReaderThread.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(ConsoleProbe.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static class ScreenReader implements Runnable {

        public void run() {
            readerActive = true;
            try {
                // Keep sleeping until there have been no more screen changes during the specified delay period
                // It is possible that the lastChange will keep being updated while in this loop
                // That is both expected and the reason this is a loop!
                long delay = 0;
                while (System.currentTimeMillis() - lastChange <= updateDelay) {
                    delay = updateDelay - System.currentTimeMillis() - lastChange;
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(ConsoleProbe.class.getName()).log(Level.SEVERE, null, ex);
            }
            // Signal that we're off to go read the screen (and any additional update will need to spawn the thread again)
            readerActive = false;
            performRead();
        }
    }

    public static class KeyReader implements Runnable {

        public Computer c;

        public void run() {
            while (true) {
                try {
                    while (enabled && (System.in.available() == 0 || Keyboard.readState() < 0)) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ex) {
                            System.out.println(ex.getMessage());
                            Logger.getLogger(ConsoleProbe.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    if (!enabled) {
                        return;
                    }
                    int ch = System.in.read();
                    if (ch == 10) {
                        ch = 13;
                    }
                    Keyboard.pressKey((byte) ch);
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                    Logger.getLogger(ConsoleProbe.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
}
