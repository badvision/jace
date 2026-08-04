package jace.terminal;

import static jace.TestUtils.initComputer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.core.Computer;
import jace.core.SoundMixer;

/**
 * Verifies that the monitor's explicit bank selectors (M / X prefixes) read the
 * bank that was asked for, and not whatever the current softswitch configuration
 * happens to map in.
 *
 * The regression this guards against: hexDump() used RAM.readRaw(), which always
 * reads the active read configuration, so an AUX dump silently mirrored MAIN.
 * Half of every DHGR pixel column was therefore unverifiable.
 *
 * @author brobert
 */
public class MonitorModeAuxMemoryTest {

    /** Inside the DHGR screen range, where 80STORE+HIRES interleaves the banks. */
    private static final int DHGR_ADDR = 0x2000;
    /** Outside the video range, to prove the fix is not video-range specific. */
    private static final int GENERAL_ADDR = 0x6000;

    private static final byte MAIN_PATTERN = (byte) 0xAA;
    private static final byte AUX_PATTERN = (byte) 0x55;

    private static RAM128k ram;

    private ByteArrayOutputStream outputStream;
    private TestableMonitorMode monitorMode;

    @BeforeClass
    public static void setupClass() {
        initComputer();
        SoundMixer.MUTE = true;
        Computer computer = Emulator.withComputer(c -> c, null);
        ram = (RAM128k) computer.getMemory();
    }

    @Before
    public void setup() {
        outputStream = new ByteArrayOutputStream();
        monitorMode = new TestableMonitorMode(outputStream);

        // Distinct patterns at the same address in each bank.
        ram.getMainMemory().writeByte(DHGR_ADDR, MAIN_PATTERN);
        ram.getAuxMemory().writeByte(DHGR_ADDR, AUX_PATTERN);
        ram.getMainMemory().writeByte(GENERAL_ADDR, MAIN_PATTERN);
        ram.getAuxMemory().writeByte(GENERAL_ADDR, AUX_PATTERN);
    }

    private String dump(int address, MemoryMode mode) {
        outputStream.reset();
        monitorMode.examineMemoryRange(address, address, mode);
        return outputStream.toString();
    }

    private static String firstByteOf(String dumpLine) {
        // Format is "2000: AA                                              | ."
        int colon = dumpLine.indexOf(':');
        return dumpLine.substring(colon + 1).trim().substring(0, 2);
    }

    @Test
    public void auxDumpReturnsAuxByteNotMainByte() {
        assertEquals("AA", firstByteOf(dump(DHGR_ADDR, MemoryMode.MAIN)));
        assertEquals("55", firstByteOf(dump(DHGR_ADDR, MemoryMode.AUX)));
    }

    @Test
    public void auxDumpWorksOutsideVideoRange() {
        assertEquals("AA", firstByteOf(dump(GENERAL_ADDR, MemoryMode.MAIN)));
        assertEquals("55", firstByteOf(dump(GENERAL_ADDR, MemoryMode.AUX)));
    }

    @Test
    public void explicitBankDumpIgnoresSoftswitchState() {
        // Point the active read configuration at aux, then ask for MAIN explicitly.
        boolean originalRamrd = SoftSwitches.RAMRD.getState();
        try {
            SoftSwitches.RAMRD.getSwitch().setState(true);
            ram.configureActiveMemory();
            assertEquals("AA", firstByteOf(dump(GENERAL_ADDR, MemoryMode.MAIN)));
            assertEquals("55", firstByteOf(dump(GENERAL_ADDR, MemoryMode.AUX)));
        } finally {
            SoftSwitches.RAMRD.getSwitch().setState(originalRamrd);
            ram.configureActiveMemory();
        }
    }

    @Test
    public void explicitBankDumpDoesNotDisturbSoftswitches() {
        String readConfigBefore = ram.getReadConfiguration();
        String writeConfigBefore = ram.getWriteConfiguration();

        dump(DHGR_ADDR, MemoryMode.AUX);
        dump(DHGR_ADDR, MemoryMode.MAIN);

        assertEquals(readConfigBefore, ram.getReadConfiguration());
        assertEquals(writeConfigBefore, ram.getWriteConfiguration());
    }

    @Test
    public void activeModeStillFollowsSoftswitchConfiguration() {
        boolean originalRamrd = SoftSwitches.RAMRD.getState();
        try {
            SoftSwitches.RAMRD.getSwitch().setState(false);
            ram.configureActiveMemory();
            assertEquals("AA", firstByteOf(dump(GENERAL_ADDR, MemoryMode.ACTIVE)));

            SoftSwitches.RAMRD.getSwitch().setState(true);
            ram.configureActiveMemory();
            assertEquals("55", firstByteOf(dump(GENERAL_ADDR, MemoryMode.ACTIVE)));
        } finally {
            SoftSwitches.RAMRD.getSwitch().setState(originalRamrd);
            ram.configureActiveMemory();
        }
    }

    @Test
    public void singleByteExamineHonorsBankSelector() {
        outputStream.reset();
        monitorMode.processCommand("X" + Integer.toHexString(DHGR_ADDR));
        String auxResult = outputStream.toString();

        outputStream.reset();
        monitorMode.processCommand("M" + Integer.toHexString(DHGR_ADDR));
        String mainResult = outputStream.toString();

        assertTrue("aux examine should report 55, got: " + auxResult, auxResult.contains("55"));
        assertTrue("main examine should report AA, got: " + mainResult, mainResult.contains("AA"));
    }
}
