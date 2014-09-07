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

import java.util.ArrayList;
import java.util.List;

/**
 * A debugger has the ability to track a list of breakpoints and step a CPU one
 * instruction at a time. This is a very generic abstraction used to describe
 * the basic contract of what a debugger should do. EmulatorUILogic creates an
 * anonymous subclass that hooks into the actual emulator.
 * Created on April 16, 2007, 10:37 PM
 *
 * @author Administrator
 */
public abstract class Debugger {

    public abstract void updateStatus();
    private boolean active = false;
    public boolean step = false;

    public void setActive(boolean state) {
        active = state;
    }

    public boolean isActive() {
        return active;
    }
    private List<Integer> breakpoints = new ArrayList<Integer>();

    public List<Integer> getBreakpoints() {
        return breakpoints;
    }
    private boolean hasBreakpoints = false;

    boolean hasBreakpoints() {
        return hasBreakpoints;
    }

    public void updateBreakpoints() {
        hasBreakpoints = false;
        for (Integer i : breakpoints) {
            if (i != null) {
                hasBreakpoints = true;
            }
        }
    }

    boolean takeStep() {
        if (step) {
            step = false;
            return true;
        }
        return false;
    }
}
