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

package jace.cheat;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import jace.apple2e.MOS65C02;
import jace.config.DeviceEnum;
import jace.config.InvokableAction;
import jace.core.Device;
import jace.core.RAMEvent;
import jace.core.RAMListener;

/**
 * Represents some combination of hacks that can be enabled or disabled through
 * the configuration interface.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public abstract class Cheats extends Device {
    public static enum Cheat implements DeviceEnum<Cheats> {
        Metacheat("Metacheat", MetaCheat.class, MetaCheat::new),
        MontezumasRevenge("Montezuma's Revenge", MontezumasRevengeCheats.class, MontezumasRevengeCheats::new),
        PrinceOfPersia("Prince of Persia", PrinceOfPersiaCheats.class, PrinceOfPersiaCheats::new),
        ProgramIdentity("Identify program", ProgramIdentity.class, ProgramIdentity::new),
        Wolfenstein("Wolfenstein", WolfensteinCheats.class, WolfensteinCheats::new);

        Supplier<Cheats> factory;
        String name;
        Class<? extends Cheats> clazz;

        Cheat(String name, Class<? extends Cheats> clazz, Supplier<Cheats> factory) {
            this.name = name;
            this.clazz = clazz;
            this.factory = factory;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Cheats create() {
            return factory.get();
        }

        @Override
        public boolean isInstance(Cheats cheat) {
            if (cheat == null) {
                return false;
            }
            return clazz == cheat.getClass();
        }
    }

    boolean cheatsActive = true;
    Set<RAMListener> listeners = new HashSet<>();
   
    @InvokableAction(name = "Toggle Cheats", alternatives = "cheat;Plug-in", defaultKeyMapping = "ctrl+shift+m")
    public void toggleCheats() {
        cheatsActive = !cheatsActive;
        if (cheatsActive) {
            attach();
        } else {
            detach();
        }
    }

    public RAMListener bypassCode(String name, int address, int addressEnd) {
        int noOperation = MOS65C02.COMMAND.NOP.ordinal();
        return addCheat(name, RAMEvent.TYPE.READ, (e) -> e.setNewValue(noOperation), address, addressEnd);
    }

    public RAMListener forceValue(String name, int value, int... address) {
        return addCheat(name, RAMEvent.TYPE.ANY, (e) -> e.setNewValue(value), address);
    }

    public RAMListener forceValue(String name, int value, Boolean auxFlag, int... address) {
        return addCheat(name, RAMEvent.TYPE.ANY, auxFlag, (e) -> e.setNewValue(value), address);
    }

    public RAMListener addCheat(String name, RAMEvent.TYPE type, RAMEvent.RAMEventHandler handler, int... address) {
        RAMListener listener;
        if (address.length == 1) {
            listener = getMemory().observe(getName() + ": " + name, type, address[0], handler);
        } else {
            listener = getMemory().observe(getName() + ": " + name, type, address[0], address[1], handler);
        }
        listeners.add(listener);
        return listener;
    }

    public RAMListener addCheat(String name, RAMEvent.TYPE type, Boolean auxFlag, RAMEvent.RAMEventHandler handler, int... address) {
        RAMListener listener;
        if (address.length == 1) {
            listener = getMemory().observe(getName() + ": " + name, type, address[0], auxFlag, handler);
        } else {
            listener = getMemory().observe(getName() + ": " + name, type, address[0], address[1], auxFlag, handler);
        }
        listeners.add(listener);
        return listener;
    }

    @Override
    public void attach() {
        registerListeners();
    }

    @Override
    public void detach() {
        unregisterListeners();
        super.detach();
    }

    public abstract void registerListeners();

    protected void unregisterListeners() {
        listeners.stream().forEach((l) -> {
            getMemory().removeListener(l);
        });
        listeners.clear();
    }

    public void removeListener(RAMListener l) {
        getMemory().removeListener(l);
        listeners.remove(l);
    }

    @Override
    public void reconfigure() {
        unregisterListeners();
        if (cheatsActive) {
            registerListeners();
        }
    }

    @Override
    public String getShortName() {
        return "cheat";
    }
}
