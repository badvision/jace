# JACE Terminal Command Reference

Loaded on demand from `CLAUDE.md`. Complete syntax and semantics for every terminal-mode
command in `MainMode.java` and `MonitorMode.java`.

**Built-in `help <cmd>` is wrong in places.** Verified discrepancies are called out inline:
`run` (claims cycles, is wall-clock), `move`/`compare` (help text was outdated).
Where this file and `help` disagree, this file was checked against the implementation.

**Numeric argument parsing:** Terminal commands uniformly accept `$` prefix to mark a number
as hexadecimal (e.g., `$800` = 2048 decimal). Monitor mode commands default to hex for all
memory-related values (addresses, bytes, counts), consistent with Wozniak monitor conventions.
Main mode commands typically default to decimal for counts and timeouts. The prefixes `x` or
`0x` are NOT accepted (use `$` instead for clarity and Apple II convention).

## Contents

- **Booting and loading** — `bootdisk`, `insertdisk`/`ejectdisk`, `loadbin`/`savebin`,
  `saveauxbin`/`saveauxrambin`, `loadbasic`, `reset`
- **Running and stepping** — `run` (**read this: not cycle-accurate**), `step`, `tick`,
  `runto`, `runvbl`, `go`, `<addr>G`, `speed`
- **Observing** — `showtext`, `screenshot`, `mem`, `memaux`/`memmain`, `cmpmem`, `cpu`,
  `registers`, `swstate`, `swlog`, `cycles`
- **Input** — `key`, `type`, `waitkey`, `expect`
- **Debugging** — `break`/`breaklist`, `watch`/`watchlist`, `cheat`/`cheatlist`, `symbols`,
  `poke`, `rdb`, `charlog`
- **Monitor mode** — Wozniak pattern syntax, `M`/`X` bank prefixes, `fill`, `move`,
  `compare`, `find`

### Alias collisions that bite

| Alias | Main mode | Monitor mode |
|---|---|---|
| `ss` | `swstate` — screenshot is **`ss2`** | — |
| `cl` | `charlog` | `cheatlist` |
| `b` | — | `break`, **not** `back` (use `q` to leave) |
| `m` | `monitor` | `move` |
| `d` | `debugger` (→ monitor mode) | — |
| `g` | `run` | — |

## Terminal Modes

The terminal is a REPL with three modes:

- **Main mode** (default, prompt `JACE>`) — system control, disks, automation
- **Monitor mode** (`monitor`/`m`; leave with `q`, `quit`, or `back`) — memory and debugging
- **Assembler mode** (`assembler`/`a`) — assembly language input

Type `?` or `help` for the command list, `help <cmd>` for one command. Most monitor commands
and all Wozniak pattern syntax also work directly from the main prompt, so a mode switch is
rarely needed. `qq` exits the terminal; `qqq` (or `exit!`) terminates the JVM.

## Core Automation Commands

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

### `symbols` / `sym` - Load Assembler Labels So Commands Accept Names

```
symbols <labelfile>     # Load symbols (merges with anything already loaded)
symbols                 # List loaded symbols and their addresses
symbols clear           # Forget all loaded symbols
```

**This is the highest-leverage command for debugging an assembled project.** Once a label
file is loaded, *any* command that takes an address accepts a label name instead of hex:

```
symbols /path/to/build/labels.txt
break mainloop
runto after_draw
go entry
mem frame_count frame_count
memaux sprite_buffer sprite_buffer
```

Two input formats are accepted, matching ACME's two emitters:

| ACME flag | Line format |
|---|---|
| `acme --symbollist FILE` | `name = $XXXX` |
| `acme --vicelabels FILE` | `al C:xxxx .name` |

Hex input is unchanged and always wins. A label whose name is *also* valid hex (e.g. a
label literally called `abcd`) must be written with a leading colon: `:abcd`. Unknown or
ambiguous names fail with a message — they never silently resolve to `$0000`.

### `speed` / `sp` - Throttle Control

```
speed max       # Remove the throttle; run as fast as the host CPU allows
speed normal    # Restore the ~1 MHz throttle
```

Calls `Motherboard.setMaxSpeed()`. Relevant to any wall-clock-based command: under
`speed max` a `run N` covers far more emulated cycles in the same real time, and `expect`
reaches its output sooner. Use `speed max` to shorten long boots, then `speed normal`
before anything timing-sensitive.

### `swstate` / `ss` - Show Softswitch State

```
swstate                 # Dump every softswitch and its ON/OFF state
swstate <SWITCH_NAME>   # Show just one, e.g. swstate HIRES
```

The name is a `jace.apple2e.SoftSwitches` enum constant (`TEXT`, `MIXED`, `PAGE2`,
`HIRES`, `_80COL`, `_80STORE`, `DHIRES`, `ALTCHARSET`, `AUXREAD`, `AUXWRITE`, ...) and is
upper-cased for you. Unknown names print `Unknown softswitch: <name>`. This is the
reliable way to confirm the display configuration — more direct than reading `$C01A`
and friends, and it does not touch the switches.

**Alias caution:** `ss` is `swstate`. The screenshot command is `ss2`, not `ss`.

### `swlog` / `sl` - Log Softswitch Changes

```
swlog       # toggle
```

Installs a `RAMListener` over `$C000-$C0FF` and prints softswitch state *transitions* as
they happen. Call again to disable. Useful for catching an unexpected mode change (e.g.
something clearing `HIRES` mid-frame); very chatty, so bracket it narrowly.

### `poke` - Write Bytes Bypassing Write Protection

```
poke <addr> <byte> [byte ...]
```

All values hex. Unlike the monitor's `<addr>:<val>` syntax, `poke` writes **directly into
the backing page array** obtained from `activeRead.getMemoryPage(addr)`, so it bypasses
write-protection entirely — **it can patch ROM and a write-protected Language Card bank.**
Because it goes to the `activeRead` page, it patches whatever is currently *readable* at
that address, which is exactly what you want for ROM patching.

It fires no RAM listeners, so watches and cheats will not see the write. Prints
`No page at <hex>` for an unmapped address.

```
poke FB39 60        # patch the non-returning ROM routine at $FB39 to an RTS
```

### `cmpmem` / `cm` - Compare Memory Against an Expected Byte List

```
cmpmem <aux|main|active> <addr> <byte> [byte ...]
```

Reports **only the differences**, which makes it far more usable than eyeballing two hex
dumps. Each mismatch prints its offset, absolute address, expected byte and actual byte;
a clean run prints a match count.

Bytes are hex, separated by spaces *or commas*, optionally `$`-prefixed — so the output of
`memaux <s> <e> --raw` or `--csv` can be pasted straight back in as the expected list.
`aux`/`main` read the physical bank regardless of softswitch state; `active` follows the
current mapping.

```
cmpmem aux 2000 D5 AA 96 FF
```

### `saveauxbin` / `sab` and `saveauxrambin` / `sarb` - Save AUX Memory

```
saveauxbin    <filename> <address> <size>    # AUX *video* memory
saveauxrambin <filename> <address> <size>    # full AUX RAM bank
```

Both are the AUX counterparts of `savebin`. Address and size may be decimal, or hex with a
`$` or `0x` prefix.

- `saveauxbin` reads auxiliary **video** memory — use it for the aux DHGR page at `$2000`.
- `saveauxrambin` reads the full AUX RAM bank (`getAuxMemory()`), covering **all**
  addresses including `$6000+`. Use this one when the data you want is outside the video
  pages.

For DHGR verification you generally want both halves:

```
runvbl
savebin    /tmp/dhgr_main.bin 2000 2000
saveauxbin /tmp/dhgr_aux.bin  2000 2000
```

### `nohints` - Disable the Apple //e Helpful-Hints Overlay

```
nohints
```

Sets `Apple2e.enableHints = false` and reconfigures. Apple //e only; prints
`This command is only supported on Apple //e` otherwise. Use it when hint overlays would
contaminate a screenshot or `showtext` capture.

### `charlog` / `cl` - Log Z-Machine Character Output

```
charlog       # toggle
```

Special-purpose: installs an execute listener at `$5DA3` (a Z-machine character writer) and
a write listener over `$0280-$02FF`, printing `CHAR: 0xNN 'C'` and `WRITE $XXXX = 0xNN`.
Only meaningful when running that specific Z-machine interpreter. Call again to disable.

### `rdb` / `cy` / `cyrene` - Aristaeus Remote Debugger

```
rdb start | rdb stop | rdb status
```

Starts a TCP debug server on **port 57867** for the
[Aristaeus](https://github.com/badvision/aristaeus) GUI debugger — registers, memory, soft
switches, breakpoints, stepping. Interactive human debugging, not agent automation. Keep
the machine cycling while connected (`run 999999999`).

### `assembler` / `a` and `debugger` / `d` - Mode Switches

```
assembler       # enter assembler mode (assembly language input)
debugger        # legacy alias — enters MONITOR mode, not a separate debugger
```

`debugger` exists only for backward compatibility; all debugging lives in monitor mode.

### `reset` - System Reset

```
reset
```

Performs a cold start reset of the Apple II.

## Monitor Mode Commands

Enter monitor mode with `monitor` (or `m`). Return to main mode with `back`, `quit`, or `q`.

**Note:** `b` is the alias for `break` (set breakpoint), NOT for `back`. Use `q` to exit monitor mode.

**Tip:** Wozniak monitor syntax (`4000G`, `3800.3820`, `E000G`, etc.) and named commands (`cpu`, `registers`, `break`, `runto`) work directly from the main `JACE>` prompt — no mode switch needed.

### Wozniak Monitor Pattern Syntax

Monitor mode recognises these argument-free patterns *in addition* to the named commands.
All addresses are 1-4 hex digits. Each pattern accepts an optional leading **bank prefix**
(see below). These also work directly from the main `JACE>` prompt.

| Pattern | Example | Effect |
|---|---|---|
| `<addr>` | `3800` | Examine a single byte: prints `3800: A9` |
| `<addr>.<addr>` | `3800.3820` | Hex-dump an inclusive range |
| `<addr>:<val> [<val>...]` | `3800:A9 FF 8D` | Write hex bytes starting at `<addr>` |
| `<addr>G` | `4000G` | Set PC to `<addr>` and start executing (**returns immediately**) |
| `<addr>L` | `4000L` | Disassemble 20 instructions starting at `<addr>` |
| `L` | `L` | Disassemble the next 20 instructions, continuing from the last `L` |

Notes:
- `<addr>:<val>` writes through the normal write path, so write-protection applies and RAM
  listeners fire. To patch ROM, use `poke` instead.
- `<addr>G` is **asynchronous** — it starts execution and returns to the prompt at once. You
  must follow it with `run N`, `expect`, `runvbl`, or `step` to actually advance the machine.
- A bare single-byte examine advances an internal "last examined" pointer.

#### `M` / `X` Bank Prefixes

Prefix any of the above patterns with `M` (MAIN bank) or `X` (AUX bank), case-insensitive,
to force the physical bank regardless of softswitch state:

```
X2000.2027      # dump AUX  $2000-$2027 (even DHGR pixel columns)
M2000.2027      # dump MAIN $2000-$2027 (odd DHGR pixel columns)
X4000:00 00     # write two zero bytes into AUX at $4000
X2000           # examine one byte of AUX
X4000G          # (prefix is parsed for G too)
```

With no prefix, the mode is monitor mode's current default (`ACTIVE` — follows the
softswitches). The prefixes are the monitor-mode equivalents of the `memaux`/`memmain`
main-mode commands. `<addr>L` and bare `L` (disassembly) do **not** take a bank prefix.

### Memory Examination
```
<addr>.<addr>           # Dump memory range (e.g., 3800.3820)
<addr>: <byte> ...      # Write bytes to memory
<addr>                  # Examine a single byte
<addr>L / L             # Disassemble 20 instructions / continue disassembly
```

### CPU Control
```
<addr>G                 # Execute from address (e.g., 4000G)
cpu                     # Show CPU state (PC, A, X, Y, SP, flags)
registers [reg value]   # Show or set registers (e.g., reg PC $4000)
step [count]            # Single-step instructions (alias: s) - EXACT, unlike `run`
runto <addr>            # Run until PC reaches address (alias: rt)
pause / resume          # Pause/resume emulation (aliases: p / r)
cycles                  # Show the CPU's pending wait-cycle count
```

`cycles` prints `CPU Wait Cycles: N` — how many cycles the CPU will stall before the next
instruction executes (nonzero mid-instruction or during a stretched cycle). It is a
read-only probe with no arguments; use it to confirm the CPU is at an instruction boundary
before a `step`.

### Breakpoints and Watches
```
break <addr>            # Set breakpoint (alias: b)
break -<addr>           # Remove breakpoint
break clear             # Remove all breakpoints
break                   # List all breakpoints (bare form)
breaklist               # List all breakpoints (alias: bl)
watch <addr> [name]     # Watch memory address for changes (alias: w)
watch -<addr>           # Remove watch by address
watch -<name>           # Remove watch by name
watch clear             # Remove all watches
watchlist               # List all watches (alias: wl)
```

`breaklist`/`watchlist` are just explicit spellings of the bare `break`/`watch` listing
forms; either works.

**Alias caution:** in monitor mode `b` is `break`, not `back`. Use `q` (or `quit`/`back`) to
leave monitor mode.

### Memory Operations
```
fill <start> <end> <value>      # Fill memory range (alias: f)
move <src> <dest> <count>       # Copy memory block (alias: m)
compare <src> <dest> <count>    # Compare memory blocks (alias: cmp)
find <start> <end> <byte> ...   # Search for byte pattern
```

**Argument radix and $ prefix support:** All monitor mode numeric arguments default to
hexadecimal, consistent with Wozniak monitor conventions. You may optionally prefix any
numeric argument with `$` to explicitly mark it as hex (e.g., `$800`).

| Command | Addresses | Count / value | Example |
|---|---|---|---|
| `fill <start> <end> <value>` | hex | hex (supports `$` prefix) | `fill 2000 2100 $FF` |
| `move <src> <dest> <count>` | hex | hex (supports `$` prefix) | `move 2000 4000 800` copies 2048 bytes |
| `compare <src> <dest> <count>` | hex | hex (supports `$` prefix) | `compare 2000 3000 $100` compares 256 bytes |
| `find <start> <end> <byte>...` | hex | hex (supports `$` prefix) | `find 2000 3000 $DE $AD` |

Note: `move 2000 4000 800` treats `800` as hex ($800 = 2048 bytes). To copy exactly 800
decimal bytes, write `move 2000 4000 $320` (since 0x320 = 800 decimal).

Other behaviour worth knowing:
- All four honour the current bank mode and accept `M`/`X`-prefixed addresses.
- `fill` requires `start <= end` and is inclusive of both ends.
- `move` reads the whole source into a buffer *before* writing, so overlapping ranges
  copy correctly in either direction.
- `move` reads without firing RAM listeners but **writes through the normal path**, so
  write-protection applies and watches fire on the destination.

### Cheats (Forced Memory Values)
```
cheat <addr> <value>    # Force reads of <addr> to always return <value> (alias: ch)
cheat -<addr>           # Remove the cheat at <addr>
cheat clear             # Remove all cheats
cheatlist               # List active cheats (alias: cl)
```

Implemented with a `RAMListener` that overrides the value on every read, so it survives the
program rewriting the location — unlike a one-shot `<addr>:<val>` poke. Address may carry an
`M`/`X` prefix; `<value>` is hex. Cheats are also recorded in a persistent collection, so
they can outlive a reconfigure. Occasionally useful for forcing a game state (lives, level)
to reach a screen quickly for capture.

**Alias caution:** `cl` is `cheatlist` **in monitor mode** but `charlog` **in main mode**.

### `debug` / `dbg` (Monitor Mode)

A stub. Prints "Debugger functionality is now integrated into monitor mode." and nothing
else. Kept for backward compatibility.

