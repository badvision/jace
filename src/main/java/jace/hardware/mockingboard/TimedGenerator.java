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
package jace.hardware.mockingboard;

/**
 * Abstraction of the generators used in the PSG chip -- this manages the
 * periodicity of each generator that is more or less the same.
 * Created on April 18, 2006, 5:47 PM
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class TimedGenerator {

    int sampleRate;
    int clock;
    // Default period to 1 so that this can be used as a regular interval timer right away
    int period = 1;
    public double counter;
    double cyclesPerSample;
    int clocksPerPeriod;

    private TimedGenerator() {
    }

    public TimedGenerator(int _clock, int _sampleRate) {
        setRate(clock, sampleRate);
        reset();
    }
    // In most cases a cycle is a step.  The AY uses 16-cycle based periods for its oscillators
    // Basically this works as a hard-coded multiplier if overridden.

    public int stepsPerCycle() {
        return 1;
    }

    public void setRate(int clock, int sample_rate) {
        sampleRate = sample_rate;
        this.clock = clock;
        cyclesPerSample = clock / sampleRate;
    }

    public void setPeriod(int _period) {
        period = _period > 0 ? _period : 1;
        clocksPerPeriod = (period * stepsPerCycle());
        // set counter back... necessary?
//        while (clocksPerPeriod > period) {
//            counter -= clocksPerPeriod;
//        }
    }

    protected int updateCounter() {
        counter += cyclesPerSample;
        int numStateChanges = 0;
        while (counter >= clocksPerPeriod) {
            counter -= clocksPerPeriod;
            numStateChanges++;
        }
        return numStateChanges;
    }

    public void reset() {
        counter = 0;
        period = 1;
    }
};