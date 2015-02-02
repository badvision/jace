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

    public void step() {
        int stateChanges = updateCounter();
        for (int i = 0; i < stateChanges; i++) {
            if (amplitude == 0 && direction == -1) {
                if (!cont) {
                    direction = 0;
                } else if (hold) {
                    direction = 0;
                    if (alt) {
                        amplitude = 15;
                    }
                } else if (alt) {
                    direction = 1;
                } else {
                    amplitude = 15;
                }
            }
            if (amplitude == 15 && direction == 1) {
                if (!cont) {
                    direction = 0;
                    amplitude = 0;
                } else if (hold) {
                    direction = 0;
                    if (alt) {
                        amplitude = 0;
                    }
                } else if (alt) {
                    direction = -1;
                } else {
                    amplitude = 0;
                }
            }
            amplitude += direction;
        }
    }

    public void setShape(int shape) {
        counter = 0;
        cont = (shape & 8) != 0;
        attk = (shape & 4) != 0;
        alt = (shape & 2) != 0;
        hold = (shape & 1) != 0;
        if (attk) {
            amplitude = 0;
            direction = 1;
        } else {
            amplitude = 15;
            direction = -1;
        }
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