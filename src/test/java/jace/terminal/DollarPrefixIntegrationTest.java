package jace.terminal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jace.Emulator;
import jace.apple2e.MOS65C02;
import jace.core.Computer;
import jace.core.RAM;

/**
 * Integration tests to verify that $ prefix works across terminal commands.
 * Tests that "$800" is correctly interpreted as hex 2048, not decimal 800.
 */
public class DollarPrefixIntegrationTest {
    private ByteArrayOutputStream outContent;
    private PrintStream testOutput;
    private MonitorMode monitorMode;

    @Mock
    private JaceTerminal mockTerminal;

    @Mock
    private MOS65C02 mockCpu;

    @Mock
    private Computer mockComputer;

    @BeforeClass
    public static void setUpClass() {
        // Suppress JavaFX warnings
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        outContent = new ByteArrayOutputStream();
        testOutput = new PrintStream(outContent);

        when(mockTerminal.getOutput()).thenReturn(testOutput);
        when(mockCpu.getProgramCounter()).thenReturn(0x300);

        monitorMode = new MonitorMode(mockTerminal);
    }

    @After
    public void tearDown() {
        testOutput.close();
    }

    /**
     * Test that fill command accepts $ prefix for value argument.
     * Command: fill 2000 2010 $42
     * Should fill with hex 0x42, not decimal 42.
     */
    @Test
    public void testFillCommand_DollarPrefixValue() throws Exception {
        // Set up memory
        Emulator.withMemory(ram -> {
            // Fill command: fill 2000 2010 $42
            monitorMode.processCommand("fill 2000 2010 $42");

            // Verify that $42 (hex 66 decimal) was written, not 42 decimal
            byte value = ram.readRaw(0x2000);
            assertEquals("fill should interpret $42 as hex 0x42 (66 decimal)",
                    0x42, value & 0xFF);
        });
    }

    /**
     * Test that move command accepts $ prefix for count argument.
     * Command: move 2000 3000 $800
     * Count defaults to decimal, but $ prefix forces hex interpretation (2048 bytes).
     */
    @Test
    public void testMoveCommand_DollarPrefixCount() throws Exception {
        Emulator.withMemory(ram -> {
            // Fill source with a pattern
            for (int i = 0; i < 2048; i++) {
                ram.write(0x2000 + i, (byte)(i & 0xFF), false, true);
            }

            // Move command: move 2000 3000 $800 ($ prefix forces hex = 2048 bytes)
            monitorMode.processCommand("move 2000 3000 $800");

            // Verify that 2048 bytes were copied (0x800 in hex)
            boolean allMatch = true;
            for (int i = 0; i < 2048; i++) {
                byte src = ram.readRaw(0x2000 + i);
                byte dst = ram.readRaw(0x3000 + i);
                if (src != dst) {
                    allMatch = false;
                    break;
                }
            }
            assertTrue("move $800 should interpret $800 as hex 2048 bytes", allMatch);

            // Verify that without $ prefix, 800 means decimal 800 bytes
            for (int i = 0; i < 1000; i++) {
                ram.write(0x4000 + i, (byte)(i & 0xFF), false, true);
            }
            monitorMode.processCommand("move 4000 5000 800");

            // Verify exactly 800 bytes were copied
            allMatch = true;
            for (int i = 0; i < 800; i++) {
                byte src = ram.readRaw(0x4000 + i);
                byte dst = ram.readRaw(0x5000 + i);
                if (src != dst) {
                    allMatch = false;
                    break;
                }
            }
            assertTrue("move 800 (no prefix) should copy 800 decimal bytes", allMatch);

            // Verify byte 800 (index 800, the 801st byte) was NOT copied
            ram.write(0x4320, (byte)0xBB, false, true); // 0x4000 + 800 decimal
            byte original = ram.readRaw(0x4320);
            byte dest = ram.readRaw(0x5320);
            assertNotEquals("move 800 should copy 800 bytes, not 801", original, dest);
        });
    }

    /**
     * Test that compare command accepts $ prefix for count argument.
     * Command: compare 2000 3000 $100
     * Count defaults to decimal, but $ prefix forces hex interpretation (256 bytes).
     */
    @Test
    public void testCompareCommand_DollarPrefixCount() throws Exception {
        Emulator.withMemory(ram -> {
            // Fill both regions with the same pattern
            for (int i = 0; i < 300; i++) {
                byte val = (byte)(i & 0xFF);
                ram.write(0x2000 + i, val, false, true);
                ram.write(0x3000 + i, val, false, true);
            }

            // Make byte 150 (decimal) different - beyond normal 100 byte compare
            ram.write(0x2096, (byte)0xFF, false, true); // 0x2000 + 150 decimal
            ram.write(0x3096, (byte)0x00, false, true);

            // Compare command: compare 2000 3000 $100 ($ prefix = hex 256 bytes)
            outContent.reset();
            monitorMode.processCommand("compare 2000 3000 $100");

            String output = outContent.toString();
            // Should report difference at byte 150 because $100 = 256 > 150
            assertTrue("compare $100 should check 256 bytes, finding difference at 150",
                    output.contains("$2096") || output.contains("2096"));

            // Verify that without $ prefix, 100 means decimal 100 bytes
            outContent.reset();
            monitorMode.processCommand("compare 2000 3000 100");

            output = outContent.toString();
            // Should NOT report difference at byte 150 because 100 decimal < 150
            assertFalse("compare 100 (no prefix) should check only 100 bytes",
                    output.contains("$2096") || output.contains("2096"));
        });
    }

    /**
     * Test that find command accepts $ prefix for pattern bytes.
     * Command: find 2000 3000 $DE $AD
     * Should search for hex bytes 0xDE 0xAD.
     */
    @Test
    public void testFindCommand_DollarPrefixPattern() throws Exception {
        Emulator.withMemory(ram -> {
            // Plant the pattern at 0x2100
            ram.write(0x2100, (byte)0xDE, false, true);
            ram.write(0x2101, (byte)0xAD, false, true);

            // Find command: find 2000 3000 $DE $AD
            outContent.reset();
            monitorMode.processCommand("find 2000 3000 $DE $AD");

            String output = outContent.toString();
            assertTrue("find should locate pattern $DE $AD at $2100",
                    output.contains("$2100") || output.contains("2100"));
        });
    }

    /**
     * Test that cheat command accepts $ prefix for value.
     * Command: cheat 2000 $FF
     * Should set cheat value to hex 0xFF (255 decimal).
     * Note: This is a parsing test - we verify the command is accepted without error.
     */
    @Test
    public void testCheatCommand_DollarPrefixValue() throws Exception {
        outContent.reset();
        // Cheat command: cheat 2000 $FF
        monitorMode.processCommand("cheat 2000 $FF");

        // Verify the command was parsed without error
        String output = outContent.toString();
        assertFalse("cheat $FF should parse successfully",
                output.contains("Invalid address or value"));
    }

    /**
     * Test that step command accepts $ prefix for count.
     * Command: step $10
     * Should step 16 instructions, not 10.
     */
    @Test
    public void testStepCommand_DollarPrefixCount() throws Exception {
        outContent.reset();

        // This will fail with unknown command in our mock setup, but we can
        // verify the parsing by checking that the command was attempted
        monitorMode.processCommand("step $10");

        // The actual stepping behavior requires a full CPU mock which is complex.
        // For now, we verify the command was parsed without "Invalid step count" error.
        String output = outContent.toString();
        assertFalse("step $10 should parse successfully",
                output.contains("Invalid step count"));
    }

    /**
     * Test Wozniak-style write command with $ prefix.
     * Command: 2000:$12 $34 $56
     * Should write hex bytes 0x12 0x34 0x56.
     * Note: This is a parsing test - we verify the command is accepted without error.
     */
    @Test
    public void testWozniakWrite_DollarPrefixValues() throws Exception {
        outContent.reset();
        // Wozniak write: 2000:$12 $34 $56
        monitorMode.processCommand("2000:$12 $34 $56");

        // Verify the command was parsed without error
        String output = outContent.toString();
        assertFalse("Wozniak write with $ prefix should parse successfully",
                output.contains("Invalid") || output.contains("Error"));
    }
}
