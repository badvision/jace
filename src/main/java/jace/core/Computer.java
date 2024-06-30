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

package jace.core;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jace.JaceApplication;
import jace.apple2e.SoftSwitches;
import jace.config.ConfigurableField;
import jace.config.Configuration;
import jace.config.InvokableAction;
import jace.config.Reconfigurable;
import jace.state.StateManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;

/**
 * This is a very generic stub of a Computer and provides a generic set of
 * overall functionality, namely boot, pause and resume features. What sort of
 * memory, video and cpu get used are totally determined by fully-baked
 * subclasses.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class Computer implements Reconfigurable {

    public RAM memory;
    public CPU cpu;
    public Video video;
    public Keyboard keyboard;
    public StateManager stateManager;
    public Motherboard motherboard;
    public final CompletableFuture<Boolean> romLoaded;
    @ConfigurableField(category = "advanced", name = "State management", shortName = "rewind", description = "This enables rewind support, but consumes a lot of memory when active.")
    public boolean enableStateManager;
    public final SoundMixer mixer = new SoundMixer();
    final private BooleanProperty runningProperty = new SimpleBooleanProperty(false);

    /**
     * Creates a new instance of Computer
     */
    public Computer() {
        romLoaded = new CompletableFuture<>();    
    }

    abstract protected RAM createMemory();

    final public RAM getMemory() {
        if (memory == null) {
            memory = createMemory();
            memory.configureActiveMemory();
        }
        return memory;
    }

    final public Motherboard getMotherboard() {
        return motherboard;
    }

    ChangeListener<Boolean> runningPropertyListener = (prop, oldVal, newVal) -> runningProperty.set(newVal);
    final public void setMotherboard(Motherboard m) {
        if (motherboard != null && motherboard.isRunning()) {
            motherboard.suspend();
        }
        motherboard = m;
    }

    public BooleanProperty getRunningProperty() {
        return runningProperty;
    }

    final public boolean isRunning() {
        return getRunningProperty().get();
    }

    public void notifyVBLStateChanged(boolean state) {
        for (Optional<Card> c : getMemory().cards) {
            c.ifPresent(card -> card.notifyVBLStateChanged(state));
        }
        if (state && stateManager != null) {
            stateManager.notifyVBLActive();
        }
    }

    final public void setMemory(RAM memory) {
        if (this.memory != memory) {
            RAM oldMemory = this.memory;
            if (oldMemory != null) {
                oldMemory.detach();
            }
            this.memory = memory;
            if (memory != null) {
                if (oldMemory != null) {
                    memory.copyFrom(oldMemory);
                    oldMemory.detach();
                }
                memory.attach();
            }
        }
    }

    public void waitForNextCycle() {
        //@TODO IMPLEMENT TIMER SLEEP CODE!
    }

    final public Video getVideo() {
        return video;
    }

    final public void setVideo(Video video) {
        if (this.video != null && this.video != video) {
            getMotherboard().removeChildDevice(this.video);
        }
        this.video = video;
        if (video != null) {
            getMotherboard().addChildDevice(video);
            video.configureVideoMode();
            video.reconfigure();
        }
        if (JaceApplication.getApplication() != null) {
            JaceApplication.getApplication().reconnectUIHooks();
            JaceApplication.getApplication().controller.connectVideo(video);
        }
    }

    final public CPU getCpu() {
        return cpu;
    }

    final public void setCpu(CPU cpu) {
        this.cpu = cpu;
    }

    abstract public void loadRom(boolean reload) throws IOException;

    public void loadRom(String path) throws IOException {
        memory.loadRom(path);
        romLoaded.complete(true);
    }

    final public void deactivate() {
        if (cpu != null) {
            cpu.suspend();
        }
        if (motherboard != null) {
            motherboard.suspend();
        }
        if (video != null) {
            video.suspend();
        }
        if (mixer != null) {
            mixer.detach();
        }
    }

    /**
     * If the user wants a full reset, use the coldStart method.
     * This ensures a more consistent state of the machine.
     * Some games make bad assumptions about the initial state of the machine
     * and that fails to work if the machine is not reset to a known state first.
     */
    @InvokableAction(
            name = "Reset",
            description = "Process user-initatiated reboot (ctrl+apple+reset)",
            category = "general",
            alternatives = "reboot;reset;three-finger-salute;restart",
            defaultKeyMapping = {"Ctrl+Ignore Alt+Ignore Meta+Backspace", "Ctrl+Ignore Alt+Ignore Meta+Delete"})
    public void invokeReset() {
        if (SoftSwitches.PDL0.isOn()) {
            coldStart();
        } else {
            warmStart();
        }
    }

    /**
     * In a cold start, memory is reset (either two bytes per page as per Sather 4-15) or full-wipe
     * Also video softswitches are reset
     * Otherwise it does the same as warm start
     **/ 
    public abstract void coldStart();

    /**
     * In a warm start, memory is not reset, but the CPU and cards are reset
     * All but video softswitches are reset, putting the MMU in a known state
     */
    public abstract void warmStart();

    public Keyboard getKeyboard() {
        return this.keyboard;
    }

    protected abstract void doPause();

    protected abstract void doResume();

    @InvokableAction(name = "Pause", description = "Stops the computer, allowing reconfiguration of core elements", alternatives = "freeze;halt", defaultKeyMapping = {"meta+pause", "alt+pause"})
    final public boolean pause() {
        boolean result = getRunningProperty().get();
        doPause();
        getRunningProperty().set(false);
        return result;
    }

    @InvokableAction(name = "Resume", description = "Resumes the computer if it was previously paused", alternatives = "unpause;unfreeze;resume;play", defaultKeyMapping = {"meta+shift+pause", "alt+shift+pause"})
    final public void resume() {
        doResume();
        getRunningProperty().set(true);
    }

    @Override
    public void reconfigure() {
        if (keyboard == null) {
            keyboard = new Keyboard();
        }
        mixer.reconfigure();
        if (enableStateManager) {
            stateManager = StateManager.getInstance(this);
        } else {
            stateManager = null;
            StateManager.getInstance(this).invalidate();
        }
        Configuration.registerKeyHandlers();
    }
}
