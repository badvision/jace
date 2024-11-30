package jace.core;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * TraceLog manages the trace log functionality for recording recent instructions or events.
 * It is used in conjunction with the CPU class to provide traceable debugging output.
 */
public class TraceLog {
    private ArrayList<String> logEntries;

    public TraceLog() {
        this.logEntries = new ArrayList<>();
    }

    public void log(String line, int maxEntries) {
        while (logEntries.size() >= maxEntries) {
            logEntries.remove(0);
        }
        logEntries.add(line);
    }

    public void dumpAndClear(Logger logger) {
        ArrayList<String> oldLog = logEntries;
        logEntries = new ArrayList<>();
        oldLog.forEach(logger::info);
        oldLog.clear();
    }
}
