# JACE Automation Recipes and Internals

Loaded on demand from `CLAUDE.md`. Full expect/stdin scripts, implementation details, and architecture notes.

## Complete Automation Example

Here's a full automation script using expect-style scripting:

```bash
#!/bin/bash
JACE_DIR="/Users/brobert/Documents/code/jace"
DISK_IMAGE="/Users/brobert/Documents/code/PLASMA/PLEIADES-MEGAFLASH-FPU.po"
TEST_PROGRAM="+FPUMF_AUTO_TEST"

cd "$JACE_DIR"

timeout 90 expect <<'EOF'
set timeout 30

# Launch JACE terminal mode
spawn mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal"

# Wait for JACE prompt
expect "JACE>"

# Boot the disk
send "bootdisk d1 $DISK_IMAGE\r"
expect "JACE>"

# Launch test program
send "key \"$TEST_PROGRAM\\n\"\r"
expect "JACE>"

# Run test cycles
send "run 3000000\r"
expect "JACE>"

# Capture screen output
send "showtext\r"
expect "=== End of Text Screen ==="

# Exit
send "qq\r"
expect eof
EOF
```

## Simpler Automation (stdin approach)

For non-interactive automation:

```bash
#!/bin/bash
JACE_DIR="/Users/brobert/Documents/code/jace"
DISK_IMAGE="/path/to/disk.po"

cd "$JACE_DIR"

mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" <<EOF
bootdisk d1 $DISK_IMAGE
key "+PROGRAM\\n"
run 2000000
showtext
qq
EOF
```

## Implementation Details

### Disk Operations

- Accesses `CardDiskII` via `Emulator.withMemory(memory -> memory.getCard(slot))`
- Uses `DiskIIDrive.insertDisk(File)` and `DiskIIDrive.eject()` methods
- Supports both drive 1 and drive 2 on any slot

### Text Screen Reading

- Reads from memory addresses $0400-$07FF (main text page 1)
- Handles interleaved row addressing (Apple II quirk)
- For 80-column mode: alternates reading auxiliary and main memory
- Character conversion handles:
  - High-bit ASCII (bit 7 set = normal display)
  - Flashing characters (bit 6 set, bit 7 clear)
  - Inverse characters (both bits clear)

### Boot Detection

- Polls PC register every 10ms
- Maximum wait: 10 million cycles
- Target: PC >= $2000 indicates boot complete
- Pauses emulation after reaching target

## Architecture Notes

### Terminal Class Hierarchy

```
JaceTerminal (base class)
  ├─ HeadlessTerminal (command-line focused)
  └─ UITerminal (GUI-integrated)

TerminalMode (interface)
  ├─ MainMode (system control)
  ├─ MonitorMode (debugging)
  └─ AssemblerMode (assembly input)
```

### Emulator Access Pattern

All emulator interactions use:
- `Emulator.withComputer(lambda)` - Access Apple2e computer
- `Emulator.withMemory(lambda)` - Access RAM directly
- `Emulator.whileSuspended(lambda)` - Atomic operations while paused

### Memory Operations

```java
// Read via the ACTIVE memory configuration (whatever softswitches currently map in)
byte value = memory.read(addr, RAMEvent.TYPE.READ_DATA, true, false);
```

**The last boolean of `RAM.read` is `requireSynchronization`, NOT an aux flag.**
`RAM.read` and `RAM.readRaw` both go through `activeRead`, so neither can select a
bank. To read a specific physical bank, go to the `PagedMemory` directly:

```java
RAM128k ram128k = (RAM128k) memory;
byte mainValue = ram128k.getMainMemory().readByte(addr);   // odd DHGR columns
byte auxValue  = ram128k.getAuxMemory().readByte(addr);    // even DHGR columns
```

This is what `MonitorMode.resolveBank()` does for the `M`/`X` prefixes and the
`memmain`/`memaux` commands. It touches no softswitches.

### `memaux` / `memmain` - Dump a Specific Bank (No Mode Switch)

```
memaux  <start> <end>    # AUX bank only  (alias: mx)
memmain <start> <end>    # MAIN bank only (alias: mm)
```

Hex dumps a range from an explicitly named bank, independent of softswitch state.
Use these instead of `mem` whenever the bank matters — `mem` follows the active
configuration and cannot distinguish the two banks.

In 80STORE+HIRES double-hi-res, `$2000-$3FFF` is interleaved: **aux holds the even
pixel columns, main the odd**. Verifying rendered output requires both:

```
memaux  2000 2027
memmain 2000 2027
```

The monitor-mode equivalents are the `X` and `M` address prefixes (`X2000.2027`).

## Testing Workflow

Typical automated test workflow:

1. **Boot disk** with test program
2. **Wait for prompt** (check screen for expected text)
3. **Inject keystrokes** to launch test
4. **Run cycles** to let test execute
5. **Capture screen** to verify results
6. **Parse output** for pass/fail indicators

