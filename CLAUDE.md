# JACE — Guide for Agents

JACE (Java Apple Computer Emulator) is a Java Apple II emulator with a scriptable terminal
mode (`--terminal`) used to automate Apple II software testing.

**This file is an index.** Detail lives in `docs/jace/*.md` — read only the file you need.

| Read this | When you are |
|---|---|
| `docs/jace/commands.md` | Looking up any terminal command's syntax or semantics (the full reference) |
| `docs/jace/setup-and-disks.md` | Launching JACE, choosing a slot, or hitting a disk-image problem (incl. the cadius 146,432-byte patch) |
| `docs/jace/debugging-guide.md` | Debugging 65C02 code: `$FC` debug opcodes, breadcrumbs, breakpoints, failure diagnosis, Language Card switches |
| `docs/jace/automation-recipes.md` | Writing a full automation script; or need implementation/architecture internals |
| `docs/jace/applesoft.md` | Working with Applesoft BASIC (cold-start without a disk, tokenizing, variable table layout) |
| `docs/jace/unit-tests.md` | Running `mvn test` on JACE itself (not the emulator REPL) |
| `docs/jace/mockingboard.md` | Touching Mockingboard / AY-3-8910 sound emulation |
| `docs/jace/changelog.md` | Wanting the history of changes to JACE and to these docs |

---

## Non-Negotiables

Read these four before doing anything. Each one has cost real debugging time.

1. **Always use Maven, never the native binary.** `/Users/brobert/Downloads/Jace` silently
   ignores `--terminal` and opens a window. All scripting goes through Maven.
2. **`run N` does NOT execute N cycles.** It free-runs for `N/1000` milliseconds with a
   **100 ms floor**, so any `N < 100,000` runs ~100,000+ cycles — up to 100x what you asked
   for. For exact stepping use `step`/`tick`; to stop at an address use `runto`; for frames
   use `runvbl`. See `docs/jace/commands.md`.
3. **Use slot 7 for ProDOS `.po` images.** Slot 6 emulates real floppy rotation — a ProDOS
   boot takes ~600 real seconds. Slot 7 (SmartPort) is instant.
4. **Always wrap invocations in `timeout`.** Programs under test hang; `timeout 90 ...` and
   check for exit code 124.

## Standard Invocation

```bash
cd ~/Documents/code/jace
timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" <<'EOF'
bootdisk d1 /path/to/disk.po 7
run 5000000
screenshot /tmp/frame.png
qq
EOF
```

`mvn -q javafx:run -Djavafx.args="--terminal"` also works. `qq` quits; `qqq` terminates the JVM.

## The 15 Commands You Will Actually Use

Full reference, including 20+ further commands, in `docs/jace/commands.md`.

| Command | Alias | Purpose |
|---|---|---|
| `bootdisk d1 <file> [slot]` | `bd` | Insert + reset + run until PC >= $2000 |
| `loadbin <file> <addr>` | `lb` | Load a binary straight into RAM (no disk needed) |
| `savebin <file> <addr> <size>` | `sb` | Dump memory to a file |
| `4000G` | — | Set PC and start executing (**async** — must follow with `run`/`expect`) |
| `step [count]` | `s` | Single-step instructions — **exact**, unlike `run` |
| `runto <addr>` | `rt` | Run until PC hits an address — exact |
| `runvbl` | `rv` | Advance to the next VBL edge; use before memory dumps |
| `run [count]` | `g` | Free-run for a while — approximate only, see caveat above |
| `expect "<text>" [secs]` | — | Poll the text screen until text appears (event-driven) |
| `key "<string>"` | `k` | Inject keystrokes (**`\n` is Return; `\r` sends the letter 'r'**) |
| `showtext` | `st` | Print the text screen (40/80 col auto-detected) |
| `screenshot <f.png> [--vbl]` | `ss2` | Render HGR/DHGR to PNG, headless — then `Read` the PNG |
| `mem <start> <end>` | — | Hex dump following current softswitches |
| `memaux` / `memmain` | `mx`/`mm` | Hex dump a **specific** bank, ignoring softswitches |
| `symbols <labelfile>` | `sym` | Load ACME labels so every command accepts label names |

Wozniak monitor syntax (`3800.3820`, `4000G`, `2000:A9 FF`, `X2000.2027`) works directly at
the `JACE>` prompt — no mode switch. `help <cmd>` gives built-in help, but **it is wrong in
places** (`run`, `move`, `compare`); `docs/jace/commands.md` records the true behaviour.

## Two Traps Worth Naming Here

- **DHGR is split across banks.** In 80STORE+HIRES, aux holds the **even** pixel columns of
  `$2000-$3FFF` and main the **odd**. `mem` follows softswitches and cannot distinguish
  them — use `memaux` *and* `memmain` (or `X`/`M` prefixes) whenever the bank matters.
- **Capture frame-coherently.** A bare `screenshot` can land mid-frame and tear a page still
  being drawn — this has produced at least one false defect report. Use `screenshot --vbl`,
  or `runvbl` before a memory dump.

## Debug Instrumentation, In One Line

JACE's `$FC` opcode prints to host stdout with no side effects on the Apple II:
`!byte $FC,$5C,$31` prints a character; **`!byte $FC,$5E,$00` prints the live accumulator**
(the only way to observe a *computed* value); `!byte $FC,$44,NN` dumps all registers.
Full table and ACME macros in `docs/jace/debugging-guide.md`.

## JACE's Own Test Suite

`mvn clean test` — **702 tests, 0 failures** as of 2026-08-05 (45 s). There is no failing
baseline, so **a failure is presumed to be your change**. Details and the one
environment-dependent exception: `docs/jace/unit-tests.md`.

## Known Limitations

1. **No asynchronous I/O** — commands block; you cannot watch the screen while emulation runs
2. **`expect` polls at 500 ms**, so fast programs may be detected slightly late
3. **Native binary has no terminal mode** — Maven only

## References

- JACE source: `src/main/java/jace/terminal/`
- Apple II memory map: https://www.kreativekorp.com/miscpages/a2info/memorymap.shtml
- Disk ][ controller: https://www.doc.ic.ac.uk/~ih/doc/stepper/others/example3/diskii_specs.html
