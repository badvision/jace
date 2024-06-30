package jace.cheat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.core.PagedMemory;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;

public class ProgramIdentity extends Cheats {
    private Map<String, String> programIdentities;

    @Override
    public void registerListeners() {
        addCheat("Track execution", TYPE.ANY, this::trackActivity, 0, 0x0ffff);
    }

    @Override   
    protected String getDeviceName() {
        return "Program Identity";
    }

    int INTERVAL = 1000000;
    int THRESHOLD_VALUE = 10000;
    int CLIP_VALUE = THRESHOLD_VALUE * 2;
    int DECAY = THRESHOLD_VALUE / 2;

    int[] programRegions = new int[512];
    private void trackActivity(RAMEvent e) {
        int bank = e.getAddress() >> 8;
        if (bank >= 0xc0 && bank < 0xd0) {
            // Skip I/O region
            return;
        }
        // Detect language card ram execution
        if (bank >= 0xd0 && SoftSwitches.LCRAM.isOff()) {
            // Skip rom execution
            return;
        }
        if (!e.isMainMemory()) {
            bank += 256;
        }
        if (e.getType() == RAMEvent.TYPE.EXECUTE) {
            programRegions[bank] = Math.min(CLIP_VALUE, programRegions[bank] + 1);
        } else if (e.getType() == RAMEvent.TYPE.WRITE) {
            programRegions[bank] = 0;
        }
    }

    private String generateChecksum() {
        CRC32 crc = new CRC32();
        RAM128k ram = (RAM128k) getMemory();
        int bankCount = 0;
        for (int i=0; i < 512; i++) {
            if (programRegions[i] > THRESHOLD_VALUE) {
                PagedMemory mem = ram.getMainMemory();
                if (i >= 0x0d0 && i < 0x0100) {
                    mem = ram.getLanguageCard();
                } else if (i >= 0x0100 && i < 0x01d0) {
                    mem = ram.getAuxMemory();
                } else if (i >= 0x01d0) {
                    mem = ram.getAuxLanguageCard();
                }
                bankCount++;
                crc.update(mem.getMemoryPage((i & 0x0ff) << 8));
            }
        }
        return Long.toHexString(crc.getValue())+"-"+bankCount;
    }

    @Override
    public void resume() {
        super.resume();
        Arrays.fill(programRegions, 0);
        readProgramIdentities();
    }

    int counter = 0;
    String lastChecksum = "";    
    @Override
    public void tick() {
        if (counter++ >= INTERVAL) {
            String checksum = generateChecksum();
            if (!checksum.equals(lastChecksum)) {
                String identity = programIdentities.getOrDefault(checksum, "UNKNOWN");
                System.out.println(checksum + "," + identity);
                lastChecksum = checksum;
            }
            counter = 0;
            for (int i=0; i < 512; i++) {
                programRegions[i] = Math.max(0, programRegions[i] - DECAY);
            }
        }
    }   

    private void readProgramIdentities() {
        // Read from resources file
        InputStream in = Cheats.class.getResourceAsStream("/jace/cheats/program-identities.txt");
        try {
            programIdentities = new HashMap<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                programIdentities.put(parts[0], parts[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }    
}
