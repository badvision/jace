package jace;

import jace.apple2e.MOS65C02;

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
