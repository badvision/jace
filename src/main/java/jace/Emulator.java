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
package jace;

import jace.apple2e.Apple2e;
import jace.config.Configuration;
import jace.ui.AbstractEmulatorFrame;
import jace.ui.EmulatorFrame;
import java.awt.Component;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

/**
 * Created on January 15, 2007, 10:10 PM
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Emulator {

    public static Emulator instance;
    public static Thread mainThread;

    public static void main(String... args) {
        mainThread = Thread.currentThread();
        instance = new Emulator(args);
    }

    public static AbstractEmulatorFrame getFrame() {
        if (instance != null) {
            return instance.theApp;
        } else {
            return null;
        }
    }
    public static Apple2e computer;
    public AbstractEmulatorFrame theApp;

    /**
     * Creates a new instance of Emulator
     * @param args
     */
    public Emulator(String... args) {
        computer = new Apple2e();
        Configuration.loadSettings();
        Map<String, String> settings = new HashMap<>();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("-")) {
                    String key = args[i].substring(1);
                    if ((i + 1) < args.length) {
                        String val = args[i + 1];
                        if (!val.startsWith("-")) {
                            settings.put(key, val);
                            i++;
                        } else {
                            settings.put(key, "true");
                        }
                    } else {
                        settings.put(key, "true");
                    }
                } else {
                    System.err.println("Did not understand parameter " + args[i] + ", skipping.");
                }
            }
        }
        Configuration.applySettings(settings);

//        theApp = new MainFrame();
        theApp = new EmulatorFrame(computer);
        try {
            theApp.setIconImage(ImageIO.read(Emulator.class.getClassLoader().getResourceAsStream("jace/data/woz_figure.gif")));
        } catch (IOException ex) {
            Logger.getLogger(Emulator.class.getName()).log(Level.SEVERE, null, ex);
        }
        //theApp.setBounds(new Rectangle((140*6),(192*3)));
        theApp.setVisible(true);
        theApp.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        theApp.setFocusTraversalKeysEnabled(false);
        theApp.setTitle("Java Apple Computer Emulator");
        theApp.addKeyListener(computer.getKeyboard().getListener());
        theApp.addComponentListener(new ComponentListener() {
            //        theApp.screen.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
//                System.out.println("Screen resized");
                resizeVideo();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                resizeVideo();
            }

            @Override
            public void componentShown(ComponentEvent e) {
            }

            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });
        theApp.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {
            }

            @Override
            public void windowClosing(WindowEvent e) {
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }

            @Override
            public void windowIconified(WindowEvent e) {
                computer.getVideo().suspend();
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                computer.getVideo().resume();
                resizeVideo();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                resizeVideo();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                resizeVideo();
            }
        });
        EmulatorUILogic.registerDebugger();
        computer.getVideo().setScreen(theApp.getScreenGraphics());
        computer.coldStart();
    }

    public static void resizeVideo() {
        AbstractEmulatorFrame window = getFrame();
        if (window != null) {
            window.resizeVideo();
        }
    }

    public static Component getScreen() {
        AbstractEmulatorFrame window = getFrame();
        if (window != null) {
            return window.getScreen();
        }
        return null;
    }
}