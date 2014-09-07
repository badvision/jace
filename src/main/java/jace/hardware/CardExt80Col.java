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
package jace.hardware;

import jace.apple2e.RAM128k;
import jace.core.PagedMemory;
import jace.state.Stateful;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
public class CardExt80Col extends RAM128k {
    @Stateful
    public PagedMemory auxMemory;
    @Stateful
    public PagedMemory auxLanguageCard;
    @Stateful
    public PagedMemory auxLanguageCard2;

    @Override
    public String getName() {
        return "Extended 80-col card (128kb)";
    }

    @Override
    public String getShortName() {
        return "128kb";
    }
    
    public CardExt80Col() {
        super();
        auxMemory = new PagedMemory(0xc000, PagedMemory.Type.ram);
        auxLanguageCard = new PagedMemory(0x3000, PagedMemory.Type.languageCard);
        auxLanguageCard2 = new PagedMemory(0x1000, PagedMemory.Type.languageCard);
        initMemoryPattern(auxMemory);
     }
    
    // This is redundant here, but necessary for Ramworks
    @Override
    public PagedMemory getAuxVideoMemory() {
        return auxMemory;
    }

    /**
     * @return the auxMemory
     */
    @Override
    public PagedMemory getAuxMemory() {
        return auxMemory;
    }

    /**
     * @return the auxLanguageCard
     */
    @Override
    public PagedMemory getAuxLanguageCard() {
        return auxLanguageCard;
    }

    /**
     * @return the auxLanguageCard2
     */
    @Override
    public PagedMemory getAuxLanguageCard2() {
        return auxLanguageCard2;
    }
    
    @Override
    public void reconfigure() {
        // Do nothing
    }

    @Override
    public void attach() {
        // Nothing to do...
    }

    @Override
    public void detach() {
        // Nothing to do...
    }
}
