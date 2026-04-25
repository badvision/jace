package jace.terminal;

import java.io.PrintStream;

/**
 * Decouples Watch from MonitorMode — Watch only needs output and instruction display,
 * not the full MonitorMode class.
 */
interface WatchCallback {
    PrintStream getOutput();
    void displayCurrentInstruction();
}
