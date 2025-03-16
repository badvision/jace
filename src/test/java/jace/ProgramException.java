package jace;

import jace.apple2e.MOS65C02;

/**
 * Exception class specifically for test program execution errors.
 * <p>
 * This exception is used to provide detailed stack traces when running assembly
 * instructions through the TestProgram framework. When tests execute assembly code
 * directly within the emulator, this exception captures the point of failure and 
 * provides context about which specific assembly instruction caused the error.
 * <p>
 * The breakpoint number helps identify the exact location in the test program
 * where the failure occurred, allowing for more precise debugging of emulator
 * functionality.
 */
public class ProgramException extends Exception {
    int breakpointNumber;
    String processorStats;
    String programLocation;
    public ProgramException(String message, int breakpointNumber) {
        super(message.replaceAll("<<.*>>", ""));
        this.breakpointNumber = breakpointNumber;
        this.processorStats = Emulator.withComputer(c-> ((MOS65C02) c.getCpu()).getState(), "N/A");
        // Look for a string pattern <<programLocation>> in the message and extract if found
        int start = message.indexOf("<<");
        if (start != -1) {
            int end = message.indexOf(">>", start);
            if (end != -1) {
                this.programLocation = message.substring(start + 2, end);
            }
        } else {
            this.programLocation = "N/A";
        }
    }
    public int getBreakpointNumber() {
        return breakpointNumber;
    }
    public String getProcessorStats() {
        return processorStats;
    }
    public String getProgramLocation() {
        return programLocation;
    }
    public String getMessage() {
        String message = super.getMessage();
        if (getBreakpointNumber() >= 0) {
            message += " at breakpoint " + getBreakpointNumber();
        }
        message += " \nStats: " + getProcessorStats();
        if (getProgramLocation() != null) {
            message += " \n        at " + getProgramLocation();
        }
        return message;
    }
}
