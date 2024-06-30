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
 * Noise generator of the PSG sound chip.
 * Created on April 18, 2006, 5:47 PM
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class NoiseGenerator extends TimedGenerator {
    int rng = 0x003333;
    public NoiseGenerator(int _clock,int _sampleRate) {
        super(_clock, _sampleRate);
    }
    @Override
    public int stepsPerCycle() {
        return 4;
    }
    public void step() {
        int stateChanges = updateCounter();
        for (int i=0; i < stateChanges; i++)
            updateRng();
    }
    public static final int BIT17 = 0x010000;
    public void updateRng() {
        rng = ((rng & 1) != 0 ? rng ^ 0x24000 : rng) >> 1;
        if ((rng & 1) == 1) {
            state = !state;
        }
    }
    
    boolean state = false;
    public boolean isOn() {
        return state;
    }
}