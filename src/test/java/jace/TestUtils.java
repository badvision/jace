/*
 * Copyright 2023 org.badvision.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jace;

import java.io.IOException;
import java.util.Arrays;

import jace.core.CPU;
import jace.core.Computer;
import jace.core.Device;
import jace.core.RAM;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import jace.ide.HeadlessProgram;
import jace.ide.Program;

/**
 *
 * @author brobert
 */
public class TestUtils {
    private TestUtils() {
        // Utility class has no constructor
    }

    public static void initComputer() {
        Utility.setHeadlessMode(true);
        Emulator.withComputer(Computer::reconfigure);
    }

    public static class FakeRAM extends RAM {
        byte[] memory = new byte[65536];
        public byte read(int address, TYPE eventType, boolean triggerEvent, boolean requireSyncronization) {
            return memory[address & 0x0ffff];
        }
        public byte readRaw(int address) {
            return memory[address & 0x0ffff];
        }

        public void write(int address, byte value, boolean triggerEvent, boolean requireSyncronization) {
            memory[address & 0x0ffff] = value;
        }

        @Override
        public String getName() {
            return "Fake ram";
        }

        @Override
        public String getShortName() {
            return "ram";
        }

        @Override
        public void reconfigure() {
        }

        @Override
        public void configureActiveMemory() {
        }

        @Override
        protected void loadRom(String path) throws IOException {
        }

        @Override
        public void attach() {
        }

        @Override
        public void performExtendedCommand(int i) {
        }

        @Override
        public String getState() {
            return "";
        }

        @Override
        public void resetState() {
        }
    }

    public static void clearFakeRam(RAM ram) {
        Arrays.fill(((FakeRAM) ram).memory, (byte) 0);
    }

    public static RAM initFakeRam() {
        RAM ram = new FakeRAM();
        Emulator.withComputer(c -> {
            c.setMemory(ram);
            c.getCpu().setMemory(ram);
        });
        return ram;
    }

    public static void assemble(String code, int addr) throws Exception {
        runAssemblyCode(code, addr, 0);
    }
    
    public static void runAssemblyCode(String code, int ticks) throws Exception {
        runAssemblyCode(code, 0x6000, ticks);
    }
    
    public static void runAssemblyCode(String code, int addr, int ticks) throws Exception {
        CPU cpu = Emulator.withComputer(c->c.getCpu(), null);
        HeadlessProgram program = new HeadlessProgram(Program.DocumentType.assembly);
        program.setValue("*=$"+Integer.toHexString(addr)+"\n "+code+"\n NOP\n RTS");
        program.execute();
        if (ticks > 0) {                
            cpu.resume();
            for (int i=0; i < ticks; i++) {
                cpu.doTick();
            }
            cpu.suspend();
        }
    }

    public static Device createSimpleDevice(Runnable r, String name) {
        return new Device() {
            @Override
            public void tick() {
                r.run();
            }
            
            @Override
            public String getShortName() {
                return name;
            }
            
            @Override
            public void reconfigure() {
            }
            
            @Override
            protected String getDeviceName() {
                return name;
            }
        };
    }
}
