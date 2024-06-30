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

package jace.hardware;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jace.Emulator;
import jace.apple2e.RAM128k;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.PagedMemory;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.state.Stateful;

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
            defaultValue = "4096",
            name = "Memory Size",
            description = "Size in KB.  Should be a multiple of 64 and not exceed 8192.  The real card cannot support more than 3072k")
    public int memorySize = 4096;
    public int maxBank = memorySize / 64;
    private Map<BankType, PagedMemory> generateBank() {
            Map<BankType, PagedMemory> memoryBank = new EnumMap<>(BankType.class);
            memoryBank.put(BankType.MAIN_MEMORY, new PagedMemory(0xc000, PagedMemory.Type.RAM));
            memoryBank.put(BankType.LANGUAGE_CARD_1, new PagedMemory(0x3000, PagedMemory.Type.LANGUAGE_CARD));
            memoryBank.put(BankType.LANGUAGE_CARD_2, new PagedMemory(0x1000, PagedMemory.Type.LANGUAGE_CARD));
            return memoryBank;
    }

    public enum BankType {
        MAIN_MEMORY, LANGUAGE_CARD_1, LANGUAGE_CARD_2
    }

    public CardRamworks() {
        super();
        memory = new ArrayList<>(maxBank);
        reconfigure();
    }

    private PagedMemory getAuxBank(BankType type, int bank) {
        if (bank >= maxBank) {
            return nullBank == null ? null : nullBank.get(type);
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
    public String getAuxZPConfiguration() {
        return super.getAuxZPConfiguration() + currentBank;
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
        Emulator.whileSuspended(computer -> {
            maxBank = memorySize / 64;
            if (maxBank < 1) {
                maxBank = 1;
            } else if (maxBank > 128) {
                maxBank = 128;
            }
            for (int i = memory.size(); i < maxBank; i++) {
                memory.add(null);
            }
            configureActiveMemory();    
        });
    }

    private RAMListener bankSelectListener;
    @Override
    public void attach() {
        bankSelectListener = observe("Ramworks bank select", RAMEvent.TYPE.WRITE, BANK_SELECT, (e) -> {
            currentBank = e.getNewValue();
            configureActiveMemory();
        });
    }

    @Override
    public void detach() {
        removeListener(bankSelectListener);
        super.detach();
    }
}