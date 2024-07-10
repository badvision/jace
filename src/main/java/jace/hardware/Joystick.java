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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import jace.Emulator;
import jace.JaceApplication;
import jace.apple2e.SoftSwitches;
import jace.apple2e.softswitch.MemorySoftSwitch;
import jace.config.ConfigurableField;
import jace.config.DynamicSelection;
import jace.config.InvokableAction;
import jace.core.Computer;
import jace.core.Device;
import jace.core.Keyboard;
import jace.core.RAMEvent;
import jace.core.RAMListener;
import jace.core.Utility;
import jace.core.Utility.OS;
import jace.state.Stateful;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;


/**
 * Simple implementation of joystick support that supports mouse or keyboard.
 * Actual joystick support isn't offered by Java at this moment in time
 * unfortunately.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
public class Joystick extends Device {
    static {
        Platform.runLater(()->{
            GLFW.glfwInit();
            // Load joystick mappings from resources
            // First read the file into a ByteBuffer
            try (InputStream inputStream = Joystick.class.getResourceAsStream("/jace/data/gamecontrollerdb.txt")) {
                // Throw it into a string
                String mappings = new String(inputStream.readAllBytes());
                parseGameControllerDB(mappings);
            } catch (Exception e) {
                System.err.println("Failed to load joystick mappings; error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    static public class ControllerMapping {
        public String name;
        public String guid;
        public String platform;
        public int button0 = -1;
        public int button0rapid = -1;
        public int button1 = -1;
        public int button1rapid = -1;
        public int pause = -1;
        public boolean xinvert = false;
        public int xaxis = -1;
        public boolean yinvert = false;
        public int yaxis = -1;
        public int up = -1;
        public int down = -1;
        public int left = -1;
        public int right = -1;

        public boolean hasGamepad() {
            return up >=0 && down >= 0 && left >= 0 && right >= 0;
        }
    }

    static Map<OS, Map<String, ControllerMapping>> controllerMappings = new HashMap<>();
    
    static void parseGameControllerDB(String mappings) {
        for (OS os : OS.values()) {
            controllerMappings.put(os, new HashMap<>());
        }
        // File format: 
        // Any line starting with a # or empty is a comment
        // Format is GUID, name, mappings
        // Mappings are a comma-separated list of buttons, axes, and hats with a : delimiter
        // Buttons are B<index>, axes are A<index>, hats are H<index>

        // Read into a map of GUID to mappings
        String[] lines = mappings.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 3) {
                continue;
            }
            String guid = parts[0].trim();
            String name = parts[1].trim();
            ControllerMapping controller = new ControllerMapping();
            controller.guid = guid;
            controller.name = name;
            OS os = OS.Unknown;
            // Split the mapping into parts
            for (int i = 2; i < parts.length; i++) {
                String[] mappingParts = parts[i].split(":");
                if (mappingParts.length < 2) {
                    continue;
                }
                String target = mappingParts[0].trim();
                String source = mappingParts[1].trim();
                boolean inverted = source.endsWith("~");
                if (inverted) {
                    source = source.substring(0, source.length() - 1);
                }
                boolean isAxis = source.charAt(0) == 'a';
                boolean isButton = source.charAt(0) == 'b';
                boolean isHat = source.charAt(0) == 'h';
                boolean isNAN = !isAxis && !isButton && !isHat;
                int index = isNAN ? -1 : Integer.parseInt(source.substring(isHat ? 3 : 1));
                if (isAxis) {
                    switch (target) {
                        case "leftx" -> {
                            controller.xaxis = index;
                            controller.xinvert = inverted;
                        }
                        case "lefty" -> {
                            controller.yaxis = index;
                            controller.yinvert = inverted;
                        }
                    }
                } else if (isButton) {
                    switch (target) {
                        case "a" -> controller.button0 = index;
                        case "b" -> controller.button1 = index;
                        case "x" -> controller.button0rapid = index;
                        case "y" -> controller.button1rapid = index;
                        case "dpup" -> controller.up = index;
                        case "dpdown" -> controller.down = index;
                        case "dpleft" -> controller.left = index;
                        case "dpright" -> controller.right = index;
                        case "start" -> controller.pause = index;
                    }
                } else {
                    if (target.equals("platform")) {
                        controller.platform = source;
                        if (source.toLowerCase().contains("windows")) {
                            os = OS.Windows;
                        } else if (source.toLowerCase().contains("mac")) {
                            os = OS.Mac;
                        } else if (source.toLowerCase().contains("linux")) {
                            os = OS.Linux;
                        }
                    }
                }
            }
            controllerMappings.get(os).put(guid, controller);
        }
    }

    @ConfigurableField(name = "Center Mouse", shortName = "center", description = "Moves mouse back to the center of the screen, can get annoying.")
    public boolean centerMouse = false;
    @ConfigurableField(name = "Use keyboard", shortName = "useKeys", description = "Arrow keys will control joystick instead of the mouse.")
    public boolean useKeyboard = false;
    @ConfigurableField(name = "Hog keypresses", shortName = "hog", description = "Key presses will not be sent to emulator.")
    public boolean hogKeyboard = false;
    @ConfigurableField(name = "Controller", shortName = "glfwController", description = "Physical game controller")
    public DynamicSelection<String> glfwController = new DynamicSelection<>(null) {
        @Override
        public boolean allowNull() {
            return true;
        }

        @Override
        public LinkedHashMap<String, String> getSelections() {
            // Get list of joysticks from GLFW
            LinkedHashMap<String, String> selections = new LinkedHashMap<>();
            selections.put("", "***Empty***");
            for (int i = GLFW.GLFW_JOYSTICK_1; i <= GLFW.GLFW_JOYSTICK_LAST; i++) {
                if (GLFW.glfwJoystickPresent(i)) {
                    selections.put(GLFW.glfwGetJoystickName(i), GLFW.glfwGetJoystickName(i));
                }
            }
            return selections;
        }
    };
    @ConfigurableField(name = "X Axis", shortName = "xaxis", description = "Physical game controller X Axis")
    public int xaxis = 0;
    @ConfigurableField(name = "Y Axis", shortName = "yaxis", description = "Physical game controller Y Axis")
    public int yaxis = 1;
    @ConfigurableField(name = "Button 0", shortName = "buttonA", description = "Physical game controller A button")
    public int button0 = 1;
    @ConfigurableField(name = "Button 0 rapid", shortName = "buttonX", description = "Physical game controller X button")
    public int button0rapid = 3;
    @ConfigurableField(name = "Button 1", shortName = "buttonB", description = "Physical game controller B button")
    public int button1 = 2;
    @ConfigurableField(name = "Button 1 rapid", shortName = "buttonX", description = "Physical game controller X button")
    public int button1rapid = 4;
    @ConfigurableField(name = "Manual mapping", shortName = "manual", description = "Use custom controller mapping instead of DB settings")
    public boolean useManualMapping = false;

    @ConfigurableField(name = "Use D-PAD", shortName = "dpad", description = "Physical game controller enable D-PAD")
    public boolean useDPad = true;
    @ConfigurableField(name = "Dead Zone", shortName = "deadZone", description = "Dead zone for joystick (0-1)")
    public static float deadZone = 0.095f;
    @ConfigurableField(name = "Sensitivity", shortName = "sensitivity", description = "Joystick value mutiplier")
    public static float sensitivity = 1.1f;
    @ConfigurableField(name = "Rapid fire interval (ms)", shortName = "rapidfire", description = "Interval for rapid fire (ms)")
    public int rapidFireInterval = 16;

    Integer controllerNumber = null;
    ControllerMapping controllerMapping = null;

    public Integer getControllerNum() {
        if (controllerNumber != null) {
            return controllerNumber >= 0 ? controllerNumber : null;
        }
        String controllerName = glfwController.getValue();
        if (controllerName == null || controllerName.isEmpty()) {
            return null;
        }
        for (int i = GLFW.GLFW_JOYSTICK_1; i <= GLFW.GLFW_JOYSTICK_LAST; i++) {
            if (controllerName.equals(GLFW.glfwGetJoystickName(i)) && GLFW.glfwJoystickPresent(i)) {
                controllerNumber = i;
                return i;
            }
        }
        return null;
    }
    private boolean selectedPhysicalController() {
        return getControllerNum() != null;
    }
    public int port;
    @Stateful
    public int x = 0;
    @Stateful
    public int y = 0;
    private int joyX = 0;
    private int joyY = 0;
    MemorySoftSwitch xSwitch;
    MemorySoftSwitch ySwitch;

    long lastPollTime = System.currentTimeMillis();
    FloatBuffer axes;
    ByteBuffer buttons;

    public Joystick(int port, Computer computer) {
        super();
        if (JaceApplication.getApplication() == null) {
            return;
        }
        Scene scene = JaceApplication.getApplication().primaryStage.getScene();
        // Register a mouse handler on the primary stage that tracks the 
        // mouse x/y position as a percentage of window width and height
        scene.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            if (!useKeyboard && !selectedPhysicalController()) {
                joyX = (int) (event.getX() / scene.getWidth() * 255);
                joyY = (int) (event.getY() / scene.getHeight() * 255);
            }
        });
        scene.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (!useKeyboard && !selectedPhysicalController()) {
                if (event.getButton() == MouseButton.PRIMARY) {
                    if (port == 0) {
                        SoftSwitches.PB0.getSwitch().setState(true);                       
                        Keyboard.isOpenApplePressed = true;
                    } else {
                        SoftSwitches.PB2.getSwitch().setState(true);
                    }
                } 
                
                if (event.getButton() == MouseButton.SECONDARY) {
                    Keyboard.isClosedApplePressed = true;
                    SoftSwitches.PB1.getSwitch().setState(true);
                }
            }
        });
        scene.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (port == 0) {
                    SoftSwitches.PB0.getSwitch().setState(false);
                    Keyboard.isOpenApplePressed = false;
                } else {
                    SoftSwitches.PB2.getSwitch().setState(false);
                }
            } 
            
            if (event.getButton() == MouseButton.SECONDARY) {
                Keyboard.isClosedApplePressed = false;
                SoftSwitches.PB1.getSwitch().setState(false);
            }
       });
        this.port = port;
        if (port == 0) {
            xSwitch = (MemorySoftSwitch) SoftSwitches.PDL0.getSwitch();
            ySwitch = (MemorySoftSwitch) SoftSwitches.PDL1.getSwitch();
        } else {
            xSwitch = (MemorySoftSwitch) SoftSwitches.PDL2.getSwitch();
            ySwitch = (MemorySoftSwitch) SoftSwitches.PDL3.getSwitch();
        }
    }
    public boolean leftPressed = false;
    public boolean rightPressed = false;
    public boolean upPressed = false;
    public boolean downPressed = false;

    private void readJoystick() {
        ticksSinceLastRead = 0;
        if (useKeyboard) {
            joyX = (leftPressed ? -128 : 0) + (rightPressed ? 256 : 128);
            joyY = (upPressed ? -128 : 0) + (downPressed ? 256 : 128);
        } else if (readGLFWJoystick()) {
            float x = -0.5f;
            float y = 0.5f;
            if (controllerMapping != null && !useManualMapping) {
                x = axes.get(controllerMapping.xaxis) * (controllerMapping.xinvert ? -1.0f : 1.0f);
                y = axes.get(controllerMapping.yaxis) * (controllerMapping.yinvert ? -1.0f : 1.0f);
            } else {
                if (xaxis >= 0 && xaxis < axes.capacity()) {
                    x = axes.get(xaxis);
                }
                if (yaxis >= 0 && yaxis < axes.capacity()) {
                    y = axes.get(yaxis);
                }
            }

            if (useDPad && controllerMapping != null) {
                if (getButton(controllerMapping.left)) {
                    x = -1;
                } else if (getButton(controllerMapping.right)) {
                    x = 1;
                }

                if (getButton(controllerMapping.up)) {
                    y = -1;
                } else if (getButton(controllerMapping.down)) {
                    y = 1;
                }
            }
            if (Math.abs(x) < deadZone) {
                x = 0;
            }
            if (Math.abs(y) < deadZone) {
                y = 0;
            }

            // We have to let the joystick go a little further in the positive direction
            // because boulderdash is a little too sensitive!
            x = Math.max(-1.0f, Math.min(1.0f, x * sensitivity));
            y = Math.max(-1.0f, Math.min(1.0f, y * sensitivity));

            joyX = (int) (x * 128.0 + 128.0);
            joyY = (int) (y * 128.0 + 128.0);

            readButtons();
        }
    }

    public static long POLLING_TIME = 15;
    public static int CALIBRATION_ITERATIONS = 15;
    private void calibrateTiming() {
        if (selectedPhysicalController()) {
            Integer controllerNum = getControllerNum();
            if (controllerNum != null) {
                long start = System.currentTimeMillis();

                Emulator.whileSuspended((c) -> {
                    for (int i = 0; i < CALIBRATION_ITERATIONS; i++) {
                        buttons = GLFW.glfwGetJoystickButtons(controllerNumber);
                        axes = GLFW.glfwGetJoystickAxes(controllerNumber);
                    }
                });
                String guid = GLFW.glfwGetJoystickGUID(controllerNumber);
                long end = System.currentTimeMillis();
                POLLING_TIME = (end - start) / CALIBRATION_ITERATIONS + 1;
                POLLING_TIME = Math.min(POLLING_TIME*2, 45);
                lastPollTime = end;
                System.out.println("Calibrated polling time to " + POLLING_TIME + "ms for joystick " + guid);
            }
        }
    }

    private boolean readGLFWJoystick() {
        if (System.currentTimeMillis() - lastPollTime >= POLLING_TIME) {
            lastPollTime = System.currentTimeMillis();
            if (selectedPhysicalController()) {
                Integer controllerNum = getControllerNum();
                if (controllerNum != null) {
                    Platform.runLater(()->{
                        buttons = GLFW.glfwGetJoystickButtons(controllerNumber);
                        axes = GLFW.glfwGetJoystickAxes(controllerNumber);
                    });
                }
            }
        }
        return axes != null && buttons != null;
    }

    long button0heldSince = 0;
    long button1heldSince = 0;
    boolean justPaused = false;

    private boolean getButton(Integer... choices) {
        for (Integer choice : choices) {
            if (choice != null && choice >= 0 && choice < buttons.capacity()) {         
                return buttons.get(choice) != 0;
            }
        }
        return false;
    }

    private void readButtons() {
        if (readGLFWJoystick()) {
            boolean hasMapping = !useManualMapping && controllerMapping != null;
            boolean b0 = getButton(hasMapping ? controllerMapping.button0 : null, button0);
            boolean b0rapid = getButton(hasMapping ? controllerMapping.button0rapid : null, button0rapid);
            boolean b1 = getButton(hasMapping ? controllerMapping.button1 : null, button1);
            boolean b1rapid = getButton(hasMapping ? controllerMapping.button1rapid : null, button1rapid);
            boolean pause = getButton(hasMapping ? controllerMapping.pause : null);

            if (b0rapid) {
                if (button0heldSince == 0) {
                    button0heldSince = System.currentTimeMillis();
                } else {
                    long timeHeld = System.currentTimeMillis() - button0heldSince;
                    int intervalNumber = (int) (timeHeld / rapidFireInterval);
                    b0 = (intervalNumber % 2 == 0);
                }
            } else {
                button0heldSince = 0;
            }

            if (b1rapid) {
                if (button1heldSince == 0) {
                    button1heldSince = System.currentTimeMillis();
                } else {
                    long timeHeld = System.currentTimeMillis() - button1heldSince;
                    int intervalNumber = (int) (timeHeld / rapidFireInterval);
                    b1 = (intervalNumber % 2 == 0);
                }
            } else {
                button1heldSince = 0;
            }

            if (pause) {
                if (!justPaused) {
                    // Paste the esc character
                    Keyboard.pasteFromString("\u001b");
                }
                justPaused = true;
            } else {
                justPaused = false;
            }

            SoftSwitches.PB0.getSwitch().setState(b0 || Keyboard.isOpenApplePressed);
            SoftSwitches.PB1.getSwitch().setState(b1 || Keyboard.isClosedApplePressed);
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

    int ticksSinceLastRead = Integer.MAX_VALUE;

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
        if (selectedPhysicalController()) {
            ticksSinceLastRead++;
            if (ticksSinceLastRead % 1000 == 0) {
                readButtons();
            }
        } else if (finished) {
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
        super.detach();
    }

    @Override
    public void reconfigure() {
        removeListeners();
        x = 0;
        y = 0;
        controllerNumber = null;
        Integer controllerNum = getControllerNum();
        if (controllerNum != null) {
            OS currentOS = Utility.getOS();
            OS[] searchOrder = {};
            switch (currentOS) {
                case Linux:
                    searchOrder = new OS[]{OS.Linux, OS.Windows, OS.Mac, OS.Unknown};
                    break;
                case Mac:
                    searchOrder = new OS[]{OS.Mac, OS.Windows, OS.Linux, OS.Unknown};
                    break;
                case Unknown:
                    searchOrder = new OS[]{OS.Unknown, OS.Linux, OS.Windows, OS.Mac};
                    break;
                case Windows:
                    searchOrder = new OS[]{OS.Windows, OS.Unknown, OS.Linux, OS.Mac};
                    break;
                default:
                    break;
            }
            String guid = GLFW.glfwGetJoystickGUID(controllerNum);
            controllerMapping = null;
            for (OS searchOS : searchOrder) {
                if (controllerMappings.get(searchOS).containsKey(guid)) {
                    System.out.println("Found mapping for %s, OS=%s".formatted(guid, searchOS));
                    controllerMapping = controllerMappings.get(searchOS).get(guid);
                    break;
                }
            }
            if (controllerMapping != null) {
                System.out.println("Using controller " + controllerMapping.name);
            } else {
                System.out.println("No controller mapping found for " + GLFW.glfwGetJoystickGUID(controllerNum));
            }
        } else {
            controllerMapping = null;
        }
        Platform.runLater(this::calibrateTiming);
        registerListeners();
    }

    @InvokableAction(name = "Left", category = "joystick", defaultKeyMapping = "left", notifyOnRelease = true)
    public boolean joystickLeft(boolean pressed) {
        System.out.println("LEFT "+pressed);
        if (!useKeyboard) {
            System.out.println("(ignored)");
            return false;
        }
        leftPressed = pressed;
        if (pressed) {
            rightPressed = false;
        }
        return hogKeyboard;
    }

    @InvokableAction(name = "Right", category = "joystick", defaultKeyMapping = "right", notifyOnRelease = true)
    public boolean joystickRight(boolean pressed) {
        System.out.println("RIGHT "+pressed);
        if (!useKeyboard) {
            System.out.println("(ignored)");
            return false;
        }
        rightPressed = pressed;
        if (pressed) {
            leftPressed = false;
        }
        return hogKeyboard;
    }

    @InvokableAction(name = "Up", category = "joystick", defaultKeyMapping = "up", notifyOnRelease = true)
    public boolean joystickUp(boolean pressed) {
        System.out.println("UP!");
        if (!useKeyboard) {
            System.out.println("(ignored)");
            return false;
        }
        upPressed = pressed;
        if (pressed) {
            downPressed = false;
        }
        return hogKeyboard;
    }

    @InvokableAction(name = "Down", category = "joystick", defaultKeyMapping = "down", notifyOnRelease = true)
    public boolean joystickDown(boolean pressed) {
        System.out.println("DOWN!");
        if (!useKeyboard) {
            System.out.println("(ignored)");
            return false;
        }
        downPressed = pressed;
        if (pressed) {
            upPressed = false;
        }
        return hogKeyboard;
    }

    public void initJoystickRead(RAMEvent e) {
        readJoystick();
        xSwitch.setState(true);
        // Some games just suck and don't want to read the joystick properly
        // Use larger-than-necessary values to try to get around this
        if (joyX >= 254) {
            joyX = 280;
        }
        if (joyY >= 255) {
            joyY = 280;
        }
        x = 10 + joyX * 11;
        ySwitch.setState(true);
        y = 10 + joyY * 11;
        Emulator.withVideo(v->e.setNewValue(v.getFloatingBus()));
        resume();
    }

    RAMListener listener;

    private void registerListeners() {
        listener = getMemory().observe("Joystick I/O", RAMEvent.TYPE.ANY, 0x0c070, 0x0c07f, this::initJoystickRead);
    }

    private void removeListeners() {
        getMemory().removeListener(listener);
    }
}
