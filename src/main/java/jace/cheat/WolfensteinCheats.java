/*
 * Copyright 2018 org.badvision.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jace.cheat;

import jace.Emulator;
import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.core.RAMEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Cheats for the Wolfenstein series games
 */
public class WolfensteinCheats extends Cheats {

    // Specific to Wolfenstein
    static final int KEYS = 0x04359;
    static final int GRENADES = 0x04348;
    // Object types
    static final int CHEST = 48;
    static final int SS = 32;

    // Specific to Beyond Wolfenstein
    static final int MARKS = 0x0434b;
    static final int PASSES = 0x04360;
    static final int CLOSET_CONTENTS_CMP = 0x05FB9; // Only locks by type, so mess up the check
    // Object types
    static final int CLOSET = 32;
    static final int ALARM = 48;
    static final int SEATED_GUARD = 80;
    static final int BW_DOOR = 96;

    // Same in both Wolfenstein and Beyond Wolfenstein
    static final int PLAYER_LOCATION = 0x04343;
    static final int BULLETS = 0x04347;
    // Object types
    static final int CORPSE = 64;
    static final int GUARD = 16;
    static final int DOOR = 80;
    static final int NOTHING = 0;

    private EventHandler<MouseEvent> mouseListener = this::processMouseEvent;
    @ConfigurableField(category = "Hack", name = "Beyond Wolfenstein", defaultValue = "false", description = "Make sure cheats work with Beyond Wolfenstein")
    public static boolean _isBeyondWolfenstein = false;

    @ConfigurableField(category = "Hack", name = "Mouse (1+2)", defaultValue = "false", description = "Left click kills/opens, Right click teleports")
    public static boolean mouseMode = true;

    @ConfigurableField(category = "Hack", name = "Ammo (1+2)", defaultValue = "false", description = "All the bullets and grenades you'll need")
    public static boolean ammo = true;

    @ConfigurableField(category = "Hack", name = "Rich (2)", defaultValue = "false", description = "All the money")
    public static boolean rich = true;

    @ConfigurableField(category = "Hack", name = "Uniform (1)", defaultValue = "false", description = "PUT SOME CLOTHES ON!")
    public static boolean uniform = true;

    @ConfigurableField(category = "Hack", name = "Vest (1)", defaultValue = "false", description = "Bulletproof vest")
    public static boolean vest = true;

    @ConfigurableField(category = "Hack", name = "Skeleton Key (1+2)", defaultValue = "false", description = "Open all things")
    public static boolean skeletonKey = true;

    @ConfigurableField(category = "Hack", name = "Fast Open (1)", defaultValue = "false", description = "Open all things quickly")
    public static boolean fastOpen = true;

    @ConfigurableField(category = "Hack", name = "All dead (1+2)", defaultValue = "false", description = "Everything is dead")
    public static boolean allDead = true;

    @ConfigurableField(category = "Hack", name = "Sleepy Time (1+2)", defaultValue = "false", description = "Nobody move, nobody get hurt")
    public static boolean sleepyTime = false;

    @ConfigurableField(category = "Hack", name = "Legendary (1)", defaultValue = "false", description = "All of them are SS guards!")
    public static boolean legendary = false;
    
    @ConfigurableField(category = "Hack", name = "Day at the office (2)", defaultValue = "false", description = "All of them are at desks")
    public static boolean dayAtTheOffice = false;    

    @Override
    public void registerListeners() {
        if (_isBeyondWolfenstein) {
            // Only work in Beyond Wolfenstein
            if (rich) {
                forceValue("Wolfenstein Money cheat", MARKS, 255);
            }
            if (dayAtTheOffice) {
                for (int i = 0x04080; i < 0x04100; i += 0x010) {
                    addCheat("Wolfenstein day at the office cheat " + i, RAMEvent.TYPE.READ, this::allDesks, i);
                }                
            }
        } else {
            // Only work in the first Wolfenstein game
            if (uniform) {
                forceValue("Wolfenstein Uniform cheat", 255, 0x04349);
            }
            if (vest) {
                forceValue("Wolfenstein Vest cheat", 255, 0x0434A);
            }
            if (fastOpen) {
                addCheat("Wolfenstein FastOpen cheat (1)", RAMEvent.TYPE.WRITE, this::fastOpenHandler, 0x04351);
                addCheat("Wolfenstein FastOpen cheat (2)", RAMEvent.TYPE.WRITE, this::fastOpenHandler, 0x0587B);
            }
        }
        if (ammo) {
            forceValue("Wolfenstein ammo cheat", 10, BULLETS);
            if (!_isBeyondWolfenstein) {
                forceValue("Wolfenstein grenades cheat", 9, GRENADES);
            }
        }
        if (skeletonKey) {
            if (_isBeyondWolfenstein) {
                forceValue("Wolfenstein passes cheat", 255, PASSES);
                forceValue("Wolfenstein unlock closets cheat", 64, CLOSET_CONTENTS_CMP); // Fake it out so it thinks all doors are unlocked
            } else {
                forceValue("Wolfenstein keys cheat", 255, KEYS);
            }
        }
        if (allDead) {
            for (int i = 0x04080; i < 0x04100; i += 0x010) {
                addCheat("Wolfenstein all dead cheat " + i, RAMEvent.TYPE.READ, this::allDead, i);
            }
        }
        if (sleepyTime) {
            for (int i = 0x04080; i < 0x04100; i += 0x010) {
                forceValue("Wolfenstein sleep cheat (1)", 0, i + 2);
                forceValue("Wolfenstein sleep cheat (2)", 0, i + 3);
// This makes them shout ACHTUNG over and over again... so don't do that.                
//                forceValue(144, i+12);
                forceValue("Wolfenstein sleep cheat (3)", 0, i + 12);
            }
        }
        if (legendary) {
            for (int i = 0x04080; i < 0x04100; i += 0x010) {
                addCheat("Wolfenstein legendary cheat", RAMEvent.TYPE.READ, this::legendaryMode, i);
            }
        }

        if (mouseMode) {
            EmulatorUILogic.addMouseListener(mouseListener);
        } else {
            EmulatorUILogic.removeMouseListener(mouseListener);
        }
    }

    private void fastOpenHandler(RAMEvent evt) {
        int newVal = evt.getNewValue() & 0x0ff;
        if (newVal > 1) {
            evt.setNewValue(1);
        }
    }

    private boolean isFinalRoom() {
        for (int i = 0x04080; i < 0x04100; i += 0x010) {
            int objectType = getMemory().readRaw(i) & 0x0ff;
            if (objectType == BW_DOOR) {
                return true;
            }
        }
        return false;
    }

    private void allDesks(RAMEvent evt) {
        int location = getMemory().readRaw(evt.getAddress() + 1);
        if (!isFinalRoom() || location < 32) {
            int type = evt.getNewValue();
            if (type == GUARD) {
                evt.setNewValue(SEATED_GUARD);
                // Reset the status flag to 0 to prevent the boss desk from rendering, but don't revive dead guards!
                if (getMemory().readRaw(evt.getAddress() + 4) != 4) {
                    getMemory().write(evt.getAddress() + 4, (byte) 0, false, false);
                }
            }
        }
    }

    private void allDead(RAMEvent evt) {
        int type = evt.getNewValue();
        if (_isBeyondWolfenstein) {
            int location = getMemory().readRaw(evt.getAddress() + 1);
            if (!isFinalRoom() || location < 32) {
                if (type == GUARD) {
                    evt.setNewValue(CORPSE);
                } else if (type == SEATED_GUARD) {
                    getMemory().write(evt.getAddress() + 4, (byte) 4, false, false);
                }
            }
        } else {
            if (type == GUARD || type == SS) {
                evt.setNewValue(CORPSE);
            }
        }
    }

    private int debugTicks = 0;

    private void legendaryMode(RAMEvent evt) {
        int type = evt.getNewValue();
        if (type == 16) {
            evt.setNewValue(32);
        }
    }

    private void processMouseEvent(MouseEvent evt) {
        if (evt.isPrimaryButtonDown() || evt.isSecondaryButtonDown()) {
            Node source = (Node) evt.getSource();
            double mouseX = evt.getSceneX() / source.getBoundsInLocal().getWidth();
            double mouseY = evt.getSceneY() / source.getBoundsInLocal().getHeight();
            int x = Math.max(0, Math.min(7, (int) ((mouseX - 0.148) * 11)));
            int y = Math.max(0, Math.min(7, (int) ((mouseY - 0.101) * 11)));
            int location = x + (y << 3);
            if (evt.getButton() == MouseButton.PRIMARY) {
                killEnemyAt(location);
            } else {
                teleportTo(location);
            }
        }
    }

    private void killEnemyAt(int location) {
        System.out.println("Looking for bad guy at " + location);
        for (int i = 0x04080; i < 0x04100; i += 0x010) {
            int enemyLocation = getMemory().readRaw(i + 1) & 0x0ff;
            System.out.print("Location " + enemyLocation);
            String type = "";
            boolean isAlive = false;
            boolean isSeatedGuard = false;
            if (_isBeyondWolfenstein) {
                switch (getMemory().readRaw(i) & 0x0ff) {
                    case GUARD:
                        type = "guard";
                        isAlive = true;
                        break;
                    case SEATED_GUARD:
                        type = "seated guard";
                        isAlive = true;
                        isSeatedGuard = true;
                        break;
                    case CLOSET:
                        type = "closet";
                        break;
                    case CORPSE:
                        type = "corpse";
                        break;
                    case NOTHING:
                        type = "nothing";
                        break;
                    default:
                        type = "unknown type " + (getMemory().readRaw(i) & 0x0ff);
                }
            } else {
                switch (getMemory().readRaw(i) & 0x0ff) {
                    case GUARD:
                        type = "guard";
                        isAlive = true;
                        break;
                    case SS:
                        type = "SS";
                        isAlive = true;
                        break;
                    case CHEST:
                        type = "chest";
                        break;
                    case CORPSE:
                        type = "corpse";
                        break;
                    case DOOR:
                        type = "door";
                        break;
                    case NOTHING:
                        type = "nothing";
                        break;
                    default:
                        type = "unknown type " + (getMemory().readRaw(i) & 0x0ff);
                }
            }
            System.out.println(" is a " + type);
            for (int j = 0x00; j < 0x0f; j++) {
                int val = getMemory().readRaw(i + j) & 0x0ff;
                System.out.print(Integer.toHexString(val) + " ");
            }
            System.out.println();

            if (isAlive && location == enemyLocation) {
                if (isSeatedGuard) {
                    getMemory().write(i + 4, (byte) 4, false, false);
                } else {
                    getMemory().write(i, (byte) CORPSE, false, true);
                }

                System.out.println("*BLAM*");
            }
        }

    }

    private void teleportTo(int location) {
        getMemory().write(0x04343, (byte) location, false, true);
    }

    @Override
    public void unregisterListeners() {
        super.unregisterListeners();
        EmulatorUILogic.removeMouseListener(mouseListener);
    }
    public static int BlueType = 0x0b700;

    @Override
    protected String getDeviceName() {
        return "Wolfenstein Cheats";
    }

    @Override
    public void tick() {
        if (debugTicks > 0) {
            debugTicks--;
            if (debugTicks == 0) {
                Emulator.withComputer(c->c.getCpu().setTraceEnabled(false));
            }
        }
    }

    /**
     * 4147-4247: Room map?
     *
     * 4080-40ff : Enemies and chests 4090-409f : Enemy 2 40a0-40af : Enemy 1 0: State/Type (0-15 = Nothing?, 16 =
     * soldier, 32 = SS, 48 = Chest, 64 = dead) 1: Location 2: Direction (0 = still) 3: Aim (0 = no gun) C: Caution?
     * (144 = stickup)
     *
     * 4341 : Player walking direction (0 = still, 1=D, 2=U, 4=L, 8=R) 4342 : Player gun direction 4343 : Real Player
     * location (4 high bits = vertical, 4 low bits = horizontal) .. use this for teleport 4344 : Player Drawing X
     * location 4345 : Player Drawing Y location 4347 : Bullets 4348 : Grenades 4349 : Uniform (0 = none, 1+ = yes) 434A
     * : Vest (0 = none, 1+ = yes) 434C : Wall collision animation timer 434D/E : Game timer (lo/high) -- no immediate
     * effect 4351 : Search / Use timer 4352 : 0 normally, 144/176 opening chest, 160 when searching body, 176 opening
     * door 4359 : Keys (8-bit flags, 255=skeleton key) 587B : Search timer
     */
}
