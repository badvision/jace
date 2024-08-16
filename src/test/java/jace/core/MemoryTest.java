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
package jace.core;
import static jace.TestUtils.initComputer;
import static jace.TestUtils.runAssemblyCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.ProgramException;
import jace.TestProgram;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.core.RAMEvent.TYPE;

/**
 * Test that memory listeners fire appropriately.
 * @author brobert
 */
public class MemoryTest {
    static Computer computer;
    static MOS65C02 cpu;
    static RAM128k ram;
    static String MEMORY_TEST_COMMONS;
    static String MACHINE_IDENTIFICATION;

    @BeforeClass
    public static void setupClass() throws IOException, URISyntaxException {
        initComputer();
        SoundMixer.MUTE = true;
        computer = Emulator.withComputer(c->c, null);
        cpu = (MOS65C02) computer.getCpu();
        ram = (RAM128k) computer.getMemory();
        ram.addExecutionTrap("COUT intercept", 0x0FDF0, (e)->{
            char c = (char) (cpu.A & 0x07f);
            if (c == '\r') {
                System.out.println();
            } else {
                System.out.print(c);
            }
        });
        MEMORY_TEST_COMMONS = Files.readString(Paths.get(MemoryTest.class.getResource("/jace/memory_test_commons.asm").toURI()));
        MACHINE_IDENTIFICATION = Files.readString(Paths.get(MemoryTest.class.getResource("/jace/machine_identification.asm").toURI()));
    }

    @Before
    public void resetEmulator() {
        computer.pause();
        cpu.clearState();
    }

    @Before
    public void resetSoftSwitches() {    
        // Reset softswitches
        for (SoftSwitches softswitch : SoftSwitches.values()) {
            softswitch.getSwitch().reset();
        }
    }

    @Test
    public void assertMemoryConfiguredCorrectly() {
        assertEquals("Active read bank 3 should be main memory page 3",
                ram.mainMemory.getMemoryPage(3),
                ram.activeRead.getMemoryPage(3));

        assertEquals("Active write bank 3 should be main memory page 3",
                ram.mainMemory.getMemoryPage(3),
                ram.activeWrite.getMemoryPage(3));
    }

    @Test
    public void testListenerRelevance() throws Exception {
        AtomicInteger anyEventCaught = new AtomicInteger();
        RAMListener anyListener = new RAMListener("Execution test", RAMEvent.TYPE.ANY, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                anyEventCaught.incrementAndGet();
            }
        };
                
        AtomicInteger readAnyEventCaught = new AtomicInteger();
        RAMListener readAnyListener = new RAMListener("Execution test 1", RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                readAnyEventCaught.incrementAndGet();
            }
        };
        
        AtomicInteger writeEventCaught = new AtomicInteger();
        RAMListener writeListener = new RAMListener("Execution test 2", RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                writeEventCaught.incrementAndGet();
            }
        };

        AtomicInteger executeEventCaught = new AtomicInteger();
        RAMListener executeListener = new RAMListener("Execution test 3", RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
            @Override
            protected void doConfig() {
                setScopeStart(0x0100);
            }
            
            @Override
            protected void doEvent(RAMEvent e) {
                executeEventCaught.incrementAndGet();
            }
        };

        
        RAMEvent readDataEvent = new RAMEvent(RAMEvent.TYPE.READ_DATA, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);
        RAMEvent readOperandEvent = new RAMEvent(RAMEvent.TYPE.READ_OPERAND, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);
        RAMEvent executeEvent = new RAMEvent(RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);
        RAMEvent writeEvent = new RAMEvent(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 0x100, 0, 0);

        // Any listener        
        assertTrue("Any listener should handle all events", anyListener.isRelevant(readDataEvent));
        assertTrue("Any listener should handle all events", anyListener.isRelevant(readOperandEvent));
        assertTrue("Any listener should handle all events", anyListener.isRelevant(executeEvent));
        assertTrue("Any listener should handle all events", anyListener.isRelevant(writeEvent));
        
        // Read listener
        assertTrue("Read listener should handle all read events", readAnyListener.isRelevant(readDataEvent));
        assertTrue("Read listener should handle all read events", readAnyListener.isRelevant(readOperandEvent));
        assertTrue("Read listener should handle all read events", readAnyListener.isRelevant(executeEvent));
        assertFalse("Read listener should ignore write events", readAnyListener.isRelevant(writeEvent));

        // Write listener
        assertFalse("Write listener should ignore all read events", writeListener.isRelevant(readDataEvent));
        assertFalse("Write listener should ignore all read events", writeListener.isRelevant(readOperandEvent));
        assertFalse("Write listener should ignore all read events", writeListener.isRelevant(executeEvent));
        assertTrue("Write listener should handle write events", writeListener.isRelevant(writeEvent));
        
        // Execution listener
        assertTrue("Execute listener should only catch execution events", executeListener.isRelevant(executeEvent));
        assertFalse("Execute listener should only catch execution events", executeListener.isRelevant(readDataEvent));
        assertFalse("Execute listener should only catch execution events", executeListener.isRelevant(readOperandEvent));
        assertFalse("Execute listener should only catch execution events", executeListener.isRelevant(writeEvent));
        
        ram.addListener(anyListener);
        ram.addListener(executeListener);
        ram.addListener(readAnyListener);
        ram.addListener(writeListener);
        
        runAssemblyCode("NOP", 0x0100, 2);
        
        assertEquals("Should have no writes for 0x0100", 0, writeEventCaught.get());
        assertEquals("Should have read event for 0x0100", 1, readAnyEventCaught.get());
        assertEquals("Should have execute for 0x0100", 1, executeEventCaught.get());
    }


    /**
     * Adapted version of the Apple II Family Identification Program from:
     * http://www.1000bit.it/support/manuali/apple/technotes/misc/tn.misc.02.html00
     * 
     * Adapted to ACME by Zellyn Hunter
     * 
     * @throws ProgramException
     */
    @Test
    public void machineIdentificationTest() throws ProgramException {
        TestProgram memoryDetectTestProgram = new TestProgram(MEMORY_TEST_COMMONS);
        memoryDetectTestProgram.add(MACHINE_IDENTIFICATION);
        // Assert this is an Apple //e
        memoryDetectTestProgram.assertAddrVal(0x0800, 0x04);
        // Assert this is an enhanced revision
        memoryDetectTestProgram.assertAddrVal(0x0801, 0x02);
        // Aser this is a 128k machine
        memoryDetectTestProgram.assertAddrVal(0x0802, 128);
        memoryDetectTestProgram.run();   
    }

    /*
     * Adapted from Zellyn Hunder's language card test:
     * https://github.com/zellyn/a2audit/blob/main/audit/langcard.asm
     * 
     * Adjusted to use JACE hooks to perform assertions and error reporting
     */
    @Test
    public void languageCardBankswitchTest() throws ProgramException {      
        TestProgram lcTestProgram = new TestProgram(MEMORY_TEST_COMMONS);
        lcTestProgram.add("""
            ;; Setup - store differing values in bank first and second banked areas.
            lda $C08B		; Read and write bank 1
            lda $C08B
            lda #$11
            sta $D17B		; $D17B is $53 in Apple II/plus/e/enhanced
            cmp $D17B
        """)
        .assertEquals("E0004: We tried to put the language card into read bank 1, write bank 1, but failed to write.")
        .add("""
        	lda #$33
            sta $FE1F		; FE1F is $60 in Apple II/plus/e/enhanced
            cmp $FE1F
        """)
        .assertEquals("E0005: We tried to put the language card into read RAM, write RAM, but failed to write.")
        .add("""
        	lda $C083		; Read and write bank 2
            lda $C083
            lda #$22
            sta $D17B
            cmp $D17B
        """)
        .assertEquals("E0006: We tried to put the language card into read bank 2, write bank 2, but failed to write.")
        .add("""
        	lda $C08B		; Read and write bank 1 with single access (only one needed if banked in already)
            lda #$11
            cmp $D17B
        """)
        .assertEquals("E000D: We tried to put the language card into read bank 1, but failed to read.")
        .add("""
        	lda $C081		; Read ROM with single access (only one needed to bank out)
            lda #$53
            cmp $D17B
        """)
        .assertEquals("E000E: We tried to put the language card into read ROM, but failed to read (from ROM).")        
        .add("""
        ;;; Main data-driven test. PCL,PCH holds the address of the next
        ;;; data-driven test routine. We expect the various softswitches
        ;;; to be reset each time we loop at .ddloop.
        .datadriventests
            lda #<.tests
            sta PCL
            lda #>.tests
            sta PCH
        ;;; Main data-drive-test loop.
        .ddloop
            ldy #0
        
            ;; Initialize to known state:
            ;; - $11 in $D17B bank 1 (ROM: $53)
            ;; - $22 in $D17B bank 2 (ROM: $53)
            ;; - $33 in $FE1F        (ROM: $60)
            lda $C08B		; Read and write bank 1
            lda $C08B
            lda #$11
            sta $D17B
            lda #$33
            sta $FE1F
            lda $C083		; Read and write bank 2
            lda $C083
            lda #$22
            sta $D17B
            lda $C080
        
            jmp (PCL)		; Jump to test routine
        
        
            ;; Test routine will JSR back to here, so the check data address is on the stack.
        
        .test	;; ... test the quintiple of test values
            inc $D17B
            inc $FE1F
        
            ;; pull address off of stack: it points just below check data for this test.
            pla
            sta .checkdata
            pla
            sta .checkdata+1
        
            ;; .checkdata now points to d17b-current,fe1f-current,bank1,bank2,fe1f-ram test quintiple
        
            ;; Test current $D17B
            jsr NEXTCHECK
            cmp $D17B
            beq +
            lda $D17B
            pha
            jsr .printseq
            +print
            !text "$D17B TO CONTAIN $"
            +printed
            jsr CURCHECK
            jsr PRBYTE
            +print
            !text ", GOT $"
            +printed
            pla
            jsr PRBYTE
            lda #$8D
            jsr COUT
            jmp .datatesturl
        
        +	;; Test current $FE1F
            jsr NEXTCHECK
            cmp $FE1F
            beq +
            lda $FE1F
            pha
            jsr .printseq
            +print
            !text "$FE1F=$"
            +printed
            jsr CURCHECK
            jsr PRBYTE
            +print
            !text ", GOT $"
            +printed
            pla
            jsr PRBYTE
            lda #$8D
            jsr COUT
            jmp .datatesturl
        
        +	;; Test bank 1 $D17B
            lda $C088
            jsr NEXTCHECK
            cmp $D17B
            beq +
            lda $D17B
            pha
            jsr .printseq
            +print
            !text "$D17B IN RAM BANK 1 TO CONTAIN $"
            +printed
            jsr CURCHECK
            jsr PRBYTE
            +print
            !text ", GOT $"
            +printed
            pla
            jsr PRBYTE
            lda #$8D
            jsr COUT
            jmp .datatesturl
        
        +	;; Test bank 2 $D17B
            lda $C080
            jsr NEXTCHECK
            cmp $D17B
            beq +
            lda $D17B
            pha
            jsr .printseq
            +print
            !text "$D17B IN RAM BANK 2 TO CONTAIN $"
            +printed
            jsr CURCHECK
            jsr PRBYTE
            +print
            !text ", GOT $"
            +printed
            pla
            jsr PRBYTE
            lda #$8D
            jsr COUT
            jmp .datatesturl
        
        +	;; Test RAM $FE1F
            lda $C080
            jsr NEXTCHECK
            cmp $FE1F
            beq +
            lda $FE1F
            pha
            jsr .printseq
            +print
            !text "RAM $FE1F=$"
            +printed
            jsr CURCHECK
            jsr PRBYTE
            +print
            !text ", GOT $"
            +printed
            pla
            jsr PRBYTE
            lda #$8D
            jsr COUT
            jmp .datatesturl
        
        +	;; Jump PCL,PCH up to after the test data, and loop.
            jsr NEXTCHECK
            bne +
            +success
        +	ldx .checkdata
            ldy .checkdata+1
            stx PCL
            sty PCH
            jmp .ddloop
        
        .datatesturl
        """)
        .throwError("E0007: This is a data-driven test of Language Card operation. We initialize $D17B in RAM bank 1 to $11, $D17B in RAM bank 2 to $22, and $FE1F in RAM to $33. Then, we perform a testdata-driven sequence of LDA and STA to the $C08X range. Finally we (try to) increment $D17B and $FE1F. Then we test (a) the current live value in $D17B, (b) the current live value in $FE1F, (c) the RAM bank 1 value of $D17B, (d) the RAM bank 2 value of $D17B, and (e) the RAM value of $FE1F, to see whether they match expected values. $D17B is usually $53 in ROM, and $FE1F is usally $60. For more information on the operation of the language card soft-switches, see Understanding the Apple IIe, by James Fielding Sather, Pg 5-24.")
        .add("""
            rts
        
        .printseq
            +print
            !text "AFTER SEQUENCE OF:",$8D,"- LDA   $C080",$8D
            +printed
            jsr PRINTTEST
            +print
            !text "- INC   $D17B",$8D,"- INC   $FE1F",$8D,"EXPECTED "
            +printed
            rts
        
        .tests
            ;; Format:
            ;; Sequence of test instructions, finishing with `jsr .test`.
            ;; - quint: expected current $d17b and fe1f, then d17b in bank1, d17b in bank 2, and fe1f
            ;; (All sequences start with lda $C080, just to reset things to a known state.)
            ;; 0-byte to terminate tests.
        
            lda $C088				; Read $C088 (RAM read, write protected)
            jsr .test				;
            !byte $11, $33, $11, $22, $33		;
            jsr .test				;
            !byte $22, $33, $11, $22, $33		;
            lda $C081				; Read $C081 (ROM read, write disabled)
            jsr .test				;
            !byte $53, $60, $11, $22, $33
            lda $C081				; Read $C081, $C089 (ROM read, bank 1 write)
            lda $C089				;
            jsr .test				;
            !byte $53, $60, $54, $22, $61
            lda $C081				; Read $C081, $C081 (read ROM, write RAM bank 2)
            lda $C081				;
            jsr .test				;
            !byte $53, $60, $11, $54, $61
            lda $C081				; Read $C081, $C081, write $C081 (read ROM, write RAM bank bank 2)
            lda $C081				; See https://github.com/zellyn/a2audit/issues/3
            sta $C081				;
            jsr .test				;
            !byte $53, $60, $11, $54, $61
            lda $C081				; Read $C081, $C081; write $C081, $C081
            lda $C081				; See https://github.com/zellyn/a2audit/issues/4
            sta $C081				;
            sta $C081				;
            jsr .test				;
            !byte $53, $60, $11, $54, $61
            lda $C08B				; Read $C08B (read RAM bank 1, no write)
            jsr .test				;
            !byte $11, $33, $11, $22, $33
            lda $C083				; Read $C083 (read RAM bank 2, no write)
            jsr .test				;
            !byte $22, $33, $11, $22, $33
            lda $C08B				; Read $C08B, $C08B (read/write RAM bank 1)
            lda $C08B				;
            jsr .test				;
            !byte $12, $34, $12, $22, $34
            lda $C08F				; Read $C08F, $C087 (read/write RAM bank 2)
            lda $C087				;
            jsr .test				;
            !byte $23, $34, $11, $23, $34
            lda $C087				; Read $C087, read $C08D (read ROM, write bank 1)
            lda $C08D				;
            jsr .test				;
            !byte $53, $60, $54, $22, $61
            lda $C08B				; Read $C08B, write $C08B, read $C08B (read RAM bank 1, no write)
            sta $C08B				; (this one is tricky: reset WRTCOUNT by writing halfway)
            lda $C08B				;
            jsr .test				;
            !byte $11, $33, $11, $22, $33
            sta $C08B				; Write $C08B, write $C08B, read $C08B (read RAM bank 1, no write)
            sta $C08B				;
            lda $C08B				;
            jsr .test				;
            !byte $11, $33, $11, $22, $33
            clc					; Read $C083, $C083 (read/write RAM bank 2)
            ldx #0					; Uses "6502 false read"
            inc $C083,x				;
            jsr .test				;
            !byte $23, $34, $11, $23, $34
            !byte 0
        """)
        // .runForTicks(10000000);
        .run();
    }

    @Test
    public void auxLanguageCardTest() throws ProgramException {
        // This is a repeat of the LC test but with AUX enabled
        SoftSwitches.AUXZP.getSwitch().setState(true);
        SoftSwitches.RAMRD.getSwitch().setState(true);
        SoftSwitches.RAMWRT.getSwitch().setState(true);
        SoftSwitches._80STORE.getSwitch().setState(true);
        languageCardBankswitchTest();        
    }
    
    public record MemoryTestCase(int[] softswitches, byte... expected) {}

    int[] testLocations = {
        0x0FF, 0x100, 0x200, 0x3FF, 0x427, 0x7FF, 0x800, 0x1FFF,
        0x2000, 0x3FFF, 0x4000, 0x5FFF, 0xBFFF
    };
    private void assertMemoryTest(MemoryTestCase testCase) {    
        // Set the values in memory in main and aux banks
        // This is done directly to ensure the values are exactly as expected
        // The next tests will try to read these values using the softswitches
        for (int location : testLocations) {
            ((RAM128k) ram).getMainMemory().writeByte(location, (byte) 1);
            ((RAM128k) ram).getAuxMemory().writeByte(location, (byte) 3);
        }
        resetSoftSwitches();
        
        for (int softswitch : testCase.softswitches) {
            System.out.println("Setting softswitch " + Integer.toHexString(softswitch));
            ram.write(softswitch, (byte) 0, true, false);
        }
        for (int i=0; i < testLocations.length; i++) {
            int address = testLocations[i];
            byte current = ram.read(address, TYPE.READ_DATA, false, false);
            ram.write(address, (byte) (current+1), false, false);
            byte expected = testCase.expected[i];
            try {
                assertEquals("Unexpected value at " + Integer.toHexString(address), expected, ram.read(address, TYPE.READ_DATA, false, false));
            } catch (AssertionError err) {
                for (SoftSwitches softswitch : SoftSwitches.values()) {
                    System.out.println(MessageFormat.format("{0}\t{1}", softswitch.name(), (softswitch.isOn() ? "on" : "off")));
                }
                throw err;
            }
        }
    }

    @Test
    public void auxBankSwitchTest() throws ProgramException {
        byte M1 = (byte) 1; // Main + no change
        byte M2 = (byte) 2; // Main + 1
        byte A1 = (byte) 3; // Aux + no change
        byte A2 = (byte) 4; // Aux + 1

        // 80 STORE + RAMWRT + HIRES / Page 1 (Main mem)
        assertMemoryTest(new MemoryTestCase(new int[] {0x0C005, 0x0C001, 0x0C057},
            M2, M2, M1, M1, M2, M2, M1, M1, M2, M2, M1, M1, M1));

        // RAMRD + AUXZP
        assertMemoryTest(new MemoryTestCase(new int[] {0xC003, 0xC009},
            A2, A2, A1, A1, A1, A1, A1, A1, A1, A1, A1, A1, A1));

        // RAMRD + MAINZP
        assertMemoryTest(new MemoryTestCase(new int[] {0xC003, 0xC008},
            M2, M2, A1, A1, A1, A1, A1, A1, A1, A1, A1, A1, A1));

        // 80 STORE + HIRES' + Page 2
        assertMemoryTest(new MemoryTestCase(new int[] {0x0C001, 0x0C056, 0x0C055},
            M2, M2, M2, M2, A2, A2, M2, M2, M2, M2, M2, M2, M2));

        // 80 STORE + HIRES + Page 2
        assertMemoryTest(new MemoryTestCase(new int[] {0x0C001, 0x0C057, 0x0C055},
            M2, M2, M2, M2, A2, A2, M2, M2, A2, A2, M2, M2, M2));
    }
}
