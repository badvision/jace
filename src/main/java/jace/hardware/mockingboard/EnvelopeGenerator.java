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
 * Envelope generator of the PSG sound chip
 * Created on April 18, 2006, 5:49 PM
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class EnvelopeGenerator extends TimedGenerator {

    boolean hold = false;
    boolean attk = false;
    boolean alt = false;
    boolean cont = false;
    int direction;
    int amplitude;

    boolean start1high = false;
    boolean start2high = false;
    boolean oneShot = false;
    boolean oddEven = false;
    
    public EnvelopeGenerator(int _clock, int _sampleRate) {
        super(_clock, _sampleRate);
    }

    @Override
    public int stepsPerCycle() {
        return 8;
    }

    @Override
    public void setPeriod(int p) {
        if (p > 0) {
            super.setPeriod(p);
        } else {
            clocksPerPeriod = stepsPerCycle() / 2;
        }
    }

    int effectiveAmplitude = 0;
    public void step() {
        int stateChanges = updateCounter();
        int total = 0;
        for (int i = 0; i < stateChanges; i++) {
            amplitude += direction;
            if (amplitude > 15 || amplitude < 0) {
                setPhase(oddEven ? start1high : start2high);
                oddEven = !oddEven;
                if (hold) {
                    direction = 0;
                }
            }
            total += amplitude;
        }
        if (stateChanges == 0) {
            effectiveAmplitude = amplitude;
        } else {
            effectiveAmplitude = Math.min(15, total / stateChanges);
        }
    }

    int shape;
    public void setShape(int shape) {
        this.shape = shape & 15;
        oddEven = false;
        counter = 0;
        cont = (shape & 8) != 0;
        attk = (shape & 4) != 0;
        alt = (shape & 2) != 0;
        hold = ((shape ^ 8) & 9) != 0;
        
        start1high = !attk;
        start2high = cont && ! (attk ^ alt ^ hold);
        
        setPhase(start1high);
    }
    
    public void setPhase(boolean isHigh) {
        if (isHigh) {
            amplitude = 15;
            direction = -1;            
        } else {
            amplitude = 0;
            direction = 1;
        }        
    }

    public int getEffectiveAmplitude() {
        return effectiveAmplitude;
    }

    public int getAmplitude() {
        return amplitude;
    }

    @Override
    public void reset() {
        super.reset();
        setShape(0);
    }
}