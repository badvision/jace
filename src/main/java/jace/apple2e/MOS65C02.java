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
package jace.apple2e;

import jace.config.ConfigurableField;
import jace.core.CPU;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.RAMEvent.TYPE;
import jace.state.Stateful;

/**
 * This is a full implementation of a MOS-65c02 processor, including the BBR,
 * BBS, RMB and SMB opcodes. It is possible that this will be later refactored
 * into a core 6502 and a separate extended 65c02 so that undocumented 6502
 * opcodes could be supported but that's not on the table currently.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
public class MOS65C02 extends CPU {

    public boolean readAddressTriggersEvent = true;
    static int RESET_VECTOR = 0x00FFFC;
    static int INT_VECTOR = 0x00FFFE;
    @Stateful
    public int A = 0x0FF;
    @Stateful
    public int X = 0x0FF;
    @Stateful
    public int Y = 0x0FF;
    @Stateful
    public int C = 1;
    @Stateful
    public boolean interruptSignalled = false;
    @Stateful
    public boolean Z = true;
    @Stateful
    public boolean I = true;
    @Stateful
    public boolean D = true;
    @Stateful
    public boolean B = true;
    @Stateful
    public boolean V = true;
    @Stateful
    public boolean N = true;
    @Stateful
    public int STACK = 0xFF;
    @ConfigurableField(name = "BRK on bad opcode", description = "If on, unrecognized opcodes will be treated as BRK.  Otherwise, they will be NOP")
    public boolean breakOnBadOpcode = false;
    @ConfigurableField(name = "Ext. opcode warnings", description = "If on, uses of 65c02 extended opcodes (or undocumented 6502 opcodes -- which will fail) will be logged to stdout for debugging purposes")
    public boolean warnAboutExtendedOpcodes = false;

    private RAM getMemory() {
        return computer.getMemory();
    }

    public MOS65C02(Computer computer) {
        super(computer);
    }

    @Override
    public void reconfigure() {
    }

    public enum OPCODE {

        ADC_IMM(0x0069, COMMAND.ADC, MODE.IMMEDIATE, 2),
        ADC_ZP(0x0065, COMMAND.ADC, MODE.ZEROPAGE, 3),
        ADC_ZP_X(0x0075, COMMAND.ADC, MODE.ZEROPAGE_X, 4),
        ADC_AB(0x006D, COMMAND.ADC, MODE.ABSOLUTE, 4),
        ADC_IND_ZP(0x0072, COMMAND.ADC, MODE.INDIRECT_ZP, 5, true),
        ADC_IND_ZP_X(0x0061, COMMAND.ADC, MODE.INDIRECT_ZP_X, 6),
        ADC_AB_X(0x007D, COMMAND.ADC, MODE.ABSOLUTE_X, 4),
        ADC_AB_Y(0x0079, COMMAND.ADC, MODE.ABSOLUTE_Y, 4),
        ADC_IND_ZP_Y(0x0071, COMMAND.ADC, MODE.INDIRECT_ZP_Y, 5),
        AND_IMM(0x0029, COMMAND.AND, MODE.IMMEDIATE, 2),
        AND_ZP(0x0025, COMMAND.AND, MODE.ZEROPAGE, 3),
        AND_ZP_X(0x0035, COMMAND.AND, MODE.ZEROPAGE_X, 4),
        AND_AB(0x002D, COMMAND.AND, MODE.ABSOLUTE, 4),
        AND_IND_ZP(0x0032, COMMAND.AND, MODE.INDIRECT_ZP, 5, true),
        AND_IND_ZP_X(0x0021, COMMAND.AND, MODE.INDIRECT_ZP_X, 6),
        AND_AB_X(0x003D, COMMAND.AND, MODE.ABSOLUTE_X, 4),
        AND_AB_Y(0x0039, COMMAND.AND, MODE.ABSOLUTE_Y, 4),
        AND_IND_ZP_Y(0x0031, COMMAND.AND, MODE.INDIRECT_ZP_Y, 5),
        ASL(0x000A, COMMAND.ASL_A, MODE.IMPLIED, 2),
        ASL_ZP(0x0006, COMMAND.ASL, MODE.ZEROPAGE, 5),
        ASL_ZP_X(0x0016, COMMAND.ASL, MODE.ZEROPAGE_X, 6),
        ASL_AB(0x000E, COMMAND.ASL, MODE.ABSOLUTE, 6),
        ASL_AB_X(0x001E, COMMAND.ASL, MODE.ABSOLUTE_X, 7),
        BCC_REL(0x0090, COMMAND.BCC, MODE.RELATIVE, 2),
        BCS_REL(0x00B0, COMMAND.BCS, MODE.RELATIVE, 2),
        BBR0(0x00f, COMMAND.BBR0, MODE.ZP_REL, 5, true),
        BBR1(0x01f, COMMAND.BBR1, MODE.ZP_REL, 5, true),
        BBR2(0x02f, COMMAND.BBR2, MODE.ZP_REL, 5, true),
        BBR3(0x03f, COMMAND.BBR3, MODE.ZP_REL, 5, true),
        BBR4(0x04f, COMMAND.BBR4, MODE.ZP_REL, 5, true),
        BBR5(0x05f, COMMAND.BBR5, MODE.ZP_REL, 5, true),
        BBR6(0x06f, COMMAND.BBR6, MODE.ZP_REL, 5, true),
        BBR7(0x07f, COMMAND.BBR7, MODE.ZP_REL, 5, true),
        BBS0(0x08f, COMMAND.BBS0, MODE.ZP_REL, 5, true),
        BBS1(0x09f, COMMAND.BBS1, MODE.ZP_REL, 5, true),
        BBS2(0x0af, COMMAND.BBS2, MODE.ZP_REL, 5, true),
        BBS3(0x0bf, COMMAND.BBS3, MODE.ZP_REL, 5, true),
        BBS4(0x0cf, COMMAND.BBS4, MODE.ZP_REL, 5, true),
        BBS5(0x0df, COMMAND.BBS5, MODE.ZP_REL, 5, true),
        BBS6(0x0ef, COMMAND.BBS6, MODE.ZP_REL, 5, true),
        BBS7(0x0ff, COMMAND.BBS7, MODE.ZP_REL, 5, true),
        BEQ_REL0(0x00F0, COMMAND.BEQ, MODE.RELATIVE, 2),
        BIT_IMM(0x0089, COMMAND.BIT, MODE.IMMEDIATE, 3, true),
        BIT_ZP(0x0024, COMMAND.BIT, MODE.ZEROPAGE, 3),
        BIT_ZP_X(0x0034, COMMAND.BIT, MODE.ZEROPAGE_X, 3, true),
        BIT_AB(0x002C, COMMAND.BIT, MODE.ABSOLUTE, 4),
        BIT_AB_X(0x003C, COMMAND.BIT, MODE.ABSOLUTE_X, 4, true),
        BMI_REL(0x0030, COMMAND.BMI, MODE.RELATIVE, 2),
        BNE_REL(0x00D0, COMMAND.BNE, MODE.RELATIVE, 2),
        BPL_REL(0x0010, COMMAND.BPL, MODE.RELATIVE, 2),
        BRA_REL(0x0080, COMMAND.BRA, MODE.RELATIVE, 2, true),
        //        BRK(0x0000, COMMAND.BRK, MODE.IMPLIED, 7),
        // Do this so that BRK is treated as a two-byte instruction
        BRK(0x0000, COMMAND.BRK, MODE.IMMEDIATE, 7),
        BVC_REL(0x0050, COMMAND.BVC, MODE.RELATIVE, 2),
        BVS_REL(0x0070, COMMAND.BVS, MODE.RELATIVE, 2),
        CLC(0x0018, COMMAND.CLC, MODE.IMPLIED, 2),
        CLD(0x00D8, COMMAND.CLD, MODE.IMPLIED, 2),
        CLI(0x0058, COMMAND.CLI, MODE.IMPLIED, 2),
        CLV(0x00B8, COMMAND.CLV, MODE.IMPLIED, 2),
        CMP_IMM(0x00C9, COMMAND.CMP, MODE.IMMEDIATE, 2),
        CMP_ZP(0x00C5, COMMAND.CMP, MODE.ZEROPAGE, 3),
        CMP_ZP_X(0x00D5, COMMAND.CMP, MODE.ZEROPAGE_X, 4),
        CMP_AB(0x00CD, COMMAND.CMP, MODE.ABSOLUTE, 4),
        CMP_IND_ZP_X(0x00C1, COMMAND.CMP, MODE.INDIRECT_ZP_X, 6),
        CMP_AB_X(0x00DD, COMMAND.CMP, MODE.ABSOLUTE_X, 4),
        CMP_AB_Y(0x00D9, COMMAND.CMP, MODE.ABSOLUTE_Y, 4),
        CMP_IND_ZP_Y(0x00D1, COMMAND.CMP, MODE.INDIRECT_ZP_Y, 5),
        CMP_IND_ZP(0x00D2, COMMAND.CMP, MODE.INDIRECT_ZP, 5, true),
        CPX_IMM(0x00E0, COMMAND.CPX, MODE.IMMEDIATE, 2),
        CPX_ZP(0x00E4, COMMAND.CPX, MODE.ZEROPAGE, 3),
        CPX_AB(0x00EC, COMMAND.CPX, MODE.ABSOLUTE, 4),
        CPY_IMM(0x00C0, COMMAND.CPY, MODE.IMMEDIATE, 2),
        CPY_ZP(0x00C4, COMMAND.CPY, MODE.ZEROPAGE, 3),
        CPY_AB(0x00CC, COMMAND.CPY, MODE.ABSOLUTE, 4),
        DEC(0x003A, COMMAND.DEA, MODE.IMPLIED, 2, true),
        DEC_ZP(0x00C6, COMMAND.DEC, MODE.ZEROPAGE, 5),
        DEC_ZP_X(0x00D6, COMMAND.DEC, MODE.ZEROPAGE_X, 6),
        DEC_AB(0x00CE, COMMAND.DEC, MODE.ABSOLUTE, 6),
        DEC_AB_X(0x00DE, COMMAND.DEC, MODE.ABSOLUTE_X, 7),
        DEX(0x00CA, COMMAND.DEX, MODE.IMPLIED, 2),
        DEY(0x0088, COMMAND.DEY, MODE.IMPLIED, 2),
        EOR_IMM(0x0049, COMMAND.EOR, MODE.IMMEDIATE, 2),
        EOR_ZP(0x0045, COMMAND.EOR, MODE.ZEROPAGE, 3),
        EOR_ZP_X(0x0055, COMMAND.EOR, MODE.ZEROPAGE_X, 4),
        EOR_AB(0x004D, COMMAND.EOR, MODE.ABSOLUTE, 4),
        EOR_IND_ZP(0x0052, COMMAND.EOR, MODE.INDIRECT_ZP, 5, true),
        EOR_IND_ZP_X(0x0041, COMMAND.EOR, MODE.INDIRECT_ZP_X, 6),
        EOR_AB_X(0x005D, COMMAND.EOR, MODE.ABSOLUTE_X, 4),
        EOR_AB_Y(0x0059, COMMAND.EOR, MODE.ABSOLUTE_Y, 4),
        EOR_IND_ZP_Y(0x0051, COMMAND.EOR, MODE.INDIRECT_ZP_Y, 5),
        INC(0x001A, COMMAND.INA, MODE.IMPLIED, 2, true),
        INC_ZP(0x00E6, COMMAND.INC, MODE.ZEROPAGE, 5),
        INC_ZP_X(0x00F6, COMMAND.INC, MODE.ZEROPAGE_X, 6),
        INC_AB(0x00EE, COMMAND.INC, MODE.ABSOLUTE, 6),
        INC_AB_X(0x00FE, COMMAND.INC, MODE.ABSOLUTE_X, 7),
        INX(0x00E8, COMMAND.INX, MODE.IMPLIED, 2),
        INY(0x00C8, COMMAND.INY, MODE.IMPLIED, 2),
        JMP_AB(0x004C, COMMAND.JMP, MODE.ABSOLUTE, 3),
        JMP_IND(0x006C, COMMAND.JMP, MODE.INDIRECT, 5),
        JMP_IND_X(0x007C, COMMAND.JMP, MODE.INDIRECT_X, 6, true),
        JSR_AB(0x0020, COMMAND.JSR, MODE.ABSOLUTE, 6),
        LDA_IMM(0x00A9, COMMAND.LDA, MODE.IMMEDIATE, 2),
        LDA_ZP(0x00A5, COMMAND.LDA, MODE.ZEROPAGE, 3),
        LDA_ZP_X(0x00B5, COMMAND.LDA, MODE.ZEROPAGE_X, 4),
        LDA_AB(0x00AD, COMMAND.LDA, MODE.ABSOLUTE, 4),
        LDA_IND_ZP_X(0x00A1, COMMAND.LDA, MODE.INDIRECT_ZP_X, 6),
        LDA_AB_X(0x00BD, COMMAND.LDA, MODE.ABSOLUTE_X, 4),
        LDA_AB_Y(0x00B9, COMMAND.LDA, MODE.ABSOLUTE_Y, 4),
        LDA_IND_ZP_Y(0x00B1, COMMAND.LDA, MODE.INDIRECT_ZP_Y, 5),
        LDA_IND_ZP(0x00B2, COMMAND.LDA, MODE.INDIRECT_ZP, 5, true),
        LDX_IMM(0x00A2, COMMAND.LDX, MODE.IMMEDIATE, 2),
        LDX_ZP(0x00A6, COMMAND.LDX, MODE.ZEROPAGE, 3),
        LDX_ZP_Y(0x00B6, COMMAND.LDX, MODE.ZEROPAGE_Y, 4),
        LDX_AB(0x00AE, COMMAND.LDX, MODE.ABSOLUTE, 4),
        LDX_AB_Y(0x00BE, COMMAND.LDX, MODE.ABSOLUTE_Y, 4),
        LDY_IMM(0x00A0, COMMAND.LDY, MODE.IMMEDIATE, 2),
        LDY_ZP(0x00A4, COMMAND.LDY, MODE.ZEROPAGE, 3),
        LDY_ZP_X(0x00B4, COMMAND.LDY, MODE.ZEROPAGE_X, 4),
        LDY_AB(0x00AC, COMMAND.LDY, MODE.ABSOLUTE, 4),
        LDY_AB_X(0x00BC, COMMAND.LDY, MODE.ABSOLUTE_X, 4),
        LSR(0x004A, COMMAND.LSR_A, MODE.IMPLIED, 2),
        LSR_ZP(0x0046, COMMAND.LSR, MODE.ZEROPAGE, 5),
        LSR_ZP_X(0x0056, COMMAND.LSR, MODE.ZEROPAGE_X, 6),
        LSR_AB(0x004E, COMMAND.LSR, MODE.ABSOLUTE, 6),
        LSR_AB_X(0x005E, COMMAND.LSR, MODE.ABSOLUTE_X, 7),
        NOP(0x00EA, COMMAND.NOP, MODE.IMPLIED, 2),
        ORA_IMM(0x0009, COMMAND.ORA, MODE.IMMEDIATE, 2),
        ORA_ZP(0x0005, COMMAND.ORA, MODE.ZEROPAGE, 3),
        ORA_ZP_X(0x0015, COMMAND.ORA, MODE.ZEROPAGE_X, 4),
        ORA_AB(0x000D, COMMAND.ORA, MODE.ABSOLUTE, 4),
        ORA_IND_ZP(0x0012, COMMAND.ORA, MODE.INDIRECT_ZP, 5, true),
        ORA_IND_ZP_X(0x0001, COMMAND.ORA, MODE.INDIRECT_ZP_X, 6),
        ORA_AB_X(0x001D, COMMAND.ORA, MODE.ABSOLUTE_X, 4),
        ORA_AB_Y(0x0019, COMMAND.ORA, MODE.ABSOLUTE_Y, 4),
        ORA_IND_ZP_Y(0x0011, COMMAND.ORA, MODE.INDIRECT_ZP_Y, 5),
        PHA(0x0048, COMMAND.PHA, MODE.IMPLIED, 3),
        PHP(0x0008, COMMAND.PHP, MODE.IMPLIED, 3),
        PHX(0x00DA, COMMAND.PHX, MODE.IMPLIED, 3, true),
        PHY(0x005A, COMMAND.PHY, MODE.IMPLIED, 3, true),
        PLA(0x0068, COMMAND.PLA, MODE.IMPLIED, 4),
        PLP(0x0028, COMMAND.PLP, MODE.IMPLIED, 4),
        PLX(0x00FA, COMMAND.PLX, MODE.IMPLIED, 4, true),
        PLY(0x007A, COMMAND.PLY, MODE.IMPLIED, 4, true),
        RMB0(0x007, COMMAND.RMB0, MODE.ZEROPAGE, 5, true),
        RMB1(0x017, COMMAND.RMB1, MODE.ZEROPAGE, 5, true),
        RMB2(0x027, COMMAND.RMB2, MODE.ZEROPAGE, 5, true),
        RMB3(0x037, COMMAND.RMB3, MODE.ZEROPAGE, 5, true),
        RMB4(0x047, COMMAND.RMB4, MODE.ZEROPAGE, 5, true),
        RMB5(0x057, COMMAND.RMB5, MODE.ZEROPAGE, 5, true),
        RMB6(0x067, COMMAND.RMB6, MODE.ZEROPAGE, 5, true),
        RMB7(0x077, COMMAND.RMB7, MODE.ZEROPAGE, 5, true),
        ROL(0x002A, COMMAND.ROL_A, MODE.IMPLIED, 2),
        ROL_ZP(0x0026, COMMAND.ROL, MODE.ZEROPAGE, 5),
        ROL_ZP_X(0x0036, COMMAND.ROL, MODE.ZEROPAGE_X, 6),
        ROL_AB(0x002E, COMMAND.ROL, MODE.ABSOLUTE, 6),
        ROL_AB_X(0x003E, COMMAND.ROL, MODE.ABSOLUTE_X, 7),
        ROR(0x006A, COMMAND.ROR_A, MODE.IMPLIED, 2),
        ROR_ZP(0x0066, COMMAND.ROR, MODE.ZEROPAGE, 5),
        ROR_ZP_X(0x0076, COMMAND.ROR, MODE.ZEROPAGE_X, 6),
        ROR_AB(0x006E, COMMAND.ROR, MODE.ABSOLUTE, 6),
        ROR_AB_X(0x007E, COMMAND.ROR, MODE.ABSOLUTE_X, 7),
        RTI(0x0040, COMMAND.RTI, MODE.IMPLIED, 6),
        RTS(0x0060, COMMAND.RTS, MODE.IMPLIED, 6),
        SBC_IMM(0x00E9, COMMAND.SBC, MODE.IMMEDIATE, 2),
        SBC_ZP(0x00E5, COMMAND.SBC, MODE.ZEROPAGE, 3),
        SBC_ZP_X(0x00F5, COMMAND.SBC, MODE.ZEROPAGE_X, 4),
        SBC_AB(0x00ED, COMMAND.SBC, MODE.ABSOLUTE, 4),
        SBC_IND_ZP(0x00F2, COMMAND.SBC, MODE.INDIRECT_ZP, 5, true),
        SBC_IND_ZP_X(0x00E1, COMMAND.SBC, MODE.INDIRECT_ZP_X, 6),
        SBC_AB_X(0x00FD, COMMAND.SBC, MODE.ABSOLUTE_X, 4),
        SBC_AB_Y(0x00F9, COMMAND.SBC, MODE.ABSOLUTE_Y, 4),
        SBC_IND_ZP_Y(0x00F1, COMMAND.SBC, MODE.INDIRECT_ZP_Y, 5),
        SEC(0x0038, COMMAND.SEC, MODE.IMPLIED, 2),
        SED(0x00F8, COMMAND.SED, MODE.IMPLIED, 2),
        SEI(0x0078, COMMAND.SEI, MODE.IMPLIED, 2),
        SMB0(0x087, COMMAND.SMB0, MODE.ZEROPAGE, 5, true),
        SMB1(0x097, COMMAND.SMB1, MODE.ZEROPAGE, 5, true),
        SMB2(0x0a7, COMMAND.SMB2, MODE.ZEROPAGE, 5, true),
        SMB3(0x0b7, COMMAND.SMB3, MODE.ZEROPAGE, 5, true),
        SMB4(0x0c7, COMMAND.SMB4, MODE.ZEROPAGE, 5, true),
        SMB5(0x0d7, COMMAND.SMB5, MODE.ZEROPAGE, 5, true),
        SMB6(0x0e7, COMMAND.SMB6, MODE.ZEROPAGE, 5, true),
        SMB7(0x0f7, COMMAND.SMB7, MODE.ZEROPAGE, 5, true),
        STA_ZP(0x0085, COMMAND.STA, MODE.ZEROPAGE, 3),
        STA_ZP_X(0x0095, COMMAND.STA, MODE.ZEROPAGE_X, 4),
        STA_AB(0x008D, COMMAND.STA, MODE.ABSOLUTE, 4),
        STA_AB_X(0x009D, COMMAND.STA, MODE.ABSOLUTE_X, 5),
        STA_AB_Y(0x0099, COMMAND.STA, MODE.ABSOLUTE_Y, 5),
        STA_IND_ZP(0x0092, COMMAND.STA, MODE.INDIRECT_ZP, 5, true),
        STA_IND_ZP_X(0x0081, COMMAND.STA, MODE.INDIRECT_ZP_X, 6),
        STA_IND_ZP_Y(0x0091, COMMAND.STA, MODE.INDIRECT_ZP_Y, 6),
        STP(0x00DB, COMMAND.STP, MODE.IMPLIED, 3, true),
        STX_ZP(0x0086, COMMAND.STX, MODE.ZEROPAGE, 3),
        STX_ZP_Y(0x0096, COMMAND.STX, MODE.ZEROPAGE_Y, 4),
        STX_AB(0x008E, COMMAND.STX, MODE.ABSOLUTE, 4),
        STY_ZP(0x0084, COMMAND.STY, MODE.ZEROPAGE, 3),
        STY_ZP_X(0x0094, COMMAND.STY, MODE.ZEROPAGE_X, 4),
        STY_AB(0x008C, COMMAND.STY, MODE.ABSOLUTE, 4),
        STZ_ZP(0x0064, COMMAND.STZ, MODE.ZEROPAGE, 3, true),
        STZ_ZP_X(0x0074, COMMAND.STZ, MODE.ZEROPAGE_X, 4, true),
        STZ_AB(0x009C, COMMAND.STZ, MODE.ABSOLUTE, 4, true),
        STZ_AB_X(0x009E, COMMAND.STZ, MODE.ABSOLUTE_X, 5, true),
        TAX(0x00AA, COMMAND.TAX, MODE.IMPLIED, 2),
        TAY(0x00A8, COMMAND.TAY, MODE.IMPLIED, 2),
        TRB_ZP(0x0014, COMMAND.TRB, MODE.ZEROPAGE, 5, true),
        TRB_AB(0x001C, COMMAND.TRB, MODE.ABSOLUTE, 6, true),
        TSB_ZP(0x0004, COMMAND.TSB, MODE.ZEROPAGE, 5, true),
        TSB_AB(0x000C, COMMAND.TSB, MODE.ABSOLUTE, 6, true),
        TSX(0x00BA, COMMAND.TSX, MODE.IMPLIED, 2),
        TXA(0x008A, COMMAND.TXA, MODE.IMPLIED, 2),
        TXS(0x009A, COMMAND.TXS, MODE.IMPLIED, 2),
        TYA(0x0098, COMMAND.TYA, MODE.IMPLIED, 2),
        WAI(0x00CB, COMMAND.WAI, MODE.IMPLIED, 3, true);
        private int code;
        private boolean isExtendedOpcode;

        public int getCode() {
            return code;
        }
        private int waitCycles;

        public int getWaitCycles() {
            return waitCycles;
        }
        private COMMAND command;

        public COMMAND getCommand() {
            return command;
        }
        private MODE addressingMode;

        public MODE getMode() {
            return addressingMode;
        }
        int address = 0;
        int value = 0;

        private void fetch(MOS65C02 cpu) {
            address = getMode().calculator.calculateAddress(cpu);
            value = getMode().calculator.getValue(!command.isStoreOnly(), cpu);
        }

        public void execute(MOS65C02 cpu) {
            command.getProcessor().processCommand(address, value, addressingMode, cpu);
        }

        private OPCODE(int val, COMMAND c, MODE m, int wait) {
            this(val, c, m, wait, false);
        }

        private OPCODE(int val, COMMAND c, MODE m, int wait, boolean extended) {
            code = val;
            waitCycles = wait - 1;
            command = c;
            addressingMode = m;
            isExtendedOpcode = extended;
        }
    }

    private static interface AddressCalculator {

        abstract int calculateAddress(MOS65C02 cpu);

        default int getValue(boolean isRead, MOS65C02 cpu) {
            int address = calculateAddress(cpu);
            return (address > -1) ? (0x0ff & cpu.getMemory().read(address, TYPE.READ_DATA, isRead, false)) : 0;
        }
    }

    public enum MODE {

        IMPLIED(1, "", (cpu) -> -1),
        //        RELATIVE(2, "#$~1 ($R)"),
        RELATIVE(2, "$R", (cpu) -> {
            int pc = cpu.getProgramCounter();
            int address = pc + 2 + cpu.getMemory().read(pc + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
            // The wait cycles are not added unless the branch actually happens!
            cpu.setPageBoundaryPenalty((address & 0x00ff00) != (pc & 0x00ff00));
            return address;
        }),
        IMMEDIATE(2, "#$~1", (cpu) -> cpu.getProgramCounter() + 1),
        ZEROPAGE(2, "$~1", (cpu) -> cpu.getMemory().read(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false) & 0x00FF),
        ZEROPAGE_X(2, "$~1,X", (cpu) -> 0x0FF & (cpu.getMemory().read(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false) + cpu.X)),
        ZEROPAGE_Y(2, "$~1,Y", (cpu) -> 0x0FF & (cpu.getMemory().read(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false) + cpu.Y)),
        INDIRECT(3, "$(~2~1)", (cpu) -> {
            int address = cpu.getMemory().readWord(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
            return cpu.getMemory().readWord(address, TYPE.READ_DATA, true, false);
        }),
        INDIRECT_X(3, "$(~2~1,X)", (cpu) -> {
            int address = cpu.getMemory().readWord(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false) + cpu.X;
            return cpu.getMemory().readWord(address & 0x0FFFF, TYPE.READ_DATA, true, false);
        }),
        INDIRECT_ZP(2, "$(~1)", (cpu) -> {
            int address = cpu.getMemory().read(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
            return cpu.getMemory().readWord(address & 0x0FF, TYPE.READ_DATA, true, false);
        }),
        INDIRECT_ZP_X(2, "$(~1,X)", (cpu) -> {
            int address = cpu.getMemory().read(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false) + cpu.X;
            return cpu.getMemory().readWord(address & 0x0FF, TYPE.READ_DATA, true, false);
        }),
        INDIRECT_ZP_Y(2, "$(~1),Y", (cpu) -> {
            int address = 0x00FF & cpu.getMemory().read(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
            address = cpu.getMemory().readWord(address, TYPE.READ_DATA, true, false) + cpu.Y;
            if ((address & 0x00ff00) > 0) {
                cpu.addWaitCycles(1);
            }
            return address;
        }),
        ABSOLUTE(3, "$~2~1", (cpu) -> cpu.getMemory().readWord(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false)),
        ABSOLUTE_X(3, "$~2~1,X", (cpu) -> {
            int address2 = cpu.getMemory().readWord(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
            int address = 0x0FFFF & (address2 + cpu.X);
            if ((address & 0x00FF00) != (address2 & 0x00FF00)) {
                cpu.addWaitCycles(1);
            }
            return address;
        }),
        ABSOLUTE_Y(3, "$~2~1,Y", (cpu) -> {
            int address2 = cpu.getMemory().readWord(cpu.getProgramCounter() + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
            int address = 0x0FFFF & (address2 + cpu.Y);
            if ((address & 0x00FF00) != (address2 & 0x00FF00)) {
                cpu.addWaitCycles(1);
            }
            return address;
        }),
        ZP_REL(2, "$~1,$R", new AddressCalculator() {
            @Override
            public int calculateAddress(MOS65C02 cpu) {
                // Note: This is two's compliment addition and the cpu.getMemory().read() returns a signed 8-bit value
                int pc = cpu.getProgramCounter();
                int address = pc + 2 + cpu.getMemory().read(pc + 2, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
                // The wait cycles are not added unless the branch actually happens!
                cpu.setPageBoundaryPenalty((address & 0x00ff00) != (pc & 0x00ff00));
                return address;
            }

            @Override
            public int getValue(boolean isRead, MOS65C02 cpu) {
                int pc = cpu.getProgramCounter();
                int address = cpu.getMemory().read(pc + 1, TYPE.READ_OPERAND, cpu.readAddressTriggersEvent, false);
                return cpu.getMemory().read(address, TYPE.READ_DATA, true, false);
            }
        });
        private int size;

        public int getSize() {
            return this.size;
        }
//        private String format;
//
//        public String getFormat() {
//            return this.format;
//        }
        private AddressCalculator calculator;

        public int calcAddress(MOS65C02 cpu) {
            return calculator.calculateAddress(cpu);
        }
        private boolean indirect;

        public boolean isIndirect() {
            return indirect;
        }
        String f1;
        String f2;
        boolean twoByte = false;
        boolean relative = false;
        boolean implied = true;

        private MODE(int size, String fmt, AddressCalculator calc) {
            this.size = size;
            if (fmt.contains("~")) {
                this.f1 = fmt.substring(0, fmt.indexOf('~'));
                this.f2 = fmt.substring(fmt.indexOf("~1") + 2);
                if (fmt.contains("~2")) {
                    twoByte = true;
                }
                implied = false;
            } else if (fmt.contains("R")) {
                this.f1 = fmt.substring(0, fmt.indexOf("R"));
                f2 = "";
                relative = true;
                implied = false;
            }
//            this.format = fmt;

            this.calculator = calc;
            this.indirect = toString().startsWith("INDIRECT");
        }

        public MOS65C02.AddressCalculator getCalculator() {
            return calculator;
        }

        public String formatMode(int pc, MOS65C02 cpu) {
            if (implied) {
                return "";
            } else {
                int b1 = 0x00ff & cpu.getMemory().readRaw((pc + 1) & 0x0FFFF);
                if (relative) {
                    String R = wordString(pc + 2 + (byte) b1);
                    return f1 + R;
                } else if (twoByte) {
                    int b2 = 0x00ff & cpu.getMemory().readRaw((pc + 2) & 0x0FFFF);
                    return f1 + byte2(b2) + byte2(b1) + f2;
                } else {
                    return f1 + byte2(b1) + f2;
                }
            }
        }
    }

    private static interface CommandProcessor {

        public void processCommand(int address, int value, MODE addressMode, MOS65C02 cpu);
    }

    private static class BBRCommand implements CommandProcessor {

        int bit;

        public BBRCommand(int bit) {
            this.bit = bit;
        }

        @Override
        public void processCommand(int address, int value, MODE addressMode, MOS65C02 cpu) {
            if (((value >> bit) & 1) != 0) {
                return;
            }
            if (cpu.C != 0) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }
    }

    private static class BBSCommand implements CommandProcessor {

        int bit;

        public BBSCommand(int bit) {
            this.bit = bit;
        }

        @Override
        public void processCommand(int address, int value, MODE addressMode, MOS65C02 cpu) {
            if (((value >> bit) & 1) == 0) {
                return;
            }
            if (cpu.C != 0) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }
    }

    private static class RMBCommand implements CommandProcessor {

        int bit;

        public RMBCommand(int bit) {
            this.bit = bit;
        }

        @Override
        public void processCommand(int address, int value, MODE addressMode, MOS65C02 cpu) {
            int mask = 0x0ff ^ (1 << bit);
            value &= mask;
            cpu.getMemory().write(address, (byte) value, true, false);
        }
    }

    private static class SMBCommand implements CommandProcessor {

        int bit;

        public SMBCommand(int bit) {
            this.bit = bit;
        }

        @Override
        public void processCommand(int address, int value, MODE addressMode, MOS65C02 cpu) {
            int mask = 1 << bit;
            value |= mask;
            cpu.getMemory().write(address, (byte) value, true, false);
        }
    }

    public enum COMMAND {

        ADC((address, value, addressMode, cpu) -> {
            int w;
            cpu.V = ((cpu.A ^ value) & 0x080) == 0;
            if (cpu.D) {
                // Decimal Mode
                w = (cpu.A & 0x0f) + (value & 0x0f) + cpu.C;
                if (w >= 10) {
                    w = 0x010 | ((w + 6) & 0x0f);
                }
                w += (cpu.A & 0x0f0) + (value & 0x00f0);
                if (w >= 0x0A0) {
                    cpu.C = 1;
                    if (cpu.V && w >= 0x0180) {
                        cpu.V = false;
                    }
                    w += 0x060;
                } else {
                    cpu.C = 0;
                    if (cpu.V && w < 0x080) {
                        cpu.V = false;
                    }
                }
            } else {
                // Binary Mode
                w = cpu.A + value + cpu.C;
                if (w >= 0x0100) {
                    cpu.C = 1;
                    if (cpu.V && w >= 0x0180) {
                        cpu.V = false;
                    }
                } else {
                    cpu.C = 0;
                    if (cpu.V && w < 0x080) {
                        cpu.V = false;
                    }
                }
            }
            cpu.A = w & 0x0ff;
            cpu.setNZ(cpu.A);
        }),
        AND((address, value, addressMode, cpu) -> {
            cpu.A &= value;
            cpu.setNZ(cpu.A);
        }),
        ASL((address, value, addressMode, cpu) -> {
            cpu.C = ((value & 0x080) != 0) ? 1 : 0;
            value = 0x0FE & (value << 1);
            cpu.setNZ(value);
            // Emulate correct behavior of fetch-store-modify
            // http://forum.6502.org/viewtopic.php?f=4&t=1617&view=previous
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.getMemory().write(address, (byte) value, true, false);
        }),
        ASL_A((address, value, addressMode, cpu) -> {
            cpu.C = cpu.A >> 7;
            cpu.A = 0x0FE & (cpu.A << 1);
            cpu.setNZ(cpu.A);
        }),
        BBR0(new BBRCommand(0)),
        BBR1(new BBRCommand(1)),
        BBR2(new BBRCommand(2)),
        BBR3(new BBRCommand(3)),
        BBR4(new BBRCommand(4)),
        BBR5(new BBRCommand(5)),
        BBR6(new BBRCommand(6)),
        BBR7(new BBRCommand(7)),
        BBS0(new BBSCommand(0)),
        BBS1(new BBSCommand(1)),
        BBS2(new BBSCommand(2)),
        BBS3(new BBSCommand(3)),
        BBS4(new BBSCommand(4)),
        BBS5(new BBSCommand(5)),
        BBS6(new BBSCommand(6)),
        BBS7(new BBSCommand(7)),
        BCC((address, value, addressMode, cpu) -> {
            if (cpu.C == 0) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        BCS((address, value, addressMode, cpu) -> {
            if (cpu.C != 0) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        BEQ((address, value, addressMode, cpu) -> {
            if (cpu.Z) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        BIT((address, value, addressMode, cpu) -> {
            int result = (cpu.A & value);
            cpu.Z = result == 0;
            cpu.N = (value & 0x080) != 0;
            // As per http://www.6502.org/tutorials/vflag.html
            if (addressMode != MODE.IMMEDIATE) {
                cpu.V = (value & 0x040) != 0;
            }
        }),
        BMI((address, value, addressMode, cpu) -> {
            if (cpu.N) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        BNE((address, value, addressMode, cpu) -> {
            if (!cpu.Z) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        BPL((address, value, addressMode, cpu) -> {
            if (!cpu.N) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        BRA((address, value, addressMode, cpu) -> {
            cpu.setProgramCounter(address);
            cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 1 : 0);
        }),
        BRK((address, value, addressMode, cpu) -> {
            cpu.BRK();
        }),
        BVC((address, value, addressMode, cpu) -> {
            if (!cpu.V) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        BVS((address, value, addressMode, cpu) -> {
            if (cpu.V) {
                cpu.setProgramCounter(address);
                cpu.addWaitCycles(cpu.pageBoundaryPenalty ? 2 : 1);
            }
        }),
        CLC((address, value, addressMode, cpu) -> {
            cpu.C = 0;
        }),
        CLD((address, value, addressMode, cpu) -> {
            cpu.D = false;
        }),
        CLI((address, value, addressMode, cpu) -> {
            cpu.I = false;
            cpu.interruptSignalled = false;
        }),
        CLV((address, value, addressMode, cpu) -> {
            cpu.V = false;
        }),
        CMP((address, value, addressMode, cpu) -> {
            int val = cpu.A - value;
            cpu.C = (val >= 0) ? 1 : 0;
            cpu.setNZ(val);
        }),
        CPX((address, value, addressMode, cpu) -> {
            int val = cpu.X - value;
            cpu.C = (val >= 0) ? 1 : 0;
            cpu.setNZ(val);
        }),
        CPY((address, value, addressMode, cpu) -> {
            int val = cpu.Y - value;
            cpu.C = (val >= 0) ? 1 : 0;
            cpu.setNZ(val);
        }),
        DEC((address, value, addressMode, cpu) -> {
            value = 0x0FF & (value - 1);
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.setNZ(value);
        }),
        DEA((address, value, addressMode, cpu) -> {
            cpu.A = 0x0FF & (cpu.A - 1);
            cpu.setNZ(cpu.A);
        }),
        DEX((address, value, addressMode, cpu) -> {
            cpu.X = 0x0FF & (cpu.X - 1);
            cpu.setNZ(cpu.X);
        }),
        DEY((address, value, addressMode, cpu) -> {
            cpu.Y = 0x0FF & (cpu.Y - 1);
            cpu.setNZ(cpu.Y);
        }),
        EOR((address, value, addressMode, cpu) -> {
            cpu.A = 0x0FF & (cpu.A ^ value);
            cpu.setNZ(cpu.A);
        }),
        INC((address, value, addressMode, cpu) -> {
            value = 0x0ff & (value + 1);
            // emulator correct fetch-modify-store behavior
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.setNZ(value);
        }),
        INA((address, value, addressMode, cpu) -> {
            cpu.A = 0x0FF & (cpu.A + 1);
            cpu.setNZ(cpu.A);
        }),
        INX((address, value, addressMode, cpu) -> {
            cpu.X = 0x0FF & (cpu.X + 1);
            cpu.setNZ(cpu.X);
        }),
        INY((address, value, addressMode, cpu) -> {
            cpu.Y = 0x0FF & (cpu.Y + 1);
            cpu.setNZ(cpu.Y);
        }),
        JMP((address, value, addressMode, cpu) -> {
            cpu.setProgramCounter(address);
        }),
        JSR((address, value, addressMode, cpu) -> {
            cpu.pushWord(cpu.getProgramCounter() - 1);
            cpu.setProgramCounter(address);
        }),
        LDA((address, value, addressMode, cpu) -> {
            cpu.A = value;
            cpu.setNZ(cpu.A);
        }),
        LDX((address, value, addressMode, cpu) -> {
            cpu.X = value;
            cpu.setNZ(cpu.X);
        }),
        LDY((address, value, addressMode, cpu) -> {
            cpu.Y = value;
            cpu.setNZ(cpu.Y);
        }),
        LSR((address, value, addressMode, cpu) -> {
            cpu.C = (value & 1);
            value = (value >> 1) & 0x07F;
            cpu.setNZ(value);
            // emulator correct fetch-modify-store behavior
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.getMemory().write(address, (byte) value, true, false);
        }),
        LSR_A((address, value, addressMode, cpu) -> {
            cpu.C = cpu.A & 1;
            cpu.A = (cpu.A >> 1) & 0x07F;
            cpu.setNZ(cpu.A);
        }),
        NOP((address, value, addressMode, cpu) -> {
        }),
        ORA((address, value, addressMode, cpu) -> {
            cpu.A |= value;
            cpu.setNZ(cpu.A);
        }),
        PHA((address, value, addressMode, cpu) -> {
            cpu.push((byte) cpu.A);
        }),
        PHP((address, value, addressMode, cpu) -> {
            cpu.push((byte) (cpu.getStatus()));
        }),
        PHX((address, value, addressMode, cpu) -> {
            cpu.push((byte) cpu.X);
        }),
        PHY((address, value, addressMode, cpu) -> {
            cpu.push((byte) cpu.Y);
        }),
        PLA((address, value, addressMode, cpu) -> {
            cpu.A = 0x0FF & cpu.pop();
            cpu.setNZ(cpu.A);
        }),
        PLP((address, value, addressMode, cpu) -> {
            cpu.setStatus(cpu.pop());
        }),
        PLX((address, value, addressMode, cpu) -> {
            cpu.X = 0x0FF & cpu.pop();
            cpu.setNZ(cpu.X);
        }),
        PLY((address, value, addressMode, cpu) -> {
            cpu.Y = 0x0FF & cpu.pop();
            cpu.setNZ(cpu.Y);
        }),
        RMB0(new RMBCommand(0)),
        RMB1(new RMBCommand(1)),
        RMB2(new RMBCommand(2)),
        RMB3(new RMBCommand(3)),
        RMB4(new RMBCommand(4)),
        RMB5(new RMBCommand(5)),
        RMB6(new RMBCommand(6)),
        RMB7(new RMBCommand(7)),
        ROL((address, value, addressMode, cpu) -> {
            int oldC = cpu.C;
            cpu.C = value >> 7;
            value = 0x0ff & ((value << 1) | oldC);
            cpu.setNZ(value);
            // emulator correct fetch-modify-store behavior
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.getMemory().write(address, (byte) value, true, false);
        }),
        ROL_A((address, value, addressMode, cpu) -> {
            int oldC = cpu.C;
            cpu.C = cpu.A >> 7;
            cpu.A = 0x0ff & ((cpu.A << 1) | oldC);
            cpu.setNZ(cpu.A);
        }),
        ROR((address, value, addressMode, cpu) -> {
            int oldC = cpu.C << 7;
            cpu.C = value & 1;
            value = 0x0ff & ((value >> 1) | oldC);
            cpu.setNZ(value);
            // emulator correct fetch-modify-store behavior
            cpu.getMemory().write(address, (byte) value, true, false);
            cpu.getMemory().write(address, (byte) value, true, false);
        }),
        ROR_A((address, value, addressMode, cpu) -> {
            int oldC = cpu.C << 7;
            cpu.C = cpu.A & 1;
            cpu.A = 0x0ff & ((cpu.A >> 1) | oldC);
            cpu.setNZ(cpu.A);
        }),
        RTI((address, value, addressMode, cpu) -> {
            cpu.returnFromInterrupt();
        }),
        RTS((address, value, addressMode, cpu) -> {
            cpu.setProgramCounter(cpu.popWord() + 1);
        }),
        SBC((address, value, addressMode, cpu) -> {
            cpu.V = ((cpu.A ^ value) & 0x080) != 0;
            int w;
            if (cpu.D) {
                int temp = 0x0f + (cpu.A & 0x0f) - (value & 0x0f) + cpu.C;
                if (temp < 0x10) {
                    w = 0;
                    temp -= 6;
                } else {
                    w = 0x10;
                    temp -= 0x10;
                }
                w += 0x00f0 + (cpu.A & 0x00f0) - (value & 0x00f0);
                if (w < 0x100) {
                    cpu.C = 0;
                    if (cpu.V && w < 0x080) {
                        cpu.V = false;
                    }
                    w -= 0x60;
                } else {
                    cpu.C = 1;
                    if (cpu.V && w >= 0x180) {
                        cpu.V = false;
                    }
                }
                w += temp;
            } else {
                w = 0x0ff + cpu.A - value + cpu.C;
                if (w < 0x100) {
                    cpu.C = 0;
                    if (cpu.V && (w < 0x080)) {
                        cpu.V = false;
                    }
                } else {
                    cpu.C = 1;
                    if (cpu.V && (w >= 0x180)) {
                        cpu.V = false;
                    }
                }
            }
            cpu.A = w & 0x0ff;
            cpu.setNZ(cpu.A);
        }),
        SEC((address, value, addressMode, cpu) -> {
            cpu.C = 1;
        }),
        SED((address, value, addressMode, cpu) -> {
            cpu.D = true;
        }),
        SEI((address, value, addressMode, cpu) -> {
            cpu.I = true;
        }),
        SMB0(new SMBCommand(0)),
        SMB1(new SMBCommand(1)),
        SMB2(new SMBCommand(2)),
        SMB3(new SMBCommand(3)),
        SMB4(new SMBCommand(4)),
        SMB5(new SMBCommand(5)),
        SMB6(new SMBCommand(6)),
        SMB7(new SMBCommand(7)),
        STA(true, (address, value, addressMode, cpu) -> {
            cpu.getMemory().write(address, (byte) cpu.A, true, false);
        }),
        STP((address, value, addressMode, cpu) -> {
            cpu.suspend();
        }),
        STX(true, (address, value, addressMode, cpu) -> {
            cpu.getMemory().write(address, (byte) cpu.X, true, false);
        }),
        STY(true, (address, value, addressMode, cpu) -> {
            cpu.getMemory().write(address, (byte) cpu.Y, true, false);
        }),
        STZ(true, (address, value, addressMode, cpu) -> {
            cpu.getMemory().write(address, (byte) 0, true, false);
        }),
        TAX((address, value, addressMode, cpu) -> {
            cpu.X = cpu.A;
            cpu.setNZ(cpu.X);
        }),
        TAY((address, value, addressMode, cpu) -> {
            cpu.Y = cpu.A;
            cpu.setNZ(cpu.Y);
        }),
        TRB((address, value, addressMode, cpu) -> {
            cpu.C = (value & cpu.A) != 0 ? 1 : 0;
            value &= ~cpu.A;
            cpu.getMemory().write(address, (byte) value, true, false);
        }),
        TSB((address, value, addressMode, cpu) -> {
            cpu.C = (value & cpu.A) != 0 ? 1 : 0;
            value |= cpu.A;
            cpu.getMemory().write(address, (byte) value, true, false);
        }),
        TSX((address, value, addressMode, cpu) -> {
            cpu.X = cpu.STACK;
            cpu.setNZ(cpu.STACK);
        }),
        TXA((address, value, addressMode, cpu) -> {
            cpu.A = cpu.X;
            cpu.setNZ(cpu.X);
        }),
        TXS((address, value, addressMode, cpu) -> {
            cpu.STACK = cpu.X;
        }),
        TYA((address, value, addressMode, cpu) -> {
            cpu.A = cpu.Y;
            cpu.setNZ(cpu.Y);
        }),
        WAI((address, value, addressMode, cpu) -> {
            cpu.waitForInterrupt();
        });
        private CommandProcessor processor;

        public CommandProcessor getProcessor() {
            return processor;
        }
        private boolean storeOnly;

        public boolean isStoreOnly() {
            return storeOnly;
        }

        private COMMAND(CommandProcessor processor) {
            this(false, processor);
        }

        private COMMAND(boolean storeOnly, CommandProcessor processor) {
            this.storeOnly = storeOnly;
            this.processor = processor;
        }
    }
    static private OPCODE[] opcodes;

    static {
        opcodes = new OPCODE[256];
        for (OPCODE o : OPCODE.values()) {
            opcodes[o.getCode()] = o;
        }
    }

    @Override
    protected void executeOpcode() {
        if (interruptSignalled) {
            processInterrupt();
        }
        int pc = getProgramCounter();

//        RAM ram = cpu.getMemory();
//        int op = 0x00ff & cpu.getMemory().read(pc, false);
        // This makes it possible to trap the memory read of an opcode, when PC == Address, you know it is executing that opcode.
        int op = 0x00ff & getMemory().read(pc, TYPE.EXECUTE, true, false);
        OPCODE opcode = opcodes[op];
        if (isTraceEnabled() || isLogEnabled() || (warnAboutExtendedOpcodes && opcode != null && opcode.isExtendedOpcode)) {
            String t = getState().toUpperCase() + "  " + Integer.toString(pc, 16) + " : " + disassemble();
            if (warnAboutExtendedOpcodes && opcode != null && opcode.isExtendedOpcode) {
                System.out.println(">>EXTENDED OPCODE DETECTED " + Integer.toHexString(opcode.code) + "<<");
                System.out.println(t);
                if (isLogEnabled()) {
                    log(">>EXTENDED OPCODE DETECTED " + Integer.toHexString(opcode.code) + "<<");
                    log(t);
                }
            } else {
                if (isTraceEnabled()) {
                    System.out.println(t);
                }
                if (isLogEnabled()) {
                    log(t);
                }
            }
        }
        if (opcode == null) {
            // handle bad opcode as a NOP
            int wait = 0;
            int bytes = 2;
            int n = op & 0x0f;
            if (n == 2) {
                wait = 2;
            } else if (n == 3 || n == 7 || n == 0x0b || n == 0x0f) {
                wait = 1;
                bytes = 1;
            } else if (n == 4) {
                bytes = 2;
                if ((op & 0x0f0) == 0x040) {
                    wait = 3;
                } else {
                    wait = 4;
                }
            } else if (n == 0x0c) {
                bytes = 3;
                if ((op & 0x0f0) == 0x050) {
                    wait = 8;
                } else {
                    wait = 4;
                }
            }
            incrementProgramCounter(bytes);
            addWaitCycles(wait);

            if (isLogEnabled() || breakOnBadOpcode) {
                System.out.println("Unrecognized opcode "
                        + Integer.toHexString(op)
                        + " at " + Integer.toHexString(pc));
            }
            if (isLogEnabled()) {
                dumpTrace();
            }
            if (breakOnBadOpcode) {
                OPCODE.BRK.execute(this);
            }
        } else {
            opcode.fetch(this);
            incrementProgramCounter(opcode.getMode().getSize());
            opcode.execute(this);
            addWaitCycles(opcode.getWaitCycles());
        }
    }

    private void setNZ(int value) {
        N = (value & 0x080) != 0;
        Z = (value & 0x0ff) == 0;
    }

    public void pushWord(int val) {
        push((byte) (val >> 8));
        push((byte) (val & 0x00ff));
    }

    public int popWord() {
        return (0x0FF & pop()) | (0x0ff00 & (pop() << 8));
    }

    public void push(byte val) {
        getMemory().write(0x0100 + STACK, val, true, false);
        STACK = (STACK - 1) & 0x0FF;
        //System.out.println("--> PUSH "+Integer.toString(0x0FF & val, 16));
    }

    public byte pop() {
        STACK = (STACK + 1) & 0x0FF;
        byte val = getMemory().read(0x0100 + STACK, TYPE.READ_DATA, true, false);
        //System.out.println("<-- POP "+Integer.toString(0x0FF & val, 16));
        return val;
    }

    private byte getStatus() {
        return (byte) ((N ? 0x080 : 0)
                | (V ? 0x040 : 0)
                | 0x020
                | (B ? 0x010 : 0)
                | (D ? 0x08 : 0)
                | (I ? 0x04 : 0)
                | (Z ? 0x02 : 0)
                | ((C > 0) ? 0x01 : 0));
    }

    private void setStatus(byte b) {
        N = (b & 0x080) != 0;
        V = (b & 0x040) != 0;
        // B flag is unaffected in this way.
        D = (b & 0x08) != 0;
        I = (b & 0x04) != 0;
        Z = (b & 0x02) != 0;
        C = (char) (b & 0x01);
    }

    private void returnFromInterrupt() {
        setStatus(pop());
        setProgramCounter(popWord());
    }

    private void waitForInterrupt() {
        I = true;
        suspend();
    }

    public void BRK() {
        if (isLogEnabled()) {
            System.out.println("BRK at $" + Integer.toString(getProgramCounter(), 16));
            dumpTrace();
        }
        B = true;
        // 65c02 clears D flag on BRK
        D = false;
        interruptSignalled = true;
    }

    // Hardware IRQ generated
    @Override
    public void generateInterrupt() {
        B = false;
        interruptSignalled = true;
        resume();
    }

    private void processInterrupt() {
        if (!interruptSignalled) {
            return;
        }
        interruptSignalled = false;
        if (!I || B) {
            I = false;
            pushWord(getProgramCounter());
            push(getStatus());
            I = true;
            int newPC = getMemory().readWord(INT_VECTOR, TYPE.READ_DATA, true, false);
//            System.out.println("Interrupt generated, setting PC to (" + Integer.toString(INT_VECTOR, 16) + ") = " + Integer.toString(newPC, 16));
            setProgramCounter(newPC);
        }
    }

    public int getSTACK() {
        return STACK;
    }

    // Cold/Warm boot procedure
    @Override
    public void reset() {
        boolean restart = computer.pause();
        pushWord(getProgramCounter());
        push(getStatus());
        //        STACK = 0x0ff;
//        B = false;
        B = true;
//        C = 1;
        D = false;
//        I = true;
//        N = true;
//        V = true;
//        Z = true;
        int newPC = getMemory().readWord(RESET_VECTOR, TYPE.READ_DATA, true, false);
        System.out.println("Reset called, setting PC to (" + Integer.toString(RESET_VECTOR, 16) + ") = " + Integer.toString(newPC, 16));
        setProgramCounter(newPC);
        if (restart) {
            computer.resume();
        }
    }

    @Override
    protected String getDeviceName() {
        return "65C02 Processor";
    }

    private static String byte2(int b) {
        String out = Integer.toString(b & 0x0FF, 16);
        if (out.length() == 1) {
            return "0" + out;
        }
        return out;
    }

    private static String wordString(int w) {
        String out = Integer.toHexString(w);
        if (out.length() == 1) {
            return "000" + out;
        }
        if (out.length() == 2) {
            return "00" + out;
        }
        if (out.length() == 3) {
            return "0" + out;
        }
        return out;
    }

    public String getState() {
        StringBuilder out = new StringBuilder();
        out.append(byte2(A)).append(" ");
        out.append(byte2(X)).append(" ");
        out.append(byte2(Y)).append(" ");
        //        out += "PC:"+wordString(getProgramCounter())+" ";
        out.append("01").append(byte2(STACK)).append(" ");
        out.append(getFlags());
        return out.toString();
    }

    public String getFlags() {
        StringBuilder out = new StringBuilder();
        out.append(N ? "N" : ".");
        out.append(V ? "V" : ".");
        out.append("R");
        out.append(B ? "B" : ".");
        out.append(D ? "D" : ".");
        out.append(I ? "I" : ".");
        out.append(Z ? "Z" : ".");
        out.append((C != 0) ? "C" : ".");
        return out.toString();
    }

    public String disassemble() {
        int pc = getProgramCounter();
//        RAM ram = cpu.getMemory();
        int op = getMemory().readRaw(pc);
        OPCODE o = opcodes[op & 0x0ff];
        if (o == null) {
            return "???";
        }
        String format = o.getMode().formatMode(pc, this);
//        format = format.replaceAll("~1", byte2(b1));
//        format = format.replaceAll("~2", byte2(b2));
//        format = format.replaceAll("R", R);
        /*
         String mem = wordString(pc) + ":" + byte2(op) + " " +
         ((o.getMode().getSize() > 1) ?
         byte2(b1) : "  " ) + " " +
         ((o.getMode().getSize() > 2) ?
         byte2(b2) : "  " ) + "  ";
         */
        StringBuilder out = new StringBuilder(o.getCommand().toString());
        out.append(" ").append(format);
        return out.toString();
    }
    private boolean pageBoundaryPenalty = false;

    private void setPageBoundaryPenalty(boolean b) {
        pageBoundaryPenalty = b;
    }

    @Override
    public void pushPC() {
        pushWord(getProgramCounter() - 1);
    }
}
