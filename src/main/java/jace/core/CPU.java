/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.core;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.config.ConfigurableField;

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
        whileSuspended(()->{
            ArrayList<String> newLog = new ArrayList<>();
            ArrayList<String> oldLog = traceLog;
            traceLog = newLog;     
            LOG.log(Level.INFO, "Most recent {0} instructions:", traceLength);
            oldLog.forEach(LOG::info);
            oldLog.clear();
        });        
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
                    if (debugger.getBreakpoints().contains(getProgramCounter())){
                        debugger.setActive(true);
                    }
                }
                if (debugger.isActive()) {
                    debugger.updateStatus();
                    if (!debugger.takeStep()) {
                        // If the debugger is active and we aren't ready for the next step, sleep and exit
                        // Without the sleep, this would constitute a very rapid-fire loop and would eat
                        // an unnecessary amount of CPU.
                        Thread.onSpinWait();
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

    boolean singleTraceEnabled = false;
    public String lastTrace = "START";
    public void performSingleTrace() {
        singleTraceEnabled = true;
    }
    public boolean isSingleTraceEnabled() {
        return singleTraceEnabled;
    }
    public String getLastTrace() {
        return lastTrace;
    }
    public void captureSingleTrace(String trace) {
        lastTrace = trace;
        singleTraceEnabled = false;
    }

    abstract public void clearState();
}