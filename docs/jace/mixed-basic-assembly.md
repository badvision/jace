# JACE: Mixed BASIC + Assembly Programs (DATA+POKE Pattern)

Loaded on demand from `CLAUDE.md`.

This doc covers building programs that combine Applesoft BASIC with embedded 6502 machine code — loading raw bytes into memory at runtime via the `DATA`/`READ`/`POKE` pattern, then calling them with `CALL`.

For pure assembly, see `docs/jace/assembly-quickstart.md`. For Applesoft BASIC reference, see `docs/jace/applesoft.md`. For advanced patterns (multiple routines, parameter passing), see `docs/jace/mixed-basic-assembly-advanced.md`.

## The DATA+POKE Pattern — Overview

Applesoft BASIC can embed raw bytes in a program using `DATA` statements. At runtime, the interpreter reads those bytes with `READ` and places them into memory via `POKE`. After loading, `CALL <addr>` transfers control to the machine code.

```
10 REM --- Machine code data (raw bytes) ---
20 DATA 169,80,32,237,253,46,255,208,253,96
30 REM --- Load them into memory ---
40 FOR I=0 TO 9: READ B: POKE 800+I,B: NEXT I
50 CALL 2048
60 DATA 0   : REM filler so READ does not crash on the final line
```

Line 10–20: `DATA` holds raw decimal byte values.
Line 30–40: A loop reads each byte and `POKE`s it to `$800+$I`.
Line 50: `CALL 2048` (decimal, i.e. $800) executes the code at `$800`.
Line 60: A trailing `DATA 0` prevents a "Redo from start" error when `READ` reaches end-of-DATA.

## Step-by-Step: Building a Mixed Program

### Step 1 — Write the assembly routine

Write your 6502 routine as usual (see `docs/jace/assembly-quickstart.md`). Use `$800` or another safe address for loading. **When called via `CALL`, the routine must end with `RTS`** to return control to BASIC. An infinite loop (`jmp done`) is only correct when launching from the Wozniak monitor with `addrG` (see Launch Methods below).

Example (`hello.asm`):
```asm
* = $800
    ldx #0
next
    lda msg,x
    beq done
    jsr $FDED     ; COUT — Apple II console output
    inx
    jmp next
done
    rts
msg:
    !text "HELLO WORLD"
    !byte $0D
```

### Step 2 — Assemble and extract raw bytes

Assemble with ACME (apple format), then strip the 4-byte header to get raw code only:

```bash
acme -f apple -o hello.apple hello.asm
tail -c +5 hello.apple > hello.bin    # strip header
xxd -p hello.bin | tr -d '\n'        # hex string of raw bytes
```

For the 29-byte hello world, `xxd` produces:
```
a200ad0080f00320edfd4cf0ff4c0fa2000648454c4c4f20574f524c440d
```

### Step 3 — Convert bytes to decimal DATA values

Each byte in the raw binary becomes one decimal value in a `DATA` statement. Use a helper script or command:

```bash
xxd -p hello.bin | fold -w2 | while read hex; do printf "%d," 0x$hex; done
# Output: 162,0,173,0,128,240,3,32,237,253,76,240,255,76,15,240,255,162,0,6,72,69,76,76,79,32,87,79,82,76,68,13,
```

Paste these into a `DATA` line in your BASIC program.

### Step 4 — Write the BASIC loader and launcher

```basic
10 REM === Machine code for hello world ===
20 DATA 162,0,173,0,128,240,3,32,237,253,76,240,255,76,15,240,255,162,0,6,72,69,76,76,79,32,87,79,82,76,68,13
30 REM === POKE into memory at $800 ===
40 FOR I=0 TO 32: READ B: POKE 800+I,B: NEXT I
50 CALL 2048
60 DATA 0   : REM filler after final DATA
```

### Step 5 — Test in JACE

```
reset
lbas hello-mixed.bas
run basic
expect "HELLO WORLD" 10
showtext
qq
```

## The Hello World! Mixed Example

The reference implementation lives at `examples/hello-world-mixed/`. It demonstrates the complete flow:

- `hello.asm` — the 6502 assembly routine (same as pure-assembly version)
- `build.sh` — ACME assemble + header strip + byte-to-decimal conversion
- `hello-mixed.bas` — the final BASIC program with embedded DATA
- `test.sh` — full end-to-end JACE test

Run the acceptance test:
```bash
./examples/hello-world-mixed/test.sh
```

## Memory Layout Considerations

### Where to load machine code

| Address | Why | Notes |
|---------|-----|-------|
| `$800`–`$BFFF` | Safe for most programs | Above BASIC's program area (LOMEM defaults to $0800) |
| `$C000`–`$FFFF` | I/O and ROM space | **DO NOT use** — conflicts with softswitches ($C000+) and ROM ($C000–$FFFF) |
| Below LOMEM | BASIC program area | Overwrites your BASIC program; only use if you explicitly set LOMEM high enough |

### Setting LOMEM and HIMEM

```basic
LOMEM: $2000    : REM move BASIC program area up, freeing $0800–$1FFF for ML
HIMEM:  $BFFF   : REM limit BASIC heap to avoid stepping on your code
```

When using DATA+POKE, you must manually ensure the POKE target does not overlap with BASIC's variable table or string space. Set `LOMEM` high enough that BASIC cannot grow into your ML area.

### What to avoid

- **Do not POKE over `$0400`–`$07FF`** — this is the text screen. Overwriting it corrupts what the user sees.
- **Do not POKE over `$C000`+** — softswitches live here; POKEing changes hardware state.
- **Do not let ML code fall through into BASIC memory** — when using `CALL`, end with `RTS` to return to BASIC. When launching from the monitor with `addrG`, end with `jmp done` (infinite loop). If execution runs past your routine, it will corrupt or crash.

## Common Pitfalls and Gotchas

### 1. Off-by-one in DATA count

If your `FOR I=0 TO N` loop does not match the number of `DATA` values, BASIC throws "Redo from start" or reads garbage. Count bytes carefully:

```bash
wc -c hello.bin    # gives exact byte count; FOR I=0 TO <count-1>
```

### 2. Missing trailing DATA filler

When `READ` reaches the last value in DATA, it raises an error unless there is a trailing `DATA 0`. Always include it:

```basic
50 ... : REM your code DATA values
60 DATA 0   : REM required filler — READ will hit this after last real byte
```

### 3. Hex vs decimal confusion

`POKE` takes **decimal** values. `DATA` stores **decimal** values. The assembly source uses hex (`$A9`, `$FDED`). Convert carefully:

- `$A9` → `169` (decimal)
- `$FDED` → `65005` (decimal) — but this is a JSR target, not a byte value
- `$0D` → `13` (decimal)

### 4. CALL address must be decimal

```basic
CALL 800    : REM correct — decimal 800 = $0320? NO! This is decimal 800 = $320
CALL 2048   : REM correct — decimal 2048 = $800 (the common load address)
```

**Critical:** `CALL` in Applesoft BASIC takes a **decimal** argument, not hex. `$800` in assembly = `2048` in BASIC's `CALL`. This is a frequent source of bugs.

### 5. ML code must preserve/restore registers

BASIC does not save any CPU state before `CALL`. If your routine modifies A, X, Y, or the stack pointer and expects to return cleanly, you must restore them. For `CALL`, end with `RTS` (not an infinite loop). The infinite-loop approach only works when launching via monitor `addrG`.

### 6. BASIC string storage overlaps

Applesoft stores strings in a memory area that grows upward from the variable table. If your POKE target is below HIMEM, BASIC's garbage collector may overwrite it with string data. Set `HIMEM` above your ML code or keep strings short.

### 7. The `]` prompt, not "READY."

When running mixed programs in JACE via `lbas` + `run basic`, the interpreter shows a `]` prompt. There is NO "READY." banner — that is C64 BASIC. Never `expect "READY"` in your test scripts. See `docs/jace/applesoft.md`.

### 8. JSR $FDED for output, not $C000

The Apple II console output routine (COUT) is at `$FDED` (`JSR $FDED`). Do **not** use `$C000` — that is a softswitch address on the C64, not an Apple II. JACE is an Apple II. See `docs/jace/assembly-quickstart.md`.


## Launch Methods: CALL vs addrG

This distinction is critical — using the wrong method causes crashes.

| Method | How it works | Routine must end with | Use case |
|--------|-------------|----------------------|----------|
| `CALL 2048` (BASIC) | Pushes return address on stack, jumps to $800 | `RTS` | Embedded ML called from BASIC |
| `800G` (monitor) | Sets PC directly to $800, no push | `jmp done` (infinite loop) | Standalone ML program from terminal |

**`CALL 2048`** is what you use in the DATA+POKE pattern above. The Wozniak monitor's CALL pushes a return address onto the stack, so the routine must end with `RTS`.

**`800G`** (or any `<addr>G`) sets the program counter directly without pushing anything. There is no return address on the stack, so `RTS` would jump to garbage. Use `jmp done` instead.

**Common mistake:** Writing an ML routine with `jmp done` and then calling it via `CALL`. The CPU never returns to BASIC — your BASIC program hangs because CALL waits for RTS that never comes.

## Quick Reference: Byte Conversion

| Hex | Decimal | Meaning |
|-----|---------|---------|
| `$A9` | 169 | LDA immediate |
| `$00` | 0 | BRK (break) |
| `$20` | 32 | JSR |
| `$ED` | 237 | $FDED low byte (COUT) |
| `$FD` | 253 | $FDED high byte (COUT) |
| `$4C` | 76 | JMP absolute |
| `$0D` | 13 | CR character |
| `$FC` | 252 | JACE debug print opcode |

## See Also

- `docs/jace/assembly-quickstart.md` — ACME syntax, hello world asm, run-verified facts
- `docs/jace/applesoft.md` — Applesoft commands, CALL, DATA, POKE, memory addresses
- `docs/jace/advanced-assembly.md` — Zero-page strategies, Apple //e memory mapping
- `docs/jace/debugging-guide.md` — `$FC` debug opcodes for ML debugging
- `docs/jace/mixed-basic-assembly-advanced.md` — Multiple routines, parameter passing, templates
