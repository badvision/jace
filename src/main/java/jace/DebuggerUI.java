package jace;

import jace.apple2e.MOS65C02;
import jace.core.Debugger;


public class DebuggerUI {
    
    static Debugger debugger;

        public static void updateCPURegisters(MOS65C02 cpu) {
//        DebuggerPanel debuggerPanel = Emulator.getFrame().getDebuggerPanel();
//        debuggerPanel.valueA.setText(Integer.toHexString(cpu.A));
//        debuggerPanel.valueX.setText(Integer.toHexString(cpu.X));
//        debuggerPanel.valueY.setText(Integer.toHexString(cpu.Y));
//        debuggerPanel.valuePC.setText(Integer.toHexString(cpu.getProgramCounter()));
//        debuggerPanel.valueSP.setText(Integer.toHexString(cpu.getSTACK()));
//        debuggerPanel.valuePC2.setText(cpu.getFlags());
//        debuggerPanel.valueINST.setText(cpu.disassemble());
    }

    public static void enableDebug(boolean b) {
        //        DebuggerPanel debuggerPanel = Emulator.getFrame().getDebuggerPanel();
        //        debugger.setActive(b);
        //        debuggerPanel.enableDebug.setSelected(b);
        //        debuggerPanel.setBackground(
        //                b ? Color.RED : new Color(0, 0, 0x040));
            }
            public static void enableTrace(boolean b) {
                Emulator.withComputer(c->c.getCpu().setTraceEnabled(b));
            }
            public static void stepForward() {
                debugger.step = true;
            }                
}