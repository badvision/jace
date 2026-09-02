# JACE: Applesoft BASIC From Terminal Mode

Loaded on demand from `CLAUDE.md`.

## Reaching Applesoft BASIC Without a Disk Image

Use `run basic`:

  lbas my_program.bas
  run basic

This loads a basic program into memory and sets up the interpreter to start running it immediately.  The emulator remains paused, so after you do this use other commands to run the emulator for a given duration, or run until an expected sequence is printed, and so on.
To reach the warm-start (re-enter BASIC without reinitializing an already-
running session), use `FF69G` followed by `run 500000`.

## Complete Applesoft BASIC Test Workflow (No Disk)

**Proven working sequence** for loading and running a BASIC program from a file:

```
reset
lbas /path/to/program.bas
run basic
expect "DONE" 10
st
screenshot --vbl /path/to/frame.png
qq
```

Notes:
- `expect` runs the emulator between polls, so it drives execution forward
- **The Applesoft prompt is `]` — there is NO "READY." banner** (that is C64 BASIC 2.0).
  Never `expect "READY"`; `expect` the text your program prints. The `]` prompt is visible
  in both `st` output and screenshots.
- Literal `expect` output strings for script gating: `Match found after Nms` on success,
  `Timeout waiting for: "<string>"` on timeout.
- `screenshot --vbl` renders text-mode screens correctly (white bg, black text) — valid
  evidence for text programs, not just HGR/DHGR.
- Re-runnable acceptance test of this exact flow: `./hello-world-test.sh [evidence_dir]`
  at the repo root (exit 0=pass, 1=fail, 124=hang); it drives `hello.bas`
  (`10 PRINT "HELLO WORLD"`) end-to-end via Maven.
- **Using `PR#3`** to switch to 80-column mode goes through the 80-col firmware's
  keyboard input loop and screen output loop. This works correctly in headless
  terminal mode; it does NOT hang -- but you will need to be at the basic interpreter for this to work.
- `waitkey` and `type` (synchronized keyboard) do NOT work immediately after
  `run basic` because the emulator is paused; use `key` + `run N` or
  `key` + `expect` instead

## Notes on the Applesoft Basic Variant
The Basic interpreter in the Apple is the same Microsoft 6502 Basic found in other 8-bit era machines, however like its cousins there are specific commands unique to the Apple // version.  It is therefore very important to pay attention to routines for graphics and I/O, as those are certainly not the same across 8-bit platforms.  Like its cousins, variable names are only useful up to two characters, after which the interpreter ignores the rest of the characters in its name.

| Category | Command | Description | Example |
| Sound | PRINT CHR$(7) | Playes the system beep | -- |
| Sound | PEEK(-16336) | Speaker tick | FOR A=0 to 10: PEEK(-16336): NEXT A |
| Keyboard | GET | Wait for a single key and store it | GET A$ |
| Keyboard | INPUT | Wait for a whole line until RETURN pressed | INPUT A$; INPUT "What is your name? "; N$ |
| Keyboard | PEEK(49152) | Get last key (no wait) plus 128 if the key was not handled yet | IF (PEEK(49152) < 128) THEN PRINT "No key pressed." |
| Text output | TEXT | Set screen to text mode | TEXT |
| Text output | HOME | Clear the text screen (NOTE: this is CLS in other Basics) | HOME |
| Text output | NORMAL, INVERSE, FLASH | Change the text output mode | NORMAL |
| Graphics (any) | PEEK(-16302) | Set graphics to full-screen | -- |
| Graphics (any) | PEEK(-16301) | Set graphics to mixed, bottom 4 rows are text | -- |
| Graphics (Lo-res) | GR | Initalize the screen for lo-res (40x48 resolution) and clear it to black | -- |
| Graphics (Lo-res) | COLOR= | Set the current color | COLOR=1 |
| Graphics (Lo-res) | PLOT | Plot a single point | PLOT 0,0 |
| Graphics (Lo-res) | HLIN, VLIN | Draw horizontal or vertical lines | HLIN 0,5 AT 1 |
| Graphics (Hi-res) | HGR | Initalize screen for hi-res page 1 (280x192 resolution) | -- |
| Graphics (Hi-res) | HGR2 | Initalize screen for hi-res page 2 | -- |
| Graphics (Hi-res) | HCOLOR= | Set the hi-res color | HCOLOR=3 |
| Graphics (Hi-res) | HPLOT [TO] X,Y [TO X1,Y1 ...] | Plot a single point or draw a line from the previous point to the next, can chain multiple coordinates | HPLOT 0,0 to 10,10 |

### Headless Graphics Program Pattern (Full-Screen + Non-Printing Completion Signal)

Two pitfalls come up repeatedly when writing a headless-testable lo-res/hi-res BASIC
program, both because **lo-res graphics memory ($0400-$07FF) is literally text page
1** — the same bytes drive both renderers, depending on the TEXT/MIXED softswitches:

- `PRINT` (or any text output) while graphics are showing writes ASCII bytes into
  that same buffer at the cursor position, corrupting pixels — even after graphics
  mode is otherwise correctly configured. Don't use `PRINT "DONE"` to signal
  completion in a graphics program; use the CALL-based signal below instead.
- **`GR` always re-arms the MIXED softswitch (the classic 4-line text window) every
  time it is called**, not just the first time. `POKE -16302,0` (clear MIXED, full
  screen) must be re-issued after **every** `GR` call, or the bottom 4 text rows
  will render as literal characters instead of pixels. The cleanest fix for an
  animated program is to call `GR` exactly **once** at the top (immediately followed
  by the `POKE -16302,0`) and animate by redrawing only the changed pixels with
  `COLOR=`/`PLOT`/`HLIN` afterward — never call `GR` again after the first frame.

**Minimal worked example** (full-screen lo-res + non-printing completion signal):

```
10 GR
20 POKE -16302,0
30 COLOR=6: FOR Y=0 TO 47: HLIN 0,39 AT Y: NEXT Y
40 POKE 768,96
50 CALL 768
```

Line 40 POKEs a single-byte `RTS` (opcode 96 = $60) into $0300 (768 decimal), which
is free machine-language scratch space (see the memory map in
`docs/jace/advanced-assembly.md`, $0300-$03CF "Free for machine language, shape
table, etc." — well below the text page at $0400 and the BASIC program/variables
that start at $0800, so it does not collide with this program). Line 50's `CALL 768`
is the program's last statement: it jumps to $0300, immediately executes the `RTS`,
and returns — no output is printed, so the pixel buffer is never touched again.

**Proven terminal command sequence** (real output, from this repo's
`cat-lores-test.sh`):

```
reset
lbas /path/to/program.bas
run basic
break 300
resume
run 6000000
cpu
screenshot --vbl /path/to/frame.png
qq
```

This produced, verbatim:

```
JACE>Breakpoint set at $0300
JACE>Emulation resumed
JACE>Running for 6000000 cycles
Breakpoint hit at $0300: RTS  A:03 X:9D Y:00 S:FD [..B.IZ.]
Ran for approximately 6038000 cycles (6038ms)
...
JACE>CPU State:
PC=$D823  A=$03  X=$9D  Y=$00  SP=$FF
```

We use **`break` + `resume` + a generous `run <cycles>`**, not `runto` alone and not
`run N #breakpoint`. `break`'s handler registers a `RAMEvent.TYPE.EXECUTE` listener
that synchronously suspends the motherboard and prints `Breakpoint hit at $XXXX` the
moment the CPU fetches that address — event-driven, not polled, so (unlike
`run N #breakpoint`, which only samples PC every 50 ms and can step over an address
that's resident for a single instruction) it reliably catches a one-instruction
target like our `RTS`. Both `runto` and `break`+`resume` return immediately without
blocking, so a trailing `run <cycles>` is required to give the emulator wall-clock
time to reach the breakpoint; grep the transcript for `Breakpoint hit at $0300` as
the pass/fail signal (equivalent to `Match found after Nms` for `expect`). The CPU
ends up parked one instruction **past** $0300 (in this trace, $D823 — inside the
interpreter's `CALL` return path), not frozen exactly at $0300; that is expected
(the RTS itself completes before the listener's suspend takes effect) and is fine
evidence of completion since no further screen writes occur once `CALL 768` is the
last statement.

**Caution:** `PEEK(-16336)` (speaker tick) is very slow in headless JACE — do not use
it as an animation delay; use a plain busy loop (`FOR D=1 TO N: NEXT D`) instead.

### Layering Pitfall: Background Fills Bleeding Through Foreground Shapes

`PLOT`/`HLIN`/`VLIN` are **immediate-mode** — each call paints pixels straight into
video memory with no compositing, no z-order, and no notion of "this shape is drawn
on top of that background." If a program draws a full-width background fill (for
example rug stripes via `HLIN 0,39 AT Y` for several `Y` values) and only *afterward*
draws a foreground shape over it, the foreground only erases the background in the
exact pixels its own drawing statements touch. **Any row (or column) the foreground
shape's code forgets to cover leaves the earlier background fully visible there** —
showing up as a stray line or hole cutting through the shape.

**Case study** (found and fixed in this repo, in `examples/cat-on-rug-lores/cat-lores.bas`):
rug stripes were drawn full-width at `Y = 28,32,36,40,44` (`FOR Y=28 TO 44 STEP 4:
HLIN 0,39 AT Y`) before the cat's body. The body's own row-by-row `HLIN` statements
originally skipped exactly those same 4 rows — a coincidental alignment with the
stripe's step-4 spacing — leaving the magenta stripe visible straight through the
orange body at 4 points. The fix was to add one `HLIN` per missing row, sized to
bridge the neighboring rows' extents.

**Practical guidance:**

- When a foreground shape is drawn over a patterned/striped background, make sure
  the shape's drawing code has **continuous coverage across every row (or column)**
  of its intended silhouette — no skipped rows, even ones needing only a narrow span.
- A reliable way to catch this class of bug is to render the frame and **sample
  pixel colors programmatically at each row within the shape's own known column
  range**, asserting they all match the shape's color — more reliable than
  eyeballing a screenshot alone, especially for periodic background patterns that
  can coincidentally align with a shape's row spacing.


## Non-Graphics Applesoft BASIC Commands (Quick Reference)

The table above covers I/O and graphics. Below is a concise reference for all other
Applesoft commands, sourced from the [Apple II Programmer's Reference](https://www.landsnail.com/a2ref.htm).

### Statements & Syntax

| Command | Description |
|---|---|
| `:` | Statement separator on one line |
| `REM` | Comment (ignored at runtime) |
| `PRINT` / `?` | Output values or strings |

### Program Operations

| Command | Description |
|---|---|
| `NEW` | Erase program and clear all variables |
| `CLEAR` | Reset all variables (keep program) |
| `LIST` | Display the entire program |
| `LIST n-m` | Display lines n through m |
| `RUN` | Execute the current program |
| `RUN n` | Execute from line n |
| `LOAD` | Load a program from disk |
| `SAVE` | Save the current program to disk |

### Variables & Arrays

| Type | Syntax | Range |
|---|---|---|
| Real | `AB` | +/- 9.9999999 E+37 |
| Integer | `AB%` | +/- 32767 |
| String | `AB$` | 0-255 characters |
| Array (real) | `AB(x,y,z)` | Up to 3 dimensions |
| Array (int) | `AB%(x,y,z)` | Up to 3 dimensions |
| Array (str) | `AB$(x,y,z)` | Up to 3 dimensions |

`DIM a(x,y,z)` defines array bounds. Variable names are significant only in the first
two characters; anything beyond is ignored.

### Arithmetic Operators

`=` `+` `-` `*` `/` `^` (exponentiation)

### Logical & Relational Operators

| Logical | Relational |
|---|---|
| `AND` | `=` (equal) |
| `OR` | `<` (less than) |
| `NOT` | `>` (greater than) |
| | `<=` (less or equal) |
| | `>=` (greater or equal) |
| | `<>` (not equal) |

### Arithmetic Functions

| Function | Description |
|---|---|
| `ABS(x)` | Absolute value |
| `SGN(x)` | -1, 0, or 1 depending on sign |
| `INT(x)` | Integer part of x |
| `SQR(x)` | Square root |
| `SIN(x)` / `COS(x)` / `TAN(x)` / `ATN(x)` | Trigonometry (radians) |
| `EXP(x)` | e^x |
| `LOG(x)` | Natural logarithm |
| `RND(x)` | Random: x>0 gives 0-1; x=0 repeats last; x<0 seeds new sequence |
| `DEF FN name(x) = expr` | Define a function |

### String Functions

| Function | Description |
|---|---|
| `LEN(s)` | Length of string |
| `LEFT$(s, n)` | Leftmost n characters |
| `MID$(s, start, len)` | len chars from s starting at start |
| `RIGHT$(s, n)` | Rightmost n characters |
| `STR$(x)` | Number to string (leading space for sign) |
| `VAL(s)` | String to number |
| `CHR$(x)` | ASCII code to character |
| `ASC(s)` | ASCII code of first char in s |
| `+` | String concatenation |

### Control Flow

| Command | Description |
|---|---|
| `GOTO n` | Branch to line n |
| `ON expr GOTO n1,n2,...` | Branch based on value of expr |
| `IF cond THEN s1:s2:...` | Conditional execution |
| `IF expr GOTO n` | Shorthand if-then-goto |
| `FOR v=x TO y STEP z` | Loop (STEP defaults to 1) |
| `NEXT v` | End of FOR loop |
| `GOSUB n` | Branch to subroutine |
| `RETURN` | Return from subroutine |
| `ON expr GOSUB n1,n2,...` | Subroutine dispatch |
| `POP` | Remove one address from return stack |
| `ONERR GOTO n` | Error handler line |
| `RESUME` | Re-execute statement after error |
| `STOP` | Halt and print line number |
| `CONT` | Continue after STOP |
| `END` | Halt normally |

### Utility

| Command | Description |
|---|---|
| `PEEK(addr)` | Read memory byte |
| `POKE addr, x` | Write memory byte |
| `CALL addr` | Execute machine code at addr |
| `USR(x)` | Call ML routine with argument |
| `HIMEM: addr` | Set highest available memory |
| `LOMEM: addr` | Set lowest available memory |
| `FRE(0)` | Available memory (bytes) |
| `TRACE` / `NOTRACE` | Show/hide line numbers during execution |

### I/O & Text Screen

| Command | Description |
|---|---|
| `IN# n` | Redirect input from slot n |
| `PR# n` | Redirect output to slot n |
| `INPUT s; x,y,...` | Prompt with string s, read values |
| `GET c` | Read single character (blocks) |
| `READ x,y,...` | Read from DATA list |
| `DATA x,y,...` | Embedded data values |
| `RESTORE` | Reset DATA pointer to start |
| `PDL(n)` | Paddle value (0 or 1) |
| `HTAB x` | Move cursor to column x |
| `VTAB x` | Move cursor to row x |
| `INVERSE` | Black-on-white text |
| `NORMAL` | White-on-black text |


## Memory Address Literals

Memory address literals in BASIC are **integer values in base 10 only** — no `$` hex prefix
or other radixes are supported. However, signed representation is commonly used for high
memory addresses:

- `$C000` → `-16384`
- `$FFFE` → `-2`
- `-16384` is the standard way to reference the softswitches at $C000

This is because Applesoft interprets the address as a signed 16-bit value, so addresses at
$8000 and above are expressed as negative numbers. This is especially common for I/O
addresses (softswitches start at $C000 = -16384).


## Notes on color
Lo-res colors range from 0 to 15:
0: Black 1: Magenta / Red 2: Dark Blue 3: Purple 4: Dark Green 5: Dark Gray / Gray 6: Medium Blue 7: Light Blue 8: Brown 9: Orange 10: Light Gray / Gray 11: Pink / Apricot 12: Light Green 13: Yellow 14: Aqua 15: White

Hi-res colors range from 0 to 7:
0 = Black (L), 1 = Green (L), 2 = Purple (L), 3 = White (L)
4 = Black (H), 5 = Orange (H), 6 = Blue (H), 7 = White (H)

Note: Colors 0 and 4 are both black; colors 3 and 7 are both white. The L/H distinction only matters for attribute clashes — it determines which color set a 7-pixel byte belongs to, not the actual pixel color.

It is important to know that the hi-res colors are tricky! The Apple II hi-res screen does **not** store colors directly. It stores 1-bit-per-pixel black/white patterns (7 bits per byte + parity). The NTSC color subcarrier phase at each bit position determines what color you see:

- **Adjacent same-color bits blend** → appear white (NTSC subcarrier averages out)
- **Isolated single-bit colors** → show their NTSC phase color based on position within the byte

The high bit in each byte is just a half-pixel shift — a 90-degree phase shift in YUV. This means L and H colors are not separate "palettes" but rather the same bit pattern shifted by half a pixel, which changes how adjacent bits blend.

This means:
- A full horizontal line with `HPLOT 0,Y TO 279,Y` using HCOLOR=3 appears solid white — every bit is adjacent to another, so they all blend.
- Alternating columns (odd-only or even-only) with HCOLOR=3 shows the "pure" NTSC phase colors: purple on even positions, green on odd positions (because each colored bit is isolated from its neighbor).

**Practical techniques:**
- **Adjacent bits blend to white**: When you draw a solid horizontal line with `HPLOT 0,Y TO 279,Y` using any L or H color (e.g., HCOLOR=3), all adjacent pixels blend and appear white. This is because the NTSC subcarrier averages out when multiple same-color bits are next to each other.
- **Isolated bits show pure NTSC colors**: When you draw only odd columns (e.g., `HPLOT 1,Y TO 279,Y` with HCOLOR=3), each colored bit is isolated from its neighbor, so you see the "pure" NTSC phase colors: purple on even positions, green on odd positions.
- **Use alternating L colors for dithering**: Draw green on every other row and white on alternating rows to create visual texture without attribute clashes — they share the same L attribute bit.
- **H-color shapes need H-background**: Keep H-color shapes (orange cat, blue objects) on regions where the background is already using H-attribute, or ensure they only touch odd columns.

The color theory gets weird because it's all a big NTSC hack using minimal circuitry: The genius of Woz at work.

### Lo-res (GR) video memory model

Lo-res graphics has **no dedicated pixel buffer or attribute memory** — `GR` draws
directly into the same bytes as 40-column text:

- **Page 1** (the page `GR` uses by default) is **$0400-$07FF**, page 2 is
  **$0800-$0BFF** — the exact text-page ranges in the memory map below, not a
  separate region. (Source: `src/main/java/jace/apple2e/VideoDHGR.java:324`
  `loresPage1` reads/writes `yTextOffset + 0x0400`; `:340` `loresPage2` uses
  `+ 0x0800`.)
- **Each byte holds two vertically-stacked lo-res pixels**, one 4-bit color nibble
  per half of the byte's 8-scanline text cell: the **low nibble** colors the top 4
  scanlines, the **high nibble** the bottom 4. (Source:
  `src/main/java/jace/apple2e/VideoNTSC.java:165-179`, `displayLores()`:
  `if ((y & 7) < 4) data &= 15; else data >>= 4;`.)
- **There is no separate attribute-color memory and no color-clash block
  constraint** — each pixel's 4-bit nibble is its own color value, independent of
  its neighbors. (This is unlike hi-res, which does have the odd/even-column and
  L/H-region clashes described above; do not assume lo-res inherits them.) This is
  why the memory map in `docs/jace/advanced-assembly.md` has no separate lo-res
  row — lo-res is not a distinct memory region, it reuses the text page.
- Related softswitch behavior: **`TEXT` and `MIXED` trigger on any memory access to
  their addresses, read or write** — e.g. both `POKE -16302,0` and `PEEK(-16302)`
  clear MIXED. (Source: `src/main/java/jace/apple2e/SoftSwitches.java:93-94`,
  `RAMEvent.TYPE.ANY` on the `TEXT` and `MIXED` switches.)


## BASIC Variable Table Layout (Applesoft Internals)

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

### Statement Separator (`:`)

In Applesoft BASIC the colon character `:` separates multiple statements on a single line.  It allows you to write compact code without needing separate line numbers for each statement.  The interpreter executes the statements from left to right.

**Example**:

```
100 X=100 : REM Sets X to 100
```

In this line, `X` is assigned the value `100`, then the `REM` comment follows after the colon.  You can chain additional statements, e.g., `100 X=100 : Y=200 : PRINT X,Y`.