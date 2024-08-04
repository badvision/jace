package jace.applesoft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.TestUtils;
import jace.apple2e.MOS65C02;
import jace.apple2e.RAM128k;
import jace.core.Computer;
import jace.core.SoundMixer;
import jace.ide.Program;
import jace.ide.Program.DocumentType;

public class ApplesoftTest {

    static Computer computer;
    public static MOS65C02 cpu;
    static RAM128k ram;

    @BeforeClass
    public static void setupClass() {
        TestUtils.initComputer();
        SoundMixer.MUTE = true;
        computer = Emulator.withComputer(c->c, null);
        cpu = (MOS65C02) computer.getCpu();
        ram = (RAM128k) computer.getMemory();
    }    

    @Test
    public void fromStringTest() {
        String programSource = "10 PRINT \"Hello, World!\"\n\n20 PRINT \"Goodbye!\"\n";
        ApplesoftHandler handler = new ApplesoftHandler();
        // We want to test as much as we can but right now it's heavily integrated with the UI
        Program program = new Program(DocumentType.applesoft, Collections.emptyMap()) {
            String value;
            @Override
            public String getValue() {
                return value;
            }
            @Override
            public void setValue(String value) {
                this.value = value;
            }
        };
        program.setValue(programSource);
        var compileResult = handler.compile(program);
        assertNotNull(compileResult.getCompiledAsset());
        assertTrue(compileResult.isSuccessful());
        assertTrue(compileResult.getErrors().isEmpty());
        assertTrue(compileResult.getWarnings().isEmpty());
        assertTrue(compileResult.getOtherMessages().isEmpty());
        assertTrue(compileResult.getRawOutput().isEmpty());
        assertEquals(2, compileResult.getCompiledAsset().lines.size());
        Line line1 = compileResult.getCompiledAsset().lines.get(0);
        assertEquals(10, line1.getNumber());
        assertEquals(1, line1.getCommands().size());
        Command command1 = line1.getCommands().get(0);
        assertEquals(0xBA, command1.parts.get(0).getByte() & 0x0ff);
        String match = "";
        for (int idx=1; idx < command1.parts.size(); idx++) {
            match += command1.parts.get(idx).toString();
        }
        assertEquals("\"Hello, World!\"", match);
        // Does nothing but test coverage is test coverage
        handler.clean(null);

        // Now let's try to execute and see if we can read the program back
        handler.execute(compileResult);

        ApplesoftProgram program2 = Emulator.withComputer(c->ApplesoftProgram.fromMemory(c.getMemory()), null);
        assertEquals(2, program2.getLength());
        Line line2 = program2.lines.get(0);
        assertEquals(10, line2.getNumber());
        assertEquals(1, line2.getCommands().size());
        Command command2 = line2.getCommands().get(0);
        assertEquals(0xBA, command2.parts.get(0).getByte() & 0x0ff);
        match = "";
        for (int idx=1; idx < command2.parts.size(); idx++) {
            match += command2.parts.get(idx).toString();
        }
        assertEquals("\"Hello, World!\"", match);
    }

    @Test
    public void toStringTest() {
        Line line1 = Line.fromString("10 print \"Hello, world!\"");
        Line line2 = Line.fromString("20 print \"Goodbye!\"");
        ApplesoftProgram program = new ApplesoftProgram();
        program.lines.add(line1);
        program.lines.add(line2);
        String programSource = program.toString();
        assertEquals("10 PRINT \"Hello, world!\"\n20 PRINT \"Goodbye!\"\n", programSource);
    }

    @Test
    public void relocateVariablesTest() {
        ApplesoftProgram program = new ApplesoftProgram();
        Line line1 = Line.fromString("10 print \"Hello, world!\"");
        Line line2 = Line.fromString("20 print \"Goodbye!\"");
        program.lines.add(line1);
        program.lines.add(line2);
        program.relocateVariables(0x6000);
        // We need better assertions here but for now we just want to make sure it doesn't crash
    }
}