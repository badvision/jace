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
        String original = addrStr;

        // Symbolic name with no bank prefix. Checked before prefix stripping so a
        // symbol like "mainloop" isn't mangled into "ainloop" by the M-prefix rule.
        // isHex() is consulted first so hex always wins, matching
        // SymbolTable.resolveAddressOrSymbol — a symbol named "abcd" needs ":abcd".
        if (addrStr.startsWith(":") || (!isHex(addrStr) && SymbolTable.isKnown(addrStr))) {
            return new AddressWithMode(SymbolTable.resolve(addrStr),
                MonitorMode.determineMemoryMode(null, defaultMode));
        }

        // Check for mode prefix
        if (addrStr.startsWith("M") || addrStr.startsWith("m")) {
            modePrefix = "M";
            addrStr = addrStr.substring(1);
        } else if (addrStr.startsWith("X") || addrStr.startsWith("x")) {
            modePrefix = "X";
            addrStr = addrStr.substring(1);
        }
        
        // Parse the address. Hex is attempted first, so "M2000" is unchanged; a
        // bank-prefixed symbol ("Xmainloop") falls through to symbol resolution.
        int address;
        try {
            address = Integer.parseInt(addrStr, 16);
        } catch (NumberFormatException notHex) {
            address = SymbolTable.resolve(original.startsWith(":") ? original : addrStr);
        }

        // Determine the mode
        MemoryMode mode = MonitorMode.determineMemoryMode(modePrefix, defaultMode);
        
        return new AddressWithMode(address, mode);
    }
    
    /** @return true if the token parses as a plain hex number */
    private static boolean isHex(String s) {
        try {
            Integer.parseInt(s, 16);
            return true;
        } catch (NumberFormatException notHex) {
            return false;
        }
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