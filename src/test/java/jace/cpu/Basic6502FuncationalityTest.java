/*
 * Copyright 2016 Brendan Robert
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
package jace.cpu;

import jace.Emulator;
import jace.apple2e.Apple2e;
import jace.apple2e.MOS65C02;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.SoundMixer;
import jace.core.Utility;
import jace.ide.HeadlessProgram;
import jace.ide.Program;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Basic test functionality to assert correct 6502 decode and execution.
 *
 * @author blurry
 */
public class Basic6502FuncationalityTest {

    static Computer computer;
    static MOS65C02 cpu;
    static RAM ram;

    @BeforeClass
    public static void setupClass() {
        Utility.setHeadlessMode(true);
        SoundMixer.MUTE = true;
        computer = new Apple2e();
        cpu = (MOS65C02) computer.getCpu();
        ram = computer.getMemory();
        Emulator.computer = (Apple2e) computer;
        computer.pause();
    }

    @AfterClass
    public static void teardownClass() {
    }

    @Before
    public void setup() {
        cpu.suspend();
        cpu.clearState();
    }

    @Test
    public void testAdditionNonDecimal() {
        cpu.A = 0;
        cpu.D = false;
        cpu.C = 0;
        assemble(" adc #1");
        assertEquals("0+1 (c=0) = 1", 1, cpu.A);
        assertFalse("Result is not zero", cpu.Z);
    }

    @Test
    public void testAdditionNonDecimalWithCarry() {
        cpu.A = 0;
        cpu.D = false;
        cpu.C = 1;
        assemble(" adc #1");
        assertEquals("0+1 (c=1) = 2", 2, cpu.A);
        assertFalse("Result is not zero", cpu.Z);
    }
    
    @Test
    public void testAdditionDecimal() {
        cpu.A = 9;
        cpu.D = true;
        cpu.C = 0;
        assemble(" adc #1");
        assertEquals("9+1 (c=0) = 0x10", 0x10, cpu.A);
        assertFalse("Result is not zero", cpu.Z);
    }
    
    @Test
    public void testAdditionDecimalWithCarry() {
        cpu.A = 9;
        cpu.D = true;
        cpu.C = 1;
        assemble(" adc #1");
        assertEquals("9+1 (c=1) = 0x11", 0x11, cpu.A);
        assertFalse("Result is not zero", cpu.Z);
    }
    
    private void assemble(String code) {
        assembleAt(code, 0x0300);
    }
    
    private void assembleAt(String code, int addr) {
        HeadlessProgram program = new HeadlessProgram(Program.DocumentType.assembly);
        program.setValue("*="+Integer.toHexString(addr)+"\n"+code+"\n BRK");
        program.execute();
        cpu.tick();
    }
}
