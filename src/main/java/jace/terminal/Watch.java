package jace.terminal;

import jace.Emulator;
import jace.core.RAMEvent;
import jace.core.RAMListener;

/**
 * Watch class to track memory changes
 * 
 */
public class Watch {
    private final MonitorMode monitorMode;
    final int address;
    final String name;
    private final MemoryMode mode;
    private final RAMListener readListener;
    private final RAMListener writeListener;
    
    public Watch(MonitorMode monitorMode, String name, int address, MemoryMode mode) {
        this.monitorMode = monitorMode;
        this.name = name;
        this.address = address;
        this.mode = mode;
        
        // Create a RAM listener to watch this address
        Boolean auxFlag = getAuxFlag();
        
        // Create separate listeners for reads and writes
        readListener = Emulator.withMemory(ram -> {
            return ram.observe("Watch-Read: " + name, RAMEvent.TYPE.READ, address, auxFlag, 
                event -> {
                    this.monitorMode.output.printf("Watch [%s] $%04X: READ $%02X%n", 
                        name, address, event.getNewValue() & 0xFF);
                    // Show current CPU state
                    this.monitorMode.displayCurrentInstruction();
                });
        }, null);
        
        writeListener = Emulator.withMemory(ram -> {
            return ram.observe("Watch-Write: " + name, RAMEvent.TYPE.WRITE, address, auxFlag, 
                event -> {
                    this.monitorMode.output.printf("Watch [%s] $%04X: WRITE $%02X -> $%02X%n", 
                        name, address, event.getOldValue() & 0xFF, event.getNewValue() & 0xFF);
                    // Show current CPU state
                    this.monitorMode.displayCurrentInstruction();
                });
        }, null);
    }
    
    /**
     * Get the auxiliary memory flag for RAM event filtering
     * 
     * @return The auxiliary memory flag (null for active, false for main, true for aux)
     */
    private Boolean getAuxFlag() {
        if (mode == MemoryMode.MAIN) {
            return false;
        } else if (mode == MemoryMode.AUX) {
            return true;
        } else {
            return null;
        }
    }
    
    public void remove() {
        if (readListener != null) {
            Emulator.withMemory(ram -> ram.removeListener(readListener));
        }
        if (writeListener != null) {
            Emulator.withMemory(ram -> ram.removeListener(writeListener));
        }
    }
    
    @Override
    public String toString() {
        String modePrefix = mode == MemoryMode.MAIN ? "M" : mode == MemoryMode.AUX ? "X" : "";
        return String.format("%s: %s$%04X", name, modePrefix, address);
    }
}