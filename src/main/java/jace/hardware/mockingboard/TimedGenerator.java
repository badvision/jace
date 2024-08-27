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
        sampleRate = sample_rate == 0 ? 44100 : sample_rate;
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
        // Skyfox and Genius use a period value of 001 for silence instead of setting volume to 0.
        if (period == 1) {
            counter = 0;
            return 0;
        }
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
}