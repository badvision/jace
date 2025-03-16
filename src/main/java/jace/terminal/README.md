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

The Terminal operates in several different modes, each providing specific functionality:

### Main Mode

This is the default mode when you start the Terminal. It provides access to basic emulator functions.

Commands:
- `monitor` (`m`) - Enter monitor mode
- `assembler` (`a`) - Enter assembler mode
- `debugger` (`d`) - Enter debugger mode
- `swlog` (`sl`) - Toggle softswitch state change logging
- `swstate` (`ss`) - Display current state of all softswitches
- `registers` (`r`) - Display CPU registers
- `setregister` (`sr`) - Set a CPU register (A|X|Y|PC|S|P|FLAGS) value
- `reset` (`re`) - Reset the Apple II
- `step` (`s`) [count] - Step the CPU for count cycles (default: 1)
- `run` (`g`) [count] - Run the CPU for count cycles or until breakpoint (default: 1000000)
- `insertdisk` (`id`) d# - Insert disk image in drive # (1 or 2)
- `ejectdisk` (`ed`) d# - Eject disk from drive # (1 or 2)
- `loadbin` (`lb`) file addr - Load binary file at specified address (hex)
- `savebin` (`sb`) file addr size - Save binary data from memory to file
- `help/?` - Show this help
- `help/? <cmd>` - Show detailed help for a specific command
- `exit/quit` - Exit the Terminal

### Monitor Mode

Monitor mode allows you to examine and manipulate memory directly.

Commands:
- `examine` (`e`) addr [count] - Display memory at address (hex)
- `deposit` (`d`) addr value [value2...] - Write values to memory
- `fill` (`f`) addr end value - Fill memory range with value
- `move` (`m`) src dest count - Copy memory block
- `compare` (`c`) src dest count - Compare memory blocks
- `search` (`s`) start end value [value2...] - Search for byte sequence
- `disasm` (`l`) addr [count] - Disassemble memory
- `back` (`b`) - Return to main mode
- `help/?` - Show help
- `help/? <cmd>` - Show detailed help for a specific command
- `exit/quit` - Exit the Terminal

Traditional monitor syntax is also supported:
- `XXXX` - Examine 16 bytes from address XXXX
- `XXXX:YY ZZ` - Deposit bytes YY, ZZ at address XXXX
- `XXXXG` - Begin execution at address XXXX
- `M/X` prefix - Access main/auxiliary memory (e.g., `MXXXX`, `XXXX:YY`)

### Assembler Mode

Assembler mode allows you to input assembly language instructions directly.

Commands:
- `org addr` - Set origin address for assembly
- `list` - List current assembly buffer
- `clear` - Clear assembly buffer
- `assemble` - Assemble buffer to memory
- `save filename` - Save assembly buffer to file
- `load filename` - Load assembly from file
- `back` - Return to main mode
- `help/?` - Show help
- `exit/quit` - Exit the Terminal

Any other input is treated as 6502 assembly code and added to the buffer.

### Debugger Mode

Debugger mode provides advanced debugging capabilities.

Commands:
- `break addr` - Set breakpoint at address
- `clear [addr]` - Clear breakpoint(s)
- `list` - List all breakpoints
- `trace on|off` - Enable/disable instruction tracing
- `watch addr` - Add memory watch
- `unwatch [addr]` - Remove memory watch(es)
- `stack` - Display stack
- `back` - Return to main mode
- `help/?` - Show help
- `exit/quit` - Exit the Terminal

## Examples

### Examining Memory

```
JACE> monitor
MONITOR> examine 2000 16
$2000: 00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F
```

### Setting Register Values

```
JACE> registers
A: 00 X: 00 Y: 00 PC: 0100 SP: 01FF P: 00110000
JACE> setregister A FF
Register A set to FF
JACE> registers
A: FF X: 00 Y: 00 PC: 0100 SP: 01FF P: 10110000
```

### Running Code

```
JACE> step 10
Executed 10 cycles, PC now at $0109
JACE> run 1000
Executed 1000 cycles, PC now at $0432
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