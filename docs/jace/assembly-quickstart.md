# JACE: 6502 Assembly From Terminal Mode

Loaded on demand from `CLAUDE.md`.

**JACE is an Apple II, not a C64.** Read the "Apple II, not C64" section before writing
any code — a prior broken example failed exactly that way.

All facts in this file are run-verified on JACE on 2026-08-26 (hello world PASS — the
re-runnable acceptance test is at the end). They form the verified list for this workflow:
a firmware entry point or address not in this file is unverified recall and must not be
used without its own small validating test.

## Which assembler

The project assembler is **ACME cross assembler 0.97 ("Zem")** (28 June 2020), invoked as
`acme`. On this machine it lives at `/opt/homebrew/bin/acme`; check with
`acme --version`.

This is the project assembler. The docs previously did not say, and that cost a prior
attempt.

## Minimal hello world

`examples/hello-world/hello.asm`, verbatim:

```asm
* = $800
start
    ldx #0
next
    lda msg,x
    beq done
    jsr $FDED         ; COUT: Apple II ROM console output, char in A (docs/jace/debugging-guide.md:102)
    inx
    jmp next
done
    jmp done
msg:
    !text "HELLO WORLD"
    !byte $0D
```

Verified: 29 bytes at $800. The loop `JSR $FDED`s one character at a time; the message is
`!text "HELLO WORLD"` plus `!byte $0D` (CR). In the verified run exactly 12 characters
(HELLO WORLD + CR) were printed and the $00 terminator then BEQ'd to `done`, where the
program parks in an infinite self-loop (CPU ended at PC=$080E, X=$0C) — so it halts
instead of falling into whatever follows.

## ACME 0.97 syntax facts

Verified — do not re-probe:

- Origin is `* = $800` (not `org`).
- Data: `!byte` / `!by`.
- Multi-character strings use `!text "..."`. After `!by`, a double-quoted string is a
  1-character literal; more than one character is the error
  `There's more than one character`.
- Labels at column 0, mnemonics indented (as in hello.asm).
- CLI: `acme -f apple -o OUT IN`. There is NO `-n` flag — `acme -n ...` is rejected with
  `Unknown switch (-n)`.

## Apple output format

`-f apple` emits: 2-byte little-endian load address + 2-byte little-endian length + raw
code. Verified: the 29-byte $800 program produces header bytes `00 08 1d 00`
($800 → `00 08`; 29 = $1D → `1d 00`). JACE's `loadbin` wants the raw code only — strip
the 4-byte header:

```
tail -c +5 x.apple > x.bin
```

## Run it in JACE

```
reset
loadbin /path/to/hello.bin 800
800G
run 20000
expect "HELLO WORLD" 10
showtext
qq
```

- `loadbin <file> <addr>`: the address is hex. Verified log line: `Successfully loaded
  29 bytes to $800`.
- `800G` sets the PC and starts executing **async** — it must be followed by
  `run`/`expect`.
- Verified run: `run 20000` → `Ran for approximately 120000 cycles (120ms)`, then
  `Match found after 0ms`, and `showtext` shows `HELLO WORLD`.
- Scripted with transcript (IMPORTANT trap): write the commands to a file and feed it via
  file redirect:

  ```
  timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" 2>&1 < cmds.txt | tee out.txt
  status=${PIPESTATUS[0]}
  ```

  The mvn exit code is `PIPESTATUS[0]` (the pipeline's own exit code would be tee's). A
  heredoc placed AFTER the pipe binds to `tee`, not mvn — that wasted a prior run.
- The existing non-negotiables still apply: Maven only (never the native binary), always
  wrap in `timeout` (exit 124 = hang), and `run N < 100000` has a ~100 ms floor — the
  verified run above asked for 20000 and got ~120,000 cycles. That is plenty for this
  program, but do not assume `run N` executes N cycles.

## Console output routine

COUT = `JSR $FDED` with the character in A (standard ASCII).

Verified three ways:

1. `docs/jace/debugging-guide.md:102` — "The Apple II ROM provides `COUT` at $FDED".
2. The passing run: `expect "HELLO WORLD"` matched and `showtext` showed it.
3. ROM dump ($FD40–$FD4E): `B4 FB A4 24 9D 00 02 20 ED FD EA EA EA BD 00` — $FD47 is
   `20 ED FD` = `JSR $FDED`, corroborating $FDED as the COUT entry point.

WARNING: $FD43 is NOT an entry point in JACE's ROM. In the dump above, the instruction at $FD42 is `A4 24` (LDY $24, zero page); $FD43
(`24`) is its operand byte, so it lands mid-instruction. The following instruction
`9D 00 02` (STY $0200,Y) starts at $FD44; the BRK (`00`) bytes in this window are at
$FD45 and $FD4E, so execution from $FD43 falls into the IRQ handler. A 2026-08-26 run with
`JSR $FD43` printed nothing (blank screen, `expect` timed out). Do not use $FD43 or
$3D11 without your own verified test.

## Apple II, not C64

JACE is an Apple II — no C64 conventions, no PETSCII, no high-bit ASCII, no $C000 screen.

- The Apple II character set is standard ASCII (ref:
  https://en.wikipedia.org/wiki/Apple_II_character_set): e.g. H=$48 E=$45 L=$4C O=$4F
  space=$20 CR=$0D.
- The text screen is $0400–$07FF.
- A prior broken example failed exactly this way: `sta $C000,y` (C64 screen address) +
  `ora #$80` (high-bit ASCII, a C64/PETSCII habit). Full record:
  /tmp/agents/jace-hello-world/iteration-2/BLOCKERS.md row 7.

## Re-runnable acceptance test

From the repo root:

```
./examples/hello-world/hello-world-asm-test.sh
```

The script's header says: "hello-world-asm-test.sh -- Acceptance test: assemble
examples/hello-world/hello.asm with ACME (apple format), strip the 4-byte header, load it
at $800 in JACE, run it, and verify HELLO WORLD appears on the text screen. Exit codes:
0 = pass, 1 = fail, 2 = ACME missing, 124 = emulator hang."

It writes a transcript to `examples/hello-world/last-run-transcript.txt`. The pass line is
`PASS: HELLO WORLD confirmed on emulated text screen (expect match + showtext)`.

## Bare binary vs bootable disk

For small programs, load a bare assembled binary with `loadbin` — instant, no disk image
needed (the approach this doc uses). Build a bootable disk image only when you
specifically need to test disk/RWTS/boot; then use slot 7 (SmartPort) per
`docs/jace/setup-and-disks.md` — never slot 6 for ProDOS (real floppy rotation, a ~600 s
boot; non-negotiable #3).
