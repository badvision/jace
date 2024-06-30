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

import jace.Emulator;
import jace.apple2e.softswitch.IntC8SoftSwitch;
import jace.apple2e.softswitch.KeyboardSoftSwitch;
import jace.apple2e.softswitch.Memory2SoftSwitch;
import jace.apple2e.softswitch.MemorySoftSwitch;
import jace.apple2e.softswitch.VideoSoftSwitch;
import jace.core.RAMEvent;
import jace.core.SoftSwitch;
import jace.core.Video;

/**
 * Softswitches reside in the addresses C000-C07f and control everything from
 * memory management to speaker sound and keyboard. Other I/O ports (c080-C0ff)
 * are managed by any registered Cards. This enumeration serves as a convenient
 * way to represent the different softswitches as well as provide a clean
 * enumeration.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public enum SoftSwitches {

    _80STORE(new MemorySoftSwitch("80Store", 0x0c000, 0x0c001, 0x0c018, RAMEvent.TYPE.WRITE, false)),
    RAMRD(new MemorySoftSwitch("AuxRead (RAMRD)", 0x0c002, 0x0c003, 0x0c013, RAMEvent.TYPE.WRITE, false)),
    RAMWRT(new MemorySoftSwitch("AuxWrite (RAMWRT)", 0x0c004, 0x0c005, 0x0c014, RAMEvent.TYPE.WRITE, false)),
    CXROM(new MemorySoftSwitch("IntCXROM", 0x0c006, 0x0c007, 0x0c015, RAMEvent.TYPE.WRITE, false)),
    AUXZP(new MemorySoftSwitch("AuxZeroPage", 0x0c008, 0x0c009, 0x0c016, RAMEvent.TYPE.WRITE, false)),
    SLOTC3ROM(new MemorySoftSwitch("C3ROM", 0x0c00a, 0x0c00b, 0x0c017, RAMEvent.TYPE.WRITE, false)),
    INTC8ROM(new IntC8SoftSwitch()),
    LCBANK1(new MemorySoftSwitch("LangCardBank1",
    new int[]{0x0c088, 0x0c089, 0x0c08a, 0x0c08b, 0x0c08c, 0x0c08d, 0x0c08e, 0x0c08f},
    new int[]{0x0c080, 0x0c081, 0x0c082, 0x0c083, 0x0c084, 0x0c085, 0x0c086, 0x0c087},
    new int[]{0x0c011}, RAMEvent.TYPE.ANY, false)),
    LCRAM(new MemorySoftSwitch("LangCardRam/HRAMRD'",
    new int[]{0x0c081, 0x0c082, 0x0c085, 0x0c086, 0x0c089, 0x0c08a, 0x0c08d, 0x0c08e},
    new int[]{0x0c080, 0x0c083, 0x0c084, 0x0c087, 0x0c088, 0x0c08b, 0x0c08c, 0x0c08f},
    new int[]{0x0c012}, RAMEvent.TYPE.ANY, false)),
    LCWRITE(new Memory2SoftSwitch("LangCardWrite",
    new int[]{0x0c080, 0x0c082, 0x0c084, 0x0c086, 0x0c088, 0x0c08a, 0x0c08c, 0x0c08e},
    new int[]{0x0c081, 0x0c083, 0x0c085, 0x0c087, 0x0c089, 0x0c08b, 0x0c08d, 0x0c08f},
    null, RAMEvent.TYPE.ANY, true)),
    //Renamed as per Sather 5-7
    _80COL(new VideoSoftSwitch("80ColumnVideo (80COL/80VID)", 0x0c00c, 0x0c00d, 0x0c01f, RAMEvent.TYPE.WRITE, false)),
    ALTCH(new VideoSoftSwitch("Mousetext", 0x0c00e, 0x0c00f, 0x0c01e, RAMEvent.TYPE.WRITE, false){
        @Override
        public void stateChanged() {
            super.stateChanged();
            Video.forceRefresh();
        }
    }),
    TEXT(new VideoSoftSwitch("Text", 0x0c050, 0x0c051, 0x0c01a, RAMEvent.TYPE.ANY, true)),
    MIXED(new VideoSoftSwitch("Mixed", 0x0c052, 0x0c053, 0x0c01b, RAMEvent.TYPE.ANY, false)),
    PAGE2(new VideoSoftSwitch("Page2", 0x0c054, 0x0c055, 0x0c01c, RAMEvent.TYPE.ANY, false) {
        @Override
        public void stateChanged() {
            // PAGE2 is a hybrid switch; 80STORE ? memory : video
            if (_80STORE.isOn()) {
                Emulator.withMemory(m->m.configureActiveMemory());
            } else {
                Emulator.withVideo(v->v.configureVideoMode());
            }
        }
    }),
    HIRES(new VideoSoftSwitch("Hires", 0x0c056, 0x0c057, 0x0c01d, RAMEvent.TYPE.ANY, false)),
    DHIRES(new VideoSoftSwitch("Double-hires", 0x0c05f, 0x0c05e, 0x0c07f, RAMEvent.TYPE.ANY, false)),
    PB0(new MemorySoftSwitch("Pushbutton0", -1, -1, 0x0c061, RAMEvent.TYPE.ANY, null)),
    PB1(new MemorySoftSwitch("Pushbutton1", -1, -1, 0x0c062, RAMEvent.TYPE.ANY, null)),
    PB2(new MemorySoftSwitch("Pushbutton2", -1, -1, 0x0c063, RAMEvent.TYPE.ANY, null)),
    PDLTRIG(new SoftSwitch("PaddleTrigger",
    null,
    new int[]{0x0c070, 0x0c071, 0x0c072, 0x0c073, 0x0c074, 0x0c075, 0x0c076, 0x0c077,
        0x0c078, 0x0c079, 0x0c07a, 0x0c07b, 0x0c07c, 0x0c07d, 0x0c07e, 0x0c07f},
    null, RAMEvent.TYPE.ANY, false) {
        @Override
        protected byte readSwitch() {
            setState(true);
            return Emulator.withComputer(c->c.getVideo().getFloatingBus(), (byte) 0);
        }

        @Override
        public void stateChanged() {
        }
    }),
    PDL0(new MemorySoftSwitch("Paddle0", -1, -1, 0x0c064, RAMEvent.TYPE.ANY, false)),
    PDL1(new MemorySoftSwitch("Paddle1", -1, -1, 0x0c065, RAMEvent.TYPE.ANY, false)),
    PDL2(new MemorySoftSwitch("Paddle2", -1, -1, 0x0c066, RAMEvent.TYPE.ANY, false)),
    PDL3(new MemorySoftSwitch("Paddle3", -1, -1, 0x0c067, RAMEvent.TYPE.ANY, false)),
    AN0(new MemorySoftSwitch("Annunciator0", 0x0c058, 0x0c059, -1, RAMEvent.TYPE.ANY, false)),
    AN1(new MemorySoftSwitch("Annunciator1", 0x0c05a, 0x0c05b, -1, RAMEvent.TYPE.ANY, false)),
    AN2(new MemorySoftSwitch("Annunciator2", 0x0c05c, 0x0c05d, -1, RAMEvent.TYPE.ANY, false)),
    AN3(new MemorySoftSwitch("Annunciator3", 0x0c05e, 0x0c05f, -1, RAMEvent.TYPE.ANY, false)),
    KEYBOARD(new KeyboardSoftSwitch(
    "Keyboard",
    new int[]{0x0c010, 0x0c11, 0x0c012, 0x0c013, 0x0c014, 0x0c015, 0x0c016, 0x0c017,
        0x0c018, 0x0c019, 0x0c01a, 0x0c01b, 0x0c01c, 0x0c01d, 0x0c01e, 0x0c01f},
    null,
    new int[]{0x0c000, 0x0c001, 0x0c002, 0x0c003, 0x0c004, 0x0c005, 0x0c006, 0x0c007,
        0x0c008, 0x0c009, 0x0c00a, 0x0c00b, 0x0c00c, 0x0c00d, 0x0c00e, 0x0c00f, 0x0c010},
    RAMEvent.TYPE.WRITE, false)),
    //C010 should clear keyboard strobe when read as well
    KEYBOARD_STROBE_READ(new SoftSwitch("KeyStrobe_Read", 0x0c010, -1, -1, RAMEvent.TYPE.READ, false) {
        @Override
        protected byte readSwitch() {
            return Emulator.withComputer(c->c.getVideo().getFloatingBus(), (byte) 0);
        }

        @Override
        public void stateChanged() {
            KEYBOARD.getSwitch().setState(false);
        }
    }),
    TAPEOUT(new MemorySoftSwitch("TapeOut", 0x0c020, 0x0c020, 0x0c060, RAMEvent.TYPE.ANY, false)),
    VBL(new VideoSoftSwitch("VBL", -1, -1, 0x0c019, RAMEvent.TYPE.ANY, false)),
    FLOATING_BUS(new SoftSwitch("FloatingBus", null, null, new int[]{0x0C050, 0x0C051, 0x0C052, 0x0C053, 0x0C054}, RAMEvent.TYPE.READ, null) {
        @Override
        protected byte readSwitch() {
            return Emulator.withComputer(c->c.getVideo().getFloatingBus(), (byte) 0);
        }

        @Override
        public void stateChanged() {
        }
    });

    /*
     2C:VBL (new MemorySoftSwitch(0x0c070, 0x0*, 0x0c041, RAMEvent.TYPE.ANY, false)),
     2C:VBLENABLE (new MemorySoftSwitch(0x0c05a, 0x0c05b, 0x0-, RAMEvent.TYPE.ANY, false)),
     2C:XINT (new MemorySoftSwitch(0x0c015 (r), c048-c04f (r/w), 0x0-, 0x0-, RAMEvent.TYPE.ANY, false)),
     2C:YINT (new MemorySoftSwitch(0x0c017 (r), c048-c04f (r/w), 0x0-, 0x0-, RAMEvent.TYPE.ANY, false)),
     2C:MBUTTON (new MemorySoftSwitch(0x0*, 0x0*, 0x0c063, RAMEvent.TYPE.ANY, false)),
     2C:80/40 switch (new MemorySoftSwitch(0x0*, 0x0*, 0x0c060, RAMEvent.TYPE.ANY, false)),
     2C:XDirection (new MemorySoftSwitch(0x0*, 0x0*, 0x0c066, RAMEvent.TYPE.ANY, false)),
     2C:YDirection (new MemorySoftSwitch(0x0*, 0x0*, 0x0c067, RAMEvent.TYPE.ANY, false)),    
     */
    private final SoftSwitch softswitch;

    /**
     * Creates a new instance of SoftSwitches
     */
    SoftSwitches(SoftSwitch softswitch) {
        this.softswitch = softswitch;
    }

    public SoftSwitch getSwitch() {
        return softswitch;
    }

    public boolean getState() {
        return softswitch.getState();
    }

    public final boolean isOn() {
        return softswitch.getState();
    }

    public final boolean isOff() {
        return !softswitch.getState();
    }

    @Override
    public String toString() {
        return softswitch.toString();
    }
}