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

import jace.apple2e.SoftSwitches;
import jace.apple2e.softswitch.MemorySoftSwitch;
import jace.config.ConfigurableField;
import jace.config.InvokableAction;
import jace.core.Computer;
import jace.core.Device;
import jace.core.Keyboard;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.state.Stateful;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple implementation of joystick support that supports mouse or keyboard.
 * Actual joystick support isn't offered by Java at this moment in time
 * unfortunately.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
public class Joystick extends Device {

    @ConfigurableField(name = "Enabled", shortName = "enabled", description = "If unchecked, then there is no joystick support.")
    public boolean enabled;
    @ConfigurableField(name = "Center Mouse", description = "Moves mouse back to the center of the screen, can get annoying.")
    public boolean centerMouse;
    @ConfigurableField(name = "Use keyboard", shortName = "useKeys", description = "Arrow keys will control joystick instead of the mouse.")
    public boolean useKeyboard;
    @ConfigurableField(name = "Hog keypresses", shortName = "hog", description = "Key presses will not be sent to emulator.")
    public boolean hogKeyboard;
    public int port;
    @Stateful
    public int x = 0;
    @Stateful
    public int y = 0;
    private int joyX = 0;
    private int joyY = 0;
    MemorySoftSwitch xSwitch;
    MemorySoftSwitch ySwitch;
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Point lastMouseLocation;
    Robot robot;
    Point centerPoint;

    public Joystick(int port, Computer computer) {
        super(computer);
        centerPoint = new Point(screenSize.width / 2, screenSize.height / 2);
        this.port = port;
        if (port == 0) {
            xSwitch = (MemorySoftSwitch) SoftSwitches.PDL0.getSwitch();
            ySwitch = (MemorySoftSwitch) SoftSwitches.PDL1.getSwitch();
        } else {
            xSwitch = (MemorySoftSwitch) SoftSwitches.PDL2.getSwitch();
            ySwitch = (MemorySoftSwitch) SoftSwitches.PDL3.getSwitch();
        }
        lastMouseLocation = MouseInfo.getPointerInfo().getLocation();
        try {
            robot = new Robot();
        } catch (AWTException ex) {
            Logger.getLogger(Joystick.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    public boolean leftPressed = false;
    public boolean rightPressed = false;
    public boolean upPressed = false;
    public boolean downPressed = false;

    private void readJoystick() {
        if (useKeyboard) {
            joyX = leftPressed ? (rightPressed ? 128 : 0) : (rightPressed ? 255 : 128);
            joyY = upPressed ? (downPressed ? 128 : 0) : (downPressed ? 255 : 128);
        } else {
            Point l = MouseInfo.getPointerInfo().getLocation();
            if (l.x < lastMouseLocation.x) {
                joyX = 0;
            } else if (l.x > lastMouseLocation.x) {
                joyX = 255;
            } else {
                joyX = 128;
            }

            if (l.y < lastMouseLocation.y) {
                joyY = 0;
            } else if (l.y > lastMouseLocation.y) {
                joyY = 255;
            } else {
                joyY = 128;
            }

            if (centerMouse) {
                lastMouseLocation = centerPoint;
                robot.mouseMove(centerPoint.x, centerPoint.y);
            } else {
                if (l.x <= 20) {
                    robot.mouseMove(20, l.y);
                    l = MouseInfo.getPointerInfo().getLocation();
                }
                if ((l.x + 21) == screenSize.getWidth()) {
                    robot.mouseMove((int) (screenSize.getWidth() - 20), l.y);
                    l = MouseInfo.getPointerInfo().getLocation();
                }
                if (l.y <= 20) {
                    robot.mouseMove(l.x, 20);
                    l = MouseInfo.getPointerInfo().getLocation();
                }
                if ((l.y + 21) == screenSize.getHeight()) {
                    robot.mouseMove(l.x, (int) (screenSize.getHeight() - 20));
                    l = MouseInfo.getPointerInfo().getLocation();
                }

                lastMouseLocation = l;
            }
        }
    }

    @Override
    protected String getDeviceName() {
        return "Joystick (port " + port + ")";
    }

    @Override
    public String getShortName() {
        return "joy" + port;
    }

    @Override
    public void tick() {
        boolean finished = true;
        if (x > 0) {
            if (--x == 0) {
                xSwitch.setState(false);
            } else {
                finished = false;
            }
        }
        if (y > 0) {
            if (--y == 0) {
                ySwitch.setState(false);
            } else {
                finished = false;
            }
        }
        if (finished) {
            setRun(false);
        }
    }

    @Override
    public void attach() {
        registerListeners();
    }

    @Override
    public void detach() {
        removeListeners();
    }

    @Override
    public void reconfigure() {
        x = 0;
        y = 0;
        if (enabled) {
            registerListeners();
        } else {
            removeListeners();
        }
    }
    RAMListener listener = new RAMListener(RAMEvent.TYPE.ANY, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
        @Override
        protected void doConfig() {
            setScopeStart(0x0C070);
            setScopeEnd(0x0C07f);
        }

        @Override
        protected void doEvent(RAMEvent e) {
            setRun(true);
            readJoystick();
//            if (x <= 0) {
            xSwitch.setState(true);
            x = 10 + joyX * 11;
//            }
//            if (y <= 0) {
            ySwitch.setState(true);
            y = 10 + joyY * 11;
//            }
        }
    };

    @InvokableAction(name="Left", category = "joystick", defaultKeyMapping = "left", notifyOnRelease = true)
    public boolean joystickLeft(boolean pressed) {
        leftPressed = pressed;
        return hogKeyboard;
    };
    @InvokableAction(name="Right", category = "joystick", defaultKeyMapping = "right", notifyOnRelease = true)
    public boolean joystickRight(boolean pressed) {
        rightPressed = pressed;
        return hogKeyboard;
    };
    @InvokableAction(name="Up", category = "joystick", defaultKeyMapping = "up", notifyOnRelease = true)
    public boolean joystickUp(boolean pressed) {
        upPressed = pressed;
        return hogKeyboard;
    };
    @InvokableAction(name="Down", category = "joystick", defaultKeyMapping = "down", notifyOnRelease = true)
    public boolean joystickDown(boolean pressed) {
        leftPressed = pressed;
        return hogKeyboard;
    };
    
    private void registerListeners() {
        computer.getMemory().addListener(listener);
    }
    
    private void removeListeners() {
        computer.getMemory().removeListener(listener);
        Keyboard.unregisterAllHandlers(this);
    }
}