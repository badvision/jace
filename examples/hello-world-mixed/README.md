# hello-world-mixed — Mixed BASIC + Assembly via DATA/POKE/CALL

## What this demonstrates

This example shows how to embed a 6502 assembly routine inside an Applesoft BASIC
program using the **DATA + POKE + CALL** pattern:

1. **DATA statements** hold the raw machine-code bytes as decimal values (0–255).
2. A **FOR/READ/POKE loop** writes each byte to a memory address ($300 / 768).
3. **CALL 768** jumps into the embedded routine. `CALL` acts like `JSR` — it pushes a
   return address onto the stack, so the routine must end with `RTS` to pop that address
   and return to BASIC.

This technique is useful when you want a self-contained BASIC program that embeds
performance-critical assembly code without needing external files or disk images.

## Files in this directory

| File | Purpose |
|------|---------|
| `hello-mixed.bas` | Applesoft BASIC program with embedded assembly bytes |
| `hello-mixed.asm` | Reference 6502 assembly (same routine, for documentation & byte verification) |
| `hello-mixed-test.sh` | Automated test: runs the BASIC program in JACE, verifies output |

## How to run it

### Manual test (interactive)

```bash
# Start JACE in terminal mode
mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal"

# In the JACE terminal:
reset
loadbasic /path/to/hello-mixed.bas
run basic
```

Expected output: `HELLO WORLD!` followed by a CR. The routine returns to BASIC via RTS.

### Automated test

```bash
./hello-world-mixed/hello-mixed-test.sh
```

Exit codes: 0 = pass, 1 = fail, 2 = ACME missing, 124 = emulator hang.

## How the DATA+POKE pattern works

### Step 1 — The assembly routine (hello-mixed.asm)

```asm
* = $300
    ldx #0
loop
    lda msg,x       ; load character from msg table
    beq done        ; if zero terminator, jump to RTS
    jsr $FDED       ; COUT: print A-register character
    inx             ; advance index
    bne loop        ; repeat
done
    rts             ; return to BASIC (CALL pushes return address — RTS pops it)
msg
    !text "HELLO WORLD!"
    !byte $0D
```

This is a 27-byte routine at address $300. The message table lives inline after the
code, so no absolute address calculation is needed — `LDA msg,X` works because ACME
resolves `msg` to $30E.

### Step 2 — Extract bytes for BASIC DATA

Assemble with ACME and strip the 4-byte Apple header:

```bash
acme -f apple -o out.apple hello-mixed.asm
tail -c +5 out.apple | xxd -p    # raw hex bytes
```

Convert to decimal values (0–255) for BASIC DATA statements. The resulting values are:

```
162, 0, 189, 14, 3, 240, 6, 32, 237, 253,   ; LDX #0 ... JSR $FDED
232, 208, 245, 96, 72, 69, 76, 76, 79, 32,   ; INX BNE RTS "HELLO W"
87, 79, 82, 76, 68, 33, 13                  ; ORLD! CR
```

### Step 3 — Embed in BASIC (hello-mixed.bas)

```basic
10 FOR I=0 TO 26
20 READ B:POKE 768+I,B
30 NEXT I
40 CALL 768
100 DATA 162, 0, 189, 14, 3, 240, 6, 32, 237, 253
110 DATA 232, 208, 245, 96, 72, 69, 76, 76, 79, 32
120 DATA 87, 79, 82, 76, 68, 33, 13
```

The FOR loop reads each value and POKEs it to consecutive memory locations starting at
$300 (768 decimal). After all bytes are loaded, `CALL 768` executes the routine. Because `CALL` acts like
`JSR`, it pushes a return address onto the stack. The routine ends with `RTS` ($60), which
pops that address and returns control to BASIC.

### Step 4 — Byte verification (optional)

The test script (`hello-mixed-test.sh`) optionally verifies that the DATA values in the
BASIC program match the assembled output from the reference `.asm` file. If ACME is
available and `hello-mixed.asm` exists, the test assembles it and diffs the bytes.

## Address reference

| Symbol | Value | Meaning |
|--------|-------|---------|
| $300 | 768 | Load address for embedded routine |
| $FDED | — | Apple II ROM COUT (console output) |
| $30E | 782 | Message table start (code + 14 bytes) |

## Notes

- The routine is self-contained: code, data, and control flow all live within the 27
  bytes. No external references needed.
- **CALL acts like JSR**: it pushes a return address onto the stack. The ML routine must
  end with `RTS` ($60) to pop that address and return to BASIC.
- An infinite loop (`jmp done`) is an ALTERNATIVE only when you never want BASIC to resume
  execution after the routine. It does not use the stack at all.
- **BRK must NOT be used**: it triggers a hardware interrupt and crashes the Apple II
  (enters the monitor). BRK is for debugging halts, not for normal program termination
  when called via CALL.
- Apple II BASIC DATA values must be in the range 0–255 (one byte each). Multi-byte
  addresses must be split into high/low bytes if needed.
