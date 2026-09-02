# JACE: Mixed BASIC + Assembly — Advanced Patterns

Loaded on demand from `CLAUDE.md`.

Advanced companion to `docs/jace/mixed-basic-assembly.md`. Covers multiple routines, parameter passing, memory management strategies, and reusable templates.

For the basics (DATA+POKE pattern, single routine, hello world), see `docs/jace/mixed-basic-assembly.md`. For the underlying analysis of the DATA+POKE mechanism, see `docs/analysis/data-poke-analysis.md`.

## Multiple Machine Code Routines in One BASIC Program

You can embed multiple routines by assigning each a distinct load address and using separate POKE ranges.

### Strategy: Contiguous blocks with a dispatch routine

```basic
10 REM === Routine 1: Hello World at $800 (29 bytes) ===
20 DATA 162,0,173,0,128,240,3,32,237,253,76,240,255,76,15,240,255,162,0,6,72,69,76,76,79,32,87,79,82,76,68,13
30 FOR I=0 TO 32: READ B: POKE 800+I,B: NEXT I
40 REM === Routine 2: Beep at $A00 (5 bytes) ===
50 DATA 169,7,32,237,253,96   : REM LDA #$07; JSR $FDED; RTS
60 FOR I=0 TO 5: READ B: POKE 1024+I,B: NEXT I
70 REM === Dispatch ===
80 CALL 800    : REM Run hello world
90 CALL 1024   : REM Run beep
```

**Key rules:**
- Each routine needs its own `FOR I=0 TO N` range matching the byte count.
- Addresses must not overlap: `$800` + 33 bytes = `$821`, so `$A00` (2560) is safe.
- Routines that return (`RTS`) need a valid stack; park-in-loop routines do not.

### Strategy: Single dispatch routine

For many small routines, build one dispatcher in ML that branches to sub-routines:

```asm
* = $800
    lda param       ; Read parameter from zero-page location
    cmp #1
    beq run_hello
    cmp #2
    beq run_beep
    rts
run_hello
    jsr hello_world
    rts
run_beep
    jsr beep_sound
    rts

hello_world
    ; ... hello world code ...
    rts

beep_sound
    ; ... beep code ...
    rts

param: .byte 0
```

BASIC calls the dispatcher once with a parameter:

```basic
10 POKE 256,1   : REM $0100 = 1 (run hello)
20 CALL 2048    : ; ML reads from $0100
30 POKE 256,2   : REM $0100 = 2 (run beep)
40 CALL 2048
```

## Passing Parameters to Machine Code

Applesoft BASIC has no direct way to pass arguments to `CALL`. Use these patterns:

### Pattern 1: POKE before CALL (zero-page variables)

Set a zero-page memory location before calling. The ML routine reads it.

```basic
10 POKE 256,5    : REM $0100 = 5 (parameter value)
20 CALL 2048     : ; ML reads from $0100
```

In assembly:
```asm
lda $0100    ; A = parameter passed from BASIC
```

### Pattern 2: POKE before CALL (absolute addresses)

Same idea but at an absolute address above `$01FF`.

### Pattern 3: Use USR() for numeric return values

`USR(x)` calls ML code and returns a value to BASIC. The returned value is the accumulator (A) after the routine finishes.

```basic
10 X = USR(2048, 5)   : REM Pass 5 as argument, get result in X
```

In assembly:
```asm
* = $800
    ; X register holds the argument passed via USR()
    ; ... do computation ...
    clc              : REM Clear carry for clean return
    rts              : REM Returns A to BASIC; if A is 0, USR returns 0
```

**Limitation:** `USR()` only returns a single byte (A register). For multi-byte results, POKE the result to memory and PEEK it in BASIC.

### Pattern 4: Shared memory block for complex data

POKE an entire data block into ML-accessible memory before calling:

```basic
10 FOR I=0 TO 99: READ B: POKE 3000+I,B: NEXT I   : REM Load lookup table
20 POKE 256,50    : REM Set index = 50
30 CALL 2048      : REM ML reads table[PEEK(256)]
```

Assembly reads the data from the shared block and returns results via another memory location:

```basic
40 RESULT = PEEK(3100)    : REM Read result that ML POKE'd there
```

## Memory Management Strategies

### Strategy 1: Fixed load addresses with LOMEM/HIMEM

Set BASIC's memory boundaries to carve out a safe zone for machine code:

```basic
10 LOMEM: $2000   : REM BASIC program starts at $2000
20 HIMEM: $BFFF   : REM Heap stops here; ML can safely use $C000? NO — see below
30 REM ML routines load at $800, $A00, etc. — these are BELOW LOMEM now
```

**Problem:** If `LOMEM` is set to `$2000`, the POKE target `$800` is *below* BASIC's program area. This is actually fine for static ML code (BASIC won't write there), but it means you cannot use DATA+POKE with addresses below LOMEM unless you are careful that BASIC's variable table does not grow downward.

### Strategy 2: Load above HIMEM, let BASIC manage below

Set `HIMEM` below your ML code so BASIC never overwrites it:

```basic
10 HIMEM: $7FFF    : REM All ML above $8000 is safe from BASIC heap
20 REM POKE code to $8000+
30 FOR I=0 TO 99: READ B: POKE 32768+I,B: NEXT I
40 CALL 32768
```

This is the safest approach for large ML programs. The downside is that `$8000`+ addresses require negative notation in BASIC (e.g., `POKE -32768, B` for `$8000`).

### Strategy 3: Zero-page cooperation with BASIC

When ML code needs to share data with BASIC without POKE overhead, use zero-page locations that both sides agree on:

| Address | Owner | Purpose |
|---------|-------|---------|
| `$00`–`$0F` | OS/BASIC | System use — **DO NOT TOUCH** |
| `$10`–`7F` | BASIC runtime | Temporary variables — risky to use |
| `$80`–`FF` | User zero-page | Safe for ML↔BASIC communication if you avoid BASIC's temp vars |

In practice, `$80`–`$FF` is the recommended range for shared state between BASIC and ML.

### Strategy 4: Save/restore context around CALL

If your ML routine modifies registers that BASIC might care about (rare, but possible with `USR`), wrap the call:

```basic
10 REM Save A by POKEing to safe location
20 REM This is not actually needed — BASIC does not use A/X/Y across statements
30 CALL 2048
40 REM If using USR, ensure ML clears carry before RTS
```

**Note:** Applesoft BASIC does not preserve any CPU registers across `CALL` or `USR`. For `CALL`, end with `RTS` (not an infinite loop) to return control to BASIC. For `USR`, the return value is simply whatever is in A when `RTS` executes.

## Building Custom Programs: Template

Use this template as a starting point for new mixed programs:

### Assembly template (`myroutine.asm`)

```asm
* = $800                 ; Load address — change if needed

    ; === Entry point ===
    ; If using dispatch, read param from zero-page here
    ; lda $0080            ; Read shared parameter

    ; === Your code here ===
    ldx #0
loop
    lda msg,x
    beq done
    jsr $FDED            ; COUT
    inx
    jmp loop
done
    rts                    ; Return to BASIC (called via CALL)

msg:
    !text "MY MESSAGE"
    !byte $0D

; === End marker for byte count calculation ===
```

### BASIC template (`myprogram.bas`)

```basic
10 REM === Configuration ===
20 LOMEM: $0800           : REM Adjust if ML loads below this
30 HIMEM:  $BFFF          : REM Adjust if ML loads above this
40
50 REM === Machine code DATA (fill in after assembling) ===
60 REM Count = <N>        : REM Update with actual byte count
70 DATA ...               : REM Raw bytes from xxd/od conversion
80
90 REM === POKE into memory ===
100 FOR I=0 TO <N-1>: READ B: POKE <ADDR>+I,B: NEXT I
110
120 REM === Run it ===
130 CALL <ADDR>
140
150 DATA 0                 : REM Required filler for READ
```

### Build script template (`build.sh`)

```bash
#!/bin/bash
set -e

ASM="myroutine.asm"
OUT="myroutine.apple"
BIN="myroutine.bin"
BASIC="myprogram.bas"

# Assemble
acme -f apple -o "$OUT" "$ASM"

# Strip 4-byte Apple header → raw binary
tail -c +5 "$OUT" > "$BIN"

# Count bytes
BYTES=$(wc -c < "$BIN")
echo "Raw code: $BYTES bytes"

# Generate DATA line (decimal values)
DATA_LINE=$(xxd -p "$BIN" | fold -w2 | while read hex; do printf "%d," 0x"$hex"; done)

# Update BASIC file
sed -i '' "s/^70 DATA .*/70 DATA $DATA_LINE/" "$BASIC"
sed -i '' "s/^100 FOR I=0 TO.*/100 FOR I=0 TO $((BYTES-1)): READ B: POKE 2048+I,B: NEXT I/" "$BASIC"

echo "Updated $BASIC with $BYTES bytes of machine code at address $800 (2048 decimal)"
```

### JACE test template (`test.sh`)

```bash
#!/bin/bash
set -e

cd "$(dirname "$0")/.."

timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" <<'EOF'
reset
lbas myprogram.bas
run basic
expect "MY MESSAGE" 10
showtext
qq
EOF

status=${PIPESTATUS[0]}
if [ $status -eq 0 ]; then
    echo "PASS: MY MESSAGE confirmed on emulated text screen"
else
    echo "FAIL: exit code $status (124 = hang)"
    exit $status
fi
```

## Common Advanced Pitfalls

### Pitfall 1: RTS with `addrG` (monitor mode) instead of `CALL`

If a routine does `RTS` but was launched via the Wozniak monitor's `<addr>G` command, the return address on the stack is garbage. The CPU will jump to whatever bytes happen to be on the stack and likely crash.

**Fix:** Use `CALL <decimal_addr>` from BASIC (which pushes a proper return address), not `<addr>G` from the monitor — if you need the routine to return. If you must use `<addr>G`, end with `jmp done` instead of `rts`:

```asm
done
    jmp done           ; Safe for addrG launch — no RTS needed
```

**Launch method summary:**
| Launch method | Pushes return addr? | Routine ends with |
|--------------|---------------------|-------------------|
| `CALL 2048` (BASIC) | Yes | `RTS` |
| `800G` (monitor) | No | `jmp done` |

See also the Launch Methods section in `docs/jace/mixed-basic-assembly.md`.
```

### Pitfall 2: DATA values exceed 255

Applesoft `POKE` and `DATA` only handle 0–255. If your hex conversion produces a value > 255, you have a bug (likely a multi-byte address being treated as a single byte).

**Fix:** Verify each byte with:
```bash
xxd -p "$BIN" | fold -w2 | while read hex; do val=$((16#$hex)); if [ $val -gt 255 ]; then echo "ERROR: $hex > 255"; fi; done
```

### Pitfall 3: BASIC string allocation overwrites ML

If your program uses strings (e.g., `INPUT`, `PRINT` with variables), BASIC's string storage may grow into your ML zone. Set `HIMEM` above all ML code and avoid large string operations after loading ML.

**Fix:** Load all ML before any string operations, or set `HIMEM` very high:
```basic
10 HIMEM: $FFFF   : REM Maximize space for strings below ML
```

### Pitfall 4: ACME label conflicts with BASIC line numbers

ACME labels are case-insensitive and may conflict with BASIC keywords if you accidentally reference them. This is rare but worth noting.

**Fix:** Use descriptive labels (`hello_msg`, `cout_addr`) rather than single letters that might clash.

### Pitfall 5: Forgetting the Apple header strip

The `-f apple` format adds a 4-byte header (load address + length). If you POKE this header into memory, your routine will execute garbage. Always strip it:

```bash
tail -c +5 hello.apple > hello.bin    # Strip 4-byte header
```

## See Also

- `docs/jace/mixed-basic-assembly.md` — DATA+POKE basics, single routine, hello world
- `docs/jace/assembly-quickstart.md` — ACME syntax, COUT, run-verified facts
- `docs/jace/applesoft.md` — CALL, USR, POKE, LOMEM/HIMEM, memory address literals
- `docs/jace/debugging-guide.md` — `$FC` debug opcodes for ML debugging
- `docs/analysis/data-poke-analysis.md` — Analysis of the DATA+POKE mechanism
