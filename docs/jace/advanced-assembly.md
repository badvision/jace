# JACE: Advanced 6502 Assembly

Loaded on demand from `CLAUDE.md`.

This is the advanced companion to `docs/jace/assembly-quickstart.md`. Read the quickstart
first — its verified list (ACME 0.97 CLI and syntax, the 4-byte apple header, COUT
`$FDED`, the `$FD43` warning, Apple-II-not-C64) is in force throughout, and this doc
cross-references it instead of restating it.

**Provenance and truth rule.** Every address or firmware fact in this doc is traceable
to one of:

| Tag | Source |
|---|---|
| [QS] | `docs/jace/assembly-quickstart.md` — run-verified on JACE (2026-08-26) |
| [S1] | ProDOS 8 Technical Reference, ch. 3 "Memory Use" — https://prodos8.com/docs/techref/memory-use/ (fetched 2026-08-26) |
| [S2] | ProDOS 8 Technical Reference, ch. 5 "Writing a ProDOS System Program" — https://prodos8.com/docs/techref/writing-a-prodos-system-program/ (fetched 2026-08-26) |
| [S3] | Kreative Korporation, "Descriptions of Memory Areas" — https://www.kreativekorp.com/miscpages/a2info/memorymap.shtml (fetched 2026-08-26) |
| [AG] | `AGENTS.md` (repo doc) |
| [CMD] | `docs/jace/commands.md` (repo doc) |
| [JACE] | JACE source, `src/main/java/jace/applesoft/ApplesoftProgram.java` with file:line citations, per the 2026-08-26 JACE Applesoft-bootstrap zero-page analysis |

Nothing in this doc is recalled firmware knowledge. Items that could not be verified
from those sources are marked **UNVERIFIED** and collected in section 5 — do not fill
them in from memory.

## 1. ACME compile-to-load pipeline

The whole pipeline, in order. Steps 2–6 restate the quickstart's verified facts only
as a checklist — the detail and the run evidence live in [QS].

1. **Check the assembler.** `acme --version` must report the project assembler.
   Verified on this machine (2026-08-26): `This is ACME, release 0.97 ("Zem"), 28 June 2020`.
2. **Source layout.** Origin is `* = $xxxx` (not `org`); labels at column 0, mnemonics
   indented; data via `!byte`/`!text "..."` (see [QS] "ACME 0.97 syntax facts" for the
   string-literal trap after `!by`).
3. **Assemble.** `acme -f apple -o OUT IN` — there is **no `-n` flag** [QS]. If you want
   a label file for the terminal, add `--symbollist FILE` (emits `name = $XXXX`) or
   `--vicelabels FILE` (emits `al C:xxxx .name`) [CMD].
4. **Strip the 4-byte header.** `-f apple` emits 2-byte little-endian load address +
   2-byte little-endian length + raw code [QS]. JACE's `loadbin` wants raw code only:

   ```
   tail -c +5 out.asm > code.bin
   ```

   Verified [QS]: a 29-byte program at $800 produces header `00 08 1d 00`.
5. **Load.** `loadbin <file> <addr>` — the address is **hex** [QS].
6. **Run.** `<addr>G` (e.g. `800G`) sets the PC and starts executing **async** — it must
   be followed by `run`/`expect`. The `run` floor caveat (~100 ms minimum) and the
   Maven/timeout non-negotiables apply [QS][AG].
7. **Debug with labels.** `symbols <labelfile>` loads ACME label files so that any
   address-taking command accepts names (`break mainloop`, `runto after_draw`,
   `go entry`, `mem frame_count frame_count`, `memaux sprite_buffer sprite_buffer`).
   Full reference, including both ACME emitter formats: `docs/jace/commands.md` [CMD].

### Example: hello world (re-verified 2026-08-26, assemble level)

`examples/hello-world/hello.asm` — the reference pipeline program, reproduced verbatim
in [QS] (COUT `JSR $FDED` loop, `!text "HELLO WORLD"` + `!byte $0D`). Re-assembled
2026-08-26:

```
$ acme -f apple -o hello.asm.bin hello.asm
$ xxd -l 4 hello.asm.bin
00000000: 0008 1d00                                ....
```

Verified: 29 bytes at $800 (33-byte file). Header `00 08 1d 00`; stripped body begins
`a2 00 bd 11 08 f0 07 20 ed fd e8 4c 02 08 ...` (`ldx #0` / `lda msg,x` / `beq done` /
`jsr $FDED` / ...). Identical to the quickstart's verified output. The run-level
evidence (`expect "HELLO WORLD"` match + `showtext`) is in [QS]; this doc's evidence
is assemble output.

## 2. Memory mapping (Apple //e)

### Consolidated memory map

One row per region, as the sources present them (overlapping regions — per-OS
$9600–$BFFF, per-language-card $D000–$FFFF — are separate rows). Sizes are
end − start + 1. Sources: S1/S2/S3 per the provenance table.

| # | Start | End | Size (dec) | Contents | Source |
|---|---|---|---|---|---|
| 1 | $0000 | $00FF | 256 | Zero page (shared scratch; OS/BASIC/MLI usage — see section 4) | S3; S1 §3.3.1 |
| 2 | $0100 | $01FF | 256 | 6502 processor stack (grows down; OS keeps the low 16 bytes for interrupts) | S3; S2 §5.2.1 |
| 3 | $0200 | $02FF | 256 | GETLN line input buffer | S3 |
| 4 | $0300 | $03CF | 208 | Free for machine language, shape table, etc. | S3 |
| 5 | $03D0 | $03FF | 48 | DOS, ProDOS, and interrupt vectors (incl. RESET vector $3F2, power-up byte $3F4) | S3; S2 §5.3.5, §5.1.5.2 |
| 6 | $0400 | $07FF | 1024 | Text video page (40-column) and peripheral screenholes; also Lo-res graphics page 1 (`GR`) and, combined with the Aux bank, Double-Lo-res page 1 | S3; JACE |
| 7 | $0800 | $0BFF | 1024 | Text video page 2 (80-column) OR Applesoft program and variables; also Lo-res graphics page 2 and, combined with the Aux bank, Double-Lo-res page 2 | S3; JACE |
| 8 | $0C00 | $1FFF | 5120 | Free for machine language/shapes (BASIC may overwrite) | S3 |
| 9 | $2000 | $3FFF | 8192 | Hi-res graphics page 1; also load address for ProDOS MLI and system programs; combined with the Aux bank, Double-Hi-res page 1 | S3; S1 §3.1; S2 §5.1.1; JACE |
| 10 | $4000 | $5FFF | 8192 | Hi-res graphics page 2; combined with the Aux bank, Double-Hi-res page 2 | S3; JACE |
| 11 | $6000 | $95FF | 13824 | Applesoft string data (some BASIC moves variables up to $4000/$6000) | S3 |
| 12 | $9600 | $9CFF | 1792 | Disk I/O buffers (DOS 3.2/3.3) | S3 |
| 13 | $9D00 | $BFFF | 8960 | DOS routines (DOS 3.2/3.3) | S3 |
| 14 | $9600 | $99FF | 1024 | BASIC.SYSTEM I/O buffers (ProDOS) | S3 |
| 15 | $9A00 | $BEFF | 9472 | Currently running SYS file (ProDOS, typical layout) | S3 |
| 16 | $BF00 | $BFFF | 256 | ProDOS MLI/kernel global page; MLI entry point $BF00 | S1 §3.3.2; S2 §5.2.3–5.2.4; S3 |
| 17 | $C000 | $C0FF | 256 | Soft switches and status locations (e.g. ROMIN $C082, RAMIN $C08B appear in S2's MLI code) | S3; S2 §5.2.3 |
| 18 | $C100 | $C7FF | 1792 | Peripheral card memory (generic) | S3 |
| 19 | $C100 | $C2FF | 512 | Apple IIe: extensions to system monitor | S3 |
| 20 | $C300 | $C3FF | 256 | Apple IIe: 80-column display routines (JSR $C300 turns the card on; disconnects BASIC.SYSTEM) | S3; S2 §5.3.1.3 |
| 21 | $C400 | $C7FF | 1024 | Apple IIe: self-test routines | S3 |
| 22 | $C800 | $CFFF | 2048 | Apple IIe: more 80-column display routines | S3 |
| 23 | $C100 | $C2FF | 512 | Apple IIc: serial firmware | S3 |
| 24 | $C300 | $C3FF | 256 | Apple IIc: 80-column firmware | S3 |
| 25 | $C400 | $C4FF | 256 | Apple IIc: mouse firmware | S3 |
| 26 | $C500 | $C6FF | 512 | Apple IIc: floppy disk drive firmware | S3 |
| 27 | $C700 | $C7FF | 256 | Apple IIc: AppleTalk firmware | S3 |
| 28 | $C800 | $CFFF | 2048 | Apple IIc: extended memory for periph card | S3 |
| 29 | $D000 | $F7FF | 10240 | Applesoft interpreter (Applesoft ROM language card) | S3 |
| 30 | $F800 | $FFFF | 2048 | System monitor | S3; S1/S2 Fig 3-1/5-1 |
| 31 | $D000 | $D7FF | 2048 | Programmer's Aid #1 ROM (Integer ROM LC) | S3 |
| 32 | $D800 | $DFFF | 2048 | Empty (no RAM or ROM) (Integer ROM LC) | S3 |
| 33 | $E000 | $F7FF | 6144 | Integer BASIC / Mini-Assembler / Sweet16 (Integer ROM LC) | S3 |
| 34 | $D000 | $DFFF | 4096 | Bank-switched RAM: 2 banks RAM, 1 bank ROM (IIe/IIc/IIgs) | S3 |
| 35 | $E000 | $FFFF | 8192 | Bank-switched RAM: 1 bank RAM, 1 bank ROM (IIe/IIc/IIgs) | S3 |
| 36 | $0800 | $BEFF | 46848 | Total space available to a ProDOS system program on a 64K machine (max load size $8F00 = 36608); the BASIC.SYSTEM area is also available when BASIC is not in use | S1 §3.3; S2 §5.1.1–5.1.2 |
| 37 | $1000 | $1000 | 1 | Dispatcher/selector jump target after ProDOS QUIT (code copied here from language-card $D100–$D3FF) | S2 §5.1.5.2 |
| 38 | $D100 | $D3FF | 768 | Apple's dispatcher code, resident in the second 4K bank of the language card (CLD signature at $D100) | S2 §5.1.1, §5.1.5.2 |
| 39 | $D100 | $DFFF | 3840¹ | "Used by ProDOS" per Figure 3-1/5-1 auxiliary panel (label anchored at the $D100 boundary) | S1/S2 figure |
| 40 | $9600 | $D000 | 14849¹ | "Used by BASIC.SYSTEM" per Figure 3-1/5-1 auxiliary panel (label anchored at the $9600 boundary) | S1/S2 figure |

¹ Size corrected: the research extraction listed 4096 and 17408 for these two figure
rows, but end − start + 1 over the stated anchors gives 3840 and 14849. The figure
labels themselves are unchanged, and the ASCII-art panel semantics remain ambiguous
(see section 5, item 5).

Notes:

- **Row 6 doubles as lo-res (GR) graphics memory** — there is no separate lo-res
  video row in this table because lo-res isn't a distinct region: `GR` writes pixels
  directly into the same $0400-$07FF text page (page 2 lo-res reuses row 7's
  $0800-$0BFF instead). Full memory-model detail (nibble-per-half-pixel layout,
  per-pixel color, source citations): `docs/jace/applesoft.md`, "Notes on color" →
  "Lo-res (GR) video memory model".
- **Double-Lo-res (Aux+Main bank) and Double-Hi-res (Aux+Main bank) are likewise not
  separate rows — they are the Main-bank row's page combined byte-for-byte with the
  same address range in the Aux bank**, not a distinct memory region of their own:
  - Double-Lo-res page 1/2 reads the *same* $0400-$07FF / $0800-$0BFF addresses as
    lo-res/text (rows 6-7), but from **both banks at once** — one color nibble from
    Aux, one from Main, per byte. (Source: `VideoDHGR.java` `dloresPage1`/`dloresPage2`
    reuse the identical `+ 0x0400` / `+ 0x0800` offsets as `loresPage1`/`loresPage2`;
    `VideoNTSC.java`'s `displayDoubleLores()` calls
    `getAuxVideoMemory().readByte(rowAddress + xOffset)` for one nibble source and
    `getMainMemory().readByte(rowAddress + xOffset)` for the other, at the *same*
    `rowAddress`.)
  - Double-Hi-res page 1/2 reads the *same* $2000-$3FFF / $4000-$5FFF addresses as
    hi-res (rows 9-10), also from **both banks at once** — 4 interleaved 7-bit bytes
    (Aux, Main, Aux, Main) become one 28-bit DHGR word. (Source: `VideoDHGR.java`
    `dhiresPage1`/`dhiresPage2` reuse the identical `+ 0x02000` / `+ 0x04000` offsets
    as `hiresPage1`/`hiresPage2`; `VideoDHGR.java`'s `displayDoubleHires()` reads `b1`/
    `b3` via `getAuxVideoMemory()` and `b2`/`b4` via `getMainMemory()` at the same
    `rowAddress`.) This matches the DHGR bank-split trap already noted below
    (aux = even columns, main = odd columns).
- Rows 39–40 transcribe ProDOS figure labels, not text-stated regions — do not assert
  more than the label says [S1/S2 Fig 3-1/5-1].
- The ProDOS 8 model that produces rows 16, 36–40 is a memory-resident **MLI +
  XXX.SYSTEM** system program (section 4b). Do not frame it with "OS Base"/"System 65"
  vocabulary — those terms are ProDOS 1.x/2.x heritage and do not occur in the ProDOS 8
  sources [S1][S2].

### Apple //e specifics

- **Auxiliary 64K bank — 128K IIe / IIc only.** It exists only on an "Apple IIe with
  Extended 80-column Text card, or Apple IIc". At ProDOS start-up it is configured as
  the RAM disk **/RAM**, appearing as slot 3 drive 2, device unit number **$BF**
  [S2 §5.2.2].
- **MLI restrictions on the aux/LC banks.** The MLI cannot read or write the Language
  Card area or the extended (aux) memory, cannot be called from them, and the alternate
  64K bank cannot contain code that makes MLI calls nor be used for system buffers
  [S2 §5.1.4, §5.3.1.2].
- **80-column card.** On: `JSR $C300` — the beginning of the ROM on the card; note this
  **disconnects BASIC.SYSTEM** [S2 §5.3.1.3]. Off:

  ```asm
  LDA #$15     ; character that turns off video firmware
  JSR $C300    ; print it to the video firmware
  ```

  Verbatim instruction-sequence quote; a standalone assemble needs an origin
  directive, e.g. `* = $200`.

  [S2 §5.3.1.3]. The 80-column MACHID bit is always set on the IIc; on a IIe it is set
  when a protocol-conforming card is in slot 3 or the auxiliary slot [S2 §5.3.1.3].
- **MACHID** (machine identification byte, ProDOS 8 global page at **$BF98**) [S2
  §5.2.3 listing and §5.2.2.3 code; the §5.2.2.2 text says $BF96 — see the conflict
  note in section 5, item 7]. Bits, verbatim from the S2 listing:

  | Bits | Meaning |
  |---|---|
  | 7,6 (bit 3 off) | 00=II, 01=II+, 10=IIe, 11=/// emulation |
  | 7,6 (bit 3 on) | 10=//c, 00/01/11=NA |
  | 5,4 | 01=48K, 10=64K, 11=128K (00=NA) |
  | 3 | modifier for bits 7,6 |
  | 2 | reserved for future definition |
  | 1 | 1 = 80-column card |
  | 0 | 1 = recognizable clock card |

- **IIe/IIc extras.** Lowercase is always available; IIe/IIc have keys absent from
  earlier models (most notably [UP], [DOWN], [OA], [SA], [DELETE]) [S2 §5.3.1.1].

### JACE-specific operating notes

Facts below come from the repo docs, not the three web sources.

- **Text screen and console.** $0400–$07FF, standard 7-bit ASCII (no PETSCII, no
  high-bit characters, no $C000 screen); console output is `JSR $FDED` (COUT) with the
  character in A [QS][AG].
- **DHGR bank split (the trap).** In 80STORE+HIRES double-hi-res, `$2000–$3FFF` is
  interleaved: **aux holds the even pixel columns, main the odd**. `mem` follows the
  softswitches and cannot distinguish the banks — use `memaux <start> <end>` /
  `memmain <start> <end>` (aliases `mx`/`mm`), or the monitor-mode `X`/`M` address
  prefixes, whenever the bank matters [AG "Two traps"][CMD]. No web source in section 2
  covers DHGR — attribute it to the repo docs (section 5, item 3).
- **80STORE's Main/Aux page-flip only works for Page 1, not Page 2 (the trap).**
  80STORE lets `PAGE2` page an entire Page-1 range (text/lo-res `$0400-$07FF`, and
  also hi-res `$2000-$3FFF` when `HIRES` is on) between Main and Aux memory — this is
  what makes `PAGE2` a *hybrid* switch: with 80STORE on, toggling `PAGE2` flips memory
  banks instead of switching the video circuitry to Page 2 [JACE:
  `SoftSwitches.java`'s `PAGE2`/`HIRES` `stateChanged()` call `configureActiveMemory()`
  instead of the normal video-mode change when `_80STORE.isOn()`; `RAM128k.java`'s
  `buildReadConfiguration()`/`buildWriteConfiguration()` show the exact effect —
  `if (_80STORE.isOn()) { read.setBanks(0x04, 0x04, 0x04, PAGE2 ? Aux : Main); if
  (HIRES.isOn()) read.setBanks(0x020, 0x020, 0x020, PAGE2 ? Aux : Main); }` — only
  pages $04-$07 ($0400-$07FF) and $20-$3F ($2000-$3FFF) are ever remapped; the
  Page-2 address ranges ($0800-$0BFF, $4000-$5FFF) are untouched by this switch].
  **There is no equivalent trick for Page 2** — 80STORE cannot page the $0800-$0BFF
  or $4000-$5FFF ranges between Main and Aux. A program that wants double-buffered
  animation using Page 2 as the off-screen buffer must therefore either give up
  page-flipping for Page 2 and draw into it directly (accepting visible screen tearing
  if drawing overlaps a redraw of the visible frame), or "race the beam" — time writes
  to land during vertical/horizontal blanking so they complete before the raster
  scans that row again.
- **ProDOS disk images.** Use **slot 7** (SmartPort) for `.po` images — slot 6 emulates
  real floppy rotation (a ProDOS boot takes ~600 s) [AG, non-negotiable #3].

### Test-compiled memory-mapping probe

A trivial probe: marker into the text screen, read of the MLI page. Both targets are
provably safe in bare JACE — $0400 is the first text-screen cell, and $BF98 is plain
main RAM unless ProDOS is actually running (then it is MACHID, and reads are the
documented use [S2 §5.2.3]).

```asm
* = $800
probe
    lda  #'M'          ; ASCII 'M' = $4D
    sta  $0400         ; first cell of the text screen page
    lda  $BF98         ; MACHID (ProDOS 8 system global page)
    jmp  probe         ; park
```

```
$ acme -f apple -o probe.bin probe.asm
$ xxd -l 4 probe.bin
00000000: 0008 0b00                                ....
```

Verified: 11 bytes at $800 (15-byte file). Header `00 08 0b 00`; body `a9 4d 8d 00 04
ad 98 bf 4c 00 08` (`lda #$4D` / `sta $0400` / `lda $BF98` / `jmp probe`).

## 3. How to access all 128K of memory

The 6502 only addresses 64K at a time. JACE's "128K" model (`RAM128k.java`) is two
64K banks — **main** and **aux** — plus a per-bank 12K Language Card RAM overlay at
$D000-$FFFF, all switched in and out by softswitches at $C000-$C009 and $C080-$C08F.
Every switch below is read/write/status-checkable at three addresses (write-off,
write-on, status-read) exactly like the switches already tabulated elsewhere in this
doc [JACE: `SoftSwitches.java`].

### 3a. The AUX memory softswitches (RAMRD/RAMWRT/AUXZP)

| Switch | Off addr | On addr | Status addr | What it does | Source |
|---|---|---|---|---|---|
| `RAMRD` | $C002 | $C003 | $C013 | Selects which bank the CPU **reads** from for the "main memory" fill (everything outside the $C000-page and LC special-cases below): off = main, on = aux | `SoftSwitches.java` `RAMRD`; `RAM128k.java` `buildReadConfiguration()`: `read.fillBanks(SoftSwitches.RAMRD.getState() ? getAuxMemory() : mainMemory);` |
| `RAMWRT` | $C004 | $C005 | $C014 | Same, for **writes**: off = main, on = aux | `SoftSwitches.java` `RAMWRT`; `RAM128k.java` `buildWriteConfiguration()`: `write.fillBanks(SoftSwitches.RAMWRT.getState() ? getAuxMemory() : mainMemory);` |
| `AUXZP` | $C008 | $C009 | $C016 | Selects which bank's **zero page + stack** ($0000-$01FF) is live, **and** (see 3c below) which bank's Language Card RAM is in play: off = main, on = aux | `SoftSwitches.java` `AUXZP`; `RAM128k.java`: `if (SoftSwitches.AUXZP.getState()) { read.setBanks(0, 2, 0, getAuxMemory()); } else { read.setBanks(0, 2, 0, mainMemory); }` (mirrored in `buildWriteConfiguration()`) |

`AUXZP` is JACE's name for what the Apple //e technical/reference literature calls
**ALTZP** ("alternate zero page") — same switch, different name in this codebase.

`RAMRD`/`RAMWRT` independently gate reads and writes, so a program can, e.g., turn
`RAMWRT` on while `RAMRD` stays off to write into aux RAM while still reading from
main RAM at the same addresses — standard 128K-bank-switching technique; nothing
JACE-specific changes that behavior, it is a direct product of the two switches being
applied separately in `buildReadConfiguration()`/`buildWriteConfiguration()` [JACE].

Note what `RAMRD`/`RAMWRT`/`AUXZP` do **not** reach: the softswitch page itself ($C000
-$C0FF) is unaffected, and — as covered next — the Page-1 video ranges have their own,
separate paging rule.

### 3b. Cross-reference: Page-1 video paging is a separate mechanism (80STORE)

The AUX softswitches above move the *generic* main/aux boundary. The video-page
ranges ($0400-$07FF text/lo-res, and $2000-$3FFF hi-res when `HIRES` is on) are
**not** paged by `RAMRD`/`RAMWRT`; they are paged by `80STORE`+`PAGE2` (and `HIRES`)
instead, and only for Page 1, not Page 2. That mechanism, its exact address ranges,
and the "no equivalent trick for Page 2" limitation are already documented in
**"### JACE-specific operating notes"** above (the *"80STORE's Main/Aux page-flip only
works for Page 1, not Page 2"* bullet) — see that bullet for the full
`setBanks()`/`stateChanged()` citations; it is not repeated here.

### 3c. The Language Card switches (LCBANK1 / LCRAM / LCWRITE) — main AND aux

Each bank (main and aux) has its **own, independent** 12K Language Card RAM region
covering $D000-$FFFF, separate from that bank's ordinary RAM at the same addresses.
JACE models this as two `PagedMemory` pairs: `languageCard`/`languageCard2` for main,
and `getAuxLanguageCard()`/`getAuxLanguageCard2()` for aux (the aux pair is built by
`CardExt80Col`, JACE's aux-80-column-card implementation, using the identical
0x3000/0x1000-byte layout as the main pair) [JACE: `RAM128k.java` lines 156-158,
529-531; `CardExt80Col.java` lines 49-50, 72-88].

Three switches, all in $C080-$C08F, control this RAM — read them from the *actual*
`int[]` arrays in `SoftSwitches.java`, not from generic Apple II lore, since JACE's
per-address mapping is confirmed here directly from source:

| Switch | Off addrs | On addrs | Status addr | Source |
|---|---|---|---|---|
| `LCBANK1` | $C088-$C08F | $C080-$C087 | $C011 | `SoftSwitches.java` `LCBANK1` |
| `LCRAM` | $C081,$C082,$C085,$C086,$C089,$C08A,$C08D,$C08E | $C080,$C083,$C084,$C087,$C088,$C08B,$C08C,$C08F | $C012 | `SoftSwitches.java` `LCRAM` |
| `LCWRITE` | $C080,$C082,$C084,$C086,$C088,$C08A,$C08C,$C08E | $C081,$C083,$C085,$C087,$C089,$C08B,$C08D,$C08F | (none — `Memory2SoftSwitch`) | `SoftSwitches.java` `LCWRITE` |

`LCWRITE` is a `Memory2SoftSwitch`: any single write, or a single read of an "on"
address, does **not** enable writing — it takes **two consecutive reads** of one of
its "on" addresses (with no intervening write) to flip it on; any access to an "off"
address flips it off immediately [JACE: `Memory2SoftSwitch.java` `setState()`].

**What each switch actually controls, per `RAM128k.java`'s `buildReadConfiguration()`/
`buildWriteConfiguration()`:**

- **`LCRAM`** gates whether $D000-$FFFF *reads* come from LC RAM at all. When
  `LCRAM.isOff()`, reads in that range come from ROM (Applesoft/Monitor) regardless of
  the other two switches: `read.fillBanks(rom);` runs unconditionally first, and the LC
  RAM overlay below only happens `if (SoftSwitches.LCRAM.isOn())` [JACE].
- **`LCWRITE`** independently gates whether $D000-$FFFF is *write-enabled* as RAM.
  When `LCWRITE.isOff()`, `buildWriteConfiguration()` sets every page from $D000-$FFFF
  to `null` (non-writable), full stop — independent of `LCRAM`'s read-side state
  [JACE: `for (int i = 0x0d0; i < 0x0100; i++) { write.set(i, null); }`]. So the
  classic four LC read/write-protect combinations are just `LCRAM` and `LCWRITE` set
  independently; there is no separate combined switch.
- **`AUXZP` decides *which bank's* LC RAM is used** — this is the same `AUXZP`
  switch from 3a, reused here, not a separate aux-LC switch: when `LCRAM.isOn()`
  (read) or `LCWRITE.isOn()` (write), JACE checks `AUXZP.isOff()` to pick the **main**
  bank's `languageCard`/`languageCard2`, or (when `AUXZP` is on) the **aux** bank's
  `getAuxLanguageCard()`/`getAuxLanguageCard2()` [JACE: identical `if
  (SoftSwitches.AUXZP.isOff()) { ...languageCard... } else { ...getAuxLanguageCard()... }`
  block appears in both `buildReadConfiguration()` and `buildWriteConfiguration()`].
  In other words: to reach the **aux** bank's Language Card RAM, `AUXZP` must be on —
  the same switch that pages the zero page/stack to aux also pages the LC RAM to aux.
- **`LCBANK1` selects bank 1 vs. bank 2, and only for $D000-$DFFF — not the whole
  12K.** The base fill (`languageCard`, or `getAuxLanguageCard()`) is a full 0x3000
  (12288-byte) `PagedMemory` covering $D000-$FFFF; this is always applied first and
  represents **bank 1**. Only `if (SoftSwitches.LCBANK1.isOff())` does JACE then
  overlay a *second*, smaller 0x1000 (4096-byte) `PagedMemory` (`languageCard2`, or
  `getAuxLanguageCard2()`) on top, covering **only $D000-$DFFF** — this represents
  **bank 2** [JACE: `RAM128k.java` `buildReadConfiguration()`/`buildWriteConfiguration()`;
  size/base confirmed from the `PagedMemory` constructors — `languageCard =
  new PagedMemory(0x3000, ...)`, `languageCard2 = new PagedMemory(0x1000, ...)` at
  lines 168-169, both typed `LANGUAGE_CARD` with base address $D000]. So:
  - `LCBANK1` **on** ($C080-$C087 accessed) → bank 1 → the full $D000-$FFFF from
    `languageCard`/`getAuxLanguageCard()` is used as-is.
  - `LCBANK1` **off** ($C088-$C08F accessed) → bank 2 → $D000-$DFFF is overlaid from
    `languageCard2`/`getAuxLanguageCard2()`; $E000-$FFFF is untouched by the overlay,
    i.e. **$E000-$FFFF is shared between bank 1 and bank 2** and only $D000-$DFFF is
    actually bank-switched. This is confirmed directly from the `PagedMemory` sizes
    above (0x1000 = exactly $D000-$DFFF), not assumed from general Apple II
    documentation.

**Putting it together** — to reach a specific 4K/12K region, set:

| Want | AUXZP | LCRAM | LCWRITE | LCBANK1 |
|---|---|---|---|---|
| Main ROM at $D000-$FFFF (read) | off | off | — | — |
| Main LC bank 1 RAM, $D000-$FFFF, read-only | off | on | off | on |
| Main LC bank 2 RAM ($D000-$DFFF only; $E000-$FFFF still bank-1/shared), read+write | off | on | on | off |
| Aux LC bank 1 RAM, $D000-$FFFF, read+write | on | on | on | on |
| Aux LC bank 2 RAM ($D000-$DFFF only), read-only | on | on | off | off |

(Table rows are the source-derived combinations above, not an exhaustive enumeration
of every switch permutation.)

## 3d. Double Hi-Res (DHGR) graphics

DHGR on the Apple IIe runs at 560×192 resolution with 16 colors via the FLIP_NYBBLE remapping system.
This section documents how to correctly program DHGR in JACE, based on a verified working example
(`examples/dhgr-color-wheel/dhgr-color-wheel.asm`).

### Softswitch addresses (Apple IIe)

All softswitches are **value-independent** — only the address matters. Any STA/STX/BIT to an ON or OFF
address triggers the switch. Query addresses return 0x80 when the switch is on, 0x00 otherwise.

| Switch | OFF addr | ON addr | Query addr | Type |
|--------|----------|---------|------------|------|
| DHIRES | $C05F | $C05E | $C07F | VideoSoftSwitch |
| HIRES | $C056 | $C057 | $C01D | VideoSoftSwitch (hybrid) |
| 80COL | $C00C | $C00D | — | MemorySoftSwitch |
| MIXED | $C052 | $C053 | — | VideoSoftSwitch |
| TEXT | $C050 | $C051 | — | VideoSoftSwitch |
| 80STORE | $C000 | $C001 | $C018 | MemorySoftSwitch (hybrid) |
| PAGE2 | $C054 | $C055 | $C01C | VideoSoftSwitch (hybrid) |

**Source:** `src/main/java/jace/apple2e/SoftSwitches.java` lines 95–116.

### Initialization sequence

Turn all video modes OFF first, then enable the desired combination:

```asm
    ldx #$FF
    stx $C05F         ; DHIRES off
    stx $C056         ; HIRES off
    stx $C00C         ; 80COL off
    stx $C052         ; MIXED off
    stx $C050         ; TEXT off
    stx $C000         ; 80STORE off (clean baseline)
    stx $C054         ; PAGE2 off

    ldx #$FF
    stx $C05E         ; DHIRES on
    stx $C057         ; HIRES on
    stx $C00D         ; 80COL on
    stx $C001         ; 80STORE on
```

### Memory layout: two banks, two-pass rendering

DHGR Page 1 occupies `$2000–$3FFF` (4096 bytes). Each row is **40 bytes** in the main bank and
**40 bytes** in the aux bank — 80 bytes total per row. The video scanner reads from BOTH banks
simultaneously to produce the final pixel data.

The critical insight: a single DHGR color (a solid repeating nibble pattern) requires **all 4 bytes**
at consecutive offsets to jointly encode 7 repetitions of a single nibble value through the
28-bit LSB-first assembly. Writing a single byte repeated cannot produce color — only `$00` and `$7F`
appear as black/white respectively.

#### Bit-interleaving model (from `VideoDHGR.java:displayDoubleHires()`)

```java
dhgrWord = (b1 & 0x7F) | (b2 & 0x7F) << 7 | (b3 & 0x7F) << 14 | (b4 & 0x7F) << 21;
// b1 = aux[x], b2 = main[x], b3 = aux[x+1], b4 = main[x+1]
```

Four consecutive bytes form one "column pair" — two Apple II columns, 28 pixels total.
The 28-bit word has 7 nibbles packed LSB-first (bits 0–3, 4–7, ..., 24–27), each passed through
the FLIP_NYBBLE remapping table before display.

(That nibble->color step is the simple `VideoDHGR` path. The authoritative `VideoNTSC` renderer works per pixel -- 560 independent colors/row, a 3-bit straddle across word boundaries, and a YIQ/color-clock palette; see "The 140-cell model is a simplification -- real DHGR is per-pixel" below.)

#### Two-pass rendering with PAGE2 bank-switching

Use `80STORE + PAGE2` to toggle between main and aux banks for `$2000–$3FFF`:

```asm
; Pass 1: write main bank (PAGE2=OFF)
    ldy #0
    lda #$00
    sta $C054         ; PAGE2 off → $2000-$3FFF maps to mainMemory
main_pass:
    lda col_main_tab,y
    sta ($30),y       ; zp indirect-indexed with Y, 40 bytes/row
    iny
    cpy #40
    bne main_pass

; Pass 2: write aux bank (PAGE2=ON)
    ldy #0
    lda #$FF
    sta $C055         ; PAGE2 on → $2000-$3FFF maps to auxMemory
aux_pass:
    lda col_aux_tab,y
    sta ($30),y       ; same zp pointer, writes to aux bank
    iny
    cpy #40
    bne aux_pass
```

**Important:** `PAGE2` is a VideoSoftSwitch with a hybrid `stateChanged()` override that calls
`configureActiveMemory()` when `_80STORE` is on. The memory cache key includes both 80STORE and
PAGE2 state (via `getAuxZPConfiguration()`), so the memoized PagedMemory objects update correctly.
See `RAM128k.java:428-441` for the caching mechanism.

#### Rendering from a per-row data buffer (and the two bugs that trap everyone)

The simple fixed-`$30` + `Y`-offset loop above works when every row of a bank is the *same*
40 bytes (a solid-color row). For per-pixel art (a pinwheel, a picture, ...), each row has its
own 40 bytes, so you keep a **7680-byte source buffer** (192 rows x 40) per bank and *copy* it
into the row. Two non-obvious traps:

**Trap 1 — re-point the destination before the aux pass.** A `copy40`-style routine advances the
destination pointer by 40 bytes as it copies. If you reuse the *advanced* `$30/$31` for the aux
pass, the aux row lands one DRAM row too far, and even/odd pixel columns are misaligned by a row.
You *must* reload `$30/$31` from the row table before the second `copy40`. (Symptom: the picture
is mostly right but every row of the aux/even columns is shifted one row down — very subtle on
horizontal-gradient backgrounds, obvious on fine detail.)

**Trap 2 — page-safe copy.** `(zp),Y` and `(abs),Y` do **not** carry when the low byte crosses
`$FF`. The *destination* is safe here because every DHGR row-start low byte is in
`{$00,$28,$50,$80,$A8,$D0}` (see the DRAM table above), so +39 never crosses a page (max `$D0+39=$EF`).
But the *source* buffer's row low bytes hit every value, so a source low byte of `$E0`+39 wraps
past `$100` and corrupts the next row. The working routine splits the copy at the page boundary
and bumps the high byte by hand:

```asm
; Copy 40 bytes from ($34,$35) to ($30,$31), page-safe on the source side.
; $36 = bytes remaining (<= 40), $38 = current sub-segment length.
copy40:
    lda #$28
    sta $36
cloop:
    lda $36
    sta $38
    lda $34
    eor #$FF
    clc
    adc #1            ; A = 256 - Ls  (0 iff Ls=0 => no source limit)
    bne src_cmp
    jmp src_done
src_cmp:
    cmp $38
    bcs src_done      ; 256-Ls >= n: no page boundary in range
    sta $38
src_done:
    lda $30
    eor #$FF
    clc
    adc #1            ; A = 256 - Ld
    bne dst_cmp
    jmp dst_done
dst_cmp:
    cmp $38
    bcs dst_done
    sta $38
dst_done:
    ldy #0
cseg:
    lda ($34),y
    sta ($30),y
    iny
    cpy $38
    bne cseg
    lda $38
    clc
    adc $34
    sta $34
    bcc s1
    inc $35
s1:
    lda $38
    clc
    adc $30
    sta $30
    bcc s2
    inc $31
s2:
    lda $36
    sec
    sbc $38
    sta $36
    bne cloop
    rts
```

#### Data placement: keep program data OUT of video RAM

The two 7680-byte source buffers plus code must live in **plain RAM that the video scanner
never reads** — e.g. code at `$800`, main buffer at `$6000`, aux buffer at `$7E00` (ending
`$9BFF`). `$2000-$3FFF` is DHGR page-1 video and `$4000-$5FFF` is page-2 video; placing any
program data there makes it vanish or self-corrupt mid-render. `$8000-$BFFF` is always safe
main RAM too. (Verified: writing per-row tables into `$2000+` garbled the image while the same
tables at `$6000`/`$7E00` rendered correctly.)

### Computing solid-color byte patterns

Each lo-res color index 0–15 maps to a DHGR nibble value N via the inverse of FLIP_NYBBLE:

```java
// VideoDHGR.java line 41
int[] FLIP_NYBBLE = {0,2,4,6,8,10,12,14,1,3,5,7,9,11,13,15};
```

To produce solid color for lo-res index P:
1. Find N such that `FLIP_NYBBLE[N] == P` (inverse lookup)
2. Build a 28-bit word with nibble N repeated 7 times LSB-first
3. Pack into 4 bytes where each byte contributes its 7 LSBs to consecutive bit positions:
   - b1 = bits 0–6, b2 = bits 7–13, b3 = bits 14–20, b4 = bits 21–27

```python
def compute_bytes(target_flip):
    flip_nybble = [0,2,4,6,8,10,12,14,1,3,5,7,9,11,13,15]
    n = {v: k for k, v in enumerate(flip_nybble)}[target_flip]
    word = sum((n & 0x0F) << (i * 4) for i in range(7))
    return ((word >> 0) & 0x7F, (word >> 7) & 0x7F,
            (word >> 14) & 0x7F, (word >> 21) & 0x7F)
```

#### Verified byte patterns for the 7 most useful colors:

| Lo-res index | Screen color | b1 | b2 | b3 | b4 | Memory order (b1,b2,b3,b4) |
|-------------|-------------|------|------|------|------|---------------------------|
| 0 | black | $00 | $00 | $00 | $00 | (00,00,00,00) |
| 1 | magenta | $08 | $11 | $22 | $44 | **(08,11,22,44)** |
| 3 | violet | $19 | $33 | $66 | $4C | **(19,33,66,4C)** |
| 4 | green | $22 | $44 | $08 | $11 | **(22,44,08,11)** |
| 6 | cyan/blue | $33 | $66 | $4C | $19 | **(33,66,4C,19)** |
| 7 | lavender | $3B | $77 | $6E | $5D | **(3B,77,6E,5D)** |
| 9 | red | $4C | $19 | $33 | $66 | **(4C,19,33,66)** |
| 10 | yellow | $4D | $2A | $4D | $2A | — (dim) |
| 11 | chartreuse | $5D | $3B | $77 | $6E | **(5D,3B,77,6E)** |
| 12 | blue | $66 | $4C | $19 | $33 | **(66,4C,19,33)** |
| 13 | yellow (bright) | $6E | $5D | $3B | $77 | **(6E,5D,3B,77)** |
| 14 | white | $77 | $6E | $5D | $3B | — |

**Memory layout for one column-pair (offsets x, x+1):**
- b1 = aux[x], b2 = main[x], b3 = aux[x+1], b4 = main[x+1]
- col_main_tab stores `[b2, b4, b2, b4, ...]` — 40 bytes per row
- col_aux_tab stores `[b1, b3, b1, b3, ...]` — 40 bytes per row

### The 140-cell model is a simplification — real DHGR is per-pixel

The "28-bit word = 7 nibbles = 7 x 4-px solid cells" framing above is a **convenience for layout
and porting**, not the hardware model. It maps cleanly to a coarse 4-px pixel grid, which keeps
screen layout simple and makes porting from other platforms (Atari, C64) more trivial. It is what
this example uses, and what the low-quality `VideoDHGR.showDhgr` fallback renders (each nibble ->
4 identical pixels).

The real Apple II DHGR model is **per-pixel: 560 independent 4-bit colors per row**. The 16 colors
are defined in **YIQ** (NTSC luma/chroma), not RGB, and their on-screen appearance is a function of
the **NTSC color-clock phase** (see "The NTSC render is the authentic display" below). The
authoritative renderer (`VideoNTSC`) builds a **128-entry YIQ-derived palette** (4 clock phases x
32) and, for each of the 560 pixels, extracts a **7-bit pattern** from a continuous bit stream that
**straddles the 28-bit word boundaries by 3 bits on each side**:

```java
// VideoNTSC, DHGR mode (hiresMode == false) — per 28-bit word s, pixel i:
bits = scanline[s] << 3;
if (s > 0)  bits |= (scanline[s - 1] >> 25) & 7;   // 3 bits from the previous word
for (i = 0; i < 28; i++) {
    writer.setArgb(p++, y, activePalette[i % 4][bits & 0x07f]);  // 128-entry YIQ palette
    bits >>= 1;
    if (i == 20) bits |= (scanline[s + 1] & 7) << 10;            // 3 bits from the next word
}
```

Because a pixel's 7 bits come from the current *and* neighboring words, a pixel's color is **not
confined to one nibble**. A general packer for arbitrary per-pixel art maps each target pixel's
color to its 7-bit NTSC pattern and **ANDs** those bits into the correct (overlapping) bit
positions of the words — a mask over the whole 560-pixel row, not a per-nibble solid fill.
(Equivalently: for each pixel, pick the "color-flip" pattern that turns that clock phase's base
color into the target color, and AND/OR its bits in.) The 140-cell model is the special case where
that per-pixel pattern is constant within every 4-px cell; it renders correctly (this example) but
cannot express per-pixel detail finer than 4 px.

**Cell packer — this example, cell-aligned art only.** For each column-pair `k` (0..19), take the
7 cells `[7k .. 7k+6]` and pack them **LSB-first** into a 28-bit word, then split into the 4 bytes:

```python
FLIP_NYBBLE = [0,2,4,6,8,10,12,14,1,3,5,7,9,11,13,15]   # nibble -> palette index
INV_FLIP    = {v: k for k, v in enumerate(FLIP_NYBBLE)}  # palette index -> nibble

def pack_pair(cells):
    """cells: 7 palette indices -> (aux[x], main[x], aux[x+1], main[x+1])."""
    word = 0
    for i in range(7):
        word |= INV_FLIP[cells[i]] << (4 * i)   # LSB-first: cell i -> bits 4i..4i+3
    return word & 0x7F, (word >> 7) & 0x7F, (word >> 14) & 0x7F, (word >> 21) & 0x7F
    #        aux[x]   main[x]      aux[x+1]      main[x+1]
```

A solid color `P` is just `pack_pair([P]*7)`. The two packing traps (nibble direction, byte
extraction) both cancel for a solid color, so a solid-color self-test alone cannot catch them —
verify the packer on a **non-uniform row** via a pack->unpack round trip
(`examples/dhgr-pinwheel/generate.py --check` does the doc solid table plus a non-uniform round trip).

### The 2:1 tall-pixel display aspect

Apple II DHGR is **560x192 raw** but displays with **2:1 tall pixels**, so the true picture is
**560x384**. A shape drawn with equal raw width and height therefore looks like a 2:1 ellipse.
To make circles (and other round shapes) actually round, design in **display space**: cell
`(c, r)` maps to display pixel `(4*c, 2*r)`. A display-space circle of radius `R` centered on
cell `(CX, CY)` is:

    (4*(c - CX))^2 + (2*(r - CY))^2 <= R*R      # CX=70, CY=96 for a 140x192 grid

so the raw shape is 2x as wide as tall, which displays as a circle.

JACE `screenshot` reads the 560x192 NTSC buffer and scales it **2x on both axes** to 1120x384
(2x too wide). For the true aspect, rescale to 560x384 (halve the width) —
`dhgr-pinwheel-test.sh` does this with `sips`.

### The NTSC render is the authentic display (colors are YIQ/color-clock, not picked RGB)

The Apple // series is designed around **NTSC timing, including the NTSC color subcarrier** — the
one Apple II thing that makes its colors what they are. The 16 DHGR colors are **defined in YIQ**
(NTSC luma + I/Q chroma) in `VideoNTSC.YIQ_VALUES`, and the on-screen RGB is just the `yiqToRgb`
decode of that. Woz did **not** pick a set of RGB values; the colors are what NTSC modulation of the
color clock produces (which is also why the phases rotate per pixel and adjacent colors can show the
characteristic NTSC dithering/interference). So the NTSC `screenshot` **is** the faithful Apple II
appearance — there is no separate "true RGB" it is degraded from.

`core/Palette.java` RGB is a **flat-RGB approximation** of those colors (used by the simpler
`VideoDHGR` renderer; close to but not identical to the authentic NTSC decode). It is fine for a
quick **geometry / palette-index** check, not the true on-screen color:

- Judge what the game **actually looks like** with the NTSC `screenshot` (what a composite monitor
  / CRT showed).
- For a quick **layout / index** check, `examples/dhgr-pinwheel/generate.py --preview out.png`
  writes a flat-RGB, true-aspect (560x384) reference from the source grid. There is **no direct
  16-color non-NTSC screenshot**; the only non-NTSC fallback is a black/white monochrome render.

### DRAM row addressing (non-linear in memory)

DHGR rows are NOT linearly spaced. The offset from the page base is computed as:

```python
def calc_text_offset(n):
    return ((n & 7) << 7) + 40 * (n >> 3)

def hires_offset(y):
    block = y >> 3
    subrow = y & 7
    text_off = calc_text_offset(block)
    return text_off + (subrow << 10)

# DHGR Page 1 row address = $2000 + hires_offset(y), for y in 0..191
```

This produces a lookup table of 192 pairs of hi/lo bytes. The assembly example includes the full
generated tables (`row_hi_tab`, `row_lo_tab`).

### Complete working examples

- `examples/dhgr-color-wheel/dhgr-color-wheel.asm` -- the minimal working example: initializes
  DHGR, uses two-pass PAGE2 bank-switching, full DRAM row-address tables, and shows the 7 useful
  colors as vertical bars. A good starting point for the *fixed-table* (solid-color-row) approach.
- `examples/dhgr-pinwheel/dhgr-pinwheel.asm` -- the full *per-row-data-buffer* approach: a round
  7-blade pinwheel over a 6-band sunset with a 16-row checkerboard dither. Demonstrates the
  page-safe `copy40` routine, the destination re-point before the aux pass, and keeps the two
  7680-byte source buffers at `$6000`/`$7E00` (out of video RAM). The image is generated by
  `examples/dhgr-pinwheel/generate.py` (self-checks the DHGR packer and writes a no-NTSC
  preview); `dhgr-pinwheel-test.sh` builds and screenshots it.

### Palette: DHGR FLIP_NYBBLE remap -> `core/Palette.java` RGB

The nibble selects a palette index via FLIP_NYBBLE (above). `core/Palette.java` defines a flat RGB
for each index. **Note:** the authentic on-screen colors are the **NTSC YIQ->RGB decode** in
`VideoNTSC.YIQ_VALUES` (see "The NTSC render is the authentic display" above); the `Palette.java`
RGB below is a close-but-not-identical flat approximation, handy for the no-NTSC preview.

| Index | RGB (`Palette.java`)  | Index | RGB (`Palette.java`) |
|-------|-----------------------|-------|----------------------|
| 0     | 0,0,0 (black)         | 8     | 128,80,0 (brown)     |
| 1     | 208,0,48 (red)        | 9     | 255,128,0 (orange)   |
| 2     | 0,0,128 (dark blue)   | 10    | 192,192,192 (gray)   |
| 3     | 255,0,255 (magenta)   | 11    | 255,144,128 (pink)   |
| 4     | 0,128,0 (green)       | 12    | 0,255,0 (bright green) |
| 5     | 128,128,128 (gray)    | 13    | 255,255,0 (yellow)   |
| 6     | 0,0,255 (blue)        | 14    | 64,255,144 (mint)    |
| 7     | 96,160,255 (light blue) | 15  | 255,255,255 (white)  |

DHGR does not produce "true" ROYGBIV hues — the palette is the lo-res 16 colors remapped through
FLIP_NYBBLE with phase-shifted NTSC composite artifacts. Select colors that produce visually
distinct, cohesive regions rather than attempting exact spectral hues.

## 4. Zero-page usage strategy

The zero page is shared scratch: the OS, Applesoft, the MLI, and your code all live in
$0000–$00FF. Two explicit modes — pick one per program.

### 4a. Cooperating with Applesoft BASIC

**Rules.**

- **Leave the OS low zero page alone.** On the Apple II the low zero page (up to about
  $2F) is OS territory that the OS/ROM rewrites at boot. This $0000–$002F / higher
  Applesoft split is an **Apple II convention** — JACE has no low-ZP-boundary constant
  (grep-verified: no such constant exists in `src/main/java`), so do not cite JACE as
  defining the boundary. No source states that "$00–$C0 is reserved for the OS" (see
  section 5, item 2); the rule is a working convention, and JACE's own behavior
  supports it: JACE writes only $00–$05 in the low region — the GOWARM/GOSTROUT vector
  bytes the OS itself owns and rewrites at boot — and writes nothing in $06–$51
  [JACE].
- **Applesoft owns the high zero page.** While BASIC is running, treat roughly $52
  upward as interpreter workspace, not your scratch. JACE's bootstrap touches exactly
  the addresses in the table below [JACE].

> **This is JACE's emulation of ROM behavior, NOT a definitive list of Applesoft
> zero-page usage.** The table records what *JACE's Java code* writes (or reads) during
> the `loadbasic` / `run basic` bootstrap, per the 2026-08-26 source analysis. Real
> Applesoft COLD_START/CLR initializes many more ZP locations at runtime — absence from
> the table is NOT evidence that Applesoft leaves a location uninitialized. "JACE sets
> $XX" is a statement about JACE's launch sequence; "Applesoft uses $XX" is a statement
> about the ROM and needs a ROM/PRM source.

**What JACE's Applesoft bootstrap touches** (`loadbasic` / `run basic`; address | value | purpose | file:line):

| Address | Value set | Purpose | file:line |
|---|---|---|---|
| $0000–$0002 | `4C 3C D4` (JMP $D43C) | GOWARM vector → Applesoft main input loop | ApplesoftProgram.java:434–436 (const `WARM_START_ENTRY`, line 318) |
| $0003–$0005 | `4C 3A DB` (JMP $DB3A) | GOSTROUT vector → Applesoft string-output routine (PRINT path) | ApplesoftProgram.java:439–441 |
| $0032 | $FF | INVFLG — normal (non-inverse) output | ApplesoftProgram.java:429 |
| $0052 | $55 | TEMPPT = TEMPST — temp-string descriptor stack empty | ApplesoftProgram.java:445 |
| $0054 | $00 | copied from COLD_START behavior | ApplesoftProgram.java:464 |
| $0067/$0068 | $01, $08 (= $0801) | TXTTAB — start-of-program pointer | ApplesoftProgram.java:355–358 (stub), 186; consts lines 43, 57 |
| $0069/$006A | programEnd | VARTAB — variable table start | ApplesoftProgram.java:367–370 (stub), 265, 285 |
| $006B/$006C | programEnd | ARYTAB — array table start | ApplesoftProgram.java:367–370 (stub), 264, 286 |
| $006D/$006E | programEnd | STREND — end of variable table | ApplesoftProgram.java:367–370 (stub), 266, 287 |
| $006F/$0070 | $00, $BF (= $BF00) | FRETOP — string heap top = HIMEM | ApplesoftProgram.java:377–380 (stub); const line 48 |
| $0073/$0074 | $00, $BF (= $BF00) | HIMEM — top of BASIC memory (no-DOS default) | ApplesoftProgram.java:361–364 (stub); consts lines 321, 49 |
| $0076 | (read; $FF = not running) | RUNNING_FLAG check + stack-scan disambiguation | ApplesoftProgram.java:218, 226–232; const lines 54–55 |
| $008F | $03 | DSCLEN — string descriptor length | ApplesoftProgram.java:447 |
| $00A4 | $00 | copied from COLD_START behavior | ApplesoftProgram.java:465 |
| $00AF/$00B0 | programEnd | END_OF_PROG_POINTER (program end) | ApplesoftProgram.java:267, 278; const line 44 |
| $00B0–$00CC | 29 bytes from ROM $F10A–$F126 | CHRGET inline routine (line-input fetcher) | ApplesoftProgram.java:458–459 |
| $00B1 | (read; copy condition) | start of CHRGET; also the DOS/ProDOS hook target | ApplesoftProgram.java:454; comment lines 51–53 |
| $00B8/$00B9 | set by ROM RESTORE ($D697) = TXTTAB−1 | TXTPTR — current text pointer | ApplesoftProgram.java:325, 382–383 |
| $00D6 | $00 | LOCK — auto-run lock cleared | ApplesoftProgram.java:449 |
| $00F1 | $01 | SPEED — normal output speed | ApplesoftProgram.java:432 |
| $00F2 | $00 | TRCFLG — trace mode off | ApplesoftProgram.java:462 |
| $00F3 | $00 | display-state flag (paired with INVFLG) | ApplesoftProgram.java:430 |

Plus, non-ZP but same bootstrap: SP = $FF (stub `ldx #$FF`/`txs` and
`cpu.STACK = 0xFF`, ApplesoftProgram.java:468), the launch stub resident at $0100
(stack page), sentinel $00 at $0800 (line 189), tokenized program at $0801+ (lines
190–212), and the $0200 line-input buffer cleared to $00 (line 452; const line 50).

**`loadbasic` alone vs `run basic`.**

- `loadbasic <file>` (no execution): tokenizes the listing, then `injectProgram()`
  writes the $0800 sentinel + tokenized program at $0801+, sets TXTTAB $67/$68 = $0801,
  and `clearVariables()` sets VARTAB/ARYTAB/STREND and END_OF_PROG ($AF/$B0) to the
  program end [JACE: ApplesoftProgram.java:184–214, 262–269; terminal command
  `MainMode.java:1646–1711`]. The interpreter is not started.
- `run basic` adds: compiles the 6502 launch stub at **$0100** and copies it in
  (skipping a 2-byte CBM load-address header), turns LCRAM off so the language-card ROM
  is visible at $D000–$FFFF, performs the Java-side ZP writes in the table above, and
  sets PC = $0100, SP = $FF [JACE: ApplesoftProgram.java:343–471]. Execution then runs
  stub → JSR ROM $D697 (RESTORE, sets TXTPTR $B8/$B9) → JMP ROM $D7E5 (RUN handler) →
  program at $0801.
- Design intent (ApplesoftProgram.java:327–342, verbatim): *"By letting the ROM do the
  ZP initialisation we avoid having to know and replicate every pointer the
  interpreter touches on cold start."*
- HIMEM = $BF00 is **JACE's no-DOS default** (`DEFAULT_HIMEM`,
  ApplesoftProgram.java:321) — a choice for a bare 64K-style machine, not necessarily
  what real Applesoft computes on a real //e.

### 4b. Running outside BASIC (e.g., ProDOS 8)

You control the zero page — but respect what the OS and firmware occupy.

**Zero page.**

| ZP range | Owner | Restored after use? | Source |
|---|---|---|---|
| $3A–$3F | disk-driver scratch (called by the MLI) | **NO** — not restored | S1 §3.3.1 |
| $40–$4E | MLI scratch | yes — MLI restores before completing the call | S1 §3.3.1 |

The ProDOS figures hatch $3A–$4F of main memory as "#Shared/#safe#" [S1/S2 Fig 3-1/5-1].
Page 0 is **protected in the system bit map at ProDOS start-up** — ProDOS "protects the
zero page, the stack, and the global page" by setting the corresponding bits
[S1 §3.3.3] — so the MLI's buffer allocator will not hand out page 0.

**Fixed locations you must not clobber.**

- **ProDOS global page $BF00–$BFFF** — the system's global variables, in the same
  location on every machine; the communication link between system programs and the OS
  [S1 §3.3.2].
- **MLI entry: `JSR $BF00`** — "the only address in the global page that you should
  ever call" (do not use JSPARE $BF03, DATETIME $BF06, SYSERR $BF09, SYSDEATH $BF0C,
  SERR $BF0F) [S2 §5.2.4].
- **System bit map $BF58–$BF6F** — 24 bytes, one bit per 256-byte page of $0000–$BFFF,
  bits in reverse order within each byte (bit 7 of $BF58 = page 0; bit 0 of $BF6F = last
  page before $C000). Initial listing: $BF58 = $C0 (pages 0, 1), last byte $01 (page BF)
  [S1 §3.3.3; S2 §5.1.4, §5.2.3]. Only three MLI calls affect it: OPEN, CLOSE, SET_BUF
  [S1 §3.3.3]. If you must force-clear it, close all files first and leave pages
  0, 1, 4–7, BF protected (zero page, stack, text, ProDOS global page) [S1 §3.3.3].
- **MACHID $BF98** — machine identification (bits above); informational, do not change
  [S2 §5.2.3]. *(Conflict: S2 §5.2.2.2 text says $BF96; the §5.2.3 listing and
  §5.2.2.3 code say $BF98 — this doc uses $BF98. See section 5, item 7.)*
- **IVERSION $BFFD** — set by your system program to its own version number [S2 §5.1.3].
- **KVERSION $BFFF** — set by ProDOS to its release id (listed initial value $02; do not
  read a ProDOS 8 version number out of it — section 5, item 8) [S2 §5.2.3].

**The ProDOS 8 model (no "OS Base"/"System 65").** Boot chain [S1 §3.1]: ROM reads the
loader from disk blocks 0–1 to $800; the loader finds the **PRODOS** file (type $FF,
the MLI), loads it at $2000, executes it; the MLI ascertains the memory size, moves
itself to its final location (figure only — no stated address, section 5 item 4),
detects devices/slots, sets up the global page; then it loads the first
**XXX.SYSTEM** file (type $FF) at $2000 and executes it. The MLI is entirely memory
resident [S1 §3.1].

**Placement rules.**

- System programs are **always loaded at $2000**; they may then relocate anywhere in
  **$0800–$BEFF** — 46848 bytes total on a 64K machine, max single load $8F00 (36608
  bytes) [S2 §5.1.1–5.1.2; S1 §3.3].
- The **BASIC.SYSTEM area ($9600–$BF00** — I/O buffers + the running SYS file under a
  normal ProDOS layout, per S3's ProDOS rows and the figure label anchored at $9600) is
  usable by a system program **only if BASIC is not being used** [S2 §5.1.1].
- Program pathname at start-up: length byte first, stored at $280 [S2 §5.1.5.1].
- **Quit:** `JSR $BF00` with call type $65 (QUIT); the MLI moves the dispatcher from
  language-card $D100–$D3FF to $1000 and jumps there; invalidate the power-up byte
  $3F4 (increment or decrement) [S2 §5.1.5.2].
- **RESET vector $3F2:** the system program must set it (the user can hit
  [CONTROL]-[RESET] at any time, including with files open) [S2 §5.1.5.1, §5.3.5].
- **Stack:** use no more than the upper 3/4 of page 1 (the interrupt handler saves the
  low 16 stack bytes only if the stack is >3/4 full); set SP = $FF at the warm-start
  entry point [S2 §5.2.1].

### 4c. Worked example: JACE's own $0100 bootstrap stub

JACE's `run basic` launches Applesoft through exactly this stub: a few ZP setups,
then hand off to ROM (section 4a). The source below is verbatim from
ApplesoftProgram.java:346–402, with the one runtime-computed value (`programEnd`)
instantiated to $0810 as a concrete example (JACE computes $0801 + program size).

```asm
!cpu 65c02
*= $0100

; Reset stack — gives CLR/RUN a clean page to work with
ldx  #$FF
txs

; TXTTAB ($67/$68) = program start
lda  #$01
sta  $67
lda  #$08
sta  $68

; HIMEM ($73/$74) = top of usable RAM
lda  #$00
sta  $73
lda  #$BF
sta  $74

; VARTAB ($69/$6A) = ARYTAB ($6B/$6C) = STREND ($6D/$6E) = program end
lda  #$10
sta  $69
sta  $6B
sta  $6D
lda  #$08
sta  $6A
sta  $6C
sta  $6E

; FRETOP ($6F/$70) = HIMEM (string heap starts at top)
lda  #$00
sta  $6F
lda  #$BF
sta  $70

; RESTORE ($D697) — sets TXTPTR ($B8/$B9) = TXTTAB-1
jsr  $D697

; JMP into RUN handler
jmp  $D7E5
```

Notes:

- The stub lives in the **stack page** ($0100) — safe only because SP is reset to $FF
  first (source comment, ApplesoftProgram.java:322–323: "Stub lives in the stack page;
  safe because we reset SP to $FF first").
- Role citations: SP (stub, lines 351–352 and `cpu.STACK = 0xFF` line 468); TXTTAB
  (stub, 355–358); HIMEM (stub, 361–364; `DEFAULT_HIMEM` line 321); VARTAB/ARYTAB/
  STREND (stub, 367–374); FRETOP (stub, 377–380); `JSR $D697` = ROM RESTORE, sets
  TXTPTR (stub, 382–383; const `APPLESOFT_RESTORE` line 325); `JMP $D7E5` = RUN handler
  entry (stub, 386; const `RUN_HANDLER_ENTRY` line 313).
- The stub's javadoc (lines 327–342) still says step 3 "JSRs to Applesoft CLR ($D665)"
  (line 336); the actual code JSRs RESTORE ($D697) and writes VARTAB/ARYTAB/STREND/FRETOP itself.
  The code is authoritative.

Test-compiled 2026-08-26:

```
$ acme -f apple -o stub.bin stub.asm
$ xxd -l 4 stub.bin
00000000: 0001 3100                                ..1.
```

Verified: 49 bytes at $0100 (53-byte file). Header `00 01 31 00`; body `a2 ff 9a a9 01
85 67 a9 08 85 68 a9 00 85 73 a9 bf 85 74 a9 10 85 69 85 6b 85 6d a9 08 85 6a 85 6c 85
6e a9 00 85 6f a9 bf 85 70 20 97 d6 4c e5 d7` (ends `20 97 d6` = `jsr $D697`,
`4c e5 d7` = `jmp $D7E5`).

## 5. Gaps & what is unverified

Carried forward explicitly — do NOT fill these from memory.

1. **No per-byte $FC–$FF low-RAM table.** None of the three web sources gives a
   byte-by-byte layout of the top of the 64K map. Low-RAM/vector addresses that DO
   appear: $280 (pathname), $3A–$3F (disk-driver scratch), $40–$4E (MLI scratch), $3F2
   (RESET vector), $3F4 (power-up byte), $03D0–$03FF (vector area as a whole), $1000
   (dispatcher target).
2. **No "zero page reserved for the OS" statement in any source.** The only sourced
   zero-page facts are MLI scratch $40–$4E (restored), disk-driver scratch $3A–$3F (not
   restored), and page-0 bit-map protection at start-up. The $0000–$002F low-ZP split in
   4a is convention + observed JACE behavior, not a sourced boundary, and JACE has no
   such constant (grep-verified).
3. **DHGR is absent from the web sources.** S3 lists only HGR page 1 ($2000–$3FFF) and
   page 2 ($4000–$5FFF). The aux=even / main=odd DHGR split in section 2 comes from the
   repo docs ([AG][CMD]) — attribute it accordingly.
4. **MLI final location is ambiguous.** S1 §3.1 says the MLI "moves itself to its final
   location, as shown in Figure 3-1" — but no source text states an address, and the
   figure's ASCII-art panels are ambiguous. Do not quote a specific MLI final address.
5. **Figure 3-1/5-1 panel ambiguity.** The caret + "This ROM area on IIc and IIe only!"
   at the main column's $C100 boundary points at the hatched $9600–$BF00 BASIC.SYSTEM
   region in the adjacent column; the art does not unambiguously say which bank the
   label qualifies. Rows 39–40 of the consolidated map transcribe those labels only.
6. **ROM-side facts are UNVERIFIED** (per the JACE Applesoft analysis caveats; no
   disassembly was performed): the reset-vector value at $FFFC/$FFFD in
   `apple2e.rom`; what the ROM COLD_START does to low ZP and the text screen page; the
   I/O routine addresses — JACE source contains no references to $FDED/$FDE8/$FD43/
   $F5D0 (grep-verified), so the quickstart's COUT $FDED rests on its passing run and
   the ROM dump, not on JACE source.
7. **MACHID address conflict within S2.** §5.2.2.2 text: "Check the MACHID byte at
   $BF96". The §5.2.3 listing and the §5.2.2.3 example code both use $BF98. This doc
   uses **$BF98** (listing + code agree).
8. **KVERSION initial value.** The S2 listing shows KVERSION = $02 in the ProDOS 8
   reference — a 2.x-style release-id byte. Do not infer a ProDOS 8 version number from
   it.
9. **S3's buffer wording is looser than S1/S2.** "Memory below $9600 can also be used
   for disk buffers by opening more files" [S3] is a looser statement of the same
   mechanism S1/S2 describe; prefer S1/S2 wording for buffer rules.
10. **JACE ZP count discrepancy.** The JACE Applesoft analysis summarizes its table as
    "53 distinct zero-page bytes" while its own enumeration totals 60; counting the
    listed ranges byte-for-byte gives 62 bytes (58 distinct if $AF–$CC is taken as one
    contiguous range). The address list itself is unchanged; treat derived counts
    with caution.
11. **S1 typo.** "while ProDOS is inn use" [sic] — verbatim from S1; do not "correct"
    other figure labels either.

---

**Evidence level of this doc.** Sections 1 and 4c carry assemble-level verification
(`acme` output, recorded per example). Run-level evidence (emulator transcript, screen
text) lives in `docs/jace/assembly-quickstart.md` and `docs/jace/applesoft.md`; the
memory-map, memory-bank-access, and zero-page facts in sections 2, 3, and 4 are
source-cited as tagged above.
