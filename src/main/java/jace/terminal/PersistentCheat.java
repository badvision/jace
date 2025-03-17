package jace.terminal;

/**
 * Persists cheat information
 */
public class PersistentCheat {
    final int address;
    final int value;
    final MemoryMode mode;
    
    PersistentCheat(int address, int value, MemoryMode mode) {
        this.address = address;
        this.value = value;
        this.mode = mode;
    }
}