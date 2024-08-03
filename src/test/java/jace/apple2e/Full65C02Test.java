/*
 * Copyright 2024 Brendan Robert
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
package jace.apple2e;

import static jace.TestProgram.Flag.CARRY_CLEAR;
import static jace.TestProgram.Flag.CARRY_SET;
import static jace.TestProgram.Flag.DECIMAL_CLEAR;
import static jace.TestProgram.Flag.DECIMAL_SET;
import static jace.TestProgram.Flag.INTERRUPT_CLEAR;
import static jace.TestProgram.Flag.INTERRUPT_SET;
import static jace.TestProgram.Flag.IS_ZERO;
import static jace.TestProgram.Flag.NEGATIVE;
import static jace.TestProgram.Flag.NOT_ZERO;
import static jace.TestProgram.Flag.OVERFLOW_CLEAR;
import static jace.TestProgram.Flag.OVERFLOW_SET;
import static jace.TestProgram.Flag.POSITIVE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.ProgramException;
import jace.TestProgram;
import jace.TestUtils;
import jace.core.Computer;
import jace.core.RAMEvent.TYPE;
import jace.core.SoundMixer;

/**
 * Basic test functionality to assert correct 6502 decode and execution.
 *
 * @author blurry
 */
public class Full65C02Test {

    static Computer computer;
    public static MOS65C02 cpu;
    static RAM128k ram;

    @BeforeClass
    public static void setupClass() {
        TestUtils.initComputer();
        SoundMixer.MUTE = true;
        computer = Emulator.withComputer(c->c, null);
        cpu = (MOS65C02) computer.getCpu();
        ram = (RAM128k) computer.getMemory();
    }

    @AfterClass
    public static void teardownClass() {
    }

    @Before
    public void setup() {
        computer.pause();
        cpu.clearState();
    }

    @Test
    /* ADC: All CPU flags/modes */
    public void testAdditionCPUFlags() throws ProgramException {
        new TestProgram()
            // Add 1 w/o carry; 1+1 = 2
            .assertTimed("ADC #1", 2)
            .assertA(1)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Add 1 w/ carry 1+0+c = 2
            .add("SEC")
            .assertTimed("ADC #0", 2)
            .assertA(2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Decimal: ADD 8 w/o carry; 2+8 = 10
            .add("SED")
            .assertTimed("ADC #8", 3)
            .assertA(0x10)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Decimal: ADD 9 w/ carry; 10+9+c = 20
            .add("SEC")
            .assertTimed("ADC #09", 3)
            .assertA(0x20)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Decimal: Overflow check; 20 + 99 + C = 20 (carry, no overflow)
            .add("""
                SED
                SEC
                LDA #$20
                ADC #$99
            """)
            .assertA(0x20)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Decimal: Overflow check; 20 + 64 + C = 85 (overflow)
            .add("ADC #$64")
            .assertA(0x85)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_SET, NEGATIVE)
            // Overflow check; 0x7F + 0x01 = 0x80 (overflow)
            .add("CLD")
            .add("LDA #$7F")
            .assertTimed("ADC #1", 2)
            .assertA(0x80)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_SET, NEGATIVE)
            .run();
    }

    @Test
    /* ADC: All addressing modes -- these are used in many opcodes so we mostly just need to test here */
    public void testAdditionAddressingModes() throws ProgramException {
        // Start test by filling zero page and $1000...$10FF with 0...255
        new TestProgram("""
                LDX #0
            -   TXA 
                STA $00,X
                STA $1000,X
                INX
                BNE -
                LDA #1
            """)
            // 1: AB,X
            .add("LDX #$7F")
            .assertTimed("ADC $1000,X", 4)
            .assertA(0x80)
            .assertFlags(OVERFLOW_SET, CARRY_CLEAR, NEGATIVE)
            // 2: AB,Y
            .add("LDY #$20")
            .assertTimed("ADC $1000,Y", 4)
            .assertA(0xA0)
            .assertFlags(OVERFLOW_CLEAR, CARRY_CLEAR, NEGATIVE)
            // 3: izp ($09) == 0x100f ==> A+=f
            .assertTimed("ADC ($0f)", 5)
            .assertA(0xAF)
            // 4: izx ($00,x) where X=f = 0x100f ==> A+=f
            .add("LDX #$0F")
            .assertTimed("ADC ($00,x)", 6)
            .assertA(0xBE)
            // 5: izy ($00),y where Y=20 = 0x102F ==> A+=2F
            .add("LDY #$21")
            .assertTimed("ADC ($0F),y", 5)
            .assertA(0xEE)
            // 6: zpx $00,x where X=10 ==> A+=10
            .add("LDX #$10")
            .assertTimed("ADC $00,x", 4)
            .assertA(0xFE)
            // 7: zp $01 ==> A+=01
            .assertTimed("ADC $01", 3)
            .assertA(0xFF)
            // 8: abs $1001 ==> A+=01
            .assertTimed("ADC $1001", 4)
            .assertA(0x00)
            .assertFlags(IS_ZERO, OVERFLOW_CLEAR, CARRY_SET, POSITIVE)
            // Now check boundary conditions on indexed addressing for timing differences
            .add("LDX #$FF")
            .assertTimed("ADC $10FF,X", 5)
            .add("LDY #$FF")
            .assertTimed("ADC $10FF,Y", 5)
            .assertTimed("ADC ($0F),Y", 6)
            .run();
    }

    @Test
    /* ABS: All CPU flags/modes */
    public void testSubtraction() throws ProgramException {
        new TestProgram("SEC")
            // 0-1 = -1
            .assertTimed("SBC #1", 2)
            .assertA(0xFF)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, NEGATIVE)
            // 127 - -1 = 128 (Overflow)
            .add("SEC")
            .add("LDA #$7F")
            .assertTimed("SBC #$FF", 2)
            .assertA(0x80)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_SET, NEGATIVE)
            // -128 - 1 = -129 (overflow)
            .add("SEC")
            .add("LDA #$80")
            .assertTimed("SBC #$01", 2)
            .assertA(0x7F)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_SET, POSITIVE)
            // 20-10=10 (no overflow)
            .add("LDA #$30")
            .assertTimed("SBC #$10", 2)
            .assertA(0x20)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Decimal: 20-5=15
            .add("SED")
            .assertTimed("SBC #$05", 3)
            .assertA(0x15)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Decimal: 0x15-0x05=0x10
            .assertTimed("SBC #$05", 3)
            .assertA(0x10)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE)
            // Decimal: 99-19=80
            .add("LDA #$99")
            .assertTimed("SBC #$19", 3)
            .assertA(0x80)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_CLEAR, NEGATIVE)
            // Decimal: 99-50=49 (unintuitively causes overflow)
            .add("LDA #$99")
            .assertTimed("SBC #$50", 3)
            .assertA(0x49)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_SET, POSITIVE)
            // Decimal: 19 - 22 = 97 (arithmetic underflow clears carry)
            .add("LDA #$19")
            .assertTimed("SBC #$22", 3)
            .assertA(0x97)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, NEGATIVE)
            // Test cycle counts for other addressing modes
            .add("CLD")
            .assertTimed("SBC $1000", 4)
            .assertTimed("SBC $1000,x", 4)
            .assertTimed("SBC $1000,y", 4)
            .assertTimed("SBC ($00)", 5)
            .assertTimed("SBC ($00,X)", 6)
            .assertTimed("SBC ($00),Y", 5)
            .assertTimed("SBC $00", 3)
            .assertTimed("SBC $00,X", 4)
            .run();
    }

    @Test
    /* Full test of ADC and SBC with binary coded decimal mode */
    public void testBCD() throws ProgramException, URISyntaxException, IOException {
        Path resource = Paths.get(getClass().getResource("/jace/bcd_test.asm").toURI());
        String testCode = Files.readString(resource);
        TestProgram test = new TestProgram(testCode);
        test.defineError("Error when performing ADC operation");
        test.defineError("Error when performing SBC operation");
        try {
            test.runForTicks(50000000);
        } catch (ProgramException e) {
            // Dump memory from 0x0006 to 0x0015
            for (int i = 0x0006; i <= 0x0015; i++) {
                System.out.printf("%04X: %02X\n", i, ram.read(i, TYPE.READ_DATA, false, false));
            }         
            throw e;
        }
    }

    @Test
    /* Test of the processor flags */
    public void testFlags() throws ProgramException {
        new TestProgram()
            // Test explicit flag set/clear commands (and the tests by way of this cover all branch instructions)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("SEC", 2)
            .assertFlags(CARRY_SET, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("CLC", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("SED", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_SET, INTERRUPT_CLEAR)
            .assertTimed("CLD", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("SEI", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_SET)
            .assertTimed("CLI", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            // Set overflow flag by hacking the P register
            .add("""
                LDA #%01000000
                PHA
                PLP
            """)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_SET, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("CLV", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            // Test Zero and Negative flags (within reason, the ADC/SBC tests cover these more thoroughly)
            .assertTimed("LDA #0",2 )
            .assertFlags(CARRY_CLEAR, IS_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("LDA #$ff", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, NEGATIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("LDY #0", 2)
            .assertFlags(CARRY_CLEAR, IS_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("LDY #$ff", 2)
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, NEGATIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("LDX #0", 2)
            .assertFlags(CARRY_CLEAR, IS_ZERO, OVERFLOW_CLEAR, POSITIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .assertTimed("LDX #$ff", 2) 
            .assertFlags(CARRY_CLEAR, NOT_ZERO, OVERFLOW_CLEAR, NEGATIVE, DECIMAL_CLEAR, INTERRUPT_CLEAR)
            .run();
    }

    /* Test stack operations */
    @Test
    public void testStack() throws ProgramException {
        new TestProgram()
            .assertTimed("TSX", 2)
            .assertX(0xFF)
            .assertTimed("LDA #255", 2)
            .assertTimed("PHA", 3)
            .assertTimed("LDX #11", 2)
            .assertTimed("PHX", 3)
            .assertTimed("LDY #12", 2)
            .assertTimed("PHY", 3)
            .assertTimed("PHP", 3)  
            .assertTimed("TSX", 2)
            .assertX(0xFB)
            .assertTimed("PLP", 4)
            .add("LDA #0")  
            .assertFlags(IS_ZERO, POSITIVE)
            .assertTimed("PLA", 4)
            .assertA(12)
            .assertFlags(NOT_ZERO, POSITIVE)
            .add("LDY #$FF")  
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("PLY", 4)
            .assertY(11)
            .assertFlags(NOT_ZERO, POSITIVE)
            .assertTimed("PLX", 4)
            .assertX(255)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .run();
    }

    /* Test logic operations */
    @Test
    public void testLogic() throws ProgramException {
        // OR, AND, EOR
        new TestProgram()
            .assertTimed("LDA #0x55", 2)
            .assertTimed("AND #0xAA", 2)
            .assertA(0x00)
            .assertFlags(IS_ZERO, POSITIVE)
            .assertTimed("LDA #0x55", 2)
            .assertTimed("ORA #0xAA", 2)
            .assertA(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("LDA #0x55", 2)
            .assertTimed("EOR #0xFF", 2)
            .assertA(0xAA)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .run();
        TestProgram cycleCounts = new TestProgram();
        for (String opcode : new String[] {"AND", "ORA", "EOR"}) {
            cycleCounts.assertTimed(opcode + " $1000", 4)
            .assertTimed(opcode + " $1000,x", 4)
            .assertTimed(opcode + " $1000,y", 4)
            .assertTimed(opcode + " ($00)", 5)
            .assertTimed(opcode + " ($00,X)", 6)
            .assertTimed(opcode + " ($00),Y", 5)
            .assertTimed(opcode + " $00", 3)
            .assertTimed(opcode + " $00,X", 4);
        }
        cycleCounts.run();
        // ASL
        new TestProgram()
            .assertTimed("LDA #0x55", 2)
            .add("STA $1000")
            .assertTimed("ASL", 2)
            .assertA(0xAA)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .assertTimed("ASL", 2)
            .assertA(0x54)
            .assertFlags(NOT_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("ASL $1000", 6)
            .assertAddrVal(0x1000, 0xAA)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .assertTimed("ASL $1000,x", 7)
            .assertTimed("ASL $00", 5)
            .assertTimed("ASL $00,x", 6)
            .run();
        // LSR
        new TestProgram()
            .assertTimed("LDA #0x55", 2)
            .add("STA $1000")
            .assertTimed("LSR", 2)
            .assertA(0x2A)
            .assertFlags(NOT_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("LSR", 2)
            .assertA(0x15)
            .assertFlags(NOT_ZERO, POSITIVE, CARRY_CLEAR)
            .assertTimed("LSR $1000", 6)
            .assertAddrVal(0x1000, 0x2A)
            .assertFlags(NOT_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("LSR $1000,x", 7)
            .assertTimed("LSR $00", 5)
            .assertTimed("LSR $00,x", 6)
            .run();
        // BIT
        new TestProgram()
            .add("LDA #$FF")
            .add("STA $FF")
            .assertTimed("BIT #0", 2)
            .assertFlags(IS_ZERO, POSITIVE, OVERFLOW_CLEAR)
            .assertTimed("BIT #$FF", 2)
            .assertFlags(NOT_ZERO, NEGATIVE, OVERFLOW_CLEAR)
            .assertTimed("BIT $FF", 3)
            .assertFlags(NOT_ZERO, NEGATIVE, OVERFLOW_SET)
            .add("CLV")
            .add("LDA #$40")
            .assertTimed("BIT #$40", 2)
            .assertFlags(NOT_ZERO, POSITIVE, OVERFLOW_CLEAR)
            .assertTimed("BIT #$80", 2)
            .assertFlags(IS_ZERO, NEGATIVE, OVERFLOW_CLEAR)
            .assertTimed("BIT $1000", 4)
            .assertTimed("BIT $1000,x", 4)
            .assertTimed("BIT $00,X", 4)
            .run();
        // ROL
        new TestProgram()
            .add("LDA #0x55")
            .add("STA $1000")
            .assertTimed("ROL", 2)
            .assertA(0xAA)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .assertTimed("ROL", 2)
            .assertA(0x54)
            .assertFlags(NOT_ZERO, POSITIVE, CARRY_SET)
            .add("CLC")
            .assertTimed("ROL $1000", 6)
            .assertAddrVal(0x1000, 0xAA)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .assertTimed("ROL $1000,x", 7)
            .assertTimed("ROL $00", 5)
            .assertTimed("ROL $00,x", 6)
            .run();
        // ROR
        new TestProgram()
            .add("LDA #0x55")
            .add("STA $1000")
            .assertTimed("ROR", 2)
            .assertA(0x2A)
            .assertFlags(NOT_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("ROR", 2)
            .assertA(0x95)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .assertTimed("ROR $1000", 6)
            .assertAddrVal(0x1000, 0x2A)
            .assertFlags(NOT_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("ROR $1000,x", 7)
            .assertTimed("ROR $00", 5)
            .assertTimed("ROR $00,x", 6)
            .run();
    }

    /* Increment/Decrement instructions */
    @Test
    public void testIncDec() throws ProgramException {
        new TestProgram()
            .add("LDA #0")
            .add("STA $1000")
            .assertTimed("INC", 2)
            .assertA(1)
            .assertFlags(NOT_ZERO, POSITIVE)
            .assertTimed("DEC", 2)
            .assertA(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .add("LDA #$FF")
            .assertTimed("INC", 2)
            .assertA(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .assertTimed("DEC", 2)
            .assertA(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("INC $1000", 6)
            .assertAddrVal(0x1000, 1)
            .assertFlags(NOT_ZERO, POSITIVE)
            .assertTimed("DEC $1000", 6)
            .assertAddrVal(0x1000, 0)
            .assertFlags(IS_ZERO, POSITIVE)
            .assertTimed("INC $1000,x", 7)
            .assertTimed("DEC $1000,x", 7)
            .assertTimed("INC $00", 5)
            .assertTimed("DEC $00", 5)
            .assertTimed("INC $00,x", 6)
            .assertTimed("DEC $00,x", 6)
            // INX/DEX/INY/DEY
            .add("LDX #0")
            .assertTimed("INX", 2)
            .assertX(1)
            .assertFlags(NOT_ZERO, POSITIVE)
            .assertTimed("DEX", 2)
            .assertX(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .assertTimed("DEX", 2)
            .assertX(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("INX", 2)
            .assertX(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .add("LDY #0")
            .assertTimed("INY", 2)
            .assertY(1)
            .assertFlags(NOT_ZERO, POSITIVE)
            .assertTimed("DEY", 2)
            .assertY(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .assertTimed("DEY", 2)
            .assertY(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("INY", 2)
            .assertY(0)
            .assertFlags(IS_ZERO, POSITIVE)

            .run();
    }

    /* All compare instructions CMP, CPX, CPY */
    @Test
    public void testComparisons() throws ProgramException {
        new TestProgram()
            .add("LDA #0")
            .assertTimed("CMP #0", 2)
            .assertFlags(IS_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("CMP #1", 2)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .add("LDA #$FF")
            .assertTimed("CMP #0", 2)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_SET)
            .assertTimed("CMP #$FF", 2)
            .assertFlags(IS_ZERO, POSITIVE, CARRY_SET)
            .add("LDX #0")
            .assertTimed("CPX #0", 2)
            .assertFlags(IS_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("CPX #1", 2)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .add("LDX #$FF")
            .assertTimed("CPX #0", 2)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_SET)
            .assertTimed("CPX #$FF", 2)
            .assertFlags(IS_ZERO, POSITIVE, CARRY_SET)
            .add("LDY #0")
            .assertTimed("CPY #0", 2)
            .assertFlags(IS_ZERO, POSITIVE, CARRY_SET)
            .assertTimed("CPY #1", 2)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_CLEAR)
            .add("LDY #$FF")
            .assertTimed("CPY #0", 2)
            .assertFlags(NOT_ZERO, NEGATIVE, CARRY_SET)
            .assertTimed("CPY #$FF", 2)
            .assertFlags(IS_ZERO, POSITIVE, CARRY_SET)
            // Cycle count other modes
            .assertTimed("CMP $1000", 4)
            .assertTimed("CMP $1000,x", 4)
            .assertTimed("CMP $1000,y", 4)
            .assertTimed("CMP ($00)", 5)
            .assertTimed("CMP ($00,X)", 6)
            .assertTimed("CMP ($00),Y", 5)
            .assertTimed("CMP $00", 3)
            .assertTimed("CMP $00,X", 4)
            .assertTimed("CPX $1000", 4)
            .assertTimed("CPX $10", 3)
            .assertTimed("CPY $1000", 4)
            .assertTimed("CPY $10", 3)
            .run();
    }

    /* Load/Store/Transfer operations */
    @Test
    public void testLoadStore() throws ProgramException {
        new TestProgram()
            .assertTimed("LDA #0", 2)
            .assertA(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .add("LDA #$FF")
            .assertA(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("LDX #0", 2)
            .assertX(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .add("LDX #$FE")
            .assertX(0xFE)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("LDY #0", 2)
            .assertY(0)
            .assertFlags(IS_ZERO, POSITIVE)
            .add("LDY #$FD")
            .assertY(0xFD)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("STA $1000", 4)
            .assertAddrVal(0x1000, 0xFF)
            .assertTimed("STX $1001", 4)
            .assertAddrVal(0x1001, 0xFE)
            .assertTimed("STY $1002", 4)
            .assertAddrVal(0x1002, 0xFD)
            .assertTimed("LDA $1002", 4)
            .assertA(0xFD)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("LDX $1000", 4)
            .assertX(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("LDY $1001", 4)
            .assertY(0xFE)
            .assertFlags(NOT_ZERO, NEGATIVE)
            // Cycle count for other LDA modes
            .assertTimed("LDA $1000,x", 4)
            .assertTimed("LDA $1000,y", 4)
            .assertTimed("LDA ($00)", 5)
            .assertTimed("LDA ($00,X)", 6)
            .assertTimed("LDA ($00),Y", 5)
            .assertTimed("LDA $00", 3)
            .assertTimed("LDA $00,X", 4)
            // Cycle counts for other STA modes
            .assertTimed("STA $1000,x", 5)
            .assertTimed("STA $1000,y", 5)
            .assertTimed("STA ($00)", 5)
            .assertTimed("STA ($00,X)", 6)
            .assertTimed("STA ($00),Y", 6)
            .assertTimed("STA $00", 3)
            .assertTimed("STA $00,X", 4)
            // Cycle counts for other LDX and LDY modes
            .assertTimed("LDX $1000", 4)
            .assertTimed("LDX $1000,y", 4)
            .assertTimed("LDX $00", 3)
            .assertTimed("LDX $00,y", 4)
            .assertTimed("LDY $1000", 4)
            .assertTimed("LDY $1000,x", 4)
            .assertTimed("LDY $00", 3)
            .assertTimed("LDY $00,x", 4)
            // Cycle counts for other STX and STY modes
            .assertTimed("STX $1000", 4)
            .assertTimed("STX $00", 3)
            .assertTimed("STX $00, Y", 4)
            .assertTimed("STY $1000", 4)
            .assertTimed("STY $00", 3)
            .assertTimed("STY $00, X", 4)
            // STZ
            .assertTimed("STZ $1000", 4)
            .assertAddrVal(0x1000, 0)
            .assertTimed("STZ $1000,x", 5)
            .assertTimed("STZ $00", 3)
            .assertTimed("STZ $00,x", 4)
            .run();
        // Now test the transfer instructions
        new TestProgram()
            .add("LDA #10")
            .add("LDX #20")
            .add("LDY #30")
            .assertTimed("TAX", 2)
            .assertX(10)
            .assertFlags(NOT_ZERO, POSITIVE)
            .assertTimed("TAY", 2)
            .assertY(10)
            .assertFlags(NOT_ZERO, POSITIVE)
            .add("LDA #$FF")
            .assertTimed("TAX", 2)
            .assertX(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("TAY", 2)
            .assertY(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .add("LDA #0")
            .assertTimed("TXA", 2)
            .assertA(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .add("LDA #0")
            .assertTimed("TYA", 2)
            .assertA(0xFF)
            .assertFlags(NOT_ZERO, NEGATIVE)
            .assertTimed("TSX", 2)
            .assertX(0xFF)
            .assertTimed("TXS", 2)            
            .run();
    }

    /* Test branch instructions */
    @Test
    public void testBranches() throws ProgramException {
        new TestProgram()
            // Zero and Negative flags
            .add("LDA #0")
            .assertTimed("BEQ *+2",3)
            .assertTimed("BNE *+2",2)
            .assertTimed("BPL *+2",3)
            .assertTimed("BMI *+2",2)
            .add("LDA #$FF")
            .assertTimed("BEQ *+2",2)
            .assertTimed("BNE *+2",3)
            .assertTimed("BPL *+2",2)
            .assertTimed("BMI *+2",3)
            .assertTimed("BRA *+2", 3)
            // Carry flag
            .assertTimed("BCC *+2", 3)
            .assertTimed("BCS *+2", 2)
            .add("SEC")
            .assertTimed("BCC *+2", 2)
            .assertTimed("BCS *+2", 3)
            // Overflow flag
            .add("CLV")
            .assertTimed("BVC *+2", 3)
            .assertTimed("BVS *+2", 2)
            .add("""
                lda #$40
                sta $1000
                bit $1000
            """)
            .assertTimed("BVC *+2", 2)
            .assertTimed("BVS *+2", 3)
            .assertTimed("NOP", 2)
            .run();
    }

    /* Test JMP */
    @Test
    public void testJmp() throws ProgramException {
        new TestProgram()
            .add("LDA #0")
            .assertTimed("""
                JMP +
                LDA #$FF
                NOP
                NOP
            +
            """, 3)
            .assertA(0)
            // Testing indirect jump using self-modifying code
            // Load the address of jmp target and store at $300
            .add("""
                LDA #<jmpTarget
                STA $300
                LDA #>jmpTarget
                STA $301
                LDX #$FF
            """)
            .assertTimed("JMP ($300)", 6)
            .add("""
                LDX #0
            jmpTarget LDA #$88        
            """)
            .assertA(0x88)
            .assertX(0xFF)
            // Perform similar test using indirect,x addressing
            .add("""
                LDA #<(jmpTargetX)
                STA $310
                LDA #>(jmpTargetX)
                STA $311
                LDX #$10
                LDY #$FF
            """)
            .assertTimed("JMP ($300,X)", 6)
            .add("""
                LDY #88
            jmpTargetX LDA #$88
            """)
            .assertA(0x88)
            .assertY(0xFF)
            .run();
    }

    /* Test JSR */
    @Test
    public void testJsr() throws ProgramException {
        // "Easy" test that JSR + RTS work as expected and together take 12 cycles
        new TestProgram()
            .add("""
                jmp +
            sub1 rts
                +throwError 69
            +
            """)                        
            .assertTimed("""
                JSR sub1
            """, 12)
            .run();
        // Check that JSR pushes the expected PC values to the stack
        new TestProgram()
            .add("""
                jmp start
            test
                plx
                ply
                phy
                phx
                rts
            """)
            .test("", "RTS did not return to the correct address")
            .add("""
            start
                jsr test
            ret
            """)
            .test("""
                cpy #>ret
                beq +
            """, "Y = MSB of return address")
            .test("""
                inx
                cpx #<ret
                beq +
            """, "X = LSB of return address-1")
            .run();
    }
}
