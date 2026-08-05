<!-- Loaded on demand from CLAUDE.md. -->

## Mockingboard / AY-3-8910 Sound Emulation

`jace.hardware.CardMockingboard` plus `jace.hardware.mockingboard.*` implement a
Mockingboard-C: two 6522 VIAs, each driving one AY PSG.

### The AY clock is 1022727 — read this before changing it

**This constant has been changed three times by three different people during one
effort. If you are about to change it again, you are almost certainly making the
same mistake they did.** The two numbers below are different *quantities*, not rival
estimates of one quantity.

| Constant | Value | What it is | What it clocks |
|---|---|---|---|
| `CardMockingboard.CLOCK_SPEED` | **1022727** | `14318181.8 / 14` — the slot bus clock | The AY oscillators (pitch) |
| `TimedDevice.NTSC_1MHZ` | **1020484** | `14318181.8 * 65 / 912` — CPU stretched-cycle average | CPU cycles, card tick pacing, 6522 timers |

**1022727 = crystal / 14.** The Apple II master oscillator is the NTSC colorburst
&times;4, 14318181.8 Hz. The bus clock wired to the peripheral slots is that divided
by 14. This is the clock a real Mockingboard's AY-3-8913 receives. It is the
canonical hardware number.

**1020484 = crystal &times; 65 / 912.** The 6502's *average* throughput once the
video logic's stretched cycle (one long cycle per 65-cycle scanline) is amortized
in. Same derivation as AppleWin's
`CLK_6502_NTSC = (_14M_NTSC * 65.0) / (65.0*14.0+2.0)`.

**Jace runs the entire machine at the average, including this card.** That is
technically incorrect for a slot card — the card's oscillator does not slow down
just because the CPU waits — and it means Jace's whole timebase is **~0.22% slow
(~3.8 cents flat)**. That inaccuracy is **known and deliberately accepted**; fixing
it properly would mean giving slot devices their own timebase, which is out of
proportion to a 3.8-cent error.

**The rule that follows: pretend the card runs at 1.0227 MHz and scale everything
against that number.** Do not "correct" `CLOCK_SPEED` down to `NTSC_1MHZ` on the
grounds that Jace runs everything at the average. Doing so applies the 0.22% error a
*second* time — once in the timebase, once in the oscillator — leaving tones a
further 3.8 cents flat. That was a real bug, fixed 2026-07.

`MockingboardClockTest` pins both numbers, records each one's derivation, and
asserts their difference is 2243, specifically to stop someone collapsing them into
one constant.

`R6522` has no clock constant of its own on purpose — it is ticked at the
motherboard's CPU rate, so one `tick()` is one timer count. Timers correctly follow
the stretched average, because the 6522 genuinely does see the same bus &Phi;2 the
CPU does.

### 6522: IER gates the IRQ *pin*, never the IFR *flag*

On real hardware — and in MAME's `6522via.cpp` — a timer expiry sets its IFR flag
unconditionally. `IER` is consulted in exactly one place, deciding whether the IRQ
line is asserted and whether IFR bit 7 (the summary bit) is set. Concretely:

- Flag bits 6/5 (T1/T2): set by the interrupt condition, regardless of IER.
- Bit 7: set from `IER & IFR`, so a masked-off flag must not claim the line.

Software that polls IFR with interrupts disabled is a standard card-detection
idiom (see the Skyfox comment in `handleFirmwareAccess`). Gating the flag on the
enable makes such a poll spin forever. Guarded by `R6522InterruptFlagTest`.

### AY reset writes $FF to register 7, not 0

MAME's `ay8910_reset_ym()` writes 0 to every register. Jace deliberately writes
`$FF` to register 7 because the mixer's six enable bits are **active-low** — a
literal 0 enables all six generators, making reset audible. `$FF` is the
all-disabled encoding. Do not "correct" this to match MAME literally.

### Mixer combines tone and noise with AND, not OR

Per MAME `ay8910.cpp:1110` the pre-DAC formula is
`(ToneOn | ToneDisable) & (NoiseOn | NoiseDisable)`. When both tone and noise are
disabled the channel output is **1**, not 0 — it becomes a DC source driven by the
amplitude register, which is how PCM sample playback works on this chip. An OR
here silences that path.

### Period 0 behaves as period 1 — except for the envelope

Tone and noise: period 0 is the same as period 1 (`TimedGenerator.setPeriod` via
`clocksAtPeriodZero()`). Envelope: period 0 is **half** of period 1
(`EnvelopeGenerator.clocksAtPeriodZero()` returns `stepsPerCycle() / 2`). MAME
ay8910.cpp:90-91 states this asymmetry explicitly. Neither case may be silenced.

Prescalers: tone `clock/(16*TP)`, noise `clock/(16*NP)`, envelope `clock/(256*EP)`.

### Verified-correct areas — do not "fix" these

Exhaustively measured against MAME and left unchanged:

- **All 16 envelope shapes.** Jace's formulation (`hold = ((shape ^ 8) & 9) != 0`,
  `start1high`/`start2high`/`oddEven`) is structurally unlike MAME's but produces
  an identical 33-sample level sequence for every one of the 16 shapes.
- **Register 13 restarts the envelope; 11/12 do not.** Matches hardware.
- **The noise LFSR.** Jace uses a Galois formulation; MAME uses Fibonacci
  (`bit0 ^ bit3`, tap position verified on real chips per ay8910.h:265-267). The
  output streams are the same maximal-length m-sequence — period 131071, 50% duty,
  identical run-length statistics — differing only in phase and polarity, which is
  inaudible. Rewriting it to match MAME byte-for-byte would be churn.
- **Register 7 polarity.** Already correctly active-low.

### The chip is an AY-3-8913 — 16 shared amplitude levels

Previously undocumented, now settled, because it determines the size and shape of
the amplitude table. A Mockingboard uses the **AY-3-8913**: the AY-3-8910's PSG core
in a 24-pin package with the parallel I/O ports omitted. Two independent citations:

- AppleWin's `Mockingboard.cpp` names the part throughout — `class AY8913`,
  `AY8913_Write`, `AY8913_Reset`, `NUM_AY8913_PER_SUBUNIT` — with the comment "AY1 is
  the primary AY-3-8913 connected to 6522" (GH#1192).
- MAME defines `ay8913_device` as `ay8910_device(..., PSG_TYPE_AY, 3, 0)`
  (`ay8910.cpp:1630-1631`): identical core, 3 sound streams, **0** I/O ports.

Because it is `PSG_TYPE_AY`, `ay8910.cpp:1578-1579` selects the same 16-entry
`ay8910_param` for **both** the tone and the envelope DAC, and `:1575` sets
`m_env_step_mask = 0x0f`. So: **16 levels, shared by tone and envelope.** The
YM2149's 32-entry `ym2149_param_env` does *not* apply here.

Jace's structure already matches — `EnvelopeGenerator` counts 0..15 and
`SoundGenerator.step` indexes the same 16-entry `VolTable` for both paths — so no
restructuring was needed. If you ever port this to a YM2149, the envelope path needs
32 levels and this is where to start.

### The volume table is measured hardware data — do not synthesize it

`buildMixerTable()` scales a fixed 16-entry `AY_MEASURED_LEVELS` table taken from
Matthew Westcott's December 2001 voltage measurements of a real AY-3-8910 (the
readings MAME cites for its active `ay8910_param`, `ay8910.cpp:678-722`), expressed
as swing above the level-0 floor so that level 0 is true silence.

It previously synthesized a uniform 3 dB/step curve. That is not what the chip does:
the measured steps range from **1.74 dB to 4.46 dB** and their size is not even
monotonic in the level. Every intermediate level sits *above* the uniform curve — by
up to +4.8 dB around levels 7-10 — which compresses the level-1-to-15 span from
42.1 dB to **39.6 dB**. Audibly, quiet passages and envelope decay tails are less
recessed than the synthesized curve made them. This was a human decision about
audible character, since it is not a correctness fix in the register-accuracy sense.

Note the table is the **measurements**, not MAME's table verbatim. MAME stores
equivalent output *resistances* and converts them through `build_single_table`, a
divider fitted in SwitcherCAD against the **ZX Spectrum's** output circuit — which
Jace does not model. No load resistance reproduces the raw readings exactly (best
residual 0.0056 of full scale at RL=1800; MAME's annotated RL=2000 leaves 0.011, a
4.0 dB error at level 1). Where the model and the measurement disagree, the
measurement wins. `VolumeTableTest` pins every level to within 0.001 of full scale,
which rejects both the old uniform curve and MAME's reconstruction.

### Writing tests for this subsystem

Two setup gotchas, both of which produce confusing failures:

1. **`Utility.setHeadlessMode(true)` first.** Anything reaching `R6522.tick()` or
   `Emulator.withComputer` will otherwise boot `Apple2e`, hit JavaFX in
   `Utility.loadIconLabel`, and throw `ExceptionInInitializerError`. Likewise,
   avoid `CardMockingboard.reconfigure()` in a unit test — construct
   `new PSG(base, clock, rate, name, mask)` directly.
2. **`CardMockingboard.VolTable` is a lazily-built static.** Tests touching
   `SoundGenerator.step` need `new CardMockingboard().buildMixerTable()` in a
   `@BeforeClass`, or they NPE — and they may pass by accident when run in a suite
   where another test built it first.

Note that `Pt3PlayerRegisterTest`, which compares AY register frames against
`vt3-cli`, is **blind to almost all of the above**: identical register values can
still produce wrong audio if the PSG core misinterprets them. On the reference
song, noise period, envelope period and envelope shape are always 0 and no
amplitude uses envelope mode. Cover the PSG with focused unit tests instead.

