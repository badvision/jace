<!-- Loaded on demand from CLAUDE.md. -->

# JACE Change Log

### 2026-08-27 — Advanced 6502 assembly guide

- New agent doc `docs/jace/advanced-assembly.md`: the advanced companion to
  `docs/jace/assembly-quickstart.md`. Covers the ACME compile-to-load pipeline; the
  Apple //e memory map as a consolidated 40-row table sourced from the ProDOS 8 Technical
  Reference (ch. 3 "Memory Use" and ch. 5) and the kreativekorp memory map, plus
  //e-specifics and JACE operating notes; and the zero-page usage strategy — cooperating
  with Applesoft vs running outside BASIC/ProDOS 8, with JACE's own Applesoft $0100
  bootstrap stub as the worked example. Every address carries a source tag (doc, web
  reference, or JACE source); what could not be verified is marked UNVERIFIED in section
  4 and left out rather than filled from memory.
- QA-verified: 162 addresses cross-checked, all examples test-compiled, live JACE terminal
  run PASS. QA verdict was SHIP-WITH-FIXES — all 3 fixes were applied and re-verified.

### 2026-08-26 — 6502 assembly quickstart + verified hello-world-asm test

- New `docs/jace/assembly-quickstart.md`: ACME 0.97 workflow (the project assembler; CLI
  `acme -f apple -o OUT IN`, no `-n`; `* = $800` origin; `!text "..."` for multi-character
  strings), the 4-byte little-endian apple header (`00 08 1d 00` for the 29-byte $800
  program) and the `tail -c +5` strip, the run-in-JACE command sequence with the
  file-redirect stdin pattern (a heredoc placed after a pipe binds to `tee`, not mvn), and
  the Apple-II-not-C64 notes. Console output is `JSR $FDED` (COUT, char in A), verified
  three ways (`docs/jace/debugging-guide.md:102`, the passing run, and the ROM dump with
  $FD47 = `20 ED FD`); $FD43 is explicitly ruled out as an entry point (mid-instruction in
  JACE's ROM per the $FD40–$FD4E dump), and the doc forbids $FD43/$3D11 without a verified
  test.
- New `examples/hello-world/hello.asm` (29 bytes at $800) and
  `examples/hello-world/hello-world-asm-test.sh` (exit 0=pass, 1=fail, 2=ACME missing,
  124=hang) — verified PASS: "HELLO WORLD confirmed on emulated text screen (expect match
  + showtext)".
- `AGENTS.md`: new index row for the quickstart; non-negotiable #5 "JACE is an Apple II,
  not a C64" (no PETSCII, no high-bit ASCII, no $C000 screen; plain 7-bit ASCII, text
  screen $0400–$07FF, console output via JSR $FDED); "Read these four" → "Read these
  five". (AGENTS.md is the repo's symlink to `CLAUDE.md` — only these two stated changes
  were made to that content.)

### 2026-08-25 — Hello-world-in-BASIC quickstart documented

- `docs/jace/applesoft.md` no-disk test workflow gained the facts an agent previously had to
  rediscover the hard way: the Applesoft prompt on JACE is `]` with **no "READY." banner**
  (that is C64 BASIC 2.0 — do not `expect "READY"`), the literal `expect` output strings for
  script gating (`Match found after Nms` on success, `Timeout waiting for: "<string>"` on
  timeout), and `screenshot --vbl` in the proven sequence — text mode renders correctly
  (white bg, black text) and is valid evidence for text programs.
- `docs/jace/commands.md`: `screenshot` section now says text-mode screens are captured
  correctly too, not just HGR/DHGR.
- `CLAUDE.md` (symlinked as `AGENTS.md`): one-line pointer to the hello-world quickstart and
  the re-runnable acceptance test `./hello-world-test.sh [evidence_dir]` (exit 0=pass,
  1=fail, 124=hang) at the repo root, which drives `hello.bas` (`10 PRINT "HELLO WORLD"`) via
  Maven, no disk image.
- Re-verified 2026-08-25: `./hello-world-test.sh` exits 0; transcript shows `Loaded 1 lines
  (23 bytes)`, `Match found after 106ms`, and a `showtext` dump with `HELLO WORLD` plus the
  `]` prompt. Evidence: `/tmp/agents/jace-hello-world/iteration-1/`.

### 2026-08-12 — `run basic`, and headless-startup ROM fix

- Added `run basic`: resets the Applesoft interpreter's zero-page workspace and jumps
  directly into the interactive main loop ($D43C), landing at a working `]` prompt
  instantly. `ApplesoftProgram.runBasic()`, reusing `executeProgram()`'s stub-injection
  logic. Documented in `docs/jace/applesoft.md` and `docs/jace/commands.md`.
- Fixed `JaceLauncher`'s `--terminal` startup path to explicitly call `Apple2e.reconfigure()`
  once before the terminal loop starts, guaranteeing ROM is fully loaded and the memory
  configuration is fresh before any command can run.
- Renamed the `AGENT.md` symlink to `AGENTS.md` (was pointing at the wrong filename for
  the convention tools actually look for).

### 2026-08-05 (b) — Progressive-disclosure restructure

- **Split `CLAUDE.md` from 1768 lines to ~108.** It is auto-loaded into every agent's context
  in this repo, so its length was a standing, unavoidable context cost on every session. It is
  now a thin index: the four non-negotiables, the standard invocation, the ~15 commands
  actually used, and a table saying which `docs/jace/*.md` file to read for what.
- Detail moved to `docs/jace/`: `commands.md` (full command reference, with a contents map and
  an alias-collision table), `setup-and-disks.md`, `debugging-guide.md`,
  `automation-recipes.md`, `applesoft.md`, `unit-tests.md`, `mockingboard.md`, `changelog.md`.
- Two caveats are **restated** in the index rather than only linked, because they cause errors
  when unread: `run`'s 100 ms floor, and the DHGR aux/main pixel-column split.
- Dropped as stale: "Graphics modes not supported / only text mode can be captured" (contradicted
  by `screenshot`/`ss2`), and the "capture command" and "graphics screen capture" future-work
  entries (both implemented). Replaced with a `Not Yet Implemented` section listing what is
  genuinely absent (`waitpc`, `script`, async I/O).
- Reconciled two contradictory suite-runtime claims: measured 45 s, not "5+ minutes".

## 2026-08-05
- **Corrected the `run` documentation, which was wrong.** `run N` does not execute N cycles:
  `MainMode.runCPU` converts N to milliseconds (`N/1000`) with a **100 ms floor** and
  free-runs the emulator for that wall-clock duration. Every `run N` for N < 100,000 therefore
  runs ~100 ms — 100,000+ cycles at ~1 MHz, roughly 100x an agent's small request. This had
  already cost real debugging time on the Pitfall! port. Added the correct cycle-accurate
  alternatives table (`step`/`tick`/`runto`/`runvbl`/`expect`), fixed the built-in `help run`
  text, and left a known-wart comment at the floor. **Timing logic itself unchanged** —
  altering the semantics would affect existing tests and callers.
- Documented `$FC $5E` and `$5F`, previously undocumented and the most useful pair in the
  set: `$5E` prints the **runtime accumulator** as hex, so it is the only $FC subcommand that
  can observe a *computed* value (`$50`/`$5B`/`$5C` can only print a compile-time constant
  baked into the operand). `$5F` ends the line and flushes. Both write to
  `MOS65C02.debugOut`, which a Java harness can retarget. Added `debug_a` / `debug_eol` /
  `debug_byte` ACME macros.
- Documented `$FC $64 NN` (delegates to `RAM.performExtendedCommand`) and its one
  implemented subcommand in `RAM128k`: `$DA`, dump active read/write bank mapping for all 256
  pages — noting the per-page lines go to `java.util.logging` at INFO, not stdout.
- Recorded the $FC operand byte order explicitly: absolute-addressing operand, so
  `param1 = address & 0xFF` is the **first** byte after the opcode (the command selector) and
  `param2 = address >> 8` is the second (the argument). The existing tables' byte order was
  already correct; it just wasn't stated.
- Documented 20 commands that existed in code but not here: main-mode `symbols`/`sym`,
  `cmpmem`/`cm`, `poke`, `saveauxbin`/`sab`, `saveauxrambin`/`sarb`, `swstate`/`ss`,
  `swlog`/`sl`, `speed`/`sp`, `nohints`, `charlog`/`cl`, `rdb`/`cy`/`cyrene`, `assembler`/`a`,
  `debugger`/`d`; monitor-mode `fill`/`f`, `move`/`m`, `cycles`, `breaklist`/`bl`,
  `watchlist`/`wl`, `cheat`/`ch`, `cheatlist`/`cl`, `debug`/`dbg`.
- Documented the Wozniak monitor pattern syntax that was entirely absent: `<addr>`,
  `<addr>.<addr>`, `<addr>:<val>...`, `<addr>G`, `<addr>L`, bare `L`, and the `M`/`X` bank
  prefixes that force MAIN/AUX regardless of softswitch state.
- **Found a second lie in the built-in help**: `help move` and `help compare` state the
  `count` argument is hex and give `move 2000 4000 800 - Copy 2048 bytes`. The
  implementation uses `Integer.parseInt(args[2])` — `count` is **DECIMAL** for both, while
  `fill`'s `value` genuinely is hex. Documented the true radix per command; help text left
  as-is pending a repo-owner decision.
- Noted the alias collisions that bite: `ss` is `swstate` (screenshot is `ss2`), monitor `b`
  is `break` not `back`, and `cl` is `charlog` in main mode but `cheatlist` in monitor mode.
- **Replaced the stale "known pre-existing test failures" list.** A full `mvn test` on this
  branch reports **702 tests, 0 failures, 0 errors, 7 skipped, BUILD SUCCESS in 45 s**. The
  named `CardSSCTest`/`CardSSCRegisterTest`/`TerminalFeatureTest` failures are fixed. There
  is no failing baseline — a failure is now presumed to be your own. Only
  `TerminalFeatureTest.testStartupWithMassStorageDisk` remains environment/order dependent
  (needs `/Users/brobert/Downloads/ProDOS_2_4_3.po`). Also corrected the recorded suite
  runtime from "5+ minutes" to the observed 45 s.

### 2026-07-09
- Fixed vaporlock/beam-racing hang: `Video.java`'s scanner address lookup tables (`textOffset`/`hiresOffset`) were sized to 192 entries (visible screen lines only); extended to the full 262-line `TOTAL_LINES` so vertical blanking now generates real hardware "screen hole" addresses instead of recycling visible-row addresses. Previously this caused vaporlock-style floating-bus timing probes (e.g. Lancaster/Elliott techniques) to hang forever waiting for byte patterns that never appeared during blanking. New `calculateBlankingScannerOffset()` ported from MAME PR #15247 (mamedev/mame, "apple2video: emulate softswitch-specific delays; improve read_floatingbus()"), specifically its `a2_video_device::scanner_address()` formula. Known limitation: the formula is only valid as a per-scanline-start constant for horizontal offsets 0-7 within a blanking line (real hardware's address formula wraps mod-16 at offset 8) — sufficient for the vaporlock probes tested against, not full per-pixel floating-bus accuracy throughout all of blanking.
- Added video softswitch propagation delay: `SoftSwitches.java`, `VideoSoftSwitch.java`, `Video.java` — new `Video.scheduleModeChange()`/`applyDueModeChanges()` deferred-apply mechanism so video mode softswitch writes (TEXT, MIXED, PAGE2, HIRES, AN3/DHIRES, 80COL, ALTCHARSET, 80STORE) take effect after a hardware-measured number of CPU cycles instead of instantaneously. Previously mode changes applied synchronously on write, causing beam-racing/split-screen programs (e.g. text/graphics window-split demos) to render mode boundaries one column too far left. Delay values derived from MAME PR #15247's `delayed_update()` call sites, adjusted for that PR's `m_delay_bias` term: TEXT/MIXED=2, PAGE2/HIRES/80STORE=1, AN3(DHIRES)/80COL/ALTCHARSET=0. Covered by new test `VideoModeDelayTest.java`.
- Fixed CPU dropped-interrupt bug: `MOS65C02.java`'s `processInterrupt()` and the `CLI` opcode handler both unconditionally cleared `interruptSignalled` regardless of the CPU's `I` (interrupt-disable) flag. On real 6502/65C02 hardware `/IRQ` is a level-held line — an interrupt that arrives while masked (e.g. during `SEI`) must stay pending and be serviced on the next `CLI`, not be silently dropped. Fixed so `interruptSignalled` is only cleared once the interrupt is actually serviced. Found incidentally while investigating an unrelated hang in a Mockingboard-timer-driven test program. Covered by new test `MOS65C02Test.testMaskedInterruptStaysPendingUntilUnmasked`.
- Fixed Mockingboard envelope generator pitch: `EnvelopeGenerator.stepsPerCycle()` returned 8, identical to the tone generator's `stepsPerCycle()`. On real AY-3-8910 hardware the envelope counter advances at half the rate of the tone counter for the same period register value (confirmed against MAME's ay8910.cpp `m_step=2` for classic AY-3-8910, vs `m_step=1` for the later YM2149), matching the datasheet formulas (tone freq = clock/(16×TP), envelope freq = clock/(256×EP)). This bug made envelope-modulated notes play a full octave too sharp. Fixed by changing `EnvelopeGenerator.stepsPerCycle()` to return 16. Covered by new test `EnvelopeGeneratorPeriodTest.java`.
- Added "Running the Java Unit Test Suite (`mvn test`)" section (this is unrelated to the
  terminal-automation workflow above — it's for JUnit tests under `src/test/java`)
- Documented a stale-class gotcha: editing a file outside the Edit tool (sed, mv, git
  stash/pop) can leave Maven's incremental compiler serving old `.class` output even after
  the source file visibly shows the fix; always `mvn clean test` to verify a source change
  actually took effect before trusting a test result
- Documented that a full `mvn clean test` run takes several minutes under JaCoCo — run it
  as a background task with a generous timeout, don't poll aggressively, and check for an
  already-running `surefire` process before launching a second one
- Recorded the current list of known pre-existing test failures (`CardSSCTest`,
  `CardSSCRegisterTest`, four `TerminalFeatureTest` methods) so future agents don't mistake
  baseline-broken tests for regressions caused by their own change, and documented the
  `git stash` isolation technique used to verify this

### 2026-03-02
- Added "Running Jace: Native Binary vs Maven" section documenting `/Users/brobert/Downloads/Jace` (Gluon GraalVM native binary)
- Native binary finding: `headless` parameter is NOT supported, runs with display window, throws harmless `MacAccessible` error but boots normally
- Noted native binary does not support terminal/scripting mode; Maven required for automation
- Added "Tokenizing Applesoft BASIC Programs" section documenting the built-in `ApplesoftProgram.fromString()` Java API; clarified there is no terminal command for tokenizing, and described the inject-then-`savebin` workflow to produce a raw binary file

### 2026-07-27
- Added "Mockingboard / AY-3-8910 Sound Emulation" section: the 1022727 vs 1020484 clock
  distinction, the 6522 IER/IFR flag-vs-pin rule, why AY reset writes $FF to register 7,
  the AND (not OR) tone/noise mix, period-0 semantics, and the verified-correct areas
  (all 16 envelope shapes, the noise LFSR, register 7 polarity) that must not be churned
- Documented two test-setup gotchas: `Utility.setHeadlessMode(true)` before anything that
  reaches `R6522.tick()`/`Emulator.withComputer`, and `CardMockingboard.VolTable` being a
  lazily-built static that needs `buildMixerTable()` in a `@BeforeClass`
- Noted that `Pt3PlayerRegisterTest` register-frame comparison cannot detect PSG core
  defects — identical register values can still produce wrong audio
- Fixed the AY oscillator clock: `CardMockingboard.CLOCK_SPEED` was `TimedDevice.NTSC_1MHZ`
  (1020484, the CPU's stretched-cycle average), making every tone ~3.8 cents flat on top of
  the ~0.22% Jace already loses by running the whole machine at that average. Now 1022727 =
  `14318181.8 / 14`, the slot bus clock a real Mockingboard's AY receives. The global
  stretched-average inaccuracy is documented as knowingly accepted. Covered by
  `MockingboardClockTest` (7 tests), which pins *both* numbers and records each one's
  derivation so they cannot be mistaken for rival estimates of the same quantity — this
  constant had been changed three times by three people
- Fixed 6522 IFR semantics: `R6522.tick()` only set the T1/T2 interrupt flags when the
  corresponding IER enable was set, so software polling IFR with interrupts disabled (a
  documented card-detection idiom) would spin forever. Flags are now set by the interrupt
  condition unconditionally, per MAME `6522via.cpp t1_tick()`, and IER is consulted only
  where it belongs: asserting the IRQ pin and computing IFR bit 7 (`m_ier & m_ifr & 0x7f`,
  per `output_irq()`). Also removed a dead `R6522.SPEED` constant that was never read.
  Covered by new `R6522InterruptFlagTest` (7 tests, 5 of which fail against the old code)
- Added characterization coverage for previously untested areas, all of which passed
  unmodified — reported as verified-correct rather than fixed: `R6522TimerModeTest`
  (10 tests: T1CH start+load vs T1LH, one-shot vs free-run, period = latch+1, counter and
  latch readback, T2 always one-shot) and `DualAyAddressingTest` (8 tests: $00/$80 base
  registers, per-chip latching, no cross-talk, independent reset, shared clock)
- Replaced the synthesized uniform 3 dB/step amplitude curve with Westcott's measured
  AY-3-8910 levels, and documented that the chip is an AY-3-8913 with 16 levels shared by
  tone and envelope (previously undocumented, and load-bearing for the table's size).
  Covered by new `VolumeTableTest` (9 tests)
- Verified against MAME and left unchanged: all 16 envelope shapes (33-sample level
  sequence identical despite a structurally different formulation), register 13 restarting
  the envelope while 11/12 do not, the noise LFSR (Galois vs MAME's Fibonacci — same
  m-sequence, differing only in phase and polarity), and register 7's active-low polarity

### 2026-03-01
- Added "Reaching Applesoft BASIC Without a Disk Image" — `E000G` cold-start, `FF69G` warm-start
- Documented `expect` first-poll behavior (returns immediately if string already on screen)
- Added caution against using prompt characters (`]`, `*`) as readiness signals with `expect`
- Added WARNING near `key`/`type`: `\r` sends the letter 'r', not carriage return; use `\n`
- Added "Testing Clean Exit to BASIC" section: sentinel lines, soft switch check at $C01A, echo-based responsiveness testing
- Added diagnosis entry for "`CALL addr` never returns to BASIC": JSR to ROM routine that ends in JMP (e.g. `$FB39` → `JMP $C100`), breakpoint strategy at `$C100`, fix guidance

### 2026-02-11
- Documented Debug NOP ($FC) extended opcode: console output, register dumps, instruction tracing
- Added ACME macro examples for debug NOP commands
- Documented `expect`, `waitkey`, `type`, `loadbin`, `savebin` commands (were implemented but undocumented)
- Added complete Monitor Mode command reference
- Added Debugging Guide with proven patterns from SectorC65 compiler testing
- Added Language Card soft switch quick reference
- Added timeout wrapper best practices
- Updated Known Limitations (removed items that are now implemented)

### 2025-01-30
- Added `bootdisk` command for automated boot workflows
- Added `showtext` command for text screen capture
- Enhanced `insertdisk`/`ejectdisk` with proper implementation
- Created this documentation

---

*This automation infrastructure enables Claude Code to autonomously test Apple II software on the JACE emulator without manual intervention.*
