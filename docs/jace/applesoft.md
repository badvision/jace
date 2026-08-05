# JACE: Applesoft BASIC From Terminal Mode

Loaded on demand from `CLAUDE.md`.

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

