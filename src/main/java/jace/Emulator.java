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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on January 15, 2007, 10:10 PM
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Emulator {

    public static Emulator instance;
    public static EmulatorUILogic logic = new EmulatorUILogic();
    public static Thread mainThread;

//    public static void main(String... args) {
//        mainThread = Thread.currentThread();
//        instance = new Emulator(args);
//    }

    public static Apple2e computer;

    /**
     * Creates a new instance of Emulator
     * @param args
     */
    public Emulator(List<String> args) {
        computer = new Apple2e();
        Configuration.buildTree();
        Configuration.loadSettings();
        mainThread = Thread.currentThread();
        Map<String, String> settings = new LinkedHashMap<>();
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                if (args.get(i).startsWith("-")) {
                    String key = args.get(i).substring(1);
                    if ((i + 1) < args.size()) {
                        String val = args.get(i + 1);
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
                    System.err.println("Did not understand parameter " + args.get(i) + ", skipping.");
                }
            }
        }
        Configuration.applySettings(settings);
//        EmulatorUILogic.registerDebugger();
        computer.coldStart();
    }

    public static void resizeVideo() {
//        AbstractEmulatorFrame window = getFrame();
//        if (window != null) {
//            window.resizeVideo();
//        }
    }
}