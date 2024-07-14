package jace.applesoft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class ApplesoftTest {

    @Test
    public void fromStringTest() {
        String programSource = "10 PRINT \"Hello, World!\"\n20 PRINT \"Goodbye!\"";
        ApplesoftProgram program = ApplesoftProgram.fromString(programSource);
        assertNotNull(program);
        assertEquals(2, program.lines.size());
        Line line1 = program.lines.get(0);
        assertEquals(10, line1.getNumber());
        assertEquals(1, line1.getCommands().size());
        Command command1 = line1.getCommands().get(0);
        assertEquals(0xBA, command1.parts.get(0).getByte() & 0x0ff);
        String match = "";
        for (int idx=1; idx < command1.parts.size(); idx++) {
            match += command1.parts.get(idx).toString();
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
}