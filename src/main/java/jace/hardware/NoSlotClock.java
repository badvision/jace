package jace.hardware;

import jace.EmulatorUILogic;
import jace.apple2e.SoftSwitches;
import jace.config.ConfigurableField;
import jace.core.Computer;
import jace.core.Device;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.Utility;
import java.util.Calendar;
import java.util.Optional;
import javafx.scene.control.Label;

/**
 * Provide No-slot-clock compatibility
 *
 * @author blurry
 */
public class NoSlotClock extends Device {

    boolean clockActive;
    public long detectSequence = 0x5ca33ac55ca33ac5L;
    public long testSequence = 0;
    public long testMask = 0;
    public long dataRegister = 0;
    public long dataRegisterBit = 0;
    public int patternCount = 0;
    public boolean writeEnabled = false;
    @ConfigurableField(category = "Clock", name = "Patch Prodos", description = "If enabled, prodos clock routines will be patched directly", defaultValue = "false")
    public boolean patchProdosClock = false;
    Optional<Label> clockIcon;

    private final RAMListener listener = new RAMListener(RAMEvent.TYPE.ANY, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
        @Override
        protected void doConfig() {
            setScopeStart(0x0C100);
            setScopeEnd(0x0CFFF);
        }

        @Override
        public boolean isRelevant(RAMEvent e) {
            // Ref: Sather UAIIe 5-28
            if (SoftSwitches.CXROM.isOn()) {
                return true;
            }
            if ((e.getAddress() & 0x0ff00) == 0x0c300 && SoftSwitches.SLOTC3ROM.isOff()) {
                return true;
            }
            return e.getAddress() >= 0x0c800 && SoftSwitches.INTC8ROM.isOn();
        }

        @Override
        protected void doEvent(RAMEvent e) {
            boolean readMode = (e.getAddress() & 0x04) != 0;
            if (clockActive) {
                if (readMode) {
                    int val = e.getOldValue() & 0b011111110;
                    int bit = getNextDataBit();
                    val |= bit;
                    e.setNewValue(val);
                } else if (writeEnabled) {
                    fakeWrite(e);
                } else {
                    return;
                }
                dataRegisterBit++;
                if (dataRegisterBit >= 64) {
                    deactivateClock();
                }
            } else if (readMode) {
                writeEnabled = true;
                testSequence = detectSequence;
                patternCount = 0;
            } else if (writeEnabled) {
                int bit = e.getAddress() & 0x01;
                if (bit == (testSequence & 0x01)) {
                    testSequence >>= 1;
                    patternCount++;
                    if (patternCount == 64) {
                        activateClock();
                    }
                } else {
                    writeEnabled = false;
                }
            }
        }
    };

    public NoSlotClock(Computer computer) {
        super(computer);
        this.clockIcon = Utility.loadIconLabel("clock.png");
        this.clockIcon.ifPresent(icon -> icon.setText("No Slot Clock"));
    }

    @Override
    protected String getDeviceName() {
        return "No Slot Clock";
    }

    @Override
    public String getShortName() {
        return "clock";
    }

    @Override
    public void tick() {
    }

    @Override
    public void reconfigure() {
    }

    @Override
    public void attach() {
        computer.getMemory().addListener(listener);
    }

    @Override
    public void detach() {
        computer.getMemory().removeListener(listener);
    }

    public void activateClock() {
        Calendar now = Calendar.getInstance();
        dataRegisterBit = 0;
        dataRegister = 0L;
        storeBCD(now.get(Calendar.MILLISECOND) / 10, 0);
        storeBCD(now.get(Calendar.SECOND), 1);
        storeBCD(now.get(Calendar.MINUTE), 2);
        storeBCD(now.get(Calendar.HOUR), 3);
        storeBCD(now.get(Calendar.DAY_OF_WEEK), 4);
        storeBCD(now.get(Calendar.DAY_OF_MONTH), 5);
        storeBCD(now.get(Calendar.MONTH) + 1, 6);
        storeBCD(now.get(Calendar.YEAR) % 100, 7);
        clockActive = true;
        clockIcon.ifPresent(icon
                -> EmulatorUILogic.addIndicator(this, icon, 1000));
        if (patchProdosClock) {
            CardThunderclock.performProdosPatch(computer);
        }
    }

    public void storeBCD(int val, int offset) {
        storeNibble(val % 10, offset * 8);
        storeNibble(val / 10, offset * 8 + 4);
    }

    public void storeNibble(int val, int offset) {
        for (int i = 0; i < 4; i++) {
            if ((val & 1) != 0) {
                dataRegister |= (1L << (offset + i));
            }
            val >>= 1;
        }
    }

    public void deactivateClock() {
        clockActive = false;
    }

    public int getNextDataBit() {
        return (int) ((dataRegister >> dataRegisterBit) & 0x01);
    }

    public void fakeWrite(RAMEvent e) {
    }

}
