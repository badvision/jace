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

    @Override
    public void dumpMemoryMap() {
        System.out.println("=== MEMORY MAP DUMP ===");
        System.out.println("Current Ramworks Bank: " + currentBank);
        System.out.println("Memory State: " + getState());
        System.out.println();
        
        // Build lookup maps for identifying memory banks
        java.util.Map<byte[], String> memoryBankNames = new java.util.HashMap<>();
        
        // Add main memory pages
        if (mainMemory != null) {
            for (int i = 0; i < mainMemory.getMemory().length; i++) {
                if (mainMemory.getMemory()[i] != null) {
                    memoryBankNames.put(mainMemory.getMemory()[i], "Main");
                }
            }
        }
        
        // Add aux memory pages (different Ramworks banks)
        for (int bank = 0; bank < Math.min(maxBank, 8); bank++) {
            PagedMemory auxMem = getAuxBank(BankType.MAIN_MEMORY, bank);
            if (auxMem != null) {
                for (int i = 0; i < auxMem.getMemory().length; i++) {
                    if (auxMem.getMemory()[i] != null) {
                        String name = bank == 0 ? "Aux" : "Aux(" + bank + ")";
                        memoryBankNames.put(auxMem.getMemory()[i], name);
                    }
                }
            }
        }
        
        // Add language card pages
        if (languageCard != null) {
            for (int i = 0; i < languageCard.getMemory().length; i++) {
                if (languageCard.getMemory()[i] != null) {
                    memoryBankNames.put(languageCard.getMemory()[i], "LC1");
                }
            }
        }
        if (languageCard2 != null) {
            for (int i = 0; i < languageCard2.getMemory().length; i++) {
                if (languageCard2.getMemory()[i] != null) {
                    memoryBankNames.put(languageCard2.getMemory()[i], "LC2");
                }
            }
        }
        
        // Add aux language card pages
        for (int bank = 0; bank < Math.min(maxBank, 8); bank++) {
            PagedMemory auxLC1 = getAuxBank(BankType.LANGUAGE_CARD_1, bank);
            PagedMemory auxLC2 = getAuxBank(BankType.LANGUAGE_CARD_2, bank);
            
            if (auxLC1 != null) {
                for (int i = 0; i < auxLC1.getMemory().length; i++) {
                    if (auxLC1.getMemory()[i] != null) {
                        String name = bank == 0 ? "AuxLC1" : "AuxLC1(" + bank + ")";
                        memoryBankNames.put(auxLC1.getMemory()[i], name);
                    }
                }
            }
            
            if (auxLC2 != null) {
                for (int i = 0; i < auxLC2.getMemory().length; i++) {
                    if (auxLC2.getMemory()[i] != null) {
                        String name = bank == 0 ? "AuxLC2" : "AuxLC2(" + bank + ")";
                        memoryBankNames.put(auxLC2.getMemory()[i], name);
                    }
                }
            }
        }
        
        // Add ROM pages
        if (rom != null) {
            for (int i = 0; i < rom.getMemory().length; i++) {
                if (rom.getMemory()[i] != null) {
                    memoryBankNames.put(rom.getMemory()[i], "ROM");
                }
            }
        }
        if (cPageRom != null) {
            for (int i = 0; i < cPageRom.getMemory().length; i++) {
                if (cPageRom.getMemory()[i] != null) {
                    memoryBankNames.put(cPageRom.getMemory()[i], "C-ROM");
                }
            }
        }
        
        // Add card pages
        for (int slot = 1; slot <= 7; slot++) {
            final int finalSlot = slot; // Make slot effectively final for lambda
            getCard(slot).ifPresent(card -> {
                if (card.getCxRom() != null) {
                    for (int i = 0; i < card.getCxRom().getMemory().length; i++) {
                        if (card.getCxRom().getMemory()[i] != null) {
                            memoryBankNames.put(card.getCxRom().getMemory()[i], "Card" + finalSlot);
                        }
                    }
                }
                if (card.getC8Rom() != null) {
                    for (int i = 0; i < card.getC8Rom().getMemory().length; i++) {
                        if (card.getC8Rom().getMemory()[i] != null) {
                            memoryBankNames.put(card.getC8Rom().getMemory()[i], "Card" + finalSlot + "C8");
                        }
                    }
                }
            });
        }
        
        // Add blank pages
        if (blank != null) {
            for (int i = 0; i < blank.getMemory().length; i++) {
                if (blank.getMemory()[i] != null) {
                    memoryBankNames.put(blank.getMemory()[i], "Blank");
                }
            }
        }
        
        // Print header
        System.out.println("Page   Read          Write");
        System.out.println("----   ----------    ----------");
        
        // Analyze each memory page
        for (int page = 0; page < 256; page++) {
            String readBank = "---";
            String writeBank = "---";
            
            // Get read page
            if (activeRead != null) {
                byte[] readPage = activeRead.getMemoryPage(page << 8);
                if (readPage != null) {
                    readBank = memoryBankNames.getOrDefault(readPage, "Unknown@" + System.identityHashCode(readPage));
                }
            }
            
            // Get write page
            if (activeWrite != null) {
                byte[] writePage = activeWrite.getMemoryPage(page << 8);
                if (writePage != null) {
                    writeBank = memoryBankNames.getOrDefault(writePage, "Unknown@" + System.identityHashCode(writePage));
                } else {
                    writeBank = "PROTECTED";
                }
            }
            
            // Only show interesting pages or ones around $20 (our test area)
            if (!readBank.equals("---") || !writeBank.equals("---") || 
                (page >= 0x1F && page <= 0x25) || page < 0x10 || page >= 0xC0) {
                System.out.printf("$%02X    %-12s  %-12s%n", page, readBank, writeBank);
            }
        }
        
        System.out.println("=== END MEMORY MAP ===");
        System.out.println();
    }
}