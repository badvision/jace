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

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.Computer;
import jace.core.PagedMemory;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.state.Stateful;
import jace.core.Utility;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javafx.scene.control.Label;

/**
 * Apple Mouse interface implementation. This is fully compatible with several
 * applications.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
@Name("Apple Mouse")
public class CardAppleMouse extends Card implements MouseListener {

    @Stateful
    public int mode;
    @Stateful
    public boolean active;
    @Stateful
    public boolean interruptOnMove;
    @Stateful
    public boolean interruptOnPress;
    @Stateful
    public boolean interruptOnVBL;
    @Stateful
    public boolean button0press;
    @Stateful
    public boolean button1press;
    @Stateful
    public boolean button0pressLast;
    @Stateful
    public boolean button1pressLast;
    @Stateful
    public boolean isInterrupt;
    @Stateful
    public boolean isVBL;
    @Stateful
    public int statusByte;
    @Stateful
    public Point lastMouseLocation;
    @Stateful
    public Point clampMin = new Point(0, 0x03ff);
    @Stateful
    public Point clampMax = new Point(0, 0x03ff);
    // By default, update 60 times a second -- roughly every VBL period (in theory)
    @ConfigurableField(name = "Update frequency", shortName = "updateFreq", category = "Mouse", description = "# of CPU cycles between updates; affects polling and interrupt-based routines")
    public static int CYCLES_PER_UPDATE = (int) (1020484L / 60L);
    @ConfigurableField(name = "Fullscreen fix", shortName = "fsfix", category = "Mouse", description = "If the mouse pointer is a little off when in fullscreen, this should fix it.")
    public boolean fullscreenFix = true;
    Label mouseActive = Utility.loadIconLabel("input-mouse.png");

    public CardAppleMouse(Computer computer) {
        super(computer);
    }

    @Override
    public String getDeviceName() {
        return "Apple Mouse";
    }

    @Override
    public void reset() {
        mode = 0;
        deactivateMouse();
    }

    /*
     * Coded against this information
     * http://stason.org/TULARC/pc/apple2/programmer/012-How-do-I-write-programs-which-use-the-mouse.html
     */
    @Override
    protected void handleFirmwareAccess(int offset, TYPE type, int value, RAMEvent e) {
        /*
         * Screen holes
         * $0478 + slot Low byte of absolute X position 
         * $04F8 + slot Low byte of absolute Y position 
         * $0578 + slot High byte of absolute X position 
         * $05F8 + slot High byte of absolute Y position 
         * $0678 + slot Reserved and used by the firmware 
         * $06F8 + slot Reserved and used by the firmware 
         * $0778 + slot Button 0/1 interrupt status byte 
         * $07F8 + slot Mode byte
         * 
         * Interrupt status byte:
         * Set by READMOUSE
         * Bit 7 6 5 4 3 2 1 0
         *     | | | | | | | |
         *     | | | | | | | `--- Previously, button 1 was up (0) or down (1)
         *     | | | | | | `----- Movement interrupt
         *     | | | | | `------- Button 0/1 interrupt
         *     | | | | `--------- VBL interrupt
         *     | | | `----------- Currently, button 1 is up (0) or down (1)
         *     | | `------------- X/Y moved since last READMOUSE
         *     | `--------------- Previously, button 0 was up (0) or down (1)
         *     `----------------- Currently, button 0 is up (0) or down (1)
         * 
         * Mode byte
         * Valid after calling SERVEMOUSE, cleared with READMOUSE
         * Bit 7 6 5 4 3 2 1 0 
         *     | | | | | | | |
         *     | | | | | | | `--- Mouse off (0) or on (1)
         *     | | | | | | `----- Interrupt if mouse is moved
         *     | | | | | `------- Interrupt if button is pressed
         *     | | | | `--------- Interrupt on VBL
         *     | | | `----------- Reserved
         *     | | `------------- Reserved
         *     | `--------------- Reserved
         *     `----------------- Reserved
         */
        if (type == RAMEvent.TYPE.EXECUTE) {
            // This means the CPU is calling firmware at this location
            switch (offset - 0x080) {
                case 0:
                    setMouse();
                    break;
                case 1:
                    serveMouse();
                    break;
                case 2:
                    readMouse();
                    break;
                case 3:
                    clearMouse();
                    break;
                case 4:
                    posMouse();
                    break;
                case 5:
                    clampMouse();
                    break;
                case 6:
                    homeMouse();
                    break;
                case 7:
                    initMouse();
                    break;
            }
            // Always pass back RTS
            e.setNewValue(0x060);
        } else if (type.isRead()) {
            /* Identification bytes
             * $Cn05 = $38 $Cn07 = $18 $Cn0B = $01 $Cn0C = $20 $CnFB = $D6
             */
            switch (offset) {
                case 0x05:
                    e.setNewValue(0x038);
                    break;
                case 0x07:
                    e.setNewValue(0x018);
                    break;
                case 0x0B:
                    e.setNewValue(0x01);
                    break;
                case 0x0C:
                    e.setNewValue(0x020);
                    break;
                case 0x0FB:
                    e.setNewValue(0x0D6);
                    break;
                // As per the //gs firmware reference manual
                case 0x08:
                    // Pascal signature byte
                    e.setNewValue(0x001);
                case 0x011:
                    e.setNewValue(0x000);
                    break;
                // Function call offsets
                case 0x12:
                    e.setNewValue(0x080);
                    break;
                case 0x13:
                    e.setNewValue(0x081);
                    break;
                case 0x14:
                    e.setNewValue(0x082);
                    break;
                case 0x15:
                    e.setNewValue(0x083);
                    break;
                case 0x16:
                    e.setNewValue(0x084);
                    break;
                case 0x17:
                    e.setNewValue(0x085);
                    break;
                case 0x18:
                    e.setNewValue(0x086);
                    break;
                case 0x19:
                    e.setNewValue(0x087);
                    break;
                default:
                    e.setNewValue(0x069);
            }
//                        System.out.println("Read mouse firmware at "+Integer.toHexString(e.getAddress())+" == "+Integer.toHexString(e.getNewValue()));
        }
    }

    private MOS65C02 getCPU() {
        return (MOS65C02) computer.getCpu();
    }

    /*
     * $Cn12 SETMOUSE Sets mouse mode 
     *      A = mouse operation mode (0-f)
     *      C = 1 if illegal mode requested
     *      mode byte updated    
     */
    private void setMouse() {
        mode = getCPU().A & 0x0ff;
        if (mode > 0x0f) {
            getCPU().C = 1;
            return;
        } else {
            getCPU().C = 0;
        }
        //Mouse off (0) or on (1)
        if ((mode & 1) == 0) {
            deactivateMouse();
            return;
        }
        //Interrupt if mouse is moved
        interruptOnMove = ((mode & 2) != 0);
        //Interrupt if button is pressed
        interruptOnPress = ((mode & 4) != 0);
        //Interrupt on VBL
        interruptOnVBL = ((mode & 8) != 0);
        activateMouse();
    }

    /*
     * $Cn13 SERVEMOUSE Services mouse interrupt 
     *      Test for interupt and clear mouse interrupt line
     *      Return C=0 if mouse interrupt occurred
     *      Updates screen hole interrupt status bits
     */
    private void serveMouse() {
        // If any interrupts are registered then 
        updateMouseState();
        if (isInterrupt) {
            getCPU().C = 0;
        } else {
            getCPU().C = 1;
//            System.out.println("MOUSE TRIGGERED INTERRUPT!");
        }
//        isInterrupt = false;
//        isVBL=false;
    }

    /*
     * $Cn14 READMOUSE Reads mouse position 
     *      Reads delta (X/Y) positions, updates abolute X/Y pos
     *      and reads button statuses
     *      Always returns C=0
     *      Interrupt status bits cleared
     *      Screen hole positions for button/movement status bits updated
     */
    private void readMouse() {
        updateMouseState();
        isInterrupt = false;
        isVBL = false;
        // set screen holes
        getCPU().C = 0;
    }

    /*
     * $Cn16 POSMOUSE Sets mouse position to a user-defined pos 
     *      Caller puts new position in screenhole
     *      Always returns C=0
     */
    private void posMouse() {
        // Ignore?
        getCPU().C = 0;
    }

    /*
     * $Cn17 CLAMPMOUSE Sets mouse bounds in a window 
     *      Sets up clamping window for mouse user
     *      Power up defaults are 0 - 1023 (0 - 3ff)
     *      Caller sets: 
     *      A = 0 if setting X, 1 if setting Y
     *      $0478 = low byte of low clamp. 
     *      $04F8 = low byte of high clamp. 
     *      $0578 = high byte of low clamp. 
     *      $05F8 = high byte of high clamp.
     *      //gs homes mouse to low address, but //c and //e do not
     */
    private void clampMouse() {
        RAM128k memory = (RAM128k) computer.memory;
        byte clampMinLo = memory.getMainMemory().readByte(0x0478);
        byte clampMaxLo = memory.getMainMemory().readByte(0x04F8);
        byte clampMinHi = memory.getMainMemory().readByte(0x0578);
        byte clampMaxHi = memory.getMainMemory().readByte(0x05F8);
        int min = (clampMinLo & 0x0ff) | ((clampMinHi << 8) & 0x0FF00);
        int max = (clampMaxLo & 0x0ff) | ((clampMaxHi << 8) & 0x0FF00);
        if (getCPU().A == 0) {
            setClampWindowX(min, max);
        } else if (getCPU().A == 1) {
            setClampWindowY(min, max);
        }
//        System.out.println("Set mouse clamping to:" + clampMin.toString() + ";" + clampMax.toString());
    }

    /*
     * $Cn19 INITMOUSE Resets mouse clamps to default values; sets mouse position to 0,0
     *      Sets screen holes to default values and sets clamping
     *      window to default value (000 - 3ff) for both X and Y
     *      Exit:C=0
     *      Screen holes are updated
     */
    private void initMouse() {
        mouseActive.setText("Active");
//        Emulator.getFrame().addIndicator(this, mouseActive, 2000);
        setClampWindowX(0, 0x3ff);
        setClampWindowY(0, 0x3ff);
        clearMouse();
    }

    /*
     * $Cn15 CLEARMOUSE Clears mouse position to 0 (for delta mode)
     *      Resets buttons, movement and interrupt status bits to 0
     *      Intended to be used for delta mouse positioning instead of absolute positioning
     *      Always returns C=0
     *      Interrupt status bits cleared
     *      Screen hole positions for button/movement status bits updated
     */
    private void clearMouse() {
        isVBL = false;
        isInterrupt = false;
        button0press = false;
        button1press = false;
        button0pressLast = false;
        button1pressLast = false;
        homeMouse();
    }

    /*
     * $Cn18 HOMEMOUSE Sets absolute position to upper-left corner of clamping window
     *      Exit: c=0
     *      Screen hole positions are updated
     */
    private void homeMouse() {
        lastMouseLocation = new Point(0, 0);
        updateMouseState();
        getCPU().C = 0;
    }

    /*
     * This is called whenever the mouse firmware has been activated in software
     */
    private void activateMouse() {
        active = true;
        Component drawingArea = Emulator.getScreen();
        if (drawingArea != null) {
            drawingArea.addMouseListener(this);
        }
    }

    /*
     * This is called whenever there is a hard reset or when the mouse is turned off
     */
    private void deactivateMouse() {
        active = false;
        mode = 0;
        interruptOnMove = false;
        interruptOnPress = false;
        interruptOnVBL = false;
        Component drawingArea = Emulator.getScreen();
        if (drawingArea != null) {
            drawingArea.removeMouseListener(this);
        }
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        // No IO access necessary (is there?)
    }
    private int delay = CYCLES_PER_UPDATE;

    @Override
    public void tick() {
        if (!active) {
            return;
        }
        delay--;
        if (delay > 0) {
            return;
        }
        delay = CYCLES_PER_UPDATE;

        // If interrupts not used, just move on
        if (!interruptOnMove && !interruptOnPress) {
            return;
        }

        if (interruptOnPress) {
            if (button0press != button0pressLast || button1press != button1pressLast) {
                isInterrupt = true;
                getCPU().generateInterrupt();
                return;
            }
        }
        if (interruptOnMove) {
            Point currentMouseLocation = MouseInfo.getPointerInfo().getLocation();
            if (!currentMouseLocation.equals(lastMouseLocation)) {
                isInterrupt = true;
                getCPU().generateInterrupt();
            }
        }
    }

    @Override
    public void notifyVBLStateChanged(boolean state) {
        // VBL is false when it is the vertical blanking period
        if (!state && interruptOnVBL && active) {
            isVBL = true;
            isInterrupt = true;
            getCPU().generateInterrupt();
        }
    }

    private void updateMouseState() {
        Component drawingArea = Emulator.getScreen();
        if (drawingArea == null) {
            return;
        }
//        Graphics2D screen = (Graphics2D) computer.getVideo().getScreen();
//        Point currentMouseLocation = MouseInfo.getPointerInfo().getLocation();
//        Point topLeft = drawingArea.getLocationOnScreen();
        Point currentMouseLocation = null; //        = Emulator.getFrame().getContentPane().getMousePosition();
//        if (currentMouseLocation == null) return;
//        Point topLeft = drawingArea.getLocationOnScreen();
        Point topLeft = new Point(0,0);

        Dimension d = drawingArea.getBounds().getSize();
//        if (screen.getTransform() != null) {
//            d = new Dimension((int) (screen.getTransform().getScaleX() * d.width),
//                    (int) (screen.getTransform().getScaleY() * d.height));
//            topLeft.x += screen.getTransform().getTranslateX();
//            topLeft.y += screen.getTransform().getTranslateY();
//        }
        if (fullscreenFix) {
//            if (Emulator.getFrame().isFullscreenActive()) {
//                Toolkit t = Toolkit.getDefaultToolkit();
//                topLeft.y -= t.getScreenInsets(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()).top;
//            }
        }

        // Scale X and Y to the clamping range of the mouse (will this work for most software?)
        double width = clampMax.x - clampMin.x;
        double x = 0;
//        double x = currentMouseLocation.getX() - topLeft.x;
        x *= width;
        x /= d.width;
        x += clampMin.x;
        if (x < clampMin.x) {
            x = clampMin.x;
        }
        if (x > clampMax.x) {
            x = clampMax.x;
        }

        double height = clampMax.y - clampMin.y;
        double y = 0;
//        double y = currentMouseLocation.getY() - topLeft.y;
        y *= height;
        y /= d.height;
        y += clampMin.y;
        if (y < clampMin.y) {
            y = clampMin.y;
        }
        if (y > clampMax.y) {
            y = clampMax.y;
        }

        PagedMemory m = ((RAM128k) computer.getMemory()).getMainMemory();
        int s = getSlot();
        /*
         * $0478 + slot Low byte of absolute X position 
         * $04F8 + slot Low byte of absolute Y position 
         */
        m.writeByte(0x0478 + s, (byte) ((int) x & 0x0ff));
        m.writeByte(0x04F8 + s, (byte) ((int) y & 0x0ff));
        /*
         * $0578 + slot High byte of absolute X position 
         * $05F8 + slot High byte of absolute Y position 
         */
        m.writeByte(0x0578 + s, (byte) (((int) x & 0x0ff00) >> 8));
        m.writeByte(0x05F8 + s, (byte) (((int) y & 0x0ff00) >> 8));
        /*
         * $0678 + slot Reserved and used by the firmware 
         * $06F8 + slot Reserved and used by the firmware 
         *
         * Interrupt status byte:
         * Set by READMOUSE
         * Bit 7 6 5 4 3 2 1 0
         *     | | | | | | | |
         *     | | | | | | | `--- Previously, button 1 was up (0) or down (1)
         *     | | | | | | `----- Movement interrupt
         *     | | | | | `------- Button 0/1 interrupt
         *     | | | | `--------- VBL interrupt
         *     | | | `----------- Currently, button 1 is up (0) or down (1)
         *     | | `------------- X/Y moved since last READMOUSE
         *     | `--------------- Previously, button 0 was up (0) or down (1)
         *     `----------------- Currently, button 0 is up (0) or down (1)        
         */
        int status = 0;
        boolean mouseMoved = !currentMouseLocation.equals(lastMouseLocation);
        if (button1pressLast) {
            status |= 1;
        }
        if (interruptOnMove && mouseMoved) {
            status |= 2;
        }
        if (interruptOnPress && (button0press != button0pressLast || button1press != button1pressLast)) {
            status |= 4;
        }
        if (isVBL) {
            status |= 8;
        }
        if (button1press) {
            status |= 16;
        }
        if (mouseMoved) {
            status |= 32;
        }
        if (button0pressLast) {
            status |= 64;
        }
        if (button0press) {
            status |= 128;
        }
        /*
         * $0778 + slot Button 0/1 interrupt status byte 
         */
        m.writeByte(0x0778 + s, (byte) (status));

        /*
         * $07F8 + slot Mode byte
         */
        m.writeByte(0x07F8 + s, (byte) (mode));

        lastMouseLocation = currentMouseLocation;
        button0pressLast = button0press;
        button1pressLast = button1press;
    }

    @Override
    public void mousePressed(MouseEvent me) {
        int button = me.getButton();
        if (button == 1 || button == 2) {
            button0press = true;
        }
        if (button == 2 || button == 3) {
            button1press = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent me) {
        int button = me.getButton();
        if (button == 1 || button == 2) {
            button0press = false;
        }
        if (button == 2 || button == 3) {
            button1press = false;
        }
    }

    @Override
    public void mouseClicked(MouseEvent me) {
    }

    @Override
    public void mouseEntered(MouseEvent me) {
    }

    @Override
    public void mouseExited(MouseEvent me) {
    }

    private void setClampWindowX(int min, int max) {
        // Fix for GEOS clamping funkiness
        if (max == 32767) {
            max = 560;
        }
        clampMin.x = min;
        clampMax.x = max;
    }

    private void setClampWindowY(int min, int max) {
        // Fix for GEOS clamping funkiness
        if (max == 32767) {
            max = 192;
        }
        clampMin.y = min;
        clampMax.y = max;
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // Do nothing, there is no need to emulate c8 rom
    }
}
