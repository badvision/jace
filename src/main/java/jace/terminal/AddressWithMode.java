package jace.terminal;

/**
 * Represents an address with its associated memory mode
 */
public class AddressWithMode {
    private final int address;
    private final MemoryMode mode;
    
    /**
     * Creates a new AddressWithMode with the specified address and mode
     * 
     * @param address The memory address
     * @param mode The memory access mode
     */
    public AddressWithMode(int address, MemoryMode mode) {
        this.address = address;
        this.mode = mode;
    }
    
    /**
     * Parses an address string that may have a mode prefix (M or X)
     * 
     * @param addrStr The address string to parse
     * @return An AddressWithMode object
     * @throws NumberFormatException if the address is not a valid hex number
     */
    public static AddressWithMode parse(String addrStr, MemoryMode defaultMode) {
        String modePrefix = null;
        
        // Check for mode prefix
        if (addrStr.startsWith("M") || addrStr.startsWith("m")) {
            modePrefix = "M";
            addrStr = addrStr.substring(1);
        } else if (addrStr.startsWith("X") || addrStr.startsWith("x")) {
            modePrefix = "X";
            addrStr = addrStr.substring(1);
        }
        
        // Parse the address
        int address = Integer.parseInt(addrStr, 16);
        
        // Determine the mode
        MemoryMode mode = MonitorMode.determineMemoryMode(modePrefix, defaultMode);
        
        return new AddressWithMode(address, mode);
    }
    
    /**
     * @return The memory address
     */
    public int getAddress() {
        return address;
    }
    
    /**
     * @return The memory mode
     */
    public MemoryMode getMode() {
        return mode;
    }
    
    /**
     * @return String representation with mode prefix if applicable
     */
    @Override
    public String toString() {
        String modePrefix = (mode == MemoryMode.MAIN) ? "M" : (mode == MemoryMode.AUX) ? "X" : "";
        return String.format("%s$%04X", modePrefix, address);
    }
}