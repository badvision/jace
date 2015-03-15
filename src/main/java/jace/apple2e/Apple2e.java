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
package jace.apple2e;

import jace.Emulator;
import jace.cheat.Cheats;
import jace.config.ClassSelection;
import jace.config.ConfigurableField;
import jace.core.Card;
import jace.core.Computer;
import jace.core.Motherboard;
import jace.core.RAM;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.state.Stateful;
import jace.core.Video;
import jace.hardware.CardDiskII;
import jace.hardware.CardExt80Col;
import jace.hardware.ConsoleProbe;
import jace.hardware.Joystick;
import jace.hardware.massStorage.CardMassStorage;
import java.awt.Graphics;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Apple2e is a computer with a 65c02 CPU, 128k of bankswitched ram,
 * double-hires graphics, and up to seven peripheral I/O cards installed. Pause
 * and resume are implemented by the Motherboard class. This class provides
 * overall configuration of the computer, but the actual operation of the
 * computer and its timing characteristics are managed in the Motherboard class.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
public class Apple2e extends Computer {

    static int IRQ_VECTOR = 0x003F2;
    @ConfigurableField(name = "Slot 1", shortName = "s1card")
    public ClassSelection card1 = new ClassSelection(Card.class, null);
    @ConfigurableField(name = "Slot 2", shortName = "s2card")
//    public Class<? extends Card> card2 = CardSSC.class;
    public ClassSelection card2 = new ClassSelection(Card.class, null);
    @ConfigurableField(name = "Slot 3", shortName = "s3card")
    public ClassSelection card3 = new ClassSelection(Card.class, null);
    @ConfigurableField(name = "Slot 4", shortName = "s4card")
    public ClassSelection card4 = new ClassSelection(Card.class, null);
    @ConfigurableField(name = "Slot 5", shortName = "s5card")
    public ClassSelection card5 = new ClassSelection(Card.class, null);
    @ConfigurableField(name = "Slot 6", shortName = "s6card")
    public ClassSelection card6 = new ClassSelection(Card.class, CardDiskII.class);
    @ConfigurableField(name = "Slot 7", shortName = "s7card")
    public ClassSelection card7 = new ClassSelection(Card.class, CardMassStorage.class);
    @ConfigurableField(name = "Debug rom", shortName = "debugRom", description = "Use debugger //e rom")
    public boolean useDebugRom = false;
    @ConfigurableField(name = "Console probe", description = "Enable console redirection (experimental!)")
    public boolean useConsoleProbe = false;
    private ConsoleProbe probe = new ConsoleProbe();
    @ConfigurableField(name = "Helpful hints", shortName = "hints")
    public boolean enableHints = true;
    @ConfigurableField(name = "Renderer", shortName = "video", description = "Video rendering implementation")
    public ClassSelection videoRenderer = new ClassSelection(Video.class, VideoNTSC.class);
    @ConfigurableField(name = "Aux Ram", shortName = "ram", description = "Aux ram card")
    public ClassSelection ramCard = new ClassSelection(RAM128k.class, CardExt80Col.class);
    public Joystick joystick1;
    public Joystick joystick2;
    @ConfigurableField(name = "Activate Cheats", shortName = "cheat", defaultValue = "")
    public ClassSelection cheatEngine = new ClassSelection(Cheats.class, null);
    public Cheats activeCheatEngine = null;

    /**
     * Creates a new instance of Apple2e
     */
    public Apple2e() {
        super();
        try {
            reconfigure();
            // Setup core resources
            joystick1 = new Joystick(0, this);
            joystick2 = new Joystick(1, this);
            setCpu(new MOS65C02(this));
            reinitMotherboard();
        } catch (Throwable t) {
            System.err.println("Unable to initalize virtual machine");
            t.printStackTrace(System.err);
        }
    }

    @Override
    public String getName() {
        return "Computer (Apple //e)";
    }

    private void reinitMotherboard() {
        if (motherboard != null && motherboard.isRunning()) {
            motherboard.suspend();
        }
        motherboard = new Motherboard(this);
        motherboard.reconfigure();
        motherboard.miscDevices.add(joystick1);
        motherboard.miscDevices.add(joystick2);
    }

    @Override
    public void coldStart() {
        pause();
        reinitMotherboard();
        reboot();
        //getMemory().dump();
        for (SoftSwitches s : SoftSwitches.values()) {
            s.getSwitch().reset();
        }
        getMemory().configureActiveMemory();
        getVideo().configureVideoMode();
        getCpu().reset();
        for (Optional<Card> c : getMemory().getAllCards()) {
            c.ifPresent(Card::reset);
        }

        resume();
        /*
         getCpu().resume();
         getVideo().resume();
         */
    }

    public void reboot() {
        RAM r = getMemory();
        r.write(IRQ_VECTOR, (byte) 0x00, false, true);
        r.write(IRQ_VECTOR + 1, (byte) 0x00, false, true);
        r.write(IRQ_VECTOR + 2, (byte) 0x00, false, true);
        warmStart();
    }

    @Override
    public void warmStart() {
        boolean restart = pause();
        for (SoftSwitches s : SoftSwitches.values()) {
            s.getSwitch().reset();
        }
        getMemory().configureActiveMemory();
        getVideo().configureVideoMode();
        getCpu().reset();
        for (Optional<Card> c : getMemory().getAllCards()) {
            c.ifPresent(Card::reset);
        }
        getCpu().resume();
        resume();
    }

    private void insertCard(Class<? extends Card> type, int slot) throws NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        if (getMemory().getCard(slot).isPresent()) {
            if (getMemory().getCard(slot).get().getClass().equals(type)) {
                return;
            }
            getMemory().removeCard(slot);
        }
        if (type != null) {
            try {
                Card card = type.getConstructor(Computer.class).newInstance(this);
                getMemory().addCard(card, slot);
            } catch (InstantiationException | IllegalAccessException ex) {
                Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public final void reconfigure() {
        boolean restart = pause();

        super.reconfigure();

        RAM128k currentMemory = (RAM128k) getMemory();
        if (currentMemory != null && !(currentMemory.getClass().equals(ramCard.getValue()))) {
            try {
                RAM128k newMemory = (RAM128k) ramCard.getValue().getConstructor(Computer.class).newInstance(this);
                newMemory.copyFrom(currentMemory);
                setMemory(newMemory);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
                Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (getMemory() == null) {
            try {
                currentMemory = (RAM128k) ramCard.getValue().getConstructor(Computer.class).newInstance(this);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException ex) {
                Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                setMemory(currentMemory);
                for (SoftSwitches s : SoftSwitches.values()) {
                    s.getSwitch().register(this);
                }
            } catch (Throwable ex) {
            }
        }
        currentMemory.reconfigure();

        try {
            if (useConsoleProbe) {
                probe.init(this);
            } else {
                probe.shutdown();
            }

            if (useDebugRom) {
                loadRom("jace/data/apple2e_debug.rom");
            } else {
                loadRom("jace/data/apple2e.rom");
            }

            if (getVideo() == null || getVideo().getClass() != videoRenderer.getValue()) {
                Graphics g = null;
                if (getVideo() != null) {
                    getVideo().suspend();
                }
                try {
                    setVideo((Video) videoRenderer.getValue().getConstructor(Computer.class).newInstance(this));
                    getVideo().configureVideoMode();
                    getVideo().reconfigure();
                    Emulator.resizeVideo();
                    getVideo().resume();
                } catch (InstantiationException | IllegalAccessException ex) {
                    Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
                } catch (NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException ex) {
                    Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            try {
                // Add all new cards
                insertCard(card1.getValue(), 1);
                insertCard(card2.getValue(), 2);
                insertCard(card3.getValue(), 3);
                insertCard(card4.getValue(), 4);
                insertCard(card5.getValue(), 5);
                insertCard(card6.getValue(), 6);
                insertCard(card7.getValue(), 7);
            } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (enableHints) {
                enableHints();
            } else {
                disableHints();
            }
            getMemory().configureActiveMemory();

            if (cheatEngine.getValue() == null) {
                if (activeCheatEngine != null) {
                    activeCheatEngine.detach();
                }
                activeCheatEngine = null;
            } else {
                boolean startCheats = true;
                if (activeCheatEngine != null) {
                    if (activeCheatEngine.getClass().equals(cheatEngine.getValue())) {
                        startCheats = false;
                    } else {
                        activeCheatEngine.detach();
                        activeCheatEngine = null;
                    }
                }
                if (startCheats) {
                    try {
                        activeCheatEngine = (Cheats) cheatEngine.getValue().newInstance();
                    } catch (InstantiationException | IllegalAccessException ex) {
                        Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    activeCheatEngine.attach();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (restart) {
            resume();
        }
    }

    @Override
    protected void doPause() {
        if (motherboard == null) {
            return;
        }
        motherboard.pause();
    }

    @Override
    protected void doResume() {
        if (motherboard == null) {
            return;
        }
        motherboard.resume();
    }

    @Override
    protected boolean isRunning() {
        if (motherboard == null) {
            return false;
        }
        return motherboard.isRunning() && !motherboard.isPaused;
    }
    private List<RAMListener> hints = new ArrayList<>();

    Thread twinkleEffect;

    private void enableHints() {
        if (hints.isEmpty()) {
            hints.add(new RAMListener(RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(0x0FB63);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    if (getCpu().getProgramCounter() != getScopeStart()) {
                        return;
                    }
                    if (twinkleEffect == null || !twinkleEffect.isAlive()) {
                        twinkleEffect = new Thread(() -> {
                            try {
                                // Give the floppy drive time to start
                                Thread.sleep(1000);
                                if (getCpu().getProgramCounter() >> 8 != 0x0c6) {
                                    return;
                                }

                                int row = 2;
                                for (String s : new String[]{
                                    "              Welcome to",
                                    "         _    __    ___   ____ ",
                                    "        | |  / /\\  / / ` | |_  ",
                                    "      \\_|_| /_/--\\ \\_\\_, |_|__ ",
                                    "",
                                    "    Java Apple Computer Emulator",
                                    "",
                                    "        Presented by BLuRry",
                                    "        http://goo.gl/SnzqG",
                                    "",
                                    "Press F1 to insert disk in Slot 6, D1",
                                    "Press F2 to insert disk in Slot 6, D2",
                                    "Press F3 to insert HDV or 2MG in slot 7",
                                    "Press F4 to open configuration",
                                    "Press F5 to run raw binary program",
                                    "Press F8 to correct the aspect ratio",
                                    "Press F9 to toggle fullscreen",
                                    "Press F10 to open/close the debugger",
                                    "",
                                    "      If metacheat is enabled:",
                                    "Press HOME to activate memory heatmap",
                                    "Press END to activate metacheat search"
                                }) {
                                    int addr = 0x0401 + VideoDHGR.calculateTextOffset(row++);
                                    for (char c : s.toCharArray()) {
                                        getMemory().write(addr++, (byte) (c | 0x080), false, true);
                                    }
                                }
                                while (getCpu().getProgramCounter() >> 8 == 0x0c6) {
                                    int x = (int) (Math.random() * 26.0) + 7;
                                    int y = (int) (Math.random() * 4.0) + 3;
                                    int addr = 0x0400 + VideoDHGR.calculateTextOffset(y) + x;
                                    byte old = getMemory().readRaw(addr);
                                    for (char c : "+xX*+".toCharArray()) {
                                        if (getCpu().getProgramCounter() >> 8 != 0x0c6) {
                                            break;
                                        }
                                        getMemory().write(addr, (byte) (c | 0x080), true, true);
                                        Thread.sleep(100);
                                    }
                                    getMemory().write(addr, old, true, true);
                                }
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Apple2e.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
                        twinkleEffect.setName("Startup Animation");
                        twinkleEffect.start();
                    }
                }
            });
            // Latch to the PRODOS SYNTAX CHECK parser
            /*
             hints.add(new RAMListener(RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
             @Override
             protected void doConfig() {setScopeStart(0x0a685);}

             @Override
             protected void doEvent(RAMEvent e) {
             String in = "";
             for (int i=0x0200; i < 0x0300; i++) {
             char c = (char) (getMemory().readRaw(i) & 0x07f);
             if (c == 0x0d) break;
             in += c;
             }
                    
             System.err.println("Intercepted command: "+in);
             }
             });
             */
        }
        hints.stream().forEach((hint) -> {
            getMemory().addListener(hint);
        });
    }

    private void disableHints() {
        hints.stream().forEach((hint) -> {
            getMemory().removeListener(hint);
        });
    }

    @Override
    public String getShortName() {
        return "computer";
    }
}
