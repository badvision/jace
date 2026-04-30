package jace.ipc;

import java.util.HashMap;
import java.util.Map;

import jace.core.Debugger;

/**
 * Translates Cyrene's text-format breakpoint table to Jace Debugger integration.
 *
 * Text format (one breakpoint per line):
 *   {@code <id> <status> <type> <address> [conditions]}
 *
 *   Fields:
 *   - id:      integer, unique breakpoint identifier
 *   - status:  E (enabled) or D (disabled)
 *   - type:    $ for PC-address breakpoint; other types are ignored
 *   - address: hex address, optionally prefixed with '$' (e.g. $4000 or 4000)
 *   - conditions: optional trailing tokens, ignored by this implementation
 *
 * Only enabled ('E') PC-address ('$') breakpoints are registered with the Debugger.
 */
class BreakpointBridge {

    private final Debugger debugger;

    /** Maps breakpoint id -> PC address for all currently enabled breakpoints. */
    private final Map<Integer, Integer> breakpointMap = new HashMap<>();

    BreakpointBridge(Debugger debugger) {
        this.debugger = debugger;
    }

    /**
     * Parses multi-line breakpoint text and registers enabled PC-address breakpoints
     * with the Jace Debugger.  Clears all existing breakpoints first.
     *
     * Supports the real Cyrene semicolon-delimited wire format:
     *   {@code <id>;Address=<bank>/<addr>;Execute;Action:Stop;Repeat:Always}
     *
     * @param breakpointText newline-delimited breakpoint definitions
     */
    void updateFromText(String breakpointText) {
        clearBreakpoints();

        if (breakpointText == null || breakpointText.isBlank()) {
            return;
        }

        for (String line : breakpointText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            parseLine(trimmed);
        }
    }

    /**
     * Returns the breakpoint id (>= 1) if op.pc matches any enabled breakpoint address,
     * or -1 if no match.
     */
    int checkBreakpoint(CyreneOperation op) {
        for (Map.Entry<Integer, Integer> entry : breakpointMap.entrySet()) {
            if (entry.getValue() == op.pc) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Returns the number of currently registered (enabled) breakpoints.
     */
    int count() {
        return breakpointMap.size();
    }

    /**
     * Removes all breakpoints from both the internal map and the Jace Debugger.
     */
    void clear() {
        clearBreakpoints();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses one breakpoint line in the real Cyrene semicolon-delimited format:
     *   {@code <id>;Address=<bank>/<addr>;Execute;Action:Stop;Repeat:Always}
     *
     * Only Execute-trigger, Address-source, Action:Stop breakpoints are registered.
     * Non-matching lines are silently skipped.
     */
    private void parseLine(String line) {
        // Real Cyrene format: <id>;Address=<bank>/<addr>;Execute;Action:Stop;Repeat:Always
        String[] tokens = line.split(";");
        if (tokens.length < 3) {
            return;
        }

        int id;
        try {
            id = Integer.parseInt(tokens[0].trim());
        } catch (NumberFormatException e) {
            return;
        }

        // Field 1: "Address=<bank>/<addr>" — e.g. "Address=00/4000"
        String sourceField = tokens[1].trim();
        if (!sourceField.startsWith("Address=")) {
            return;
        }
        String addressPart = sourceField.substring("Address=".length());

        // Address format: "<bank>/<addr>" — bank is 2 hex digits, addr is 4 hex digits
        int slashIdx = addressPart.indexOf('/');
        if (slashIdx < 0) {
            return;
        }
        String addrHex = addressPart.substring(slashIdx + 1);

        int address;
        try {
            address = Integer.parseInt(addrHex, 16);
        } catch (NumberFormatException e) {
            return;
        }

        // Field 2: must be "Execute" (PC-address breakpoint)
        String triggerField = tokens[2].trim();
        if (!"Execute".equalsIgnoreCase(triggerField)) {
            return;
        }

        // Register with Jace Debugger
        debugger.getBreakpoints().add(address);

        // Store internally
        breakpointMap.put(id, address);
    }

    /**
     * Removes all breakpoints from the internal map and from the Jace Debugger's
     * shared static breakpoint list.
     */
    private void clearBreakpoints() {
        // The Debugger's breakpoint list is static/shared: only remove entries we own.
        for (int address : breakpointMap.values()) {
            debugger.getBreakpoints().remove(Integer.valueOf(address));
        }
        breakpointMap.clear();
    }
}
