package jace.cheat;

import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.core.Computer;
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

    public static int X_MAX = 80;
    public static int Y_MAX = 160;
    public static int MAX_VEL = 4;
    public static int MOON_JUMP_VELOCITY = -14;
    public static int ROOM_LEVEL = 0x0d1;
    public static int LIVES = 0x0e0;
    public static int SCORE = 0x0e8;
    public static int SCORE_END = 0x0ea;
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
            forceValue(LIVES, 11);
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
        } else {
            EmulatorUILogic.removeMouseListener(listener);
        }
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
