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

package jace.applesoft;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jace.Emulator;
import jace.assembly.AssemblyHandler;
import jace.core.RAM;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.ide.HeadlessProgram;
import jace.ide.Program;

/**
 * Decode an applesoft program into a list of program lines Right now this is an
 * example/test program but it successfully tokenized the source of Lemonade
 * Stand.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class ApplesoftProgram {

    List<Line> lines = new ArrayList<>();
    public static final int START_OF_PROG_POINTER = 0x067;
    public static final int END_OF_PROG_POINTER = 0x0AF;            
    public static final int VARIABLE_TABLE = 0x069;
    public static final int ARRAY_TABLE = 0x06b;
    public static final int VARIABLE_TABLE_END = 0x06d;
    public static final int STRING_TABLE = 0x06f;
    public static final int HIMEM = 0x073;
    public static final int LINE_INPUT_BUFFER = 0x0200;
    // After GETLN in the BASIC main loop — DOS/ProDOS hook at $00B1 fires here,
    // then BASIC tokenizes and dispatches whatever is in $0200.
    public static final int BASIC_MAIN_LOOP_AFTER_GETLN = 0x0D444;
    public static final int RUNNING_FLAG = 0x076;
    public static final int NOT_RUNNING = 0x0FF;
    public static final int GOTO_CMD = 0x0D944;  //actually starts at D93E
    public static final int START_ADDRESS = 0x0801;

    public static Byte[] toObjects(byte[] bytesPrim) {
        Byte[] bytes = new Byte[bytesPrim.length];
        Arrays.setAll(bytes, n -> bytesPrim[n]);
        return bytes;
    }

    public static ApplesoftProgram fromMemory(RAM memory) {
        int startAddress = memory.readWordRaw(START_OF_PROG_POINTER);
        // Guard: if pointer is 0 or points into ROM (>= $C000), return empty
        if (startAddress == 0 || startAddress >= 0xC000) {
            return new ApplesoftProgram();
        }
        int nextCheck = memory.readWordRaw(startAddress);
        int pos = startAddress;
        List<Byte> bytes = new ArrayList<>();
        int maxIterations = 10000; // safety limit
        while (nextCheck != 0 && maxIterations-- > 0) {
            // Guard: nextCheck should be in BASIC RAM range ($0800-$3FFF)
            if (nextCheck < 0x0800 || nextCheck >= 0xC000) {
                break;
            }
            while (pos < nextCheck + 2) {
                bytes.add(memory.readRaw(pos++));
            }
            nextCheck = memory.readWordRaw(nextCheck);
        }
        return fromBinary(bytes, startAddress);
    }

    public static ApplesoftProgram fromBinary(List<Byte> binary) {
        return fromBinary(binary, START_ADDRESS);
    }

    public static ApplesoftProgram fromBinary(List<Byte> binary, int startAddress) {
        ApplesoftProgram program = new ApplesoftProgram();
        int currentAddress = startAddress;
        int pos = 0;
        while (pos < binary.size()) {
            int nextAddress = (binary.get(pos) & 0x0ff) + ((binary.get(pos + 1) & 0x0ff) << 8);
            if (nextAddress == 0) {
                break;
            }
            int length = nextAddress - currentAddress;
            Line l = Line.fromBinary(binary, pos);
            if (l == null) {
                break;
            }
            program.lines.add(l);
            if (l.getLength() != length) {
                System.out.println("Line " + l.getNumber() + " parsed as " + l.getLength() + " bytes long, but that leaves "
                        + (length - l.getLength()) + " bytes hidden behind next line");
            }
            pos += length;
            currentAddress = nextAddress;
        }
        return program;
    }

    @Override
    public String toString() {
        String out = "";
        out = lines.stream().map((l) -> l.toString() + "\n").reduce(out, String::concat);
        return out;
    }

    private static final java.util.regex.Pattern COMMENT_LINE =
            java.util.regex.Pattern.compile("\\s*(REM|;).*", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Returns true if the given source line is a comment line that should be silently skipped
     * during tokenization. Comment lines match {@code \s*REM.*} or {@code \s*;.*}.
     */
    public static boolean isCommentLine(String line) {
        return COMMENT_LINE.matcher(line).matches();
    }

    public static ApplesoftProgram fromString(String programSource) {
        ApplesoftProgram program = new ApplesoftProgram();
        for (String line : programSource.split("\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (COMMENT_LINE.matcher(line).matches()) {
                continue;
            }
            program.lines.add(Line.fromString(line));
        }
        //correct line linkage
        for (int i = 0; i < program.lines.size(); i++) {
            if (i > 0) {
                program.lines.get(i).setPrevious(program.lines.get(i - 1));
            }
            if (i < program.lines.size() - 1) {
                program.lines.get(i).setNext(program.lines.get(i + 1));
            }
        }
        return program;
    }

    public void run() {
        Emulator.whileSuspended(c-> {
            int programStart = c.getMemory().readWordRaw(START_OF_PROG_POINTER);
            int programEnd = programStart + getProgramSize();
            if (isProgramRunning()) {
                whenReady(()->{
                    relocateVariables(programEnd);
                    injectProgram();
                });
            } else {
                injectProgram();
                clearVariables(programEnd);
            }
        });
    }

    /**
     * Force-inject the program synchronously regardless of machine state.
     * Used by the Execute command, which explicitly overrides whatever is running.
     */
    public void forceInjectAndRun() {
        int programEnd = START_ADDRESS + getProgramSize();
        injectProgram();
        clearVariables(programEnd);
    }
    
    private void injectProgram() {
        Emulator.withMemory(memory->{
            memory.writeWord(START_OF_PROG_POINTER, START_ADDRESS, false, true);
            // RESTORE sets TXTPTR = TXTTAB-1 = $0800.  The RUN handler reads
            // (TXTPTR),Y with Y=0 and expects $00 as a sentinel — write it.
            memory.write(START_ADDRESS - 1, (byte) 0x00, false, true);
            int pos = START_ADDRESS;
            for (Line line : lines) {
                int nextPos = pos + line.getLength();
                memory.writeWord(pos, nextPos, false, true);
                pos += 2;
                memory.writeWord(pos, line.getNumber(), false, true);
                pos += 2;
                boolean isFirst = true;
                for (Command command : line.getCommands()) {
                    if (!isFirst) {
                        memory.write(pos++, (byte) ':', false, true);
                    }
                    isFirst = false;
                    for (Command.ByteOrToken part : command.parts) {
                        memory.write(pos++, part.getByte(), false, true);
                    }
                }
                memory.write(pos++, (byte) 0, false, true);
            }
            memory.write(pos++, (byte) 0, false, true);
            memory.write(pos++, (byte) 0, false, true);
            memory.write(pos++, (byte) 0, false, true);
            memory.write(pos++, (byte) 0, false, true);        
        });
    }
    
    private boolean isProgramRunning() {
        return Emulator.withComputer(c -> {
            int flag = c.getMemory().readRaw(RUNNING_FLAG) & 0xFF;
            if (flag == NOT_RUNNING) return false;
            // $76 != 0xFF is not sufficient — during disk boot or assembly programs
            // $76 can be any garbage value.  Confirm by scanning the 6502 stack for
            // a return address whose hi byte falls in the Applesoft interpreter range
            // $D7–$DA.  Boot ROM ($FA–$FF), DOS ($9D+), and assembly code in RAM
            // ($00–$BF) all have different hi bytes, so this is unambiguous.
            jace.apple2e.MOS65C02 cpu = (jace.apple2e.MOS65C02) c.getCpu();
            int sp = cpu.STACK & 0xFF;
            for (int i = sp + 1; i < 0xFF; i++) {
                int hi = c.getMemory().readRaw(0x0101 + i) & 0xFF;
                if (hi >= 0xD7 && hi <= 0xDA) {
                    return true;
                }
            }
            return false;
        }, false);
    }
    
    /**
     * If the program is running, wait until it advances to the next line
     */
    private void whenReady(Runnable r) {
        Emulator.withMemory(memory->{
            memory.addListener(new RAMListener("Applesoft: Trap GOTO command", RAMEvent.TYPE.EXECUTE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
                @Override
                protected void doConfig() {
                    setScopeStart(GOTO_CMD);
                }

                @Override
                protected void doEvent(RAMEvent e) {
                    r.run();
                    memory.removeListener(this);
                }
            });
        });
    }

    /**
     * Rough approximation of the CLEAR command at $D66A.
     * http://www.txbobsc.com/scsc/scdocumentor/D52C.html
     * @param programEnd Program ending address
     */
    private void clearVariables(int programEnd) {
        Emulator.withMemory(memory->{
            memory.writeWord(ARRAY_TABLE, programEnd, false, true);
            memory.writeWord(VARIABLE_TABLE, programEnd, false, true);
            memory.writeWord(VARIABLE_TABLE_END, programEnd, false, true);
            memory.writeWord(END_OF_PROG_POINTER, programEnd, false, true);        
        });
    }
    
    /**
     * Move variables around to accommodate bigger program
     * @param programEnd Program ending address
     */
    public void relocateVariables(int programEnd) {
        Emulator.withMemory(memory->{
            int currentEnd = memory.readWordRaw(END_OF_PROG_POINTER);
            memory.writeWord(END_OF_PROG_POINTER, programEnd, false, true);
            if (programEnd > currentEnd) {
                int diff = programEnd - currentEnd;
                int himem = memory.readWordRaw(HIMEM);
                for (int i=himem - diff; i >= programEnd; i--) {
                    memory.write(i+diff, memory.readRaw(i), false, true);
                }
                memory.writeWord(VARIABLE_TABLE, memory.readWordRaw(VARIABLE_TABLE) + diff, false, true);
                memory.writeWord(ARRAY_TABLE, memory.readWordRaw(ARRAY_TABLE) + diff, false, true);
                memory.writeWord(VARIABLE_TABLE_END, memory.readWordRaw(VARIABLE_TABLE_END) + diff, false, true);
                memory.writeWord(STRING_TABLE, memory.readWordRaw(STRING_TABLE) + diff, false, true);
            }
        });
    }

    public int getProgramSize() {
        int size = lines.stream().collect(Collectors.summingInt(Line::getLength)) + 4;
        return size;
    }

    public int getLength() {
        return lines.size();
    }

    /**
     * Execute the BASIC program currently in memory by writing "RUN\r" into
     * the line input buffer at $0200, resetting the stack, and jumping to
     * $D444 — the point in the BASIC main loop right after GETLN, so any
     * DOS/ProDOS hook at $00B1 fires first before BASIC dispatches the command.
     *
     * Works whether the emulator is idle at a BASIC prompt or mid-execution:
     * whileSuspended guarantees the CPU sees the new PC/SP before its next tick.
     */
    // Entry point inside the RUN handler, past the Ctrl-C check and stack-save preamble.
    // At this address: Y=0, LDA (TXTPTR),Y — reads sentinel byte before program start.
    public static final int RUN_HANDLER_ENTRY = 0xD7E5;

    // Interactive main loop, past GETLN's prompt-print — the same target the GOWARM
    // zero-page vector ($00-$02) points at. Landing here with an empty program gives
    // a clean "]" READY prompt instead of running anything.
    public static final int WARM_START_ENTRY = 0xD43C;

    // Default HIMEM for bare Applesoft (no DOS) — top of main RAM before I/O space.
    public static final int DEFAULT_HIMEM  = 0xBF00;
    // Stub lives in the stack page; safe because we reset SP to $FF first.
    public static final int STUB_ADDRESS   = 0x0100;
    // RESTORE routine — sets TXTPTR ($B8/$B9) = TXTTAB-1.  Safe to JSR.
    public static final int APPLESOFT_RESTORE = 0xD697;

    /**
     * Compiles and injects a small 6502 stub into the stack page ($0100) that
     * initialises the Applesoft interpreter state from scratch and runs the
     * program at $0801.  Using ACME to build the stub keeps the machine code
     * readable and self-documenting.
     *
     * The stub:
     *   1. Disables LC RAM so ROM is visible at $D000–$FFFF.
     *   2. Writes TXTTAB ($67/$68) = $0801 and HIMEM ($73/$74) = $BF00.
     *   3. JSRs to Applesoft CLR ($D665) which initialises all other ZP
     *      workspace pointers (VARTAB, ARYTAB, STREND, etc.) from TXTTAB/HIMEM.
     *   4. JMPs to the RUN handler ($D7E5).
     *
     * By letting the ROM do the ZP initialisation we avoid having to know and
     * replicate every pointer the interpreter touches on cold start.
     */
    public void executeProgram() {
        int programEnd = START_ADDRESS + getProgramSize();

        String stubSource = String.format("""
                !cpu 65c02
                *= $%04X

                ; Reset stack — gives CLR/RUN a clean page to work with
                ldx  #$FF
                txs

                ; TXTTAB ($67/$68) = program start
                lda  #$%02X
                sta  $67
                lda  #$%02X
                sta  $68

                ; HIMEM ($73/$74) = top of usable RAM
                lda  #$%02X
                sta  $73
                lda  #$%02X
                sta  $74

                ; VARTAB ($69/$6A) = ARYTAB ($6B/$6C) = STREND ($6D/$6E) = program end
                lda  #$%02X
                sta  $69
                sta  $6B
                sta  $6D
                lda  #$%02X
                sta  $6A
                sta  $6C
                sta  $6E

                ; FRETOP ($6F/$70) = HIMEM (string heap starts at top)
                lda  #$%02X
                sta  $6F
                lda  #$%02X
                sta  $70

                ; RESTORE ($D697) — sets TXTPTR ($B8/$B9) = TXTTAB-1
                jsr  $%04X

                ; JMP into RUN handler
                jmp  $%04X
                """,
                STUB_ADDRESS,
                // TXTTAB
                START_ADDRESS & 0xFF,
                START_ADDRESS >> 8,
                // HIMEM
                DEFAULT_HIMEM & 0xFF,
                DEFAULT_HIMEM >> 8,
                // VARTAB lo (used for VARTAB, ARYTAB, STREND hi+lo)
                programEnd & 0xFF,
                programEnd >> 8,
                // FRETOP = HIMEM
                DEFAULT_HIMEM & 0xFF,
                DEFAULT_HIMEM >> 8,
                APPLESOFT_RESTORE,
                RUN_HANDLER_ENTRY);

        HeadlessProgram prog = new HeadlessProgram(Program.DocumentType.assembly);
        prog.setValue(stubSource);
        AssemblyHandler handler = new AssemblyHandler();
        jace.ide.CompileResult<ByteBuffer> result = handler.compile(prog);
        if (!result.isSuccessful()) {
            result.getRawOutput().forEach(System.err::println);
            throw new RuntimeException("Applesoft launch stub failed to compile");
        }

        ByteBuffer bytes = result.getCompiledAsset();
        Emulator.withComputer(c -> {
            jace.core.RAM memory = c.getMemory();
            jace.apple2e.MOS65C02 cpu = (jace.apple2e.MOS65C02) c.getCpu();

            cpu.whileSuspended(() -> {
                bytes.rewind();
                bytes.get(); bytes.get(); // skip CBM load-address header
                int pos = STUB_ADDRESS;
                while (bytes.hasRemaining()) {
                    memory.write(pos++, bytes.get(), false, true);
                }
                jace.apple2e.SoftSwitches.LCRAM.getSwitch().setState(false);
                memory.configureActiveMemory();
                // Always reset display/speed state regardless of interpreter init status.
                // NORMAL: INVFLG ($32) = $FF, $F3 = $00 (prevents inverse/checkerboard output).
                memory.write(0x0032, (byte) 0xFF, false, true);
                memory.write(0x00F3, (byte) 0x00, false, true);
                // SPEED=1: $F1 = $01 (normal output speed).
                memory.write(0x00F1, (byte) 0x01, false, true);
                // GOWARM ($00-$02): JMP $D43C — Applesoft main input loop.
                memory.write(0x0000, (byte) 0x4C, false, true);
                memory.write(0x0001, (byte) 0x3C, false, true);
                memory.write(0x0002, (byte) 0xD4, false, true);
                // GOSTROUT ($03-$05): JMP $DB3A — string output routine.
                // PRINT calls JMP ($0004) to reach STROUT; garbage here = garbage output.
                memory.write(0x0003, (byte) 0x4C, false, true);
                memory.write(0x0004, (byte) 0x3A, false, true);
                memory.write(0x0005, (byte) 0xDB, false, true);
                // TEMPPT ($52) = $55 = TEMPST: temp string descriptor stack empty.
                // FREFAC checks TEMPPT == TEMPST to know stack is empty; garbage here
                // causes it to read a phantom descriptor → wrong string printed.
                memory.write(0x0052, (byte) 0x55, false, true);
                // DSCLEN ($8F) = $03: string descriptor is 3 bytes (length + 2-byte ptr).
                memory.write(0x008F, (byte) 0x03, false, true);
                // LOCK ($D6) = $00: clear auto-run lock so BASIC prompt works normally.
                memory.write(0x00D6, (byte) 0x00, false, true);
                // Clear input buffer ($0200) so stale content isn't re-parsed as a command
                // when the program ends and the main loop calls CHRGET on $0200.
                memory.write(0x0200, (byte) 0x00, false, true);
                // If CHRGET ($B0..$CC) is uninitialized, copy it from ROM.
                int b1 = memory.readRaw(0x00B1) & 0xFF;
                if (b1 == 0x00 || b1 == 0xFF) {
                    // COLD_START's loop: LDA $F10A,X / STA $B0,X with X=$1C..0
                    // copies 29 bytes $F10A..$F126 → ZP $B0..$CC.
                    for (int i = 0; i <= 0x1C; i++) {
                        memory.write(0x00B0 + i, memory.readRaw(0xF10A + i), false, true);
                    }
                    // TRCFLG ($F2) = $00: trace mode off — BPL $D81D in RUN handler branches.
                    memory.write(0x00F2, (byte) 0x00, false, true);
                    // $54/$A4 = $00: set by COLD_START after the CHRGET copy loop (X=0).
                    memory.write(0x0054, (byte) 0x00, false, true);
                    memory.write(0x00A4, (byte) 0x00, false, true);
                }
                cpu.setProgramCounter(STUB_ADDRESS);
                cpu.STACK = 0xFF;
            });
        });
    }
}
