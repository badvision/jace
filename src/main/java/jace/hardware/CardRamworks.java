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
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Computer;
import jace.core.PagedMemory;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.state.Stateful;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Emulates the Ramworks Basic and Ramworks III cards
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
@Name("Ramworks III Memory Expansion")
public class CardRamworks extends RAM128k {
    public static int BANK_SELECT = 0x0c073;
    @Stateful
    public int currentBank = 0;
    @Stateful
    public List<Map<BankType, PagedMemory>> memory;
    public Map<BankType, PagedMemory> nullBank = generateBank();
    @ConfigurableField(
            category = "memory",
            defaultValue = "3072",
            name = "Memory Size",
            description = "Size in KB.  Should be a multiple of 64 and not exceed 8192.  The real card cannot support more than 3072k")
    public int memorySize = 3072;
    public int maxBank = memorySize / 64;
    private Map<BankType, PagedMemory> generateBank() {
            Map<BankType, PagedMemory> memoryBank = new EnumMap<BankType, PagedMemory>(BankType.class);
            memoryBank.put(BankType.MAIN_MEMORY, new PagedMemory(0xc000, PagedMemory.Type.ram));
            memoryBank.put(BankType.LANGUAGE_CARD_1, new PagedMemory(0x3000, PagedMemory.Type.languageCard));
            memoryBank.put(BankType.LANGUAGE_CARD_2, new PagedMemory(0x1000, PagedMemory.Type.languageCard));
            return memoryBank;
    }

    public static enum BankType {
        MAIN_MEMORY, LANGUAGE_CARD_1, LANGUAGE_CARD_2
    };

    public CardRamworks() {
        super();
        memory = new ArrayList<Map<BankType, PagedMemory>>(maxBank);
        reconfigure();
    }
    
    private PagedMemory getAuxBank(BankType type, int bank) {
        if (bank >= maxBank) {
            return nullBank.get(type);
        }
        Map<BankType, PagedMemory> memoryBank = memory.get(bank);
        if (memoryBank == null) {
            memoryBank = generateBank();
            memory.set(bank, memoryBank);
        }
        return memoryBank.get(type);
    }

    @Override
    public PagedMemory getAuxVideoMemory() {
        return getAuxBank(BankType.MAIN_MEMORY, 0);
    }

    PagedMemory lastAux = null;
    @Override
    public PagedMemory getAuxMemory() {
        return getAuxBank(BankType.MAIN_MEMORY, currentBank);
    }

    @Override
    public PagedMemory getAuxLanguageCard() {
        return getAuxBank(BankType.LANGUAGE_CARD_1, currentBank);
    }

    @Override
    public PagedMemory getAuxLanguageCard2() {
        return getAuxBank(BankType.LANGUAGE_CARD_2, currentBank);
    }

    @Override
    public String getName() {
        return "Ramworks III";
    }

    @Override
    public String getShortName() {
        return "Ramworks3";
    }

    @Override
    public void reconfigure() {
        boolean resume = Computer.pause();
        maxBank = memorySize / 64;
        if (maxBank < 1) maxBank = 1;
        if (maxBank > 128) maxBank = 128;
        for (int i = memory.size(); i < maxBank; i++) {
            memory.add(null);
        }
        configureActiveMemory();
        if (resume) {
            Computer.resume();
        }
    }
    RAMListener bankSelectListener = new RAMListener(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
        @Override
        protected void doConfig() {
            setScopeStart(BANK_SELECT);
            setScopeEnd(BANK_SELECT);
        }

        @Override
        protected void doEvent(RAMEvent e) {
            currentBank = e.getNewValue();
            configureActiveMemory();
        }
    };

    @Override
    public void attach() {
        addListener(bankSelectListener);
    }

    @Override
    public void detach() {
        removeListener(bankSelectListener);
    }
}