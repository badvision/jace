# JACE Terminal Automation Guide

## Overview

This document describes the automation capabilities added to JACE's terminal mode to enable automated testing of Apple II software.

## Background

JACE (Java Apple Computer Emulator) is a Java-based Apple II emulator. It includes a terminal mode (`--terminal` flag) that provides a command-line interface for scripting and automation. This work extends the terminal to support fully automated testing workflows.

## Running Jace: Use Maven — Always

**Use the Maven/Java version for everything.** Do not use the native binary for automation.

### Native Binary (`/Users/brobert/Downloads/Jace`)

- Interactive use only (drag-and-drop disk images, manual play)
- Does NOT support `--terminal` scripting mode — parameter is silently ignored
- Throws harmless `MacAccessible` JavaFX accessibility error on startup
- **Do not use for any automated testing or CI workflows**

### Maven — The Only Way to Script Jace

All automation, testing, memory inspection, and screenshot capture goes through Maven terminal mode.

```bash
# Standard invocation — all scripting/automation
# Use slot 7 (SmartPort) for ProDOS disk images — instant reads, no spinning-disk emulation
cd ~/Documents/code/jace
mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" <<'EOF'
bootdisk d1 /path/to/disk.po 7
run 5000000
screenshot /tmp/frame.png
mem C07F C07F
qq
EOF
```

### Slot 6 vs Slot 7 — Always Use Slot 7 for ProDOS

| Slot | Type | Load time | Use for |
|------|------|-----------|---------|
| 6 (default) | Disk ][ | Real-time spinning disk emulation — ~600s for full ProDOS boot | Floppy-specific testing only |
| 7 | SmartPort | Instant — no spin delay | **All ProDOS .po disk images** |

`bootdisk d1 /path/to/disk.po 7` — the trailing `7` selects slot 7.
`insertdisk d1 /path/to/disk.po 7` — same syntax for manual insertion.

Slot 6 (Disk ][) emulates a real floppy drive including rotation speed, making ProDOS file I/O take hundreds of real seconds. Slot 7 (SmartPort) is a virtual hard-disk interface — reads are instantaneous. All ChoplifterReverse validation should use slot 7.

**The `screenshot` command** (`ss2` alias) renders the current DHGR/HGR framebuffer directly to
a 1120×384 PNG with NTSC color — fully headless, no display window required. Use `Read` tool on
the output PNG for multimodal visual review.

### Known Issue: cadius ProDOS Disk Images (146,432 bytes)

Jace's `FloppyDisk.java` expects exactly 143,360 bytes (280 blocks × 512). Disk images built
with `cadius` are 146,432 bytes (286 blocks × 512) — the extra 3,072 bytes are zero padding.

**Fix already applied** to `src/main/java/jace/hardware/FloppyDisk.java`: truncates to 143,360
before nibblizing when the image is exactly 146,432 bytes and the trailing bytes are zero.

If this patch is ever lost, re-apply it: in `FloppyDisk.java`, before the nibblize step, add:
```java
if (diskData.length == 146432) {
    diskData = Arrays.copyOf(diskData, 143360);
}
```

---

## Terminal Mode Access

### Starting Terminal Mode

```bash
# Standard — preferred
cd ~/Documents/code/jace
mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal"

# Alternative
mvn -q javafx:run -Djavafx.args="--terminal"
```

### Reaching Applesoft BASIC Without a Disk Image

When Jace starts without a disk image, or after `reset` with no disk mounted,
the system is in the Disk ][ boot ROM at $C600, waiting for a floppy. To start
Applesoft BASIC directly from the ROM, use the monitor:

  reset
  E000G
  run 2000000

**Critical**: `E000G` starts execution at $E000 asynchronously and returns
immediately to the main prompt. BASIC cold-start has NOT finished yet.
You MUST run cycles after `E000G` to let BASIC initialize.
`run 2000000` gives enough cycles for BASIC to cold-start and reach its `]` prompt.

After `run 2000000` completes, TXTTAB ($67-$68) should be `01 08` ($0801).
**Verify with `67.68` before calling `lbas`** — if TXTTAB is
wrong (e.g. pointing at screen memory $0400), `lbas` will overwrite screen RAM
with the program text, causing garbled screen output and BASIC failures.

To reach the warm-start (re-enter BASIC without reinitializing), use `FF69G`
followed by `run 500000`.

### Complete Applesoft BASIC Test Workflow (No Disk)

**Proven working sequence** for loading and running a BASIC program from a file:

```
reset
E000G
run 2000000
lbas /path/to/program.bas
key "RUN\n"
expect "DONE" 60
st
qq
```

Notes:
- `key "RUN\n"` injects keystrokes immediately; they land in the keyboard buffer
  and BASIC picks them up during subsequent `expect`-driven emulation cycles
- `expect` runs the emulator between polls, so it drives execution forward
- **Using `PR#3`** to switch to 80-column mode goes through the 80-col firmware's
  keyboard input loop and screen output loop. This works correctly in headless
  terminal mode; it does NOT hang.
- `waitkey` and `type` (synchronized keyboard) do NOT work after `E000G`
  because the emulator is paused; use `key` + `run N` or `key` + `expect` instead

### BASIC Variable Table Layout (Applesoft Internals)

Applesoft stores float variables as 7 bytes in the variable table (VARTAB):
- Bytes 0-1: variable name (ASCII)
- Byte 2: exponent ($9D equivalent)
- Byte 3: MSB mantissa ($9E) with **sign in bit 7** (0=positive, 1=negative)
          NOTE: bit 7 of the actual mantissa is cleared and replaced by the sign!
          For 7.0: $9E=$E0 → stored as ($E0 & $7F) | (sign << 7) = $60 (positive)
- Byte 4: $9F (2nd mantissa byte)
- Byte 5: $A0 (3rd mantissa byte)
- Byte 6: $A1 (LSB mantissa)

To find VARTAB base: `VA = PEEK(105) + PEEK(106)*256`
If the result variable is declared first in the program, it will be at VA.

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
type <string>[, timeout_seconds]
```

**Examples**:
```
type "hello world\n"       # Per-char mode: emulator resumes/pauses for each character
type "RUN\n", 5            # Timeout mode: resume emulator for up to 5s, type all chars, re-pause
```

The timeout is indicated by a trailing `,N` or `, N` (comma + 1–2 digit number) so it can't be
confused with a number at the end of the string content.

**Timeout mode behavior**: When a timeout (in seconds) is provided:
- If the emulator was paused, it is resumed once at the start
- All characters are typed, each waiting for a keyboard read, but bounded by the total timeout
- After the last character is typed (or timeout reached), if the emulator was paused before, it is re-paused
- Use this when running a BASIC program after `E000G` since a single resume cycle is needed

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

### `screenshot` / `ss2` - Capture Display as PNG

```
screenshot <filename.png>
ss2 <filename.png>
```

Renders the current Apple II display (HGR or DHGR) to a PNG file using NTSC color simulation.
Output is 1120×384 (2× scale). Works fully headless — no display window required.

```
screenshot /tmp/frame.png     # save current frame
```

After saving, use the `Read` tool on the PNG path for multimodal visual inspection.
This is the correct way to do visual validation — do NOT try to screencapture a window.

---

### `run` - Free-Run the Emulator (NOT cycle-accurate)

```
run [count] [#breakpoint]
```

Alias: `g`. Default `count` is 1,000,000.

**`run N` does NOT execute N cycles.** Despite the name and the argument, `count` is
converted to a **wall-clock duration** and the emulator is simply left free-running for
that long (`MainMode.runCPU`):

```java
long runTimeMs = finalCycleCount / 1000;      // cycles -> milliseconds at a notional 1 MHz
if (runTimeMs < 100) runTimeMs = 100;         // *** 100 ms FLOOR ***
...
while (System.currentTimeMillis() - startTime < runTimeMs) { ... Thread.sleep(50); }
```

Consequences, all of which have cost real debugging time:

- **Any `count` below 100,000 runs for ~100 ms of real time.** `run 1000`, `run 100`,
  `run 5` are all identical: ~100 ms of free-running emulation, which at ~1 MHz is
  **100,000+ cycles — on the order of 100x more than a small `count` asks for.**
  An agent debugging the Pitfall! port hit exactly this and reported that "the emulator
  capped each run at ~120k cycles regardless of the argument"; it could not land screen
  captures at chosen sprite positions.
- Even for large `count`, the cycle total is only approximate. It is wall-clock bounded,
  so **host load affects it**, and if `speed max` is in effect (see `speed`/`sp`) the
  machine is unthrottled and will execute far more cycles than `count` implies.
- The `#breakpoint` form is polled every 50 ms, so PC can pass through the target
  between polls and the "breakpoint" is missed. Use `runto`/`rt` (or `break` + `resume`)
  when you actually need to stop at an address.

**Use the right tool instead:**

| Need | Use | Accuracy |
|---|---|---|
| Exact instruction stepping | `step [count]` | Exact — N instructions |
| Exact device/motherboard ticks | `tick [count]` (`tc`) | Exact — N ticks |
| Stop at an address | `runto <addr>` (`rt`), or `break` + `resume` | Exact |
| Advance one frame / get frame-coherent memory | `runvbl` (`rv`), `screenshot ... --vbl` | Exact — VBL edge |
| "Let it run a while" (boot, BASIC cold-start, waiting on output) | `run N` | Approximate only |
| Wait for known text output | `expect <string> [timeout]` | Event-driven |

`run` is fine for its real purpose — letting the machine churn for a while, e.g.
`run 2000000` after `E000G` to let BASIC cold-start. It is **not** a stepping primitive.

Examples:
```
run 2000000          # ~2 seconds of free-running emulation (approximate)
run 1000             # NOT 1000 cycles — ~100 ms, i.e. ~100,000+ cycles. Use `step`/`tick`.
```

The misleading behaviour is documented in `help run` and flagged in a comment in
`MainMode.java` next to the floor. **Do not "fix" the timing logic casually** — existing
tests and scripts depend on the current semantics; changing them is a repo-owner decision.

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

### `tick` - Step Motherboard (All Devices)

```
tick [count]
```

Steps the full motherboard cascade (all devices) for N ticks (default: 1). Use for timing-sensitive device stepping. Alias: `tc`.

### `step` - Single-Step CPU (Monitor Mode)

```
step [count]
```

Steps the CPU instruction-by-instruction (default: 1 step). Available in monitor mode and directly from main mode (no mode switch required).

### `go` - Execute from Address (No Mode Switch)

```
go <addr>
```

Sets PC to the specified hex address and begins execution. Equivalent to entering monitor and typing `4000G`. Example: `go 4000`

### `mem` - Dump Memory Range (No Mode Switch)

```
mem <start> <end>
```

Hex dump of a memory address range. Equivalent to entering monitor and typing `3800.3820`. Example: `mem 3800 3820`

### `cpu` / `registers` / `break` / `runto` (No Mode Switch)

These monitor commands are now available directly in main mode:

```
cpu                     # Show CPU state (PC, A, X, Y, SP, flags)
registers [reg value]   # Show or set registers (alias: reg)
break <addr>            # Set/remove/list breakpoints (alias: bp)
runto <addr>            # Run until PC reaches address (alias: rt)
```

### `reset` - System Reset

```
reset
```

Performs a cold start reset of the Apple II.

## Monitor Mode Commands

Enter monitor mode with `monitor` (or `m`). Return to main mode with `back`, `quit`, or `q`.

**Note:** `b` is the alias for `break` (set breakpoint), NOT for `back`. Use `q` to exit monitor mode.

**Tip:** Wozniak monitor syntax (`4000G`, `3800.3820`, `E000G`, etc.) and named commands (`cpu`, `registers`, `break`, `runto`) work directly from the main `JACE>` prompt — no mode switch needed.

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
// Read via the ACTIVE memory configuration (whatever softswitches currently map in)
byte value = memory.read(addr, RAMEvent.TYPE.READ_DATA, true, false);
```

**The last boolean of `RAM.read` is `requireSynchronization`, NOT an aux flag.**
`RAM.read` and `RAM.readRaw` both go through `activeRead`, so neither can select a
bank. To read a specific physical bank, go to the `PagedMemory` directly:

```java
RAM128k ram128k = (RAM128k) memory;
byte mainValue = ram128k.getMainMemory().readByte(addr);   // odd DHGR columns
byte auxValue  = ram128k.getAuxMemory().readByte(addr);    // even DHGR columns
```

This is what `MonitorMode.resolveBank()` does for the `M`/`X` prefixes and the
`memmain`/`memaux` commands. It touches no softswitches.

### `memaux` / `memmain` - Dump a Specific Bank (No Mode Switch)

```
memaux  <start> <end>    # AUX bank only  (alias: mx)
memmain <start> <end>    # MAIN bank only (alias: mm)
```

Hex dumps a range from an explicitly named bank, independent of softswitch state.
Use these instead of `mem` whenever the bank matters — `mem` follows the active
configuration and cannot distinguish the two banks.

In 80STORE+HIRES double-hi-res, `$2000-$3FFF` is interleaved: **aux holds the even
pixel columns, main the odd**. Verifying rendered output requires both:

```
memaux  2000 2027
memmain 2000 2027
```

The monitor-mode equivalents are the `X` and `M` address prefixes (`X2000.2027`).

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

## Running the Java Unit Test Suite (`mvn test`)

This is a different workflow from the terminal automation sections above — it exercises
JUnit tests under `src/test/java`, not the emulator's terminal REPL.

### Always use `mvn clean test`, never bare `mvn test`, after editing a file outside the Edit tool

If a file was edited via sed, mv, or git stash/pop instead of the Edit tool, always run
`mvn clean test` afterward, not bare `mvn test`.

Maven's incremental compiler can silently skip recompiling a source file if its mtime
doesn't look newer than the existing `.class` file — this has been observed in practice
after using `sed -i` to patch a file and then restoring it via `mv file.java.bak file.java`.
The symptom is confusing: you edit the source, `grep` confirms the new content is on disk,
but `mvn test` (no `clean`) still runs against the old behavior and reports test results
consistent with the *previous* code. If a test result seems to contradict what the source
clearly says, don't assume the test or your reasoning is wrong — re-run with `mvn clean test`
first to rule out a stale-class artifact.

### Full suite run time

A full `mvn clean test` run on this project takes several minutes (observed: 5+ minutes,
sometimes more) because it runs under the JaCoCo coverage agent across ~300 tests including
emulator boot/CPU/video subsystems. Launch it with a generous background timeout (run it as
a background task, don't poll every few seconds) rather than assuming a hang. Don't
re-launch a second `mvn clean test` while one is still running — check for a running
`surefire` java process first (`ps aux | grep surefire`), since Maven's own build lock will
just queue/serialize a second invocation and waste time.

### Known pre-existing test failures (not caused by your change)

As of 2026-07, the following tests fail on a clean baseline checkout, unrelated to sound,
video, CPU, or terminal-automation work — do not treat these as regressions introduced by
your change. If you see *new* failures beyond this list, investigate; if you see exactly
this list, it's the known baseline:

- `CardSSCTest` (multiple methods) — Super Serial Card, e.g. `testExpectedFirmwareContent`,
  `testInputDelegationMechanism`, `testPhantomInputFixed`, `testSSCFirmwareExecution`,
  `testCompleteSSCInitialization`
- `CardSSCRegisterTest.testACIARegisterInitialValues`
- `TerminalFeatureTest.testDeviceTickingDuringStep`
- `TerminalFeatureTest.testSaveBinFunctionality`
- `TerminalFeatureTest.testStartupWithMassStorageDisk` (depends on a local file,
  `/Users/brobert/Downloads/ProDOS_2_4_3.po`, not present in this environment)
- `TerminalFeatureTest.testStepModeBehavior`

To confirm whether a failure is pre-existing vs. caused by your change, isolate your edit
with `git stash push -- <your-file>`, re-run `mvn clean test -Dtest=<TheFailingClass>`
against the unmodified baseline, then `git stash pop` to restore your work.

## Mockingboard / AY-3-8910 Sound Emulation

`jace.hardware.CardMockingboard` plus `jace.hardware.mockingboard.*` implement a
Mockingboard-C: two 6522 VIAs, each driving one AY PSG.

### The AY clock is 1022727 — read this before changing it

**This constant has been changed three times by three different people during one
effort. If you are about to change it again, you are almost certainly making the
same mistake they did.** The two numbers below are different *quantities*, not rival
estimates of one quantity.

| Constant | Value | What it is | What it clocks |
|---|---|---|---|
| `CardMockingboard.CLOCK_SPEED` | **1022727** | `14318181.8 / 14` — the slot bus clock | The AY oscillators (pitch) |
| `TimedDevice.NTSC_1MHZ` | **1020484** | `14318181.8 * 65 / 912` — CPU stretched-cycle average | CPU cycles, card tick pacing, 6522 timers |

**1022727 = crystal / 14.** The Apple II master oscillator is the NTSC colorburst
&times;4, 14318181.8 Hz. The bus clock wired to the peripheral slots is that divided
by 14. This is the clock a real Mockingboard's AY-3-8913 receives. It is the
canonical hardware number.

**1020484 = crystal &times; 65 / 912.** The 6502's *average* throughput once the
video logic's stretched cycle (one long cycle per 65-cycle scanline) is amortized
in. Same derivation as AppleWin's
`CLK_6502_NTSC = (_14M_NTSC * 65.0) / (65.0*14.0+2.0)`.

**Jace runs the entire machine at the average, including this card.** That is
technically incorrect for a slot card — the card's oscillator does not slow down
just because the CPU waits — and it means Jace's whole timebase is **~0.22% slow
(~3.8 cents flat)**. That inaccuracy is **known and deliberately accepted**; fixing
it properly would mean giving slot devices their own timebase, which is out of
proportion to a 3.8-cent error.

**The rule that follows: pretend the card runs at 1.0227 MHz and scale everything
against that number.** Do not "correct" `CLOCK_SPEED` down to `NTSC_1MHZ` on the
grounds that Jace runs everything at the average. Doing so applies the 0.22% error a
*second* time — once in the timebase, once in the oscillator — leaving tones a
further 3.8 cents flat. That was a real bug, fixed 2026-07.

`MockingboardClockTest` pins both numbers, records each one's derivation, and
asserts their difference is 2243, specifically to stop someone collapsing them into
one constant.

`R6522` has no clock constant of its own on purpose — it is ticked at the
motherboard's CPU rate, so one `tick()` is one timer count. Timers correctly follow
the stretched average, because the 6522 genuinely does see the same bus &Phi;2 the
CPU does.

### 6522: IER gates the IRQ *pin*, never the IFR *flag*

On real hardware — and in MAME's `6522via.cpp` — a timer expiry sets its IFR flag
unconditionally. `IER` is consulted in exactly one place, deciding whether the IRQ
line is asserted and whether IFR bit 7 (the summary bit) is set. Concretely:

- Flag bits 6/5 (T1/T2): set by the interrupt condition, regardless of IER.
- Bit 7: set from `IER & IFR`, so a masked-off flag must not claim the line.

Software that polls IFR with interrupts disabled is a standard card-detection
idiom (see the Skyfox comment in `handleFirmwareAccess`). Gating the flag on the
enable makes such a poll spin forever. Guarded by `R6522InterruptFlagTest`.

### AY reset writes $FF to register 7, not 0

MAME's `ay8910_reset_ym()` writes 0 to every register. Jace deliberately writes
`$FF` to register 7 because the mixer's six enable bits are **active-low** — a
literal 0 enables all six generators, making reset audible. `$FF` is the
all-disabled encoding. Do not "correct" this to match MAME literally.

### Mixer combines tone and noise with AND, not OR

Per MAME `ay8910.cpp:1110` the pre-DAC formula is
`(ToneOn | ToneDisable) & (NoiseOn | NoiseDisable)`. When both tone and noise are
disabled the channel output is **1**, not 0 — it becomes a DC source driven by the
amplitude register, which is how PCM sample playback works on this chip. An OR
here silences that path.

### Period 0 behaves as period 1 — except for the envelope

Tone and noise: period 0 is the same as period 1 (`TimedGenerator.setPeriod` via
`clocksAtPeriodZero()`). Envelope: period 0 is **half** of period 1
(`EnvelopeGenerator.clocksAtPeriodZero()` returns `stepsPerCycle() / 2`). MAME
ay8910.cpp:90-91 states this asymmetry explicitly. Neither case may be silenced.

Prescalers: tone `clock/(16*TP)`, noise `clock/(16*NP)`, envelope `clock/(256*EP)`.

### Verified-correct areas — do not "fix" these

Exhaustively measured against MAME and left unchanged:

- **All 16 envelope shapes.** Jace's formulation (`hold = ((shape ^ 8) & 9) != 0`,
  `start1high`/`start2high`/`oddEven`) is structurally unlike MAME's but produces
  an identical 33-sample level sequence for every one of the 16 shapes.
- **Register 13 restarts the envelope; 11/12 do not.** Matches hardware.
- **The noise LFSR.** Jace uses a Galois formulation; MAME uses Fibonacci
  (`bit0 ^ bit3`, tap position verified on real chips per ay8910.h:265-267). The
  output streams are the same maximal-length m-sequence — period 131071, 50% duty,
  identical run-length statistics — differing only in phase and polarity, which is
  inaudible. Rewriting it to match MAME byte-for-byte would be churn.
- **Register 7 polarity.** Already correctly active-low.

### The chip is an AY-3-8913 — 16 shared amplitude levels

Previously undocumented, now settled, because it determines the size and shape of
the amplitude table. A Mockingboard uses the **AY-3-8913**: the AY-3-8910's PSG core
in a 24-pin package with the parallel I/O ports omitted. Two independent citations:

- AppleWin's `Mockingboard.cpp` names the part throughout — `class AY8913`,
  `AY8913_Write`, `AY8913_Reset`, `NUM_AY8913_PER_SUBUNIT` — with the comment "AY1 is
  the primary AY-3-8913 connected to 6522" (GH#1192).
- MAME defines `ay8913_device` as `ay8910_device(..., PSG_TYPE_AY, 3, 0)`
  (`ay8910.cpp:1630-1631`): identical core, 3 sound streams, **0** I/O ports.

Because it is `PSG_TYPE_AY`, `ay8910.cpp:1578-1579` selects the same 16-entry
`ay8910_param` for **both** the tone and the envelope DAC, and `:1575` sets
`m_env_step_mask = 0x0f`. So: **16 levels, shared by tone and envelope.** The
YM2149's 32-entry `ym2149_param_env` does *not* apply here.

Jace's structure already matches — `EnvelopeGenerator` counts 0..15 and
`SoundGenerator.step` indexes the same 16-entry `VolTable` for both paths — so no
restructuring was needed. If you ever port this to a YM2149, the envelope path needs
32 levels and this is where to start.

### The volume table is measured hardware data — do not synthesize it

`buildMixerTable()` scales a fixed 16-entry `AY_MEASURED_LEVELS` table taken from
Matthew Westcott's December 2001 voltage measurements of a real AY-3-8910 (the
readings MAME cites for its active `ay8910_param`, `ay8910.cpp:678-722`), expressed
as swing above the level-0 floor so that level 0 is true silence.

It previously synthesized a uniform 3 dB/step curve. That is not what the chip does:
the measured steps range from **1.74 dB to 4.46 dB** and their size is not even
monotonic in the level. Every intermediate level sits *above* the uniform curve — by
up to +4.8 dB around levels 7-10 — which compresses the level-1-to-15 span from
42.1 dB to **39.6 dB**. Audibly, quiet passages and envelope decay tails are less
recessed than the synthesized curve made them. This was a human decision about
audible character, since it is not a correctness fix in the register-accuracy sense.

Note the table is the **measurements**, not MAME's table verbatim. MAME stores
equivalent output *resistances* and converts them through `build_single_table`, a
divider fitted in SwitcherCAD against the **ZX Spectrum's** output circuit — which
Jace does not model. No load resistance reproduces the raw readings exactly (best
residual 0.0056 of full scale at RL=1800; MAME's annotated RL=2000 leaves 0.011, a
4.0 dB error at level 1). Where the model and the measurement disagree, the
measurement wins. `VolumeTableTest` pins every level to within 0.001 of full scale,
which rejects both the old uniform curve and MAME's reconstruction.

### Writing tests for this subsystem

Two setup gotchas, both of which produce confusing failures:

1. **`Utility.setHeadlessMode(true)` first.** Anything reaching `R6522.tick()` or
   `Emulator.withComputer` will otherwise boot `Apple2e`, hit JavaFX in
   `Utility.loadIconLabel`, and throw `ExceptionInInitializerError`. Likewise,
   avoid `CardMockingboard.reconfigure()` in a unit test — construct
   `new PSG(base, clock, rate, name, mask)` directly.
2. **`CardMockingboard.VolTable` is a lazily-built static.** Tests touching
   `SoundGenerator.step` need `new CardMockingboard().buildMixerTable()` in a
   `@BeforeClass`, or they NPE — and they may pass by accident when run in a suite
   where another test built it first.

Note that `Pt3PlayerRegisterTest`, which compares AY register frames against
`vt3-cli`, is **blind to almost all of the above**: identical register values can
still produce wrong audio if the PSG core misinterprets them. On the reference
song, noise period, envelope period and envelope shape are always 0 and no
amplitude uses envelope mode. Cover the PSG with focused unit tests instead.

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

### 2026-07-09
- Fixed vaporlock/beam-racing hang: `Video.java`'s scanner address lookup tables (`textOffset`/`hiresOffset`) were sized to 192 entries (visible screen lines only); extended to the full 262-line `TOTAL_LINES` so vertical blanking now generates real hardware "screen hole" addresses instead of recycling visible-row addresses. Previously this caused vaporlock-style floating-bus timing probes (e.g. Lancaster/Elliott techniques) to hang forever waiting for byte patterns that never appeared during blanking. New `calculateBlankingScannerOffset()` ported from MAME PR #15247 (mamedev/mame, "apple2video: emulate softswitch-specific delays; improve read_floatingbus()"), specifically its `a2_video_device::scanner_address()` formula. Known limitation: the formula is only valid as a per-scanline-start constant for horizontal offsets 0-7 within a blanking line (real hardware's address formula wraps mod-16 at offset 8) — sufficient for the vaporlock probes tested against, not full per-pixel floating-bus accuracy throughout all of blanking.
- Added video softswitch propagation delay: `SoftSwitches.java`, `VideoSoftSwitch.java`, `Video.java` — new `Video.scheduleModeChange()`/`applyDueModeChanges()` deferred-apply mechanism so video mode softswitch writes (TEXT, MIXED, PAGE2, HIRES, AN3/DHIRES, 80COL, ALTCHARSET, 80STORE) take effect after a hardware-measured number of CPU cycles instead of instantaneously. Previously mode changes applied synchronously on write, causing beam-racing/split-screen programs (e.g. text/graphics window-split demos) to render mode boundaries one column too far left. Delay values derived from MAME PR #15247's `delayed_update()` call sites, adjusted for that PR's `m_delay_bias` term: TEXT/MIXED=2, PAGE2/HIRES/80STORE=1, AN3(DHIRES)/80COL/ALTCHARSET=0. Covered by new test `VideoModeDelayTest.java`.
- Fixed CPU dropped-interrupt bug: `MOS65C02.java`'s `processInterrupt()` and the `CLI` opcode handler both unconditionally cleared `interruptSignalled` regardless of the CPU's `I` (interrupt-disable) flag. On real 6502/65C02 hardware `/IRQ` is a level-held line — an interrupt that arrives while masked (e.g. during `SEI`) must stay pending and be serviced on the next `CLI`, not be silently dropped. Fixed so `interruptSignalled` is only cleared once the interrupt is actually serviced. Found incidentally while investigating an unrelated hang in a Mockingboard-timer-driven test program. Covered by new test `MOS65C02Test.testMaskedInterruptStaysPendingUntilUnmasked`.
- Fixed Mockingboard envelope generator pitch: `EnvelopeGenerator.stepsPerCycle()` returned 8, identical to the tone generator's `stepsPerCycle()`. On real AY-3-8910 hardware the envelope counter advances at half the rate of the tone counter for the same period register value (confirmed against MAME's ay8910.cpp `m_step=2` for classic AY-3-8910, vs `m_step=1` for the later YM2149), matching the datasheet formulas (tone freq = clock/(16×TP), envelope freq = clock/(256×EP)). This bug made envelope-modulated notes play a full octave too sharp. Fixed by changing `EnvelopeGenerator.stepsPerCycle()` to return 16. Covered by new test `EnvelopeGeneratorPeriodTest.java`.
- Added "Running the Java Unit Test Suite (`mvn test`)" section (this is unrelated to the
  terminal-automation workflow above — it's for JUnit tests under `src/test/java`)
- Documented a stale-class gotcha: editing a file outside the Edit tool (sed, mv, git
  stash/pop) can leave Maven's incremental compiler serving old `.class` output even after
  the source file visibly shows the fix; always `mvn clean test` to verify a source change
  actually took effect before trusting a test result
- Documented that a full `mvn clean test` run takes several minutes under JaCoCo — run it
  as a background task with a generous timeout, don't poll aggressively, and check for an
  already-running `surefire` process before launching a second one
- Recorded the current list of known pre-existing test failures (`CardSSCTest`,
  `CardSSCRegisterTest`, four `TerminalFeatureTest` methods) so future agents don't mistake
  baseline-broken tests for regressions caused by their own change, and documented the
  `git stash` isolation technique used to verify this

### 2026-03-02
- Added "Running Jace: Native Binary vs Maven" section documenting `/Users/brobert/Downloads/Jace` (Gluon GraalVM native binary)
- Native binary finding: `headless` parameter is NOT supported, runs with display window, throws harmless `MacAccessible` error but boots normally
- Noted native binary does not support terminal/scripting mode; Maven required for automation
- Added "Tokenizing Applesoft BASIC Programs" section documenting the built-in `ApplesoftProgram.fromString()` Java API; clarified there is no terminal command for tokenizing, and described the inject-then-`savebin` workflow to produce a raw binary file

### 2026-07-27
- Added "Mockingboard / AY-3-8910 Sound Emulation" section: the 1022727 vs 1020484 clock
  distinction, the 6522 IER/IFR flag-vs-pin rule, why AY reset writes $FF to register 7,
  the AND (not OR) tone/noise mix, period-0 semantics, and the verified-correct areas
  (all 16 envelope shapes, the noise LFSR, register 7 polarity) that must not be churned
- Documented two test-setup gotchas: `Utility.setHeadlessMode(true)` before anything that
  reaches `R6522.tick()`/`Emulator.withComputer`, and `CardMockingboard.VolTable` being a
  lazily-built static that needs `buildMixerTable()` in a `@BeforeClass`
- Noted that `Pt3PlayerRegisterTest` register-frame comparison cannot detect PSG core
  defects — identical register values can still produce wrong audio
- Fixed the AY oscillator clock: `CardMockingboard.CLOCK_SPEED` was `TimedDevice.NTSC_1MHZ`
  (1020484, the CPU's stretched-cycle average), making every tone ~3.8 cents flat on top of
  the ~0.22% Jace already loses by running the whole machine at that average. Now 1022727 =
  `14318181.8 / 14`, the slot bus clock a real Mockingboard's AY receives. The global
  stretched-average inaccuracy is documented as knowingly accepted. Covered by
  `MockingboardClockTest` (7 tests), which pins *both* numbers and records each one's
  derivation so they cannot be mistaken for rival estimates of the same quantity — this
  constant had been changed three times by three people
- Fixed 6522 IFR semantics: `R6522.tick()` only set the T1/T2 interrupt flags when the
  corresponding IER enable was set, so software polling IFR with interrupts disabled (a
  documented card-detection idiom) would spin forever. Flags are now set by the interrupt
  condition unconditionally, per MAME `6522via.cpp t1_tick()`, and IER is consulted only
  where it belongs: asserting the IRQ pin and computing IFR bit 7 (`m_ier & m_ifr & 0x7f`,
  per `output_irq()`). Also removed a dead `R6522.SPEED` constant that was never read.
  Covered by new `R6522InterruptFlagTest` (7 tests, 5 of which fail against the old code)
- Added characterization coverage for previously untested areas, all of which passed
  unmodified — reported as verified-correct rather than fixed: `R6522TimerModeTest`
  (10 tests: T1CH start+load vs T1LH, one-shot vs free-run, period = latch+1, counter and
  latch readback, T2 always one-shot) and `DualAyAddressingTest` (8 tests: $00/$80 base
  registers, per-chip latching, no cross-talk, independent reset, shared clock)
- Replaced the synthesized uniform 3 dB/step amplitude curve with Westcott's measured
  AY-3-8910 levels, and documented that the chip is an AY-3-8913 with 16 levels shared by
  tone and envelope (previously undocumented, and load-bearing for the table's size).
  Covered by new `VolumeTableTest` (9 tests)
- Verified against MAME and left unchanged: all 16 envelope shapes (33-sample level
  sequence identical despite a structurally different formulation), register 13 restarting
  the envelope while 11/12 do not, the noise LFSR (Galois vs MAME's Fibonacci — same
  m-sequence, differing only in phase and polarity), and register 7's active-low polarity

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
