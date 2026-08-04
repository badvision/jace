package jace.terminal;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jace.AbstractJaceTest;
import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.Motherboard;
import jace.core.RAM;
import jace.core.RAMEvent;
import jace.hardware.CardDiskII;
import jace.hardware.massStorage.CardMassStorage;

/**
 * Comprehensive unit tests for terminal feature functionality.
 * Tests terminal mode startup, emulator configuration, loadbin/savebin,
 * step mode behavior, and command-line argument processing.
 */
public class TerminalFeatureTest extends AbstractJaceTest {
    
    private static final Logger LOG = Logger.getLogger(TerminalFeatureTest.class.getName());
    
    // Generated blank 140K ProDOS-ordered image; see TestDiskImages for why this
    // replaced a hard-coded path under a developer's Downloads folder.
    private static final String PRODOS_DISK_PATH = TestDiskImages.blankProdos140k();
    
    // Memory locations for testing
    private static final int BOOT_VECTOR_LOCATION = 0x2000;
    private static final int TEST_MEMORY_START = 0x0800;
    private static final int ROM_START = 0xC000;
    
    private ByteArrayOutputStream testOutput;
    private PrintStream originalOut;
    private HeadlessTerminal terminal;
    
    @Override
    @Before
    public void commonSetup() {
        super.commonSetup();
        
        // Capture output for verification
        originalOut = System.out;
        testOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOutput));
        
        // Create terminal instance for testing
        terminal = new HeadlessTerminal();
    }
    
    @Override
    @After
    public void commonTeardown() {
        // Restore output
        System.setOut(originalOut);
        
        // Clean up terminal
        if (terminal != null) {
            terminal.stop();
        }
        
        super.commonTeardown();
    }
    
    /**
     * Test 1: Terminal mode emulator configuration
     * Ensures emulator is completely configured with memory, cards, etc.
     */
    @Test
    public void testTerminalModeEmulatorConfiguration() {
        LOG.info("Testing terminal mode emulator configuration");
        
        // Initialize emulator in terminal mode
        terminal.initializeEmulator();
        
        // Verify computer is properly configured
        Emulator.withComputer(computer -> {
            assertNotNull("Computer should be available", computer);
            
            // Test memory configuration
            RAM memory = computer.getMemory();
            assertNotNull("Memory should be configured", memory);
            assertTrue("Memory should be RAM128k", memory instanceof RAM128k);
            
            // Test CPU configuration  
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            assertNotNull("CPU should be configured", cpu);
            
            // Test motherboard configuration
            Motherboard motherboard = computer.getMotherboard();
            assertNotNull("Motherboard should be configured", motherboard);
            
            // Verify memory is writable in RAM areas and readable everywhere
            testMemoryConfiguration(memory);
            
            // Verify default cards are attached
            testDefaultCardsConfiguration(computer);
        });
    }
    
    /**
     * Helper method to test memory configuration
     */
    private void testMemoryConfiguration(RAM memory) {
        // Test writing to RAM areas (should work)
        int ramTestAddr = TEST_MEMORY_START;
        byte testValue = (byte) 0xAB;
        
        memory.write(ramTestAddr, testValue, false, false);
        byte readValue = memory.read(ramTestAddr, RAMEvent.TYPE.READ_DATA, false, false);
        assertEquals("Memory write/read should work in RAM areas", testValue, readValue);
        
        // Test different values to confirm memory is working
        for (int i = 0; i < 256; i++) {
            int addr = ramTestAddr + i;
            byte val = (byte) i;
            memory.write(addr, val, false, false);
            byte read = memory.read(addr, RAMEvent.TYPE.READ_DATA, false, false);
            assertEquals("Memory should retain written values at " + Integer.toHexString(addr), 
                        val, read);
        }
        
        // Test ROM areas - writes should not affect reads (ROM is read-only)
        int romTestAddr = ROM_START;
        byte originalRomValue = memory.read(romTestAddr, RAMEvent.TYPE.READ_DATA, false, false);
        
        // Try to write to ROM
        memory.write(romTestAddr, (byte) 0xFF, false, false);
        byte afterWriteValue = memory.read(romTestAddr, RAMEvent.TYPE.READ_DATA, false, false);
        
        // ROM should not be affected by writes (original value should be preserved)
        assertEquals("ROM should not be writable", originalRomValue, afterWriteValue);
    }
    
    /**
     * Helper method to test default cards configuration
     */
    private void testDefaultCardsConfiguration(Computer computer) {
        // Check that mass storage card is available in slot 7
        computer.getMemory().getCard(7).ifPresent(slot7Card -> {
            assertTrue("Slot 7 should have mass storage card", 
                      slot7Card instanceof CardMassStorage);
        });
        
        // Check that disk controller is available in slot 6
        computer.getMemory().getCard(6).ifPresent(slot6Card -> {
            assertTrue("Slot 6 should have disk controller", 
                      slot6Card instanceof CardDiskII);
        });
    }
    
    /**
     * Test 2: loadbin functionality
     * Verifies that loadbin command works correctly
     */
    @Test 
    public void testLoadBinFunctionality() throws Exception {
        LOG.info("Testing loadbin functionality");
        
        // Create test binary file
        byte[] testData = {0x01, 0x02, 0x03, 0x04, 0x05};
        File testFile = File.createTempFile("test_loadbin", ".bin");
        testFile.deleteOnExit();
        Files.write(testFile.toPath(), testData);
        
        // Initialize terminal and get MainMode
        terminal.initializeEmulator();
        MainMode mainMode = new MainMode(terminal);
        
        // Test loadbin command
        int loadAddress = 0x2000;
        String[] args = {testFile.getAbsolutePath(), "$" + Integer.toHexString(loadAddress)};
        
        // Clear memory first
        Emulator.withMemory(memory -> {
            for (int i = 0; i < testData.length; i++) {
                memory.write(loadAddress + i, (byte) 0, false, false);
            }
        });
        
        // Execute loadbin
        mainMode.processCommand("loadbin " + testFile.getAbsolutePath() + " $" + Integer.toHexString(loadAddress));
        
        // Verify data was loaded correctly
        Emulator.withMemory(memory -> {
            for (int i = 0; i < testData.length; i++) {
                byte loaded = memory.read(loadAddress + i, RAMEvent.TYPE.READ_DATA, false, false);
                assertEquals("Loaded data should match at offset " + i, testData[i], loaded);
            }
        });
        
        // Clean up
        testFile.delete();
    }
    
    // NOTE: a former "Test 3: testSaveBinFunctionality" was deleted. It asserted that
    // savebin prints "not yet implemented" -- savebin has since been implemented, and
    // RealTerminalFeatureTest.test1_SaveBinActuallyWorks asserts the correct behavior
    // (a load/save round-trip preserves bytes). Keeping a test that demands the feature
    // stay unimplemented is worse than having no test.

    // NOTE: a former "Test 5: testDeviceTickingDuringStep" was deleted. Its own comments
    // conceded it verified nothing: it built a Mockito TimedDevice that was never attached
    // to the motherboard and never had verify() called on it, then asserted only that some
    // output was produced -- which the neighbouring test already covers.
    // RealTerminalFeatureTest.test2_TickSteppingDrivesAllDevices does the real check by
    // attaching a counting Device and asserting an exact tick count.

    /**
     * Test 4: Motherboard tick stepping does not start the free-running timer.
     *
     * The command that advances the whole motherboard cascade is `tick` (it was named
     * `step` until commit 39881bb, which reassigned `step` to per-instruction CPU
     * stepping in MonitorMode). This test pins the invariant that matters for
     * automation: manual stepping must not leave a free-running worker thread behind.
     */
    @Test
    public void testTickModeBehavior() {
        LOG.info("Testing motherboard tick stepping behavior");

        terminal.initializeEmulator();
        MainMode mainMode = new MainMode(terminal);

        Emulator.withComputer(computer -> {
            Motherboard motherboard = computer.getMotherboard();

            // Ensure motherboard is suspended initially
            motherboard.suspend();
            assertFalse("Motherboard should not be running initially", motherboard.isRunning());

            // Execute tick command
            mainMode.processCommand("tick 1");

            // Verify motherboard is still suspended (not free-running)
            assertFalse("Motherboard should remain suspended after tick", motherboard.isRunning());

            String output = testOutput.toString();
            assertTrue("Output should indicate stepping occurred, got: " + output,
                      output.contains("Stepped"));

            // Test multiple ticks
            testOutput.reset();
            mainMode.processCommand("tick 5");

            // Verify motherboard is still suspended after multiple ticks
            assertFalse("Motherboard should remain suspended after multiple ticks", motherboard.isRunning());

            String multiStepOutput = testOutput.toString();
            assertTrue("Output should indicate multiple steps, got: " + multiStepOutput,
                      multiStepOutput.contains("Stepped 5"));
        });
    }

    /**
     * Test 6: Startup process with vararg inputs - Mass Storage Boot
     * Tests -s7.d1 disk_image_name.po functionality
     */
    @Test
    public void testStartupWithMassStorageDisk() throws Exception {
        LOG.info("Testing startup with mass storage disk assignment");

        // The image is generated in a temp dir, so this is an invariant of the
        // fixture rather than an environment dependency.
        assertTrue("Test disk image should exist at " + PRODOS_DISK_PATH,
                  Files.exists(Paths.get(PRODOS_DISK_PATH)));

        // Test arguments for slot 7 drive 1
        List<String> args = Arrays.asList("--terminal", "-s7.d1", PRODOS_DISK_PATH);

        // Reset emulator for clean test
        Emulator.abort();

        // Process command line arguments (simulates JaceLauncher)
        Emulator.getInstance(args.subList(1, args.size())); // Skip --terminal

        // Verify the -s7.d1 argument actually reached the slot 7 card and mounted
        // the image. The old version of this test only asserted the card object
        // was non-null inside an ifPresent, which could never fail.
        CardMassStorage slot7 = Emulator.withComputer(computer ->
            computer.getMemory().getCard(7)
                .filter(CardMassStorage.class::isInstance)
                .map(CardMassStorage.class::cast)
                .orElse(null), null);

        assertNotNull("Slot 7 should hold a CardMassStorage", slot7);
        assertNotNull("-s7.d1 should have mounted a disk in drive 1",
                      slot7.drive1.getCurrentDisk());
        assertEquals("Mounted image should report 280 blocks",
                     TestDiskImages.PRODOS_140K_LENGTH / 512,
                     slot7.drive1.getCurrentDisk().getSize());
    }

    /**
     * Test 7: Case-insensitive argument handling
     * Verifies that argument names and parameter values are case-insensitive
     */
    @Test
    public void testCaseInsensitiveArgumentHandling() {
        LOG.info("Testing case-insensitive argument handling");
        
        // Test various case combinations for slot arguments
        String[][] testCases = {
            {"-s7.d1", PRODOS_DISK_PATH},
            {"-S7.D1", PRODOS_DISK_PATH},
            {"-s7.D1", PRODOS_DISK_PATH}, 
            {"-S7.d1", PRODOS_DISK_PATH}
        };
        
        for (String[] testCase : testCases) {
            LOG.info("Testing case combination: " + testCase[0] + " " + testCase[1]);
            
            // Reset emulator for each test
            Emulator.abort();
            
            try {
                List<String> args = Arrays.asList(testCase[0], testCase[1]);
                Emulator.getInstance(args);
                
                // If no exception was thrown, the case combination was accepted
                LOG.info("Case combination accepted: " + testCase[0]);
                
            } catch (Exception e) {
                fail("Case-insensitive argument handling failed for: " + testCase[0] + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * Test 8: Disk assignment to slot 6 (Disk II controller)
     * Tests that s6.d1 assignment works for floppy disk boot
     */
    @Test
    public void testSlot6DiskAssignment() {
        LOG.info("Testing slot 6 disk assignment");
        
        // Reset emulator
        Emulator.abort();
        
        // Test slot 6 drive 1 assignment
        List<String> args = Arrays.asList("-s6.d1", PRODOS_DISK_PATH);
        Emulator.getInstance(args);
        
        // Verify disk controller is configured
        Emulator.withComputer(computer -> {
            computer.getMemory().getCard(6).ifPresent(slot6Card -> {
                if (slot6Card instanceof CardDiskII) {
                    CardDiskII diskController = (CardDiskII) slot6Card;
                    assertNotNull("Disk controller should be present in slot 6", diskController);
                    
                    // Note: Detailed disk insertion verification would require
                    // examining the CardDiskII internal state, which may not be
                    // easily accessible without additional test methods
                }
            });
        });
    }
    
    /**
     * Test 9: Boot sequence verification
     * Verifies that disk assignment results in successful boot sequence
     */
    @Test
    public void testBootSequenceVerification() {
        LOG.info("Testing boot sequence verification");
        
        // This test verifies that boot sequence can be detected by
        // monitoring memory changes and execution flow
        
        // Reset emulator and load disk
        Emulator.abort();
        List<String> args = Arrays.asList("-s7.d1", PRODOS_DISK_PATH);
        Emulator.getInstance(args);
        
        // Initialize terminal to trigger emulator startup
        terminal.initializeEmulator();
        
        Emulator.withComputer(computer -> {
            MOS65C02 cpu = (MOS65C02) computer.getCpu();
            RAM memory = computer.getMemory();
            
            // Check if we can detect boot activity
            int currentPC = cpu.getProgramCounter();
            LOG.info("Current PC: $" + Integer.toHexString(currentPC));
            
            // Check memory areas that would be modified during boot
            byte[] bootArea = new byte[16];
            for (int i = 0; i < bootArea.length; i++) {
                bootArea[i] = memory.read(BOOT_VECTOR_LOCATION + i, RAMEvent.TYPE.READ_DATA, false, false);
            }
            
            // Log boot area contents for analysis
            StringBuilder bootHex = new StringBuilder();
            for (byte b : bootArea) {
                bootHex.append(String.format("%02X ", b & 0xFF));
            }
            LOG.info("Boot area memory: " + bootHex.toString());
            
            // A successful boot would typically modify memory and set PC to boot code
            boolean likelyBooted = (currentPC != 0) && !isMemoryAllZeros(bootArea);
            
            if (likelyBooted) {
                LOG.info("Boot sequence appears to have executed");
            } else {
                LOG.info("Boot sequence may not have completed");
            }
        });
    }
    
    /**
     * Helper method to check if memory area is all zeros
     */
    private boolean isMemoryAllZeros(byte[] memory) {
        for (byte b : memory) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Test 10: Terminal mode integration test
     * Comprehensive test of terminal mode with all features
     */
    @Test
    public void testTerminalModeIntegration() {
        LOG.info("Running terminal mode integration test");
        
        // Reset and configure emulator with disk
        Emulator.abort();
        List<String> args = Arrays.asList("-s7.d1", PRODOS_DISK_PATH);
        Emulator.getInstance(args);
        
        // Initialize terminal
        terminal.initializeEmulator();
        MainMode mainMode = new MainMode(terminal);
        
        // Test command processing
        assertTrue("Help command should work", mainMode.processCommand("help"));
        assertTrue("Monitor command should work", mainMode.processCommand("monitor"));
        
        // Test step functionality
        testOutput.reset();
        assertTrue("Step command should work", mainMode.processCommand("step 1"));
        
        String stepOutput = testOutput.toString();
        assertTrue("Step should produce output", !stepOutput.trim().isEmpty());
        
        // Verify emulator is still in a good state
        Emulator.withComputer(computer -> {
            assertNotNull("Computer should still be available", computer);
            assertNotNull("CPU should still be available", computer.getCpu());
            assertNotNull("Memory should still be available", computer.getMemory());
            
            Motherboard motherboard = computer.getMotherboard();
            assertNotNull("Motherboard should be available", motherboard);
            
            // Motherboard should be suspended (not free-running) after step
            assertFalse("Motherboard should remain suspended", motherboard.isRunning());
        });
        
        LOG.info("Terminal mode integration test completed successfully");
    }
    
}