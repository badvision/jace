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

    public void setShape(int shape) {
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