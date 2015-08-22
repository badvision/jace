package jace.cheat;

import jace.Emulator;
import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.RAMEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;

public class MontezumasRevengeCheats extends Cheats {

    @ConfigurableField(category = "Hack", name = "Repulsive", defaultValue = "false", description = "YOU STINK!")
    public static boolean repulsiveHack = false;

    @ConfigurableField(category = "Hack", name = "Feather Fall", defaultValue = "false", description = "Falling will not result in death")
    public static boolean featherFall = false;

    @ConfigurableField(category = "Hack", name = "Moon Jump", defaultValue = "false", description = "Wheeee!")
    public static boolean moonJump = false;

    @ConfigurableField(category = "Hack", name = "Infinite Lives", defaultValue = "false", description = "Game on!")
    public static boolean infiniteLives = false;

    @ConfigurableField(category = "Hack", name = "Score hack", defaultValue = "false", description = "Change the score")
    public static boolean scoreHack = false;

    @ConfigurableField(category = "Hack", name = "Snake Charmer", defaultValue = "false", description = "Disable collision detection with enemies")
    public static boolean snakeCharmer = false;

    @ConfigurableField(category = "Hack", name = "Teleport", defaultValue = "false", description = "Click to teleport!")
    public static boolean mouseHack = false;

    @ConfigurableField(category = "Hack", name = "Safe Passage", defaultValue = "false", description = "Deadly floors and doors disabled!")
    public static boolean safePassage = false;

    public static int X_MAX = 80;
    public static int Y_MAX = 160;
    public static int MAX_VEL = 4;
    public static int MOON_JUMP_VELOCITY = -14;
    public static int ROOM_LEVEL = 0x0d1;
    public static int LIVES = 0x0e0;
    public static int SCORE = 0x0e8;
    public static int SCORE_END = 0x0ea;
    public static int FLOOR_TIMER = 0x030a;
    public static int HAZARD_TIMER = 0x030b;
    public static int HAZARD_FLAG = 0x030f;
    public static int PLAYER_X = 0x01508;
    public static int PLAYER_Y = 0x01510;
    public static int Y_VELOCITY = 0x01550;
    public static int CHAR_STATE = 0x01570;

    public static int lastX = 0;

    public MontezumasRevengeCheats(Computer computer) {
        super(computer);
    }

    double mouseX;
    double mouseY;
    EventHandler<javafx.scene.input.MouseEvent> listener = (event) -> {
        Node source = (Node) event.getSource();
        mouseX = event.getSceneX() / source.getBoundsInLocal().getWidth();
        mouseY = event.getSceneY() / source.getBoundsInLocal().getHeight();
        if (event.isPrimaryButtonDown()) {
            mouseClicked(event.getButton());
        }
    };

    @Override
    void registerListeners() {
        RAM memory = Emulator.computer.memory;
        if (repulsiveHack) {
            addCheat(RAMEvent.TYPE.WRITE, this::repulsiveBehavior, 0x1508, 0x1518);
        }

        if (featherFall) {
            addCheat(RAMEvent.TYPE.WRITE, this::featherFallBehavior, PLAYER_Y);
            // Bypass the part that realizes you should die when you hit the floor
            bypassCode(0x6bb3, 0x6bb4);
        }

        if (moonJump) {
            addCheat(RAMEvent.TYPE.WRITE, this::moonJumpBehavior, Y_VELOCITY);
        }

        if (infiniteLives) {
            forceValue(11, LIVES);
        }

        if (safePassage) {
            //blank out pattern for floors/doors
            for (int addr = 0x0b54; addr <= 0xb5f; addr++) {
                memory.write(addr, (byte) 0, false, false);
                memory.write(addr + 0x0400, (byte) 0, false, false);
            }
            memory.write(0x0b50, (byte) 0b11010111, false, false);
            memory.write(0x0b51, (byte) 0b00010000, false, false);
            memory.write(0x0b52, (byte) 0b10001000, false, false);
            memory.write(0x0b53, (byte) 0b10101010, false, false);
            memory.write(0x0f50, (byte) 0b10101110, false, false);
            memory.write(0x0f51, (byte) 0b00001000, false, false);
            memory.write(0x0f52, (byte) 0b10000100, false, false);
            memory.write(0x0f53, (byte) 0b11010101, false, false);            
            forceValue(32, FLOOR_TIMER);
            forceValue(32, HAZARD_TIMER);
            forceValue(1, HAZARD_FLAG);
        }

        if (scoreHack) {
            // Score: 900913
            forceValue(0x90, SCORE);
            forceValue(0x09, SCORE + 1);
            forceValue(0x13, SCORE + 2);
        }

        if (snakeCharmer) {
            // Skip the code that determines you're touching an enemy
            bypassCode(0x07963, 0x07964);
        }
        if (mouseHack) {
            EmulatorUILogic.addMouseListener(listener);
        }
    }

    @Override
    protected void unregisterListeners() {
        super.unregisterListeners();
        EmulatorUILogic.removeMouseListener(listener);
    }
        
    private void repulsiveBehavior(RAMEvent e) {
        int playerX = computer.getMemory().readRaw(PLAYER_X);
        int playerY = computer.getMemory().readRaw(PLAYER_Y);
        for (int num = 7; num > 0; num--) {
            int monsterX = computer.getMemory().readRaw(PLAYER_X + num);
            int monsterY = computer.getMemory().readRaw(PLAYER_Y + num);
            if (monsterX != 0 && monsterY != 0) {
                if (Math.abs(monsterY - playerY) < 19) {
                    if (Math.abs(monsterX - playerX) < 7) {
                        int movement = Math.max(1, Math.abs(lastX - playerX));
                        if (monsterX > playerX) {
                            monsterX += movement;
                        } else {
                            monsterX -= movement;
                            if (monsterX <= 0) {
                                monsterX = 80;
                            }
                        }
                        computer.getMemory().write(PLAYER_X + num, (byte) monsterX, false, false);
                    }
                }
            }
        }
        lastX = playerX;
    }

    private void featherFallBehavior(RAMEvent yCoordChangeEvent) {
        if (yCoordChangeEvent.getNewValue() != yCoordChangeEvent.getOldValue()) {
            int yVel = computer.getMemory().readRaw(Y_VELOCITY);
            if (yVel > MAX_VEL) {
                computer.getMemory().write(Y_VELOCITY, (byte) MAX_VEL, false, false);
            }
        }
    }

    private void moonJumpBehavior(RAMEvent velocityChangeEvent) {
        if (inStartingSequence()) {
            return;
        }
        if (velocityChangeEvent.getNewValue() < 0
                && velocityChangeEvent.getNewValue() < velocityChangeEvent.getOldValue()) {
            velocityChangeEvent.setNewValue(MOON_JUMP_VELOCITY);
        }
    }

    private boolean inStartingSequence() {
        int roomLevel = computer.getMemory().readRaw(ROOM_LEVEL);
        return roomLevel == -1;
    }

    @Override
    public String getName() {
        return "Montezuma's Revenge";
    }

    @Override
    protected String getDeviceName() {
        return "Montezuma's Revenge";
    }

    @Override
    public void tick() {
    }

    private void mouseClicked(MouseButton button) {
        byte newX = (byte) (mouseX * X_MAX);
        byte newY = (byte) (mouseY * Y_MAX);
        computer.memory.write(PLAYER_X, newX, false, false);
        computer.memory.write(PLAYER_Y, newY, false, false);
    }
}