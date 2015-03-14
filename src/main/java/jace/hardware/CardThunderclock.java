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

import jace.apple2e.MOS65C02;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.Computer;
import jace.core.Motherboard;
import jace.core.PagedMemory;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Label;
import javax.swing.ImageIcon;

/**
 * Implementation of the Thunderclock Plus with some limitations:
 *
 * The apple cannot set time. The firmware will act like it is working but
 * nothing will actually happen when a time set command is sent.
 *
 * Though the interrupt features are implemented, they have not been tested.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("ThunderClock Plus")
public class CardThunderclock extends Card {

    Label clockIcon;
    Label clockFixIcon;
    long lastShownIcon = -1;
    // Only mention that the clock is read if it hasn't been checked for over 30 seconds
    // This is to avoid showing it all the time in programs that poll it constantly
    long MIN_WAIT = 30000;
    @ConfigurableField(category = "OS", name = "Patch Prodos Year", description = "If enabled, the Prodos clock driver will be patched to use the current year.")
    public boolean attemptYearPatch = true;

    public CardThunderclock(Computer computer) {
        super(computer);
        try {
            loadRom("jace/data/thunderclock_plus.rom");
        } catch (IOException ex) {
            Logger.getLogger(CardDiskII.class.getName()).log(Level.SEVERE, null, ex);
        }
        clockIcon = Utility.loadIconLabel("clock.png");
        clockFixIcon = Utility.loadIconLabel("clock_fix.png");
        clockFixIcon.setText("Fixed");
    }

    // Raw format: 40 bits, in BCD form (it actually streams out in the reverse order of this, bit 0 first)
    // The data format is fully elaborated in the datasheet of the calendar/clock chip: NEC uPD1990AC
    //  month (1-12) -- hex
    // day of week (0-6)
    // day of month, tens digit (0-3)
    // day of month, ones digit (0-9)
    // hour, tens digit (0-2)
    // hour, ones digit (0-9)
    // minute, tens digit (0-5)
    // minute, ones digit (0-9)
    // second, tens digit (0-5)
    // second, ones digit (0-9)
    @Override
    public void reset() {
        irqAsserted = false;
        irqEnabled = false;
        ticks = 0;
        timerRate = 0;
    }
    public boolean strobe = false;
    public boolean clock = false;
    public boolean shiftMode = false;
    public boolean irqEnabled = false;
    public boolean irqAsserted = false;
    public boolean timerEnabled = false;
    public int timerRate = 0;

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        // Data is read via bit-banging the status register
        // Nibbles are sent lowest significant bit first.
        // Commands are sent to the register followed by a strobe pulse on bit 2 on and off
        // So senting the time read command would be a string of bytes: 0x018, 0x01c and then 0x018 again
        // 
        // Time read is signaled by 0x018 followed by a register shift command 0x08
        // When register shift is active, a clock signal is used to move to the next bit.
        //
        // A bit is placed in data-in (bit 0)
        // Then the clock is raised (bit 1 set) and then lowered (bit 1 unset)
        // After this, the next time the register is read it will have the next bit
        // of the register in the hibit (bit 7)
        // 
        // Reg 0: Command register
        // data in = 0x01
        // clock = 0x02
        // strobe = 0x04
        // register hold = 0x0
        // register shift = 0x08
        // time set = 0x010
        // time read = 0x018
        // Timer modes = 0x020 (64hz), 0x028 (256hz), 0x030 (2048hz)
        // Interrupt enable = 0x040 (IRQ assert is read as 0x020 in the status register)
        // data out = 0x080
        if (type.isRead() && register == 0) {
            e.setNewValue((peekBit()) | (irqAsserted ? 0x020 : 0));
            return;
        }

        if (register == 8) {
            irqAsserted = false;
            return;
        } else if (register != 0) {
            return;
        }

        boolean isClock = (value & 0x02) != 0;
        boolean isStrobe = (value & 0x04) != 0;
        boolean isShift = (value & 0x08) != 0;
        boolean isRead = (value & 0x18) != 0;

        if (!isClock && clock) {
            if (buffer != null) {
                buffer.pop();
            }
        }

        if (!isStrobe && strobe) {
            shiftMode = isShift;
            if (isRead) {
                if (attemptYearPatch) {
                    performProdosPatch();
                }
                getTime();
                clockIcon.setText("Slot " + getSlot());
                long now = System.currentTimeMillis();
                if ((now - lastShownIcon) > MIN_WAIT) {
//                    Emulator.getFrame().addIndicator(this, clockIcon, 3000);
                }
                lastShownIcon = now;
            }
            shiftMode = isShift;
        }

        timerEnabled = (value & 0x020) != 0;
        ticks = 0;
        if (timerEnabled) {
            switch (value & 0x038) {
                case 0x020:
                    timerRate = (int) (Motherboard.SPEED / 64);
                    break;
                case 0x028:
                    timerRate = (int) (Motherboard.SPEED / 256);
                    break;
                case 0x030:
                    timerRate = (int) (Motherboard.SPEED / 2048);
                    break;
                default:
                    timerEnabled = false;
                    timerRate = 0;
            }
        } else {
            timerRate = 0;
        }

        irqEnabled = (value & 0x040) != 0;
        clock = isClock;
        strobe = isStrobe;
    }

    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // Firmware ROM is used -- only I/O port was needed for proper emulation
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // C8 access is used to read the clock directly
    }

    @Override
    protected String getDeviceName() {
        return "Thunderclock Plus";
    }

    int ticks = 0;
    @Override
    public void tick() {
        if (timerEnabled) {
            ticks++;
            if (ticks >= timerRate) {
                ticks = 0;
                irqAsserted = true;
                if (irqEnabled) {
                    computer.getCpu().generateInterrupt();
                }
            }
        }
    }

    private void getTime() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        clearBuffer();
        pushNibble(cal.get(Calendar.MONTH) + 1);
        pushNibble(cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY);
        pushNibble(cal.get(Calendar.DAY_OF_MONTH) / 10);
        pushNibble(cal.get(Calendar.DAY_OF_MONTH) % 10);
        pushNibble(cal.get(Calendar.HOUR_OF_DAY) / 10);
        pushNibble(cal.get(Calendar.HOUR_OF_DAY) % 10);
        pushNibble(cal.get(Calendar.MINUTE) / 10);
        pushNibble(cal.get(Calendar.MINUTE) % 10);
        pushNibble(cal.get(Calendar.SECOND) / 10);
        pushNibble(cal.get(Calendar.SECOND) % 10);
    }
    Stack<Boolean> buffer;

    private void clearBuffer() {
        if (buffer == null) {
            buffer = new Stack<Boolean>();
        } else {
            buffer.clear();
        }
    }

    private void pushNibble(int value) {
        for (int i = 0; i < 4; i++) {
            boolean val = (value & 8) != 0;
            buffer.push(val);
            value <<= 1;
        }
    }

    private int peekBit() {
        if (buffer == null || buffer.isEmpty()) {
            return 0;
        }
        return buffer.peek() ? 0x080 : 0;
    }

    public void loadRom(String path) throws IOException {
        InputStream romFile = CardThunderclock.class.getClassLoader().getResourceAsStream(path);
        final int cxRomLength = 0x0100;
        final int c8RomLength = 0x0700;
        byte[] romxData = new byte[cxRomLength];
        byte[] rom8Data = new byte[c8RomLength];
        try {
            if (romFile.read(romxData) != cxRomLength) {
                throw new IOException("Bad Thunderclock rom size");
            }
            getCxRom().loadData(romxData);
            romFile.close();
            romFile = CardThunderclock.class.getClassLoader().getResourceAsStream(path);
            if (romFile.read(rom8Data) != c8RomLength) {
                throw new IOException("Bad Thunderclock rom size");
            }
            getC8Rom().loadData(rom8Data);
            romFile.close();
        } catch (IOException ex) {
            throw ex;
        }
    }
    static byte[] DRIVER_PATTERN = {
        (byte) 0x00, (byte) 0x01f, (byte) 0x03b, (byte) 0x05a,
        (byte) 0x078, (byte) 0x097, (byte) 0x0b5, (byte) 0x0d3,
        (byte) 0x0f2
    };
    static int DRIVER_OFFSET = -26;
    int patchLoc = -1;

    /**
     * Scan active memory for the Prodos clock driver and patch the internal
     * code to use a fixed value for the present year. This means Prodos will
     * always tell time correctly.
     */
    private void performProdosPatch() {
        PagedMemory ram = computer.getMemory().activeRead;
        if (patchLoc > 0) {
            // We've already patched, just validate
            if (ram.readByte(patchLoc) == (byte) MOS65C02.OPCODE.LDA_IMM.getCode()) {
                return;
            }
        }
        int match = 0;
        int matchStart = 0;
        for (int addr = 0x08000; addr < 0x010000; addr++) {
            if (ram.readByte(addr) == DRIVER_PATTERN[match]) {
                match++;
                if (match == DRIVER_PATTERN.length) {
                    break;
                }
            } else {
                match = 0;
                matchStart = addr;
            }
        }
        if (match != DRIVER_PATTERN.length) {
            return;
        }
        patchLoc = matchStart + DRIVER_OFFSET;
        ram.writeByte(patchLoc, (byte) MOS65C02.OPCODE.LDA_IMM.getCode());
        int year = Calendar.getInstance().get(Calendar.YEAR) % 100;
        ram.writeByte(patchLoc + 1, (byte) year);
        ram.writeByte(patchLoc + 2, (byte) MOS65C02.OPCODE.NOP.getCode());
        ram.writeByte(patchLoc + 3, (byte) MOS65C02.OPCODE.NOP.getCode());
//        Emulator.getFrame().addIndicator(this, clockFixIcon, 4000);
    }
}