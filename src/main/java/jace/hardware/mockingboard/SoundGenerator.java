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

import jace.hardware.CardMockingboard;

/**
 * Square-wave generator used in the PSG sound chip.
 * Created on April 18, 2006, 5:48 PM
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class SoundGenerator extends TimedGenerator {
    int amplitude;
    boolean useEnvGen;
    boolean active;
    boolean noiseActive;
    boolean inverted;
    public SoundGenerator(int _clock, int _sampleRate) {
        super(_clock, _sampleRate);
    }
    
    @Override
    public int stepsPerCycle() {
        return 8;
    }
    public void setAmplitude(int _amp) {
        amplitude = (_amp & 0x0F);
        useEnvGen = (_amp & 0x010) != 0;
    }
    public void setActive(boolean _active) {
        active = _active;
    }
    public void setNoiseActive(boolean _active) {
        noiseActive = _active;
    }

    @Override
    public void setPeriod(int _period) {
        super.setPeriod(_period);
    }
    
    public int step(NoiseGenerator noiseGen, EnvelopeGenerator envGen) {
        int stateChanges = updateCounter();
        if (((stateChanges & 1) == 1)) inverted = !inverted;
        double amp = stateChanges == 0 ? 1 : 1.0 / Math.max(stateChanges-1, 1);
        int vol = useEnvGen ? envGen.getEffectiveAmplitude() : amplitude;
        boolean on = noiseActive && noiseGen.isOn() || (active && inverted);
        return on ? (int) (CardMockingboard.VolTable[vol] * amp) : 0;
    }
    
    @Override
    public void reset() {
        super.reset();
        amplitude = 0;
        useEnvGen = false;
        active = false;
        noiseActive = false;
        inverted = false;
    }
}