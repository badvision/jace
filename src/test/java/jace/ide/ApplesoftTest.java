/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jace.ide;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static jace.TestUtils.initComputer;
import jace.applesoft.ApplesoftProgram;

/**
 *
 * @author blurry
 */
public class ApplesoftTest {
    
    public ApplesoftTest() {
    }
    
    static Byte[] lemonadeStandBinary;
    
    @BeforeClass
    public static void setUpClass() throws URISyntaxException, IOException {
        initComputer();
        byte[] lemonadeStand = readBinary("/jace/lemonade_stand.bin");
        lemonadeStandBinary = ApplesoftProgram.toObjects(lemonadeStand);
    }
    
    public static byte[] readBinary(String path) throws IOException, URISyntaxException {
        Path resource = Paths.get(ApplesoftTest.class.getResource(path).toURI());
        return Files.readAllBytes(resource);
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    @Test
    public void deserializeBinaryTest() {
        ApplesoftProgram program = ApplesoftProgram.fromBinary(Arrays.asList(lemonadeStandBinary), 0x0801);
        assertNotNull(program);
        assertNotSame("", program.toString());
        assertEquals("Lemonade stand has 380 lines", 380, program.getLength());
        assertTrue("Should have last line 31114", program.toString().contains("31114 "));
    }
    
    @Test
    public void roundTripStringComparisonTest() {
        ApplesoftProgram program = ApplesoftProgram.fromBinary(Arrays.asList(lemonadeStandBinary), 0x0801);
        String serialized = program.toString();
        ApplesoftProgram deserialized = ApplesoftProgram.fromString(serialized);
        String[] serializedLines = serialized.split("\\n");
        String[] researializedLines = deserialized.toString().split("\\n");
        assertEquals("Lemonade stand has 380 lines", 380, deserialized.getLength());
        assertArrayEquals("Program listing should be not change if re-keyed in as printed", serializedLines, researializedLines);
    }
}
