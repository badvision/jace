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
package jace.tracker;

import jace.apple2e.MOS65C02;
import jace.core.Card;
import jace.core.Computer;
import jace.core.Motherboard;
import jace.hardware.CardExt80Col;
import jace.hardware.CardMockingboard;
import java.util.Optional;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class PlaybackEngine extends Computer {

    Computer dummyComputer = new Computer() {

        @Override
        public void coldStart() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void warmStart() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected boolean isRunning() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void doPause() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void doResume() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String getShortName() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    };
    Motherboard motherboard = new Motherboard(dummyComputer);
    CardMockingboard mockingboard = new CardMockingboard(dummyComputer);

    public PlaybackEngine() {
        setMemory(new CardExt80Col(dummyComputer));
        setCpu(new MOS65C02(dummyComputer));
        getMemory().addCard(mockingboard, 5);
    }

    @Override
    public void coldStart() {
        for (Optional<Card> c : getMemory().getAllCards()) {
            c.ifPresent(Card::reset);
        }
    }

    @Override
    public void warmStart() {
        for (Optional<Card> c : getMemory().getAllCards()) {
            c.ifPresent(Card::reset);
        }
    }

    @Override
    protected boolean isRunning() {
        return motherboard.isRunning();
    }

    @Override
    protected void doPause() {
        motherboard.suspend();
    }

    @Override
    protected void doResume() {
        motherboard.resume();
    }

    @Override
    public String getName() {
        return "Playback Computer";
    }

    @Override
    public String getShortName() {
        return "Computer";
    }

    @Override
    public void reconfigure() {
        // do nothing
    }
}