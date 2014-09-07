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
package jace.core;

import jace.config.ConfigurableField;
import jace.config.InvokableAction;
import jace.config.Reconfigurable;
import jace.library.MediaLibrary;
import jace.state.StateManager;
import java.io.IOException;

/**
 * This is a very generic stub of a Computer and provides a generic set of
 * overall functionality, namely boot, pause and resume features. What sort of
 * memory, video and cpu get used are totally determined by fully-baked
 * subclasses.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class Computer implements Reconfigurable {

    static private Computer theComputer;
    public RAM memory;
    public CPU cpu;
    public Video video;
    public Keyboard keyboard;
    public StateManager stateManager;
    public MediaLibrary mediaLibrary = MediaLibrary.getInstance();
    @ConfigurableField(category = "advanced", name = "State management", shortName = "rewind", description = "This enables rewind support, but consumes a lot of memory when active.")
    public boolean enableStateManager;

    /**
     * Creates a new instance of Computer
     */
    public Computer() {
        theComputer = this;
        keyboard = new Keyboard();
    }

    public RAM getMemory() {
        return memory;
    }

    public void notifyVBLStateChanged(boolean state) {
        for (Card c : getMemory().cards) {
            if (c == null) {
                continue;
            }
            c.notifyVBLStateChanged(state);
        }
        if (state && stateManager != null) {
            stateManager.notifyVBLActive();
        }
    }

    public void setMemory(RAM memory) {
        if (this.memory != memory) {
            if (this.memory != null) {
                this.memory.detach();
            }
            memory.attach();
        }
        this.memory = memory;
    }

    public void waitForNextCycle() {
        //@TODO IMPLEMENT TIMER SLEEP CODE!
    }

    public Video getVideo() {
        return video;
    }

    public void setVideo(Video video) {
        this.video = video;
    }

    public CPU getCpu() {
        return cpu;
    }

    public void setCpu(CPU cpu) {
        this.cpu = cpu;
    }

    public void loadRom(String path) throws IOException {
        memory.loadRom(path);
    }

    @InvokableAction(
            name = "Cold boot",
    description = "Process startup sequence from power-up",
    category = "general",
    alternatives = "Full reset;reset emulator")
    public abstract void coldStart();

    @InvokableAction(
            name = "Warm boot",
    description = "Process user-initatiated reboot (ctrl+apple+reset)",
    category = "general",
    alternatives = "reboot;reset;three-finger-salute")
    public abstract void warmStart();

    static public Computer getComputer() {
        return theComputer;
    }

    public Keyboard getKeyboard() {
        return this.keyboard;
    }

    protected abstract boolean isRunning();

    protected abstract void doPause();

    protected abstract void doResume();

    @InvokableAction(name = "Pause", description = "Stops the computer, allowing reconfiguration of core elements", alternatives = "freeze;halt")
    public static boolean pause() {
        boolean result = false;
        if (theComputer != null) {
            result = theComputer.isRunning();
            theComputer.doPause();
        }
        return result;
    }

    @InvokableAction(name = "Resume", description = "Resumes the computer if it was previously paused", alternatives = "unpause;unfreeze;resume")
    public static void resume() {
        if (theComputer != null) {
            theComputer.doResume();
        }
    }

    public void reconfigure() {
        if (enableStateManager) {
            stateManager = StateManager.getInstance();
        } else {
            stateManager = null;
            StateManager.getInstance().invalidate();
        }
    }
}
