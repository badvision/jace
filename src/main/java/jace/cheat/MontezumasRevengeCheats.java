package jace.cheat;

import jace.config.ConfigurableField;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMListener;

public class MontezumasRevengeCheats extends Cheats {
    @ConfigurableField(category = "Hack", name = "Repulsive", defaultValue = "false", description = "YOU STINK!")
    public static boolean repulsiveHack = false;
    
    public static int PLAYER_X = 0x01508;
    public static int PLAYER_Y = 0x01510;
    
    public MontezumasRevengeCheats(Computer computer) {
        super(computer);
    }

    @Override
    void registerListeners() {
        if (repulsiveHack) {
            addCheat(new RAMListener(RAMEvent.TYPE.WRITE, RAMEvent.SCOPE.RANGE, RAMEvent.VALUE.ANY) {
                
                @Override
                protected void doConfig() {
                    setScopeStart(0x1508);
                    setScopeEnd(0x1518);
                }
                
                @Override
                protected void doEvent(RAMEvent e) {
                    int playerX = computer.getMemory().readRaw(PLAYER_X);
                    int playerY = computer.getMemory().readRaw(PLAYER_Y);
                    for (int num = 7; num >0; num--) {
                       int monsterX = computer.getMemory().readRaw(PLAYER_X + num);
                       int monsterY = computer.getMemory().readRaw(PLAYER_Y + num);
                       if (monsterX != 0 && monsterY != 0) {
                           if (Math.abs(monsterY - playerY) < 15) {
                               if (Math.abs(monsterX - playerX) < 7) {
                                   if (monsterX > playerX) {
                                       monsterX+=1;
                                   } else {
                                       monsterX-=1;                                       
                                       if (monsterX <= 0) {
                                           monsterX = 80;
                                       }
                                   }
                                   computer.getMemory().write(PLAYER_X+num, (byte) monsterX, false, false);
                               }
                           }
                       }
                    }
                }
            });
        }
    }

    @Override
    protected String getDeviceName() {
        return "Montezuma's Revenge";
    }

    @Override
    public void tick() {
    }
}
