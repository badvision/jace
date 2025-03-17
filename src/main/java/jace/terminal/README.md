# Jace Terminal

The Jace Terminal provides a command-line interface for interacting with the Jace Apple II emulator. It allows you to execute commands, inspect and modify the emulator state, and perform various operations without using the graphical interface.

## Starting the Terminal

There are three ways to start the Terminal:

1. **From the UI**: Click the "Open Terminal" button in the emulator's overlay menu, or use the keyboard shortcut `Ctrl+Shift+T`.

   > **Note**: If the "Open Terminal" button opens the IDE instead, you may need to recompile the project with `mvn clean compile` to regenerate the action registry that connects buttons to their functions. This ensures the Terminal button works correctly.

2. **From the command line with the emulator**: You can start the Jace application directly in Terminal mode:
   ```
   mvn javafx:run -Djavafx.args="--terminal"
   ```
   This launches the application in Terminal mode instead of the graphical interface.

3. **Starting in Terminal mode**: You can start the Terminal in command-line focused mode using Maven's JavaFX plugin:
   ```
   mvn javafx:run -Djavafx.mainClass=jace.terminal.HeadlessTerminal
   ```
   This launches Jace with the Terminal interface as the primary interaction method but still initializes the emulator with full JavaFX support, ensuring all commands work properly.

   > **Important**: The Terminal requires JavaFX classes to be available. Always use `mvn javafx:run` to ensure all dependencies are properly loaded, even when running in command-line mode.

## Command Shortcuts

All Terminal commands support single-letter shortcuts for faster typing. These shortcuts are shown in parentheses in the command listings below. You can also use `help <command>` or `? <command>` to get detailed help for any command.

## Terminal Modes

The Terminal operates in several different modes, each providing specific functionality. Each mode has its own command set, though some commands may appear similar but have different behaviors across modes.

### Main Mode Commands

This is the default mode when you start the Terminal. It provides access to basic emulator functions.

- `monitor` (`m`) - Enter monitor mode (includes all debugging functionality)
- `assembler` (`a`) - Enter assembler mode
- `swlog` (`sl`) - Toggle softswitch state change logging
- `swstate` (`ss`) [switch_name] - Display current state of all softswitches or specific switch
- `reset` (`re`) - Reset the Apple II
- `step` (`s`) [count] - Step the CPU for specified number of CYCLES (default: 1)
  - Note: This steps individual CPU clock cycles, not complete instructions
- `insertdisk` (`id`) d# - Insert disk image in drive # (1 or 2)
- `ejectdisk` (`ed`) d# - Eject disk from drive # (1 or 2)
- `loadbin` (`lb`) file addr - Load binary file at specified address (hex)
- `savebin` (`sb`) file addr size - Save binary data from memory to file
- `help` (`?`) - Show this help
- `help` (`?`) <cmd> - Show detailed help for a specific command
- `exit` / `quit` - Exit the Terminal
- `qq` - Quick exit from the terminal

### Monitor Mode Commands

Monitor mode allows you to examine and manipulate memory directly, and provides all debugging capabilities.

Memory Commands:
- `fill` (`f`) start end value - Fill memory range with value
- `move` (`m`) src dest count - Copy memory block
- `compare` (`c`) src dest count - Compare memory blocks
- `find` (`f`) start end value [value2...] - Search for byte sequence
- `disasm` (`l`) addr [count] - Disassemble memory

Debugger Commands:
- `pause` (`p`) - Pause emulation
- `resume` (`r`) - Resume emulation
- `cpu` - Display CPU state
- `registers` (`reg`) [register] [value] - Display or set CPU registers
  - With no arguments: Shows all register values
  - With arguments: Sets the specified register to the given value
  - Registers: A, X, Y, PC, S, N, V, B, D, I, Z, C
  - Values can be decimal, hex with $ prefix, or hex with 0x prefix
- `break` (`br`) addr - Add breakpoint at address
- `break remove addr` - Remove breakpoint
- `break clear` - Remove all breakpoints
- `breakpoints` (`bp`) - List all breakpoints
- `watch` (`w`) addr [name] - Add memory watch (triggers on READ or WRITE)
- `watch remove addr|name` - Remove watch
- `watch clear` - Remove all watches
- `watches` (`ws`) - List all watches
- `cheat` (`ch`) addr value - Add memory cheat
- `cheat remove addr` - Remove a cheat
- `cheat clear` - Remove all cheats
- `cheats` (`cs`) - List all cheats
- `step` (`s`) [count] - Step CPU INSTRUCTIONS (default: 1)
  - Note: This steps complete instructions, not individual cycles
- `run` (`g`) [count] [#breakpoint] - Run the CPU for count cycles or until breakpoint (default: 1000000)
- `runto` (`rt`) addr - Run until PC reaches address
- `back` (`b`/`q`) - Return to main mode

Direct Apple II Syntax:
- `XXXX` - Examine memory at address XXXX
- `XXXX:YY ZZ` - Deposit bytes YY, ZZ at address XXXX
- `XXXXG` - Begin execution at address XXXX
- `XXXXL` - Disassemble from address XXXX
- `L` - Continue disassembly from last address
- `XXXX.YYYY` - Show memory range from XXXX to YYYY
- `M/X` prefix - Access main/auxiliary memory (e.g., `MXXXX`, `XXXX:YY`)

### Assembler Mode Commands

Assembler mode allows you to input assembly language instructions directly.

- `org addr` - Set origin address for assembly
- `list` - List current assembly buffer
- `clear` - Clear assembly buffer
- `assemble` - Assemble buffer to memory
- `save filename` - Save assembly buffer to file
- `load filename` - Load assembly from file
- `back` - Return to main mode
- `help` (`?`) - Show help
- `exit` / `quit` - Exit the Terminal

Any other input is treated as 6502 assembly code and added to the buffer.

## Memory Access Modes

When accessing memory in Monitor mode, you can specify the memory bank:

- `MXXXX` - Access main memory bank at address XXXX
- `XXXXX` - Access auxiliary memory bank at address XXXX
- Default (no prefix) - Use active memory configuration

## Examples

### Main Mode

#### Stepping by Cycles
```
JACE> s 5
Stepping CPU for 5 cycles...
CPU stepped 5 cycles
CPU Registers:
  A: $00
  X: $00
  Y: $00
  PC: $0105
  S: $FF
  Flags: nv-bdizc
```

#### Checking SoftSwitch Status
```
JACE> ss
Current SoftSwitch states:
  TEXT = ON
  MIXED = OFF
  PAGE2 = OFF
  HIRES = OFF
  ALTCHARSET = OFF
  COL80 = OFF
  ...

JACE> swstate HIRES
HIRES = OFF
```

### Monitor Mode

#### Examining Memory
```
JACE> m
MONITOR> 2000
2000: 00
MONITOR> 2000.200F
2000: 00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F | ................
```

#### Working with Registers
```
MONITOR> registers
CPU Registers:
  A: $00
  X: $00
  Y: $00
  PC: $0100
  S: $FF
  Flags: nv-bdizc

MONITOR> registers A $FF
Register A set to $FF

MONITOR> reg
CPU Registers:
  A: $FF
  X: $00
  Y: $00
  PC: $0100
  S: $FF
  Flags: Nv-bdizc

MONITOR> reg PC $C000
Register PC set to $C000
```

#### Setting and Using Breakpoints
```
MONITOR> br C600
Breakpoint added at $C600

MONITOR> r
Emulation resumed
Breakpoint hit at $C600
C600: LDX #$03            A:00 X:00 Y:00 S:FF [nv-bdizc]

MONITOR> s 5
C602: STX $3C             A:00 X:03 Y:00 S:FF [nv-bdiz-] (1/5)
C604: CLD                 A:00 X:03 Y:00 S:FF [nv-bdiz-] (2/5)
C605: CLC                 A:00 X:03 Y:00 S:FF [nv-bdiz-] (3/5)
C606: LDA C700,X          A:00 X:03 Y:00 S:FF [nv-bdiz-] (4/5)
C609: STA $26             A:01 X:03 Y:00 S:FF [nv-bdiz-] (5/5)
```

#### Using Watches and Cheats
```
MONITOR> w 300 zero_page_ptr
Watch added for zero_page_ptr at $0300

MONITOR> r
Emulation resumed
Watch [zero_page_ptr] $0300: READ $20
0800: LDA $0300           A:00 X:03 Y:00 S:FF [nv-bdizc]

MONITOR> watch 301 data_byte
Watch added for data_byte at $0301
Watch [data_byte] $0301: WRITE $00 -> $42
0805: STA $0301           A:42 X:03 Y:00 S:FF [nv-bdiz-] 

MONITOR> ch 02F0 42
Cheat added: $02F0 = $42

MONITOR> 2F0
02F0: 42
```

#### Running to an Address
```
MONITOR> rt 0C00
Running until PC = $0C00...
PC reached $0C00
0C00: LDA #$00            A:42 X:03 Y:00 S:FF [nv-bdizc]
```

## Programmatic Usage

The Terminal can also be used programmatically by creating an instance of `JaceTerminal` with appropriate input and output streams:

```java
BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
PrintStream output = System.out;
JaceTerminal terminal = new JaceTerminal(reader, output);
terminal.run();
```

This allows for integration with other tools or custom interfaces. 