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

package jace.apple2e;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jace.Emulator;
import jace.config.DeviceEnum;
import jace.core.CPU;
import jace.core.Card;
import jace.core.PagedMemory;
import jace.core.RAM;
import jace.hardware.CardExt80Col;
import jace.hardware.CardRamworks;
import jace.state.Stateful;

/**
 * Implementation of a 128k memory space and the MMU found in an Apple //e. The
 * MMU behavior is mimicked by configureActiveMemory.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
abstract public class RAM128k extends RAM {
    // Memory card implementations
    public static enum RamCards implements DeviceEnum<RAM128k> {
        CardExt80Col("80-Column Card (128k)", CardExt80Col.class, CardExt80Col::new),
        CardRamworks("Ramworks (4mb)", CardRamworks.class, CardRamworks::new);

        Supplier<? extends RAM128k> factory;
        String name;
        Class<? extends RAM128k> clazz;

        RamCards(String name, Class<? extends RAM128k> clazz, Supplier<? extends RAM128k> factory) {
            this.factory = factory;
            this.name = name;
            this.clazz = clazz;
        }

        @Override
        public RAM128k create() {
            return factory.get();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInstance(RAM128k card) {
            return card != null && clazz.equals(card.getClass());
        }
    }

    static final Logger LOG = Logger.getLogger(RAM128k.class.getName());

    Map<String, PagedMemory> banks;
    Map<String, PagedMemory> memoryConfigurations = new HashMap<>();
    String state = "???";

    private Map<String, PagedMemory> getBanks() {
        if (banks == null) {
            banks = new HashMap<>();

            banks.put("main", mainMemory);
            banks.put("lc", languageCard);
            banks.put("lc2", languageCard2);
            banks.put("//e rom (80-col)", cPageRom);
            banks.put("//e rom", rom);
            banks.put("blank", blank);
            banks.put("aux", getAuxMemory());
            banks.put("aux lc", getAuxLanguageCard());
            banks.put("aux lc2", getAuxLanguageCard2());
            cards[1].ifPresent(c -> banks.put("card1a", c.getCxRom()));
            cards[1].ifPresent(c -> banks.put("card1b", c.getC8Rom()));
            cards[2].ifPresent(c -> banks.put("card2a", c.getCxRom()));
            cards[2].ifPresent(c -> banks.put("card2b", c.getC8Rom()));
            cards[3].ifPresent(c -> banks.put("card3a", c.getCxRom()));
            cards[3].ifPresent(c -> banks.put("card3b", c.getC8Rom()));
            cards[4].ifPresent(c -> banks.put("card4a", c.getCxRom()));
            cards[4].ifPresent(c -> banks.put("card4b", c.getC8Rom()));
            cards[5].ifPresent(c -> banks.put("card5a", c.getCxRom()));
            cards[5].ifPresent(c -> banks.put("card5b", c.getC8Rom()));
            cards[6].ifPresent(c -> banks.put("card6a", c.getCxRom()));
            cards[6].ifPresent(c -> banks.put("card6b", c.getC8Rom()));
            cards[7].ifPresent(c -> banks.put("card7a", c.getCxRom()));
            cards[7].ifPresent(c -> banks.put("card7b", c.getC8Rom()));
        }

        return banks;
    }

    @Override
    public String getState() {
        return state;
    }

    @Override
    public void performExtendedCommand(int param) {
        if (param == 0xda) {// 64 da : Dump all memory mappings
            System.out.println("Active banks");
            for (int i = 0; i < 256; i++) {
                byte[] read = this.activeRead.get(i);
                byte[] write = this.activeWrite.get(i);
                String readBank = getBanks().keySet().stream().filter(bank -> {
                    PagedMemory mem = getBanks().get(bank);
                    for (byte[] page : mem.getMemory()) {
                        if (page == read) {
                            return true;
                        }
                    }
                    return false;
                }).findFirst().orElse("unknown");
                String writeBank = getBanks().keySet().stream().filter(bank -> {
                    PagedMemory mem = getBanks().get(bank);
                    for (byte[] page : mem.getMemory()) {
                        if (page == write) {
                            return true;
                        }
                    }
                    return false;
                }).findFirst().orElse("unknown");
                LOG.log(Level.INFO, "Bank {0}\t{1}\t{2}", new Object[]{Integer.toHexString(i), readBank, writeBank});
            }
        }
    }

    @Stateful
    public PagedMemory mainMemory;
    @Stateful
    public PagedMemory languageCard;
    @Stateful
    public PagedMemory languageCard2;
    public PagedMemory cPageRom;
    public PagedMemory rom;
    public PagedMemory blank;

    public RAM128k() {
        super();
        mainMemory = new PagedMemory(0xc000, PagedMemory.Type.RAM);
        rom = new PagedMemory(0x3000, PagedMemory.Type.FIRMWARE_MAIN);
        cPageRom = new PagedMemory(0x1000, PagedMemory.Type.SLOW_ROM);
        languageCard = new PagedMemory(0x3000, PagedMemory.Type.LANGUAGE_CARD);
        languageCard2 = new PagedMemory(0x1000, PagedMemory.Type.LANGUAGE_CARD);
        activeRead = new PagedMemory(0x10000, PagedMemory.Type.RAM);
        activeWrite = new PagedMemory(0x10000, PagedMemory.Type.RAM);
        blank = new PagedMemory(0x100, PagedMemory.Type.RAM);
        zeroAllRam();
    }

    public final void initMemoryPattern(PagedMemory mem) {
        // Format memory with FF FF 00 00 pattern
        for (int i = 0; i < 0x0100; i++) {
            for (int j = 0; j < 0x0c0; j++) {
                byte use = (byte) ((i % 4) > 1 ? 0x0FF : 0x00);
                mem.get(j)[i] = use;
            }
        }
    }

    public final void zeroAllRam() {
        for (int i = 0; i < 0x0100; i++) {
            blank.get(0)[i] = (byte) 0x0FF;
        }
        initMemoryPattern(mainMemory);
        if (getAuxMemory() != null) {
            initMemoryPattern(getAuxMemory());
        }
    }

    public String getReadConfiguration() {
        String rstate = "";
        if (SoftSwitches.RAMRD.getState()) {
            rstate += "Ra";
        } else {
            rstate += "R0";
        }
        String LCR = "L0R";
        if (SoftSwitches.LCRAM.isOn()) {
            if (SoftSwitches.AUXZP.isOff()) {
                LCR = "L1R";
                if (SoftSwitches.LCBANK1.isOff()) {
                    LCR = "L2R";
                }
            } else {
                LCR = "L1aR";
                if (SoftSwitches.LCBANK1.isOff()) {
                    LCR = "L2aR";
                }
            }
        }
        rstate += LCR;
        if (SoftSwitches.CXROM.getState()) {
            rstate += "CXROM";
        } else {
            rstate += "!CX";
            if (SoftSwitches.SLOTC3ROM.isOff()) {
                rstate += "C3";
            }
            if (SoftSwitches.INTC8ROM.isOn()) {
                rstate += "C8";
            } else {
                rstate += "C8"+getActiveSlot();
            }
        }

        return rstate;
    }

    public String getWriteConfiguration() {
        String wstate = "";
        if (SoftSwitches.RAMWRT.getState()) {
            wstate += "Wa";
        } else {
            wstate += "W0";
        }
        String LCW = "L0W";
        if (SoftSwitches.LCWRITE.isOn()) {
            if (SoftSwitches.AUXZP.isOff()) {
                LCW = "L1W";
                if (SoftSwitches.LCBANK1.isOff()) {
                    LCW = "L2W";
                }
            } else {
                LCW = "L1aW";
                if (SoftSwitches.LCBANK1.isOff()) {
                    LCW = "L2aW";
                }
            }
        }
        wstate += LCW;
        return wstate;
    }

    public String getAuxZPConfiguration() {
        String astate = "";
        if (SoftSwitches._80STORE.isOn()) {
            astate += "80S";
            if (SoftSwitches.PAGE2.isOn()) {
                astate += "2";
            }
            if (SoftSwitches.HIRES.isOn()) {
                astate += "H";
            }
        }

        // Handle zero-page bankswitching
        if (SoftSwitches.AUXZP.getState()) {
            astate += "Za";
        } else {
            astate += "Z0";
        }
        return astate;
    }

    public PagedMemory buildReadConfiguration() {
        PagedMemory read = new PagedMemory(0x10000, PagedMemory.Type.RAM);
        // First off, set up read/write for main memory (might get changed later on)
        read.fillBanks(SoftSwitches.RAMRD.getState() ? getAuxMemory() : mainMemory);

        // Handle language card softswitches
        read.fillBanks(rom);
        if (SoftSwitches.LCRAM.isOn()) {
            if (SoftSwitches.AUXZP.isOff()) {
                read.fillBanks(languageCard);
                if (SoftSwitches.LCBANK1.isOff()) {
                    read.fillBanks(languageCard2);
                }
            } else {
                read.fillBanks(getAuxLanguageCard());
                if (SoftSwitches.LCBANK1.isOff()) {
                    read.fillBanks(getAuxLanguageCard2());
                }
            }
        }

        // Handle 80STORE logic for bankswitching video ram
        if (SoftSwitches._80STORE.isOn()) {
            read.setBanks(0x04, 0x04, 0x04,
                    SoftSwitches.PAGE2.isOn() ? getAuxMemory() : mainMemory);
            if (SoftSwitches.HIRES.isOn()) {
                read.setBanks(0x020, 0x020, 0x020,
                        SoftSwitches.PAGE2.isOn() ? getAuxMemory() : mainMemory);
            }
        }

        // Handle zero-page bankswitching
        if (SoftSwitches.AUXZP.getState()) {
            // Aux pages 0 and 1
            read.setBanks(0, 2, 0, getAuxMemory());
        } else {
            // Main pages 0 and 1
            read.setBanks(0, 2, 0, mainMemory);
        }

        /*
            INTCXROM   SLOTC3ROM  C1,C2,C4-CF   C3
            0         0          slot         rom
            0         1          slot         slot
            1         -          rom          rom
         */
        if (SoftSwitches.CXROM.getState()) {
            // Enable C1-CF to point to rom
            read.setBanks(0, 0x0F, 0x0C1, cPageRom);
        } else {
            // Enable C1-CF to point to slots
            for (int slot = 1; slot <= 7; slot++) {
                PagedMemory page = getCard(slot).map(Card::getCxRom).orElse(blank);
                read.setBanks(0, 1, 0x0c0 + slot, page);
            }
            if (getActiveSlot() == 0) {
                for (int i = 0x0C8; i < 0x0D0; i++) {
                    read.set(i, blank.get(0));
                }
            } else {
                getCard(getActiveSlot()).ifPresent(c -> read.setBanks(0, 8, 0x0c8, c.getC8Rom()));
            }
            if (SoftSwitches.SLOTC3ROM.isOff()) {
                // Enable C3 to point to internal ROM
                read.setBanks(2, 1, 0x0C3, cPageRom);
            }
            if (SoftSwitches.INTC8ROM.isOn()) {
                // Enable C8-CF to point to internal ROM
                read.setBanks(7, 8, 0x0C8, cPageRom);
            }
        }
        // All ROM reads not intecepted will return 0xFF!
        read.set(0x0c0, blank.get(0));
        return read;
    }

    public PagedMemory buildWriteConfiguration() {
        PagedMemory write = new PagedMemory(0x10000, PagedMemory.Type.RAM);
        // First off, set up read/write for main memory (might get changed later on)
        write.fillBanks(SoftSwitches.RAMWRT.getState() ? getAuxMemory() : mainMemory);

        // Handle language card softswitches
        for (int i = 0x0c0; i < 0x0d0; i++) {
            write.set(i, null);
        }
        if (SoftSwitches.LCWRITE.isOn()) {
            if (SoftSwitches.AUXZP.isOff()) {
                write.fillBanks(languageCard);
                if (SoftSwitches.LCBANK1.isOff()) {
                    write.fillBanks(languageCard2);
                }
            } else {
                write.fillBanks(getAuxLanguageCard());
                if (SoftSwitches.LCBANK1.isOff()) {
                    write.fillBanks(getAuxLanguageCard2());
                }
            }
        } else {
            // Make 0xd000 - 0xffff non-writable!
            for (int i = 0x0d0; i < 0x0100; i++) {
                write.set(i, null);
            }
        }

        // Handle 80STORE logic for bankswitching video ram
        if (SoftSwitches._80STORE.isOn()) {
            write.setBanks(0x04, 0x04, 0x04,
                    SoftSwitches.PAGE2.isOn() ? getAuxMemory() : mainMemory);
            if (SoftSwitches.HIRES.isOn()) {
                write.setBanks(0x020, 0x020, 0x020,
                        SoftSwitches.PAGE2.isOn() ? getAuxMemory() : mainMemory);
            }
        }

        // Handle zero-page bankswitching
        if (SoftSwitches.AUXZP.getState()) {
            // Aux pages 0 and 1
            write.setBanks(0, 2, 0, getAuxMemory());
        } else {
            // Main pages 0 and 1
            write.setBanks(0, 2, 0, mainMemory);
        }

        return write;
    }

    /**
     *
     */
    @Override
    public void configureActiveMemory() {
        String auxZpConfiguration = getAuxZPConfiguration();
        String readConfiguration = getReadConfiguration() + auxZpConfiguration;
        String writeConfiguration = getWriteConfiguration() + auxZpConfiguration;
        String newState = readConfiguration + ";" + writeConfiguration;
        if (newState.equals(state)) {
            return;
        }
        state = newState;

        log("MMU Switches");

        if (memoryConfigurations.containsKey(readConfiguration)) {
            activeRead = memoryConfigurations.get(readConfiguration);
        } else {
            activeRead = buildReadConfiguration();
            memoryConfigurations.put(readConfiguration, activeRead);
        }

        if (memoryConfigurations.containsKey(writeConfiguration)) {
            activeWrite = memoryConfigurations.get(writeConfiguration);
        } else {
            activeWrite = buildWriteConfiguration();
            memoryConfigurations.put(writeConfiguration, activeWrite);
        }
    }

    public void log(String message) {
        Emulator.withComputer(computer -> {
            CPU cpu = computer.getCpu();
            if (cpu != null && cpu.isLogEnabled()) {
                StringBuilder stack = new StringBuilder();
                for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                    stack.append(String.format("%s.%s(%s);",e.getClassName(), e.getMethodName(), e.getLineNumber()));
                }
                cpu.log(stack.toString());
                String switches = Stream.of(
                        SoftSwitches.RAMRD, SoftSwitches.RAMWRT, SoftSwitches.AUXZP,
                        SoftSwitches._80STORE, SoftSwitches.HIRES, SoftSwitches.PAGE2,
                        SoftSwitches.LCBANK1, SoftSwitches.LCRAM, SoftSwitches.LCWRITE
                ).map(Object::toString).collect(Collectors.joining(";"));
                cpu.log(String.join(";", message, switches));
            }
        });
    }

    /**
     *
     * @param path
     * @throws java.io.IOException
     */
    @Override
    protected void loadRom(String path) throws IOException {
        // Remap writable ram to reflect rom file structure
        byte[] ignore = new byte[256];
        byte[][] restore = new byte[18][];
        for (int i = 0; i < 17; i++) {
            restore[i] = activeWrite.get(i);
            activeWrite.set(i, ignore);
        }
        activeWrite.setBanks(0, cPageRom.getMemory().length, 0x011, cPageRom);
        activeWrite.setBanks(0, rom.getMemory().length, 0x020, rom);
        //----------------------
        InputStream inputRom = getClass().getResourceAsStream(path);
        if (inputRom == null) {
            LOG.log(Level.SEVERE, "Rom not found: {0}", path);
            return;
        }
        // Clear cached configurations as we might have outdated references now        
        memoryConfigurations.clear();
        int read = 0;
        int addr = 0;
        byte[] in = new byte[1024];
        while (addr < 0x00FFFF && (read = inputRom.read(in)) > 0) {
            for (int i = 0; i < read; i++) {
                write(addr++, in[i], false, false);
            }
        }
//            System.out.println("Finished reading rom with " + inputRom.available() + " bytes left unread!");
        //dump();
        for (int i = 0; i < 17; i++) {
            activeWrite.set(i, restore[i]);
        }
        configureActiveMemory();
    }

    /**
     * @return the mainMemory
     */
    public PagedMemory getMainMemory() {
        return mainMemory;
    }

    abstract public PagedMemory getAuxVideoMemory();

    abstract public PagedMemory getAuxMemory();

    abstract public PagedMemory getAuxLanguageCard();

    abstract public PagedMemory getAuxLanguageCard2();

    /**
     * @return the languageCard
     */
    public PagedMemory getLanguageCard() {
        return languageCard;
    }

    /**
     * @return the languageCard2
     */
    public PagedMemory getLanguageCard2() {
        return languageCard2;
    }

    /**
     * @return the cPageRom
     */
    public PagedMemory getcPageRom() {
        return cPageRom;
    }

    /**
     * @return the rom
     */
    public PagedMemory getRom() {
        return rom;
    }

    @Override
    public void copyFrom(RAM otherMemory) {
        RAM128k currentMemory = (RAM128k) otherMemory;

        // This is really quick and dirty but should be sufficient to avoid most crashes...
        blank = currentMemory.blank;
        cPageRom = currentMemory.cPageRom;
        rom = currentMemory.rom;
        mainMemory = currentMemory.mainMemory;
        languageCard = currentMemory.languageCard;
        languageCard2 = currentMemory.languageCard2;
        cards = currentMemory.cards;
        activeSlot = currentMemory.activeSlot;
        // Clear cached configurations as we might have outdated references now
        memoryConfigurations.clear();

        super.copyFrom(otherMemory);
    }
    
    @Override
    public void resetState() {
        memoryConfigurations.clear();
    }
}
