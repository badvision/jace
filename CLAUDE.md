# JACE Terminal Automation Guide

## Overview

This document describes the automation capabilities added to JACE's terminal mode to enable automated testing of Apple II software.

## Background

JACE (Java Apple Computer Emulator) is a Java-based Apple II emulator. It includes a terminal mode (`--terminal` flag) that provides a command-line interface for scripting and automation. This work extends the terminal to support fully automated testing workflows.

## Running Jace: Native Binary vs Maven

Two ways to run Jace:

### Native Binary

`/Users/brobert/Downloads/Jace` (Gluon GraalVM native image, ~472MB)

- Faster startup, no Maven/Java required
- `headless` parameter is NOT recognized — the emulator runs with a display window ("Did not understand parameter headless, skipping")
- Throws harmless `MacAccessible` JavaFX accessibility error on startup but continues normally
- Boots and drops media on S6D1 correctly
- Usage: `/Users/brobert/Downloads/Jace [disk_image.po]`
- **Use for**: Running disk images interactively; does NOT support terminal/scripting mode

### Maven (Required for Terminal/Scripting Mode)

`mvn exec:java ...` from `~/Documents/code/jace/`

- Required for headless/terminal testing mode
- Supports the full terminal scripting interface documented in this file
- Usage: `mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal"`

---

## Terminal Mode Access

### Starting Terminal Mode

```bash
# From jace directory
mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal"

# Alternative: using javafx:run
mvn -q javafx:run -Djavafx.args="--terminal"
```

### Reaching Applesoft BASIC Without a Disk Image

When Jace starts without a disk image, or after `reset` with no disk mounted,
the system is in the Disk ][ boot ROM at $C600, waiting for a floppy. To start
Applesoft BASIC directly from the ROM, use the monitor:

  reset
  monitor
  E000G

`E000G` jumps to the Applesoft BASIC cold-start entry in ROM, which initializes
BASIC and presents the `]` prompt. To reach the warm-start (re-enter BASIC
without reinitializing), use `FF69G` instead.

### Basic Terminal Commands

The terminal provides a REPL (Read-Eval-Print Loop) with these modes:
- **Main mode** (default) - System control and disk operations
- **Monitor mode** - Memory examination and debugging
- **Assembler mode** - Assembly language input

Type `?` or `help` for command list, `help <cmd>` for specific command help.

## New Automation Commands

Three key commands were added to enable automated testing:

### 1. `bootdisk` - Mount and Boot Disk Image

**Purpose**: Combines disk insertion, system reset, and auto-boot into one command.

**Usage**:
```
bootdisk d<drive_number> <filepath> [slot]
```

**Examples**:
```
bootdisk d1 /path/to/disk.po
bootdisk d1 /path/to/PLEIADES-MEGAFLASH-FPU.po 6
```

**What it does**:
1. Inserts the specified disk image into the drive
2. Performs a cold reset of the Apple II
3. Runs the emulator until PC (Program Counter) >= $2000
4. Displays CPU state when boot completes

**Why PC >= $2000?**: The Apple II boot ROM runs from $C600-$CFFF, and most programs load into memory starting at $0800 or higher. By the time PC reaches $2000, the system has fully booted and the program is running.

**Alias**: `bd`

### 2. `showtext` - Display Text Screen Contents

**Purpose**: Captures and displays the current Apple II text screen, handling both 40-column and 80-column modes.

**Usage**:
```
showtext
```

**Example output**:
```
=== Text Screen (40 columns) ===

  APPLE II PLUS

  READY

]
=== End of Text Screen ===
```

**Features**:
- Automatically detects 40-column vs 80-column mode (via SoftSwitch `_80COL`)
- Correctly linearizes Apple II's non-sequential text memory layout
- Converts Apple II high-bit ASCII to standard ASCII
- Handles inverse and flashing characters

**Memory Layout**: Apple II text screen uses an interleaved row layout starting at $0400. The command handles this correctly.

**Alias**: `st`

### 3. Enhanced `insertdisk` and `ejectdisk`

**Purpose**: Programmatically control disk drive operations.

**Updated Usage**:
```
insertdisk d<drive_number> <filepath> [slot]
ejectdisk d<drive_number> [slot]
```

**Examples**:
```
insertdisk d1 /Users/brobert/Documents/code/PLASMA/PLEIADES-MEGAFLASH-FPU.po
insertdisk d2 /path/to/utilities.po 6
ejectdisk d1
```

**Default**: Slot 6 (standard Disk ][ controller slot)

**Aliases**: `id`, `ed`

### 4. `expect` - Wait for Text on Screen

**Purpose**: Polls the Apple II text screen until a specific string appears, or times out. Essential for synchronizing automation with program output.

**Usage**:
```
expect <string> [timeout_seconds]
```

**Examples**:
```
expect "DONE" 30          # Wait up to 30s for "DONE" to appear on screen
expect "Press any key" 10 # Wait up to 10s
expect "TEST PASSED"      # Default 30s timeout
```

**Behavior**:
- Polls the screen every 500ms
- Runs the emulator between polls
- Returns immediately when the string is found
- Prints timeout message if string not found within timeout

**First-poll behavior**: The first screen check happens immediately when `expect`
is called, before running any emulator cycles. If the expected string is already
present on the text screen at the moment `expect` is invoked, it returns
immediately without waiting. This means `expect` is safe to call even if the
program may have already finished — it will not miss output that appeared before
the command ran.

**Caution**: Do NOT use `expect` with a string that is permanently present on
screen (like the `]` BASIC prompt or `*` monitor prompt) as a way to wait for
the computer to become responsive. Since the prompt is already there, `expect`
returns immediately regardless of whether the machine is actually executing your
command. Instead, to verify the machine is responding to input, type a test
message (e.g. `type "TEST\n"`) and use `expect "TEST"` to confirm the keystrokes
were echoed — typed characters echo to the text screen and indicate live response.

**Alias**: None

### 5. `waitkey` - Wait for Keyboard Read

**Purpose**: Blocks until the emulated program reads from the keyboard ($C000). Useful for detecting when a program is waiting for user input.

**Usage**:
```
waitkey [timeout_ms]
```

**Default timeout**: 30000ms (30 seconds)

### 6. `type` - Synchronized Keyboard Input

**Purpose**: Types a string character-by-character, waiting for the program to read each keystroke before sending the next. More reliable than `key` for programs that process input slowly.

**Usage**:
```
type <string>
```

**Example**:
```
type "hello world\n"
```

### 7. Tokenizing Applesoft BASIC Programs

Jace includes a built-in Applesoft BASIC tokenizer. Use it to convert a plain-text
BASIC listing into a tokenized program ready to inject into emulator memory — do NOT
write a custom tokenizer.

#### Terminal Command (easiest way)

Use the `loadBasic` terminal command to load a plain-text BASIC listing directly:

```
loadBasic /path/to/program.bas
```

- Alias: `lbas`
- On success: prints `Loaded N lines (M bytes) from /path/to/program.bas`
- On failure: prints the error and file line number (e.g., `Error at file line 23: ...`)

The program is injected into emulator RAM at the standard BASIC start address ($0801).
Each non-blank line in the file must start with a BASIC line number; blank lines are
silently skipped.

Error cases handled: file not found, file not readable, lines missing a BASIC line
number (reports 1-based file line number), empty file, and tokenizer exceptions.

**Java API** (call it from Java/test code):

```java
// Parse plain-text BASIC source into a tokenized ApplesoftProgram
ApplesoftProgram program = ApplesoftProgram.fromString(
    "10 PRINT \"HELLO\"\n20 GOTO 10\n"
);

// Inject the tokenized program into the running emulator at the standard
// BASIC start address ($0801) and run it
program.run();

// Alternatively, read the current BASIC program back out of emulator RAM
ApplesoftProgram existing = ApplesoftProgram.fromMemory(memory);
```

**To produce a raw tokenized binary** (e.g. to save as a .BAS/.PRG file):

1. Inject with `loadBasic <file>` or `program.run()` (writes tokenized bytes into
   emulator memory starting at the address stored in the BASIC pointer at $0067,
   normally $0801)
2. Use `savebin <filename> 0801 <size>` in terminal mode to dump those bytes to a file

The token byte codes match the standard Applesoft token table ($80–$EA); see
`jace.applesoft.Command.TOKEN` for the complete mapping.

**Source**: `src/main/java/jace/applesoft/ApplesoftProgram.java` and
`src/main/java/jace/applesoft/Command.java`

---

### 8. `loadbin` / `savebin` - Binary Memory Operations

**Purpose**: Load binary files directly into emulator memory or save memory to files. Critical for testing compiled programs without needing bootable disk images.

**Usage**:
```
loadbin <filename> <address>
savebin <filename> <address> <size>
```

**Examples**:
```
loadbin /path/to/program.bin 4000    # Load binary at $4000
savebin /tmp/memdump.bin 0900 100    # Save 256 bytes from $0900
```

**Aliases**: `lb`, `sb`

## Other Useful Commands

### `run` - Execute CPU Cycles

```
run [count] [#breakpoint]
```

Runs the CPU for a specified number of cycles (default: 1,000,000).

Examples:
```
run 1000000          # Run for 1 million cycles
run 500000 #$2000    # Run until PC reaches $2000
```

### `key` - Simulate Keypresses

**WARNING — Carriage Return**: `\r` does NOT send carriage return. Only `\n`
sends carriage return (code 13, the Apple II Enter/Return key). If you use `\r`
in a `key` or `type` command, it sends the letter 'r' (ASCII code 114).
Always use `\n` for the Apple II Return/Enter key.

```
key <value1> [value2] ...
```

Simulates keyboard input. Supports multiple formats:
- Strings: `key "Hello World"`
- Characters: `key a b c`
- Escape sequences: `key "+PROGRAM\n"`
- Hex values: `key $41`
- Decimal: `key 65`

The `\n` escape sequence sends carriage return (code 13).

Example for running a program:
```
key "+FPUMF_AUTO_TEST\n"
```

### `step` - Single-Step CPU

```
step [count]
```

Steps the CPU instruction-by-instruction (default: 1 step).

### `reset` - System Reset

```
reset
```

Performs a cold start reset of the Apple II.

## Monitor Mode Commands

Enter monitor mode with `monitor` (or `m`). Return to main mode with `back` (or `b`).

### Memory Examination
```
<addr>.<addr>           # Dump memory range (e.g., 3800.3820)
<addr>: <byte> ...      # Write bytes to memory
```

### CPU Control
```
<addr>G                 # Execute from address (e.g., 4000G)
cpu                     # Show CPU state (PC, A, X, Y, SP, flags)
registers [reg value]   # Show or set registers (e.g., reg PC $4000)
step [count]            # Single-step instructions
runto <addr>            # Run until PC reaches address
pause / resume          # Pause/resume emulation
```

### Breakpoints and Watches
```
break <addr>            # Set breakpoint
break -<addr>           # Remove breakpoint
break clear             # Remove all breakpoints
watch <addr> [name]     # Watch memory address for changes
watch clear             # Remove all watches
```

### Memory Operations
```
fill <start> <end> <value>      # Fill memory range
move <src> <dest> <count>       # Copy memory block
compare <src> <dest> <count>    # Compare memory blocks
find <start> <end> <byte> ...   # Search for byte pattern
```

## Complete Automation Example

Here's a full automation script using expect-style scripting:

```bash
#!/bin/bash
JACE_DIR="/Users/brobert/Documents/code/jace"
DISK_IMAGE="/Users/brobert/Documents/code/PLASMA/PLEIADES-MEGAFLASH-FPU.po"
TEST_PROGRAM="+FPUMF_AUTO_TEST"

cd "$JACE_DIR"

timeout 90 expect <<'EOF'
set timeout 30

# Launch JACE terminal mode
spawn mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal"

# Wait for JACE prompt
expect "JACE>"

# Boot the disk
send "bootdisk d1 $DISK_IMAGE\r"
expect "JACE>"

# Launch test program
send "key \"$TEST_PROGRAM\\n\"\r"
expect "JACE>"

# Run test cycles
send "run 3000000\r"
expect "JACE>"

# Capture screen output
send "showtext\r"
expect "=== End of Text Screen ==="

# Exit
send "qq\r"
expect eof
EOF
```

## Simpler Automation (stdin approach)

For non-interactive automation:

```bash
#!/bin/bash
JACE_DIR="/Users/brobert/Documents/code/jace"
DISK_IMAGE="/path/to/disk.po"

cd "$JACE_DIR"

mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" <<EOF
bootdisk d1 $DISK_IMAGE
key "+PROGRAM\\n"
run 2000000
showtext
qq
EOF
```

## Implementation Details

### Disk Operations

- Accesses `CardDiskII` via `Emulator.withMemory(memory -> memory.getCard(slot))`
- Uses `DiskIIDrive.insertDisk(File)` and `DiskIIDrive.eject()` methods
- Supports both drive 1 and drive 2 on any slot

### Text Screen Reading

- Reads from memory addresses $0400-$07FF (main text page 1)
- Handles interleaved row addressing (Apple II quirk)
- For 80-column mode: alternates reading auxiliary and main memory
- Character conversion handles:
  - High-bit ASCII (bit 7 set = normal display)
  - Flashing characters (bit 6 set, bit 7 clear)
  - Inverse characters (both bits clear)

### Boot Detection

- Polls PC register every 10ms
- Maximum wait: 10 million cycles
- Target: PC >= $2000 indicates boot complete
- Pauses emulation after reaching target

## Architecture Notes

### Terminal Class Hierarchy

```
JaceTerminal (base class)
  ├─ HeadlessTerminal (command-line focused)
  └─ UITerminal (GUI-integrated)

TerminalMode (interface)
  ├─ MainMode (system control)
  ├─ MonitorMode (debugging)
  └─ AssemblerMode (assembly input)
```

### Emulator Access Pattern

All emulator interactions use:
- `Emulator.withComputer(lambda)` - Access Apple2e computer
- `Emulator.withMemory(lambda)` - Access RAM directly
- `Emulator.whileSuspended(lambda)` - Atomic operations while paused

### Memory Operations

```java
// Read from main memory
byte value = memory.read(addr, RAMEvent.TYPE.READ_DATA, true, false);

// Read from auxiliary memory (80-column mode)
byte auxValue = memory.read(addr, RAMEvent.TYPE.READ_DATA, true, true);
```

The last boolean parameter controls auxiliary vs main memory access.

## Testing Workflow

Typical automated test workflow:

1. **Boot disk** with test program
2. **Wait for prompt** (check screen for expected text)
3. **Inject keystrokes** to launch test
4. **Run cycles** to let test execute
5. **Capture screen** to verify results
6. **Parse output** for pass/fail indicators

## Debugging Guide for Agents

This section documents proven debugging techniques learned from real-world use of JACE terminal mode for testing compiled 65C02 code.

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
monitor
4000G
quit
run 5000000
showtext
monitor
3800.3820
quit
qq
EOF

timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" \
    -Dexec.args="--terminal" < /tmp/jace_test.txt > /tmp/jace_out.txt 2>&1
```

The flow:
1. `reset` - Clean emulator state
2. `loadbin` - Load binary directly into memory (no disk boot needed)
3. `monitor` / `4000G` - Enter monitor, execute from address
4. `quit` - Return to main mode
5. `run N` - Let program run for N CPU cycles
6. `showtext` - Capture what's on screen
7. `monitor` / `ADDR.ADDR` - Dump memory to verify results
8. `qq` - Quit JACE

### Using `expect` Instead of Fixed Cycle Counts

When you know what text the program should produce, `expect` is more reliable than guessing cycle counts:

```
reset
loadbin /path/to/program.bin 4000
monitor
4000G
quit
expect "DONE" 30
showtext
monitor
3800.3820
quit
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

   To verify TEXT mode was restored, check the display soft switch via monitor:
   ```
   monitor
   C01A.C01A
   quit
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

#### Available Commands

| Bytes | ACME Syntax | Effect |
|-------|-------------|--------|
| `$FC $50 NN` | `!byte $FC, $50, NN` | Print NN as decimal number to stdout |
| `$FC $5B NN` | `!byte $FC, $5B, NN` | Print NN as decimal number + newline to stdout |
| `$FC $5C NN` | `!byte $FC, $5C, NN` | Print ASCII character NN to stdout |
| `$FC $44 NN` | `!byte $FC, $44, NN` | Dump full CPU state with identifier NN |
| `$FC $65 $01` | `!byte $FC, $65, $01` | Turn ON instruction tracing |
| `$FC $65 $00` | `!byte $FC, $65, $00` | Turn OFF instruction tracing |

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
- **Tracing**: Can enable/disable full instruction tracing around suspect code

#### Custom Command Handlers (Advanced)

JACE supports registering custom extended command handlers programmatically via:
```java
cpu.registerExtendedCommandHandler(0xNN, (param2) -> { ... });
cpu.unregisterExtendedCommandHandler(0xNN);
```
This allows test harnesses to extend the $FC command set for specialized testing needs.

### Memory Inspection for Verification

After running code, use the monitor to verify memory contents. This is more reliable than screen output for checking computed values:

```
monitor
3800.3810       # Dump variable storage
0900.0940       # Dump generated code
D000.D020       # Check Language Card contents
cpu             # Show CPU state (PC tells you where it stopped)
quit
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
monitor
break 4050          # Break when PC reaches $4050
4000G               # Start execution
cpu                 # Check state at breakpoint
step 5              # Step through 5 instructions
cpu                 # Check state again
3800.3808           # Inspect memory
resume              # Continue execution
quit
```

### Diagnosing Common Failures

**`CALL addr` never returns to BASIC (caller never resumes)**:
- Common cause: a JSR inside the machine-language routine calls a ROM entry point that does not exit via RTS. Example: on the Apple IIe, `JSR $FB39` does not return — it ends with `JMP $C100` (unconditional jump to slot 1 firmware ROM). The `RTS` that was supposed to follow the JSR is never reached.
- How to detect: the machine is not crashed (it keeps running), but the BASIC line after the `CALL` never executes. A test BASIC program such as `10 CALL 24576 / 20 PRINT "DONE"` combined with `expect "DONE"` will time out.
- Debug strategy: before running, set a breakpoint at `$C100` in the monitor (`break C100`). If the breakpoint fires, a ROM routine hijacked execution instead of returning.
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

## Known Limitations

1. **Graphics modes not supported**: Only text mode can be captured via `showtext`
2. **No asynchronous I/O**: All commands block until complete - cannot monitor screen while emulation runs
3. **`expect` polling interval**: 500ms granularity means fast-completing programs may have slight delay before detection
4. **Native binary does not support terminal mode**: `/Users/brobert/Downloads/Jace` (Gluon native) ignores the `headless` flag and opens a display window; use Maven for all scripted/automated testing

## Future Enhancements

Potential improvements:

1. **waitpc command**: Generalized version of bootdisk's PC-based wait logic
2. **capture command**: Save screen to file (currently must parse stdout)
3. **script command**: Execute multiple commands from file
4. **Graphics screen capture**: Support for HGR/DHGR modes

## References

- JACE source: `src/main/java/jace/terminal/`
- Apple II memory map: https://www.kreativekorp.com/miscpages/a2info/memorymap.shtml
- Disk ][ controller: https://www.doc.ic.ac.uk/~ih/doc/stepper/others/example3/diskii_specs.html

## Change Log

### 2026-03-02
- Added "Running Jace: Native Binary vs Maven" section documenting `/Users/brobert/Downloads/Jace` (Gluon GraalVM native binary)
- Native binary finding: `headless` parameter is NOT supported, runs with display window, throws harmless `MacAccessible` error but boots normally
- Noted native binary does not support terminal/scripting mode; Maven required for automation
- Added "Tokenizing Applesoft BASIC Programs" section documenting the built-in `ApplesoftProgram.fromString()` Java API; clarified there is no terminal command for tokenizing, and described the inject-then-`savebin` workflow to produce a raw binary file

### 2026-03-01
- Added "Reaching Applesoft BASIC Without a Disk Image" — `E000G` cold-start, `FF69G` warm-start
- Documented `expect` first-poll behavior (returns immediately if string already on screen)
- Added caution against using prompt characters (`]`, `*`) as readiness signals with `expect`
- Added WARNING near `key`/`type`: `\r` sends the letter 'r', not carriage return; use `\n`
- Added "Testing Clean Exit to BASIC" section: sentinel lines, soft switch check at $C01A, echo-based responsiveness testing
- Added diagnosis entry for "`CALL addr` never returns to BASIC": JSR to ROM routine that ends in JMP (e.g. `$FB39` → `JMP $C100`), breakpoint strategy at `$C100`, fix guidance

### 2026-02-11
- Documented Debug NOP ($FC) extended opcode: console output, register dumps, instruction tracing
- Added ACME macro examples for debug NOP commands
- Documented `expect`, `waitkey`, `type`, `loadbin`, `savebin` commands (were implemented but undocumented)
- Added complete Monitor Mode command reference
- Added Debugging Guide with proven patterns from SectorC65 compiler testing
- Added Language Card soft switch quick reference
- Added timeout wrapper best practices
- Updated Known Limitations (removed items that are now implemented)

### 2025-01-30
- Added `bootdisk` command for automated boot workflows
- Added `showtext` command for text screen capture
- Enhanced `insertdisk`/`ejectdisk` with proper implementation
- Created this documentation

---

*This automation infrastructure enables Claude Code to autonomously test Apple II software on the JACE emulator without manual intervention.*
