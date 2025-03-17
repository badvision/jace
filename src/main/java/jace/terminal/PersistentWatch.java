package jace.terminal;

/**
 * Persists important state information for watches, breakpoints, and cheats
 */
public class PersistentWatch {
    final String name;
    final int address;
    final MemoryMode mode;
    
    PersistentWatch(String name, int address, MemoryMode mode) {
        this.name = name;
        this.address = address;
        this.mode = mode;
    }
}