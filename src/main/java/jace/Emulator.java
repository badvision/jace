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

package jace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import jace.config.Configuration;
import jace.core.RAM;
import jace.apple2e.Apple2e;

/**
 * Created on January 15, 2007, 10:10 PM
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Emulator {

    public static Emulator instance;
    private static EmulatorUILogic logic;
    public static Thread mainThread;

//    public static void main(String... args) {
//        mainThread = Thread.currentThread();
//        instance = new Emulator(args);
//    }

    private final Apple2e computer;

    public static EmulatorUILogic getUILogic() {
        if (logic == null) {
            logic = new EmulatorUILogic();
        }
        return logic;
    }
    
    public static Emulator getInstance(List<String> args) {
        Emulator i = getInstance();
        i.processCmdlineArgs(args);
        return i;
    }
    
    public static void abort() {
        if (instance != null) {
            if (instance.computer != null) {
                instance.computer.getMotherboard().suspend();
                instance.computer.getMotherboard().detach();
                if (instance.computer.getVideo() != null) {
                    instance.computer.getVideo().suspend();
                    instance.computer.getVideo().detach();
                }
            }
        }
        instance = null;
    }
    
    public static Emulator getInstance() {
        if (instance == null) {
            instance = new Emulator();
        }
        return instance;
    }
    
    private static Apple2e getComputer() {
        return getInstance().computer;        
    }

    public static void whileSuspended(Consumer<Apple2e> action) {
        withComputer(c->c.getMotherboard().whileSuspended(()->action.accept(c)));
    }

    public static <T> T whileSuspended(Function<Apple2e, T> action, T defaultValue) {
        return withComputer(c->c.getMotherboard().whileSuspended(()->action.apply(c), defaultValue), defaultValue);
    }

    public static void withComputer(Consumer<Apple2e> c) {
        Apple2e computer = getComputer();
        if (computer != null) {
            c.accept(computer);
        } else {
            System.err.println("No computer available!");
            Thread.dumpStack();
        }
    }

    public static <T> T withComputer(Function<Apple2e, T> f, T defaultValue) {
        Apple2e computer = getComputer();
        if (computer != null) {
            return f.apply(computer);
        } else {
            System.err.println("No computer available!");
            Thread.dumpStack();
            return defaultValue;
        }
    }

    public static void withMemory(Consumer<RAM> m) {
        Emulator.withMemory(mem-> {
            m.accept(mem);
            return null;
        }, null);
    }

    public static <T> T withMemory(Function<RAM, T> m, T defaultValue) {
        return withComputer(c->{
            RAM memory = c.getMemory();
            if (memory != null) {
                return m.apply(memory);
            } else {
                System.err.println("No memory available!");
                Thread.dumpStack();
                return defaultValue;
            }
        }, defaultValue);
    }

    public static void withVideo(Consumer<jace.core.Video> v) {
        withComputer(c->{
            jace.core.Video video = c.getVideo();
            if (video != null) {
                v.accept(video);
            } else {
                System.err.println("No video available!");
                Thread.dumpStack();
            }
        });
    }

    /**
     * Creates a new instance of Emulator
     */
    private Emulator() {
        instance = this;
        computer = new Apple2e();
        Configuration.buildTree();
        Configuration.loadSettings();
        Configuration.applySettings(Configuration.BASE);
        mainThread = Thread.currentThread();
//        EmulatorUILogic.registerDebugger();
//        computer.coldStart();
    }

    private void processCmdlineArgs(List<String> args) {
        if (args == null || args.isEmpty()) {
            return;
        }
        Map<String, String> settings = new LinkedHashMap<>();
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
        Configuration.applySettings(settings);
    }
}