# JACE: Calling the Applesoft Floating-Point ROM from Assembly

Loaded on demand from `CLAUDE.md`.

The Applesoft interpreter ROM includes a complete floating-point package (add,
subtract, multiply, divide, sqrt, atan, int<->float conversion). Raw 6502 code
loaded with `loadbin`/`BRUN` can call these routines directly — no BASIC
interpreter needs to be running. This is useful for anything needing real
math (geometry, trig, scaling) without hand-writing fixed-point arithmetic.

All addresses below were confirmed against a full ROM disassembly (not
guessed) and verified empirically in JACE with the `NOP_SPECIAL` debug opcode
(see **Debugging FP code** below).

## The float registers

Applesoft's internal float format is **not** the 5-byte ROM constant table
format. There are two live registers, each 6 bytes:

| Register | Address | Layout |
|---|---|---|
| `FAC` (accumulator) | `$9D`-`$A2` | exponent, 4 mantissa bytes, sign byte |
| `ARG` (second operand) | `$A5`-`$AA` | exponent, 4 mantissa bytes, sign byte |
| `SGNCPR` | `$AB` | sign-compare scratch, see below |
| `FAC_EXTENSION` | `$AC` | rounding guard byte for `FAC` |
| `LINNUM` | `$50`-`$51` | 16-bit int result of `GETADR` |

The **sign byte only has its bit 7 meaningful** (0=positive, 1=negative).
The other 7 bits are leftover mantissa noise from unpacking and must not be
compared for an exact value — always test with `bmi`/`bpl`, never
`cmp #$FF`/`beq`.

Almost every routine below is a "T" entry point (`FADDT`, `FSUBT`, ...): it
operates directly on `FAC`/`ARG` and skips the pointer-unpacking wrapper
(`FADD`, `FSUB`, ...) that the BASIC interpreter uses when parsing source
text. Raw assembly always uses the "T" entry points.

## Loading values into FAC/ARG

**`GIVAYF` ($E2F2)** — convert a signed 16-bit integer to float, into `FAC`.
Convention: **A = high byte, Y = low byte** of the value.

```asm
; raw
GIVAYF = $E2F2
    lda #>(-192)
    ldy #<(-192)
    jsr GIVAYF        ; FAC = -192.0

; macro
!macro ld_fac_int .hib, .lob {
    lda .hib
    ldy .lob
    jsr GIVAYF
}
    +ld_fac_int DY_HI, DY_LO
```

**`LOAD_FAC_FROM_YA` ($EAF9)** and **`LOAD_ARG_FROM_YA`** ($E9E3) — load a
5-byte ROM-format float constant (see next section) from memory into `FAC`
or `ARG`. Convention: **A = low byte, Y = high byte** of the pointer — the
*opposite* order from `GIVAYF`. This bit the author badly during development;
double-check every call site.

```asm
; raw
LDFAC = $EAF9
PI_CONST !byte $82, $49, $0F, $DA, $A2   ; 3.14159...
    lda #<PI_CONST
    ldy #>PI_CONST
    jsr LDFAC         ; FAC = pi

; macro
!macro ld_fac_const .tbl {
    lda #<.tbl
    ldy #>.tbl
    jsr LDFAC
}
    +ld_fac_const PI_CONST
```

**`MAF`** ($EB66) — copy `FAC` to `ARG` (6 bytes). No parameters.

```asm
MAF = $EB66
    jsr MAF           ; ARG = FAC
```

**`COPY_ARG_TO_FAC`** ($EB53) — copy `ARG` to `FAC`. Useful for restoring a
value after a destructive comparison (see `FSUBT` below).

## Arithmetic

All of these compute into `FAC`, consuming `FAC` and `ARG` as inputs.

**`FADDT`** ($E7C1) — `FAC = FAC + ARG`.

Two things the ROM requires and does **not** set up for you:
1. The instruction immediately before the `jsr` must leave the Z flag
   reflecting whether `FAC`'s exponent is zero — i.e. the actual previous
   instruction must be `lda FAC` (`lda $9D`). This is because ROM code that
   normally *does* call `FADDT` (like `FADD`) ends its own setup with exactly
   that load; a bare `jsr FADDT` from outside skips it and gets garbage
   flags otherwise.
2. **`SGNCPR` ($AB) must be pre-set to `FAC_SIGN XOR ARG_SIGN`.** `FADDT`
   trusts this byte completely to decide whether it's adding or subtracting
   magnitudes internally — it never recomputes it. Forgetting this is the
   single most common bug: the add "succeeds" (no crash) but silently uses
   the wrong effective sign whenever `FAC` and `ARG` have different signs,
   which is exactly the case that only shows up for some inputs and not
   others — classic "works for the case I tested, breaks in the field."

```asm
; raw
FADDT = $E7C1
    lda FACSGN
    eor ARGSGN
    sta SGNCPR
    lda FAC
    jsr FADDT

; macro
!macro fadd {
    lda FACSGN
    eor ARGSGN
    sta SGNCPR
    lda FAC
    jsr FADDT
}
    +fadd
```

**`FSUBT`** ($E7AA) — `FAC = ARG - FAC`. Note the argument order: it
subtracts `FAC` *from* `ARG`, not the other way round.

Unlike `FADDT`, `FSUBT` is fully self-contained: it negates `FAC_SIGN` in
place, recomputes `SGNCPR` itself from the (now-negated) sign, and does its
own `lda FAC` right before falling into `FADDT`'s body. **Do not** pre-set
`SGNCPR` or add a `lda FAC` before it — both are silently discarded and
change nothing. The only side effect to watch for: it mutates `FAC_SIGN` in
place, so if you need the original sign afterward, save it first.

```asm
; raw — compute ARG - FAC
FSUBT = $E7AA
    jsr FSUBT

; macro (no wrapper needed; it's already a single instruction)
```

**`FMULTT`** ($E982) — `FAC = FAC * ARG`. **`FDIVT`** ($EA69) — `FAC = ARG /
FAC`. Both require the same two preconditions as `FADDT`: `SGNCPR` pre-set,
and a `lda FAC` immediately before the `jsr`. `FDIVT` additionally traps
**division by zero** as a ROM error if `FAC` is zero when called — always
check for a zero divisor before calling it, there is no way to recover from
the error handler in a raw-boot context (no BASIC environment is set up to
catch it, and execution ends up wandering into unrelated ROM/firmware code).

```asm
; raw
FMULTT = $E982
    lda FACSGN
    eor ARGSGN
    sta SGNCPR
    lda FAC
    jsr FMULTT

; macro
!macro fmul {
    lda FACSGN
    eor ARGSGN
    sta SGNCPR
    lda FAC
    jsr FMULTT
}
    +fmul
```

`fdiv` is identical to `fmul` but calls `FDIVT`.

**`SQR`** ($EE8D) — `FAC = sqrt(FAC)`. No `ARG`, no `SGNCPR`, no preceding
`lda FAC` needed; it's self-contained. `FAC` must not be negative.

**`ATN`** ($F09E) — `FAC = atan(FAC)`, result in `(-pi/2, pi/2)`, sign
matching the input. Self-contained, no preconditions.

## Converting back to an integer

**`GETADR`** ($E752) — floors `FAC` into an **unsigned 16-bit** integer at
`LINNUM` ($50/$51). It only accepts values where `FAC`'s exponent byte is
`< $91` — i.e. **the value must be strictly less than 65536**. If it's
larger, the ROM raises an "illegal quantity" error, and — same caveat as
`FDIVT` — there's no error handler set up in a raw-boot context, so
execution goes off into the weeds. Always guard the range before calling it:

```asm
    lda FAC
    cmp #$91
    bcs too_big     ; skip GETADR, handle out-of-range case directly
    jsr GETADR
    lda LINNUM      ; low byte of the result
```

## Debugging FP code

`step`/`break` are unreliable for this: a `break` set before `<addr>G` is not
reliably honored by a subsequent `run N`, and single-stepping through a
ROM call that's hundreds of instructions long is slow to follow. Use the
`$FC` `NOP_SPECIAL` debug opcode instead — see `docs/jace/debugging-guide.md`
for the full command reference. The two calls used throughout this doc's
examples:

```asm
!macro dbg .id {
    !byte $FC, $44, .id      ; dumps A/X/Y/SP/PC/flags to stdout, tagged .id
}
    lda ROW_IDX
    ldx COL_IDX
    +dbg $10                 ; "CPU[10]: A=.. X=.. Y=.. SP=.. PC=.... N=.. ..."
```

```asm
    ldx #0
dump: lda FAC,x
    !byte $FC, $5E, $00      ; print A as 2-digit hex, no newline
    inx
    cpx #6
    bne dump
    !byte $FC, $5F, $00      ; newline
```

This turns "does this crash" debugging into "print the exact bytes at every
step and compare against hand-calculated expected values" — which is how the
`FADDT`/`SGNCPR` and `LOAD_FAC_FROM_YA` A/Y-order bugs described above were
actually found: dumping `FAC` immediately before and after a suspect call and
comparing byte-for-byte against a value computed by hand.

## Common pitfalls, summarized

| Symptom | Likely cause |
|---|---|
| Wild jump into `$C000`-`$C800` (slot ROM) space, or PC ends up in unrelated ROM code | An FP call errored (illegal quantity from `GETADR`, or divide-by-zero from `FDIVT`) with no BASIC environment to catch it. Guard the input range before the call. |
| Result looks like `FAC` was untouched, or equals `ARG` unchanged | Stale Z flag going into `FADDT`/`FMULTT`: the caller didn't `lda FAC` as the instruction immediately before `jsr`. |
| Add/subtract silently uses the wrong sign for *some* inputs but not others | `SGNCPR` not set (or stale) before `FADDT`/`FMULTT`/`FDIVT`. |
| A restored/copied `FAC` value produces wrong results in a later `FADDT`/`FMULTT` | `FAC_EXTENSION` ($AC) wasn't cleared. Every ROM routine that deposits a fresh value into `FAC` (`GIVAYF`, `LOAD_FAC_FROM_YA`, `MAF`) clears it as a side effect; a raw byte-copy restore of a saved `FAC` does not, and must clear it explicitly. |
| Loading a ROM float constant produces nonsense | `LOAD_FAC_FROM_YA`/`LOAD_ARG_FROM_YA` want **A=low, Y=high** — opposite of `GIVAYF`'s **A=high, Y=low**. |
