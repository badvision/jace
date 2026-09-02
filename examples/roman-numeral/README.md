# Roman Numeral Converter

A 6502 assembly program (ACME syntax) for the Apple II. It prints a prompt,
reads an integer from the keyboard, and prints the corresponding Roman numeral
on the text screen. Range is 1..3999; anything else prints `*`.

Verified against the JACE 65C02 emulator in this repo (see the test script
below for a captured transcript).

## What it does

1. Prints `NUMBER: ` (its own prompt, char by char via COUT).
2. Reads a line from the keyboard with the ROM line editor.
3. Parses the ASCII digits into a 16-bit integer `n`.
4. Converts `n` to a Roman numeral (greedy table walk) and prints it.

Example session:

```
NUMBER: 1994
MCMXCIV
```

## Files

- `roman-numeral.asm` -- the source (code + data tables), self-documenting header.
- `roman-numeral-test.sh` -- assembles to a temp dir, then runs all 8 cases
  (each in its own fresh JACE machine) and verifies them.
- `last-run-transcript.txt` -- combined transcript from the most recent test run.
- `README.md` -- this file.

## How to build and run

From the `jace/` repo root:

```
./examples/roman-numeral/roman-numeral-test.sh
```

This assembles with ACME, strips the 4-byte Apple II header, then runs each
case (1994, 1456, 44, 8, 1, 3999, plus the out-of-range 0 and 4000) in its own
fresh JACE machine (one JVM per case, a few in parallel). Each case waits for
the Apple //e boot banner (`expect "Apple //"`) before jumping to `$800`, so
the emulator is in its interruptible slot-6 disk-poll state and the `800G` start
is deterministic rather than racy against the mid-boot ROM. It exits 0 only if
every case's `expect` matches and none times out or hangs.

To run it interactively instead (watch yourself type a number):

```
acme -f apple -o /tmp/rn.apple examples/roman-numeral/roman-numeral.asm
tail -c +5 /tmp/rn.apple > /tmp/rn.bin
# then in a JACE terminal:
#   reset
#   loadbin /tmp/rn.bin 800
#   800G
# (type a number, press Enter)
```

## Key Apple II / ACME facts this relies on

1. **Keyboard characters arrive with bit 7 set.** CGET returns every typed
   character high-bit-set: `'1'` is `$B1`, and Enter is `$8D` (not `$0D`). The
   parser does `AND #$7F` before subtracting `'0'`. Forgetting this makes every
   digit parse as garbage.
2. **The ROM line editor is the input routine.** Entering at `$FD6F` (GETLN,
   minus the two instructions at `$FD6A-$FD6E` that would print the ROM's own
   prompt character) reads the line into the buffer at `$200` and returns the
   character count in `X` (the terminating CR is not counted). On return `A`
   holds the CR (`$8D`). The program therefore prints its own prompt and reads
   the length from `X`.
3. **COUT is `$FDED`.** Console output, character in `A`, other registers
   preserved.
4. **No multiply on the 6502.** `n*10` is done as `n*8 + n*2` with `asl`/`rol`
   (rotate the 16-bit `nl:nh` left three times for `n*8`, keep a saved `n*2`).
5. **ACME uses the implied accumulator.** There is no `asl a` / `rol a` — ACME
   treats the operand as a memory address and errors with "Value not defined
   (a)". Write bare `asl` / `rol` for the accumulator form (`asl $20` shifts
   memory at `$20`).
6. **Zero-page is shared with the ROM.** The ROM uses `$00-$3F` for its own
   variables (KBD, DSKRD, INVFLG, ...). This program keeps its string pointer
   at `$40/$41` and its parser scratch in the data block, so it never clobbers
   ROM state around the line-editor call.

See `docs/jace/advanced-assembly.md` and `docs/jace/debugging-guide.md` for the
full ACME / JACE assembly reference.

## Note on the flashing / errant "M"

If you run this live in the JACE terminal you may see a brief flash of the text
and a stray character (often an "M") near the output for a moment. This is a
**display-side re-rendering artifact of the JACE terminal**, not a bug in the
program: the program fires a fast burst of COUT (the prompt, the echoed
key-strokes from the ROM line editor, and the 7-character numeral, back to back)
while the Apple II's blinking text cursor is active, and the terminal's
per-character redraws produce the flash. The **final text screen is clean and
correct** -- see the `showtext` dumps in `last-run-transcript.txt` (each case
shows exactly `NUMBER: <n>` and the numeral, with no extra character).
