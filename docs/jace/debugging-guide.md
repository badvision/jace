<!-- Loaded on demand from CLAUDE.md. -->

# Debugging 65C02 Code on JACE

Proven techniques from real-world use of JACE terminal mode to test compiled 65C02 code.

### Always Use a Timeout Wrapper

Programs under test can hang (infinite loops, crashes to BRK, branches into garbage). **Always wrap JACE invocations with `timeout`** to prevent wasting time and context:

```bash
# 90 seconds is generous for most tests. Adjust as needed.
timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" \
    -Dexec.args="--terminal" < commands.txt > output.txt 2>&1

# Check exit code: 124 = timeout (program hung)
if [ $? -eq 124 ]; then
    echo "Program hung - likely infinite loop"
fi
```

### Proven Test Pattern: Load-Execute-Inspect

This exact sequence has been validated to work reliably. Use it as a template:

```bash
cat > /tmp/jace_test.txt << 'EOF'
reset
loadbin /path/to/program.bin 4000
4000G
run 5000000
showtext
3800.3820
qq
EOF

timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" \
    -Dexec.args="--terminal" < /tmp/jace_test.txt > /tmp/jace_out.txt 2>&1
```

The flow:
1. `reset` - Clean emulator state
2. `loadbin` - Load binary directly into memory (no disk boot needed)
3. `4000G` - Set PC to $4000 and begin execution (Wozniak monitor syntax, works from main prompt)
4. `run N` - Let program run for N CPU cycles
5. `showtext` - Capture what's on screen
6. `3800.3820` - Dump memory to verify results (Wozniak range syntax, works from main prompt)
7. `qq` - Quit JACE

### Using `expect` Instead of Fixed Cycle Counts

When you know what text the program should produce, `expect` is more reliable than guessing cycle counts:

```
reset
loadbin /path/to/program.bin 4000
4000G
expect "DONE" 30
showtext
3800.3820
qq
```

`expect` polls the screen every 500ms and returns as soon as the text appears. If the program hangs, it times out after the specified seconds rather than running forever.

### Testing Clean Exit to BASIC

When testing whether a machine-language program properly returns to Applesoft
BASIC, the key behaviors to verify are:

1. **Control returns to BASIC**: After `CALL addr`, the next BASIC statement
   should execute. Test by having a BASIC line after the CALL that prints a
   sentinel value, e.g.:
   ```
   10 CALL 24576
   20 PRINT "OK"
   ```
   Then use `expect "OK"` to confirm line 20 executed.

2. **Display mode is restored**: `showtext` and `expect` read from text memory
   ($0400-$07FF) regardless of the active display mode. A program can return to
   BASIC with the display stuck in HGR mode, and `expect "]"` will still match
   because BASIC wrote its prompt to text memory — but the user sees graphics.

   To verify TEXT mode was restored, check the display soft switch:
   ```
   C01A.C01A
   ```
   If bit 7 of the value at $C01A is 0, TEXT mode is active. If bit 7 is 1,
   graphics mode is still on.

3. **Verifying machine responsiveness without using prompt characters**: Since
   the `]` BASIC prompt or `*` monitor prompt may already be on screen when
   `expect` is called (causing immediate return), use typed input echoing instead:
   ```
   type "TEST\x18"   ; type TEST then Ctrl-X (cancel/delete)
   expect "TEST"     ; confirm keystrokes echoed = machine is running
   ```

### Debug Instrumentation: Character Breadcrumbs

When a program hangs and you don't know where, add character prints at key points in the 65C02 code. The Apple II ROM provides `COUT` at $FDED:

```asm
LDA #'1'        ; Breadcrumb: reached phase 1
JSR $FDED
; ... code ...
LDA #'2'        ; Breadcrumb: reached phase 2
JSR $FDED
```

Then run on JACE and check `showtext`. If the screen shows `"12"` but not `"3"`, the hang is between breadcrumb 2 and 3. This binary-search approach is the fastest way to localize hangs.

### Debug NOP: Extended Opcode $FC (Preferred over Breadcrumbs)

JACE provides a special opcode `$FC` (NOP_SPECIAL) that acts as a debug command interface. This outputs directly to the host console (stdout), bypassing the Apple II screen entirely. It is faster, more reliable, and easier to parse than COUT breadcrumbs.

**Opcode format**: `$FC <param1> <param2>` (3 bytes, 4 cycles)

$FC is decoded as an **absolute-addressing** opcode, so the two bytes after it are the
usual little-endian operand. `MOS65C02.NOP_SPECIAL` splits it as:

```java
byte param1 = (byte) (address & 0x0ff);   // LOW byte  = FIRST byte after the opcode
byte param2 = (byte) (address >> 8);      // HIGH byte = SECOND byte after the opcode
```

So in `!byte $FC, $5C, $31` the **command selector is the first byte (`$5C`)** and the
**argument is the second (`$31`)** — the byte order in the tables below is the literal
source order, which is what you want.

#### Available Commands

| Bytes | ACME Syntax | Effect |
|-------|-------------|--------|
| `$FC $50 NN` | `!byte $FC, $50, NN` | Print NN as decimal number to stdout (no newline) |
| `$FC $5B NN` | `!byte $FC, $5B, NN` | Print NN as decimal number + newline to stdout |
| `$FC $5C NN` | `!byte $FC, $5C, NN` | Print ASCII character NN to stdout |
| `$FC $5E xx` | `!byte $FC, $5E, $00` | **Print the RUNTIME accumulator** as 2-digit hex + a space |
| `$FC $5F xx` | `!byte $FC, $5F, $00` | Emit a newline (and flush) — ends a `$5E` dump line |
| `$FC $44 NN` | `!byte $FC, $44, NN` | Dump full CPU state with identifier NN |
| `$FC $65 $01` | `!byte $FC, $65, $01` | Turn ON instruction tracing |
| `$FC $65 $00` | `!byte $FC, $65, $00` | Turn OFF instruction tracing |
| `$FC $64 NN` | `!byte $FC, $64, NN` | Delegate NN to the memory subsystem (see below) |

#### `$5E` / `$5F` — The Only Way to Print a COMPUTED Value

This distinction matters more than anything else in this section:

- `$50`, `$5B`, `$5C` print **`param2`** — a **compile-time constant** baked into the
  instruction stream. They can only ever print the literal you assembled.
- **`$5E` prints the CPU's live `A` register at the moment the opcode executes.** Its
  second operand byte is ignored (use `$00`). This is the only $FC subcommand that can
  observe a value the program actually computed.
- `$5F` prints a newline and flushes. Its operand byte is likewise ignored.

`$5E`/`$5F` write to `MOS65C02.debugOut` (a `public static PrintStream`, defaulting to
`System.out`), so a Java test harness can retarget them to a buffer; `$50`/`$5B`/`$5C`/`$44`
write to `System.out` unconditionally.

Together they let running 6502 code dump a sequence of computed bytes as one hex line:

```asm
        lda sprite_x
        !byte $FC, $5E, $00        ; prints e.g. "2A "
        lda sprite_y
        !byte $FC, $5E, $00        ; prints e.g. "70 "
        lda frame_count
        !byte $FC, $5E, $00        ; prints e.g. "0C "
        !byte $FC, $5F, $00        ; newline + flush -> "2A 70 0C\n"
```

Note `$5E` clobbers nothing — `A` is only read — so it can be dropped between any two
instructions without disturbing the program, unlike a `JSR $FDED` breadcrumb.

#### `$64` — Memory Subsystem Commands

`$FC $64 NN` forwards `NN` to `getMemory().performExtendedCommand(NN)`. For the standard
Apple //e memory implementation (`RAM128k.performExtendedCommand`) exactly **one**
subcommand is implemented:

| Bytes | ACME Syntax | Effect |
|-------|-------------|--------|
| `$FC $64 $DA` | `!byte $FC, $64, $DA` | Dump the active read/write bank mapping for all 256 pages |

`$DA` prints `Active banks` to stdout, then logs one `Bank <page> <readBank> <writeBank>`
line per page **via `java.util.logging` at INFO level** (not stdout) — so if you don't see
the per-page lines, the logger is filtered, not the command. Any other `NN` is silently
ignored. Other `RAM` subclasses may implement different subcommands; they are not
enumerated here.

#### Unhandled Selectors

Any `param1` not listed above falls through to a programmatically registered handler
(see Custom Command Handlers below); with no handler registered, the opcode is a
silent 4-cycle NOP.

#### CPU State Dump Format ($44)

The register dump (`$FC $44 NN`) prints a complete snapshot:
```
CPU[02]: A=FF X=42 Y=00 SP=FD PC=2000 N=1 V=0 B=1 D=0 I=1 Z=0 C=1
```
The NN identifier lets you place multiple dumps and distinguish them in output.

#### Assembly Usage Examples

```asm
; Breadcrumb: print '1' to host console (not Apple II screen)
!byte $FC, $5C, $31        ; $31 = ASCII '1'

; Print a newline-terminated number
!byte $FC, $5B, $42        ; prints "66\n" to stdout

; Dump CPU state at a critical point
!byte $FC, $44, $01        ; prints CPU[01]: A=XX X=XX ...

; Turn on full instruction tracing before suspect code
!byte $FC, $65, $01        ; trace ON
JSR suspect_routine
!byte $FC, $65, $00        ; trace OFF
```

#### ACME Macros for Convenience

```asm
!macro debug_char .ch {
    !byte $FC, $5C, .ch
}
!macro debug_num .n {
    !byte $FC, $5B, .n
}
!macro debug_regs .id {
    !byte $FC, $44, .id
}
; Print the RUNTIME accumulator as 2 hex digits + space (A is not modified)
!macro debug_a {
    !byte $FC, $5E, $00
}
; End a debug_a sequence: newline + flush
!macro debug_eol {
    !byte $FC, $5F, $00
}
; Print the byte at an address as hex, without disturbing A
!macro debug_byte .addr {
    pha
    lda .addr
    !byte $FC, $5E, $00
    pla
}
!macro debug_membanks {
    !byte $FC, $64, $DA
}
!macro trace_on {
    !byte $FC, $65, $01
}
!macro trace_off {
    !byte $FC, $65, $00
}
```

#### Instruction Tracing

When tracing is enabled (`$FC $65 $01`), JACE logs every instruction to stdout in the format:
```
{cpu_state} {PC} : {disassembly}; {memory_state}
```
This produces massive output, so bracket it tightly around the suspect code. Trace output appears in the JACE stdout alongside terminal mode output.

#### Why Prefer $FC Over COUT Breadcrumbs

- **No side effects**: Does not modify Apple II screen memory, cursor position, or any registers
- **No ROM dependency**: Works even if ROM is not loaded or COUT vector is corrupted
- **Parseable output**: Goes to host stdout, not interleaved with Apple II text screen
- **Register dumps**: COUT breadcrumbs can only print characters; $FC $44 dumps everything
- **Computed values**: `$FC $5E` prints the live accumulator, so you can observe values the
  program calculated — a COUT breadcrumb or `$FC $5C` can only report a constant
- **Tracing**: Can enable/disable full instruction tracing around suspect code

#### Custom Command Handlers (Advanced)

JACE supports registering custom extended command handlers programmatically via:
```java
cpu.registerExtendedCommandHandler(0xNN, (param2) -> { ... });
cpu.unregisterExtendedCommandHandler(0xNN);
```
This allows test harnesses to extend the $FC command set for specialized testing needs.

### Memory Inspection for Verification

After running code, use these commands to verify memory contents. This is more reliable than screen output for checking computed values:

```
3800.3810       # Dump variable storage
0900.0940       # Dump generated code
D000.D020       # Check Language Card contents
cpu             # Show CPU state (PC tells you where it stopped)
```

Common patterns in memory dumps:
- `00 00 FF FF` repeating = uninitialized memory (never written)
- `2A 00` at variable address = value 42 (little-endian)
- `4C xx xx` = JMP instruction (generated code)
- `20 xx xx` = JSR instruction (function call)
- `60` = RTS (function return)
- `00` = BRK (crash / uninitialized code execution)

### Breakpoints for Targeted Debugging

When you know roughly where a problem is, use breakpoints:

```
break 4050          # Break when PC reaches $4050
4000G               # Start execution
cpu                 # Check state at breakpoint
step 5              # Step through 5 instructions
cpu                 # Check state again
3800.3808           # Inspect memory
resume              # Continue execution
```

### Diagnosing Common Failures

**`CALL addr` never returns to BASIC (caller never resumes)**:
- Common cause: a JSR inside the machine-language routine calls a ROM entry point that does not exit via RTS. Example: on the Apple IIe, `JSR $FB39` does not return — it ends with `JMP $C100` (unconditional jump to slot 1 firmware ROM). The `RTS` that was supposed to follow the JSR is never reached.
- How to detect: the machine is not crashed (it keeps running), but the BASIC line after the `CALL` never executes. A test BASIC program such as `10 CALL 24576 / 20 PRINT "DONE"` combined with `expect "DONE"` will time out.
- Debug strategy: before running, set a breakpoint at `$C100` (`break C100`). If the breakpoint fires, a ROM routine hijacked execution instead of returning.
- Fix: remove or replace the offending JSR. If the goal was to restore TEXT mode, use the correct ROM entry point (`$FB36` is SETTXT on the Apple IIe) or simply omit the call if the caller does not require a display-mode switch.

**Program prints banner then hangs**:
- Add breadcrumbs after each initialization phase
- Common: infinite loop in memory clear routine (wrong termination condition)

**Program crashes to $0000 or $FFFE**:
- BRK instruction hit ($00) or invalid opcode
- Check: was generated code actually written? (dump $0900+)
- Check: was a JMP/JSR target address correct?

**Generated code at $0900 is `4C 00 00` (JMP $0000)**:
- The compiler never patched the initial JMP-to-main placeholder
- Parsing failed silently or the compiler crashed before patching

**Variables all zero / uninitialized**:
- Compiled code never executed (JMP to wrong address)
- Code generation emitted wrong store addresses

**Language Card routines don't work**:
- Verify LC soft switch: `LDA $C083` twice for Bank 1 read+write
- Common mistake: `$C08B` is Bank 2, not Bank 1
- Dump $D000-$D010 after copy to verify runtime is there
- The "two consecutive reads" requirement means you must use LDA, not STA (writes reset the counter)

### Apple IIe Language Card Soft Switch Quick Reference

```
$C080: Bank 1, read RAM, write-protect
$C081: Bank 1, read ROM, write RAM (2 reads)
$C082: Bank 1, read ROM, write-protect
$C083: Bank 1, read+write RAM (2 reads)  ← MOST COMMON
$C088: Bank 2, read RAM, write-protect
$C089: Bank 2, read ROM, write RAM (2 reads)
$C08A: Bank 2, read ROM, write-protect
$C08B: Bank 2, read+write RAM (2 reads)

Write-enable requires TWO consecutive LDA (read) operations.
STA (write) to the switch RESETS the read counter!
```

Reference: "Understanding the Apple IIe" by James Fielding Sather, p. 5-24.

