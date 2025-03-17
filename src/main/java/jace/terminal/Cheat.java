package jace.terminal;

import jace.terminal.MonitorMode.MemoryMode;

/**
 * Cheat class to store cheat information
 */
public class Cheat {
    final int address;
    final int value;
    final MemoryMode mode;
    
    public Cheat(int address, int value, MemoryMode mode) {
        this.address = address;
        this.value = value;
        this.mode = mode;
    }
    
    /**
     * Get the auxiliary memory flag for RAM event filtering
     * 
     * @return The auxiliary memory flag (null for active, false for main, true for aux)
     */
    public Boolean getAuxFlag() {
        if (mode == MemoryMode.MAIN) {
            return false;
        } else if (mode == MemoryMode.AUX) {
            return true;
        } else {
            return null;
        }
    }
    
    @Override
    public String toString() {
        String modePrefix = mode == MemoryMode.MAIN ? "M" : mode == MemoryMode.AUX ? "X" : "";
        return String.format("%s$%04X = $%02X", modePrefix, address, value);
    }
}