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

import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.core.Computer;
import jace.core.RAMEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Cheats for the Wolfenstein series games
 */
public class WolfensteinCheats extends Cheats {

    public WolfensteinCheats(Computer computer) {
        super(computer);
    }

    private EventHandler<MouseEvent> mouseListener = this::processMouseEvent;

    @ConfigurableField(category = "Hack", name = "Mouse", defaultValue = "false", description = "Left click kills/opens, Right click teleports")
    public static boolean mouseMode = true;

    @ConfigurableField(category = "Hack", name = "Ammo", defaultValue = "false", description = "All the bullets and grenades you'll need")
    public static boolean ammo = true;

    @ConfigurableField(category = "Hack", name = "Uniform", defaultValue = "false", description = "PUT SOME CLOTHES ON!")
    public static boolean uniform = true;

    @ConfigurableField(category = "Hack", name = "Protection", defaultValue = "false", description = "Bulletproof")
    public static boolean vest = true;

    @ConfigurableField(category = "Hack", name = "Skeleton Key", defaultValue = "false", description = "Open all things")
    public static boolean skeletonKey = true;

    @ConfigurableField(category = "Hack", name = "Fast Open", defaultValue = "false", description = "Open all things quickly")
    public static boolean fastOpen = true;
    
    @ConfigurableField(category = "Hack", name = "All dead", defaultValue = "false", description = "Everything is dead")
    public static boolean allDead = false;
    
    @ConfigurableField(category = "Hack", name = "Sleepy Time", defaultValue = "false", description = "Nobody move, nobody get hurt")
    public static boolean sleepyTime = false;
   
    @ConfigurableField(category = "Hack", name = "Legendary", defaultValue = "false", description = "All of them are SS guards!")
    public static boolean legendary = false;
    
    @Override
    public void registerListeners() {
        if (ammo) {
            forceValue(10, 0x04347);
            forceValue(9, 0x04348);
        }
        if (uniform) {
            forceValue(255, 0x04349);
        }
        if (vest) {
            forceValue(255, 0x0434A);
        }
        if (skeletonKey) {
            forceValue(255, 0x04359);
        }
        if (fastOpen) {
            addCheat(RAMEvent.TYPE.WRITE, this::fastOpenHandler, 0x04351);
            addCheat(RAMEvent.TYPE.WRITE, this::fastOpenHandler, 0x0587B);
        }
        if (allDead) {
            for (int i = 0x04080; i < 0x04100; i += 0x010) {
                addCheat(RAMEvent.TYPE.READ, this::allDead, i);
            }            
        }        
        if (sleepyTime) {
            for (int i = 0x04080; i < 0x04100; i += 0x010) {
                forceValue(0, i+2);
                forceValue(0, i+3);
// This makes them shout ACHTUNG over and over again... so don't do that.                
//                forceValue(144, i+12);
                forceValue(0, i+12);
            }            
        }        
        if (legendary) {
            for (int i = 0x04080; i < 0x04100; i += 0x010) {
                addCheat(RAMEvent.TYPE.READ, this::legendaryMode, i);
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
    
    private void allDead(RAMEvent evt) {
        int type = evt.getNewValue();
        if (type == 16 || type == 32) {
            evt.setNewValue(64);
        }        
    }
    
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
            int enemyLocation = computer.getMemory().readRaw(i + 1) & 0x0ff;
            System.out.print("Location " + enemyLocation);
            String type = "";
            boolean isAlive = false;
            switch (computer.getMemory().readRaw(i) & 0x0ff) {
                case 16:
                    type = "soldier";
                    isAlive = true;
                    break;
                case 32:
                    type = "SS";
                    isAlive = true;
                    break;
                case 48:
                    type = "chest";
                    break;
                case 64:
                    type = "corpse";
                    break;
                case 80:
                    type = "door";
                    break;
                default:
                    type = "unknown type " + (computer.getMemory().readRaw(i) & 0x0ff);
            }
            System.out.println(" is a " + type);
            if (isAlive && location == enemyLocation) {
                computer.getMemory().write(i, (byte) 64, false, true);
                System.out.println("*BLAM*");
            }
        }

    }

    private void teleportTo(int location) {
        computer.getMemory().write(0x04343, (byte) location, false, true);
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
