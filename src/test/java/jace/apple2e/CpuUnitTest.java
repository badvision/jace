package jace.apple2e;
import static jace.TestUtils.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import jace.Emulator;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.SoundMixer;
import jace.core.RAMEvent.TYPE;

public class CpuUnitTest {
    // This will loop through each of the files in 65x02_unit_tests/wdc65c02 and run the tests in each file
    // The goal is to produce an output report that shows the number of tests that passed and failed
    // The output should be reported in a format compatible with junit but also capture multiple potential failures, not just the first faliure

    static Computer computer;
    static MOS65C02 cpu;
    static RAM ram;

    public static enum Operation {
        read, write
    }
    TypeToken<Collection<TestRecord>> testCollectionType = new TypeToken<Collection<TestRecord>>(){};
    record TestResult(String source, String testName, boolean passed, String message) {}
    // Note cycles are a mix of int and string so the parser doesn't like to serialize that into well-formed objects
    record TestRecord(String name, @SerializedName("initial") MachineState initialState, @SerializedName("final") MachineState finalState, List<List<String>> cycles) {}
    record MachineState(int pc, int s, int a, int x, int y, byte p, List<int[]> ram) {}

    public static boolean BREAK_ON_FAIL = false;

    @BeforeClass
    public static void setUp() {
        initComputer();
        SoundMixer.MUTE = true;
        computer = Emulator.withComputer(c->c, null);
        cpu = (MOS65C02) computer.getCpu();
        ram = initFakeRam();
    }

    @Before
    public void resetState() {
        // Reinit memory on each test to avoid weird side effects
        cpu.reset();
        cpu.resume();
    }

    // Make a list of tests to skip
    public static String[] SKIP_TESTS = new String[] {
        "cb", "db"
    };

    public static String TEST_FOLDER = "/65x02_unit_tests/wdc65c02/v1";
    @Test
    public void testAll() throws IOException, URISyntaxException {
        // Read all the files in the directory
        // For each file, read the contents and run the tests

        List<TestResult> results = new ArrayList<>();
        // Path testFolder = Paths.get(getClass().getResource("/65x02_unit_tests_wdc65c02/v1").toURI());
        for (String path : getSorted(getResourceListing(TEST_FOLDER))) {
            boolean skip = false;
            for (String skipPattern : SKIP_TESTS) {
                if (path.contains(skipPattern)) {
                    skip = true;
                }
            }
            if (skip) {
                continue;
            }
            
            String file = TEST_FOLDER + "/" + path;
            results.addAll(runTest(file));
            if (BREAK_ON_FAIL && results.stream().anyMatch(r->!r.passed())) {
                break;
            }
        }
        // Report results
        int passed = 0;
        Set<String> failedTests = new HashSet<>();
        for (TestResult result : results) {
            if (result.passed()) {
                passed++;
            } else {
                failedTests.add(result.testName());
                if (failedTests.size() < 20) {
                    System.err.println(result.source() + ";" + result.testName() + " " + "FAILED" + ": " + result.message());
                }
            }
        }
        System.err.println("Passed: " + passed + " Failed: " + failedTests.size());
        if (failedTests.size() > 0) {
            throw new RuntimeException("One or more tests failed, see log for details");
        }
    }

    private String getStatusBits(int status) {
        StringBuilder sb = new StringBuilder();
        sb.append((status & 0x80) != 0 ? "N" : "-");
        sb.append((status & 0x40) != 0 ? "V" : "-");
        sb.append((status & 0x20) != 0 ? "-" : "?");
        sb.append((status & 0x10) != 0 ? "B" : "-");
        sb.append((status & 0x08) != 0 ? "D" : "-");
        sb.append((status & 0x04) != 0 ? "I" : "-");
        sb.append((status & 0x02) != 0 ? "Z" : "-");
        sb.append((status & 0x01) != 0 ? "C" : "-");
        return sb.toString();
    }

    private Collection<? extends TestResult> runTest(String file) {
        Gson gson = new Gson();
        List<TestResult> results = new ArrayList<>();
        // Read the file which is a JSON file and parse it.
        try {
            // Given the JSON data in source, parse it to a usable list of tests
            // For each test, run the test
            Collection<TestRecord> tests = gson.fromJson(new InputStreamReader(getClass().getResourceAsStream(file)), testCollectionType.getType());
            for (TestRecord t : tests) {
                String name = t.name() + "_%d cycles_%04X->%04X".formatted(t.cycles().size(), t.initialState().pc(), t.finalState().pc());

                // Set up the initial state by setting CPU registers and RAM
                cpu.reset();
                cpu.setProgramCounter(t.initialState().pc());
                cpu.STACK = t.initialState().s();
                cpu.A = t.initialState().a();
                cpu.X = t.initialState().x();
                cpu.Y = t.initialState().y();
                cpu.setStatus(t.initialState().p(), true);
                // Set up the memory values
                for (int[] mem : t.initialState().ram()) {
                    ram.write(mem[0], (byte) mem[1], false, false);
                }
                // Step the CPU for each cycle
                for (List<String> c : t.cycles()) {
                    if (BREAK_ON_FAIL) {
                        cpu.traceLength = 100;
                        cpu.setTraceEnabled(true);
                    }
                    cpu.doTick();
                    // TODO: Check the memory accesses
                }
                // Check the final state
                boolean passed = true;
                if (cpu.getProgramCounter() != t.finalState().pc()) {
                    results.add(new TestResult(file.toString(), name, false, "Program Counter mismatch, expected %04X but got %04X".formatted(t.finalState().pc(), cpu.getProgramCounter())));
                    passed = false;
                } 
                if (cpu.STACK != t.finalState().s()) {
                    results.add(new TestResult(file.toString(), name, false, "Stack Pointer mismatch, expected %02X but got %02X".formatted(t.finalState().s(), cpu.STACK)));
                    passed = false;
                }
                if (cpu.A != t.finalState().a()) {
                    results.add(new TestResult(file.toString(), name, false, "Accumulator mismatch, expected %02X but got %02X".formatted(t.finalState().a(), cpu.A)));
                    passed = false;
                }
                if (cpu.X != t.finalState().x()) {
                    results.add(new TestResult(file.toString(), name, false, "X Register mismatch, expected %02X but got %02X".formatted(t.finalState().x(), cpu.X)));
                    passed = false;
                }
                if (cpu.Y != t.finalState().y()) {
                    results.add(new TestResult(file.toString(), name, false, "Y Register mismatch, expected %02X but got %02X".formatted(t.finalState().y(), cpu.Y)));
                    passed = false;
                }
                if (cpu.getStatus() != t.finalState().p()) {
                    results.add(new TestResult(file.toString(), name, false, "Status Register mismatch, expected %s but got %s".formatted(getStatusBits(t.finalState().p()),getStatusBits(cpu.getStatus()))));
                    passed = false;
                }
                // Check the memory values
                for (int[] mem : t.finalState().ram()) {
                    byte value = ram.read(mem[0], TYPE.EXECUTE, false, false);
                    if (value != (byte) mem[1]) {
                        results.add(new TestResult(file.toString(), name, false, "Memory mismatch at address %04X, expected %02X but got %02X".formatted(mem[0], mem[1], value)));
                        // results.add(new TestResult(file.toString(), name, false, "Memory mismatch, expected %s but got %s".formatted(getStatusBits(mem[1]),getStatusBits(value))));
                        passed = false;
                    }
                }
                if (passed) {
                    results.add(new TestResult(file.toString(), t.name(), true, "All checks passed"));
                } else if (BREAK_ON_FAIL) {
                    break;
                }
                // Clear out the memory for the next test
                for (int[] mem : t.finalState().ram()) {
                    ram.write(mem[0], (byte) 0, false, false);
                }                
            }
        } catch (Exception e) {
            results.add(new TestResult(file.toString(), "<INIT>", false, "Unable to read file: " + e.getMessage()));
            return results;
        }

        return results;
    }

private String[] getSorted(String[] values) {
    Set<String> set = new TreeSet<>();
    for (String value : values) {
        set.add(value);
    }
    return set.toArray(new String[0]);
}

 private String[] getResourceListing(String path) throws URISyntaxException, IOException {
      URL dirURL = getClass().getResource(path);
      if (dirURL != null && dirURL.getProtocol().equals("file")) {
        /* A file path: easy enough */
        return new File(dirURL.toURI()).list();
      } 

      if (dirURL == null) {
        /* 
         * In case of a jar file, we can't actually find a directory.
         * Have to assume the same jar as clazz.
         */
        String me = getClass().getName().replace(".", "/")+".class";
        dirURL = getClass().getClassLoader().getResource(me);
      }
      
      if (dirURL.getProtocol().equals("jar")) {
        /* A JAR path */
        String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); //strip out only the JAR file
        try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
            Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
            Set<String> result = new HashSet<String>(); //avoid duplicates in case it is a subdirectory
            while(entries.hasMoreElements()) {
              String name = entries.nextElement().getName();
              if (name.startsWith(path)) { //filter according to the path
                String entry = name.substring(path.length());
                int checkSubdir = entry.indexOf("/");
                if (checkSubdir >= 0) {
                  // if it is a subdirectory, we just return the directory name
                  entry = entry.substring(0, checkSubdir);
                }
                result.add(entry);
              }
            }
            return result.toArray(new String[result.size()]);
        }
      }
      throw new IOException("Unable to locate resource folder for path: " + path);
    }    
}
 