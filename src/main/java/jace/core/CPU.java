/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package jace.core;

import jace.config.ConfigurableField;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CPU is a vague abstraction of a CPU. It is defined as something which can be
 * debugged or traced. It has a program counter which can be incremented or
 * change.  Most importantly, it is a device which does something on every clock tick.
 * Subclasses should implement "executeOpcode" rather than override the tick method.
 * Created on January 4, 2007, 7:27 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public abstract class CPU extends Device {
    private static final Logger LOG = Logger.getLogger(CPU.class.getName());

    public CPU(Computer computer) {
        super(computer);
    }
    
    @Override
    public String getShortName() {
        return "cpu";
    }
    private Debugger debugger = null;
    @ConfigurableField(name = "Enable trace to STDOUT", shortName = "trace")
    public boolean trace = false;

    public boolean isTraceEnabled() {
        return trace;
    }

    public void setTraceEnabled(boolean t) {
        trace = t;
    }
    @ConfigurableField(name = "Trace length", shortName = "traceSize", description = "Number of most recent trace lines to keep for debugging errors.  Zero == disabled")
    public int traceLength = 0;
    private ArrayList<String> traceLog = new ArrayList<>();

    public boolean isLogEnabled() {
        return (traceLength > 0);
    }

    public void log(String line) {
        if (!isLogEnabled()) {
            return;
        }
        while (traceLog.size() >= traceLength) {
            traceLog.remove(0);
        }
        traceLog.add(line);
    }

    public void dumpTrace() {
        computer.pause();
        ArrayList<String> newLog = new ArrayList<>();
        ArrayList<String> oldLog = traceLog;
        traceLog = newLog;
        computer.resume();
        LOG.log(Level.INFO, "Most recent {0} instructions:", traceLength);
        oldLog.stream().forEach(LOG::info);
        oldLog.clear();
    }

    public void setDebug(Debugger d) {
        debugger = d;
        suspend();
    }

    public void clearDebug() {
        debugger = null;
        resume();
    }
    //@ConfigurableField(name="Program Counter")
    public int programCounter = 0;

    public int getProgramCounter() {
        return programCounter;
    }

    public void setProgramCounter(int programCounter) {
        this.programCounter = 0x00FFFF & programCounter;
    }

    public void incrementProgramCounter(int amount) {
        this.programCounter += amount;
        this.programCounter = 0x00FFFF & this.programCounter;
    }

    /**
     * Process a single tick of the main processor clock. Either we're waiting
     * to execute the next instruction, or the next instruction is ready to go
     */
    @Override
    public void tick() {
        try {
            if (debugger != null) {
                if (!debugger.isActive() && debugger.hasBreakpoints()) {
                    debugger.getBreakpoints().stream().filter((i) -> (i == getProgramCounter())).forEach((_item) -> {
                        debugger.setActive(true);
                    });
                }
                if (debugger.isActive()) {
                    debugger.updateStatus();
                    if (!debugger.takeStep()) {
                        // If the debugger is active and we aren't ready for the next step, sleep and exit
                        // Without the sleep, this would constitute a very rapid-fire loop and would eat
                        // an unnecessary amount of CPU.
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        executeOpcode();
    }
    /*
     * Execute the current opcode at the current program counter
     *@return number of cycles to wait until next command can be executed
     */

    protected abstract void executeOpcode();

    public abstract void reset();

    public abstract void generateInterrupt();

    abstract public void pushPC();

    @Override
    public void attach() {
    }

    abstract public void JSR(int pointer);
}