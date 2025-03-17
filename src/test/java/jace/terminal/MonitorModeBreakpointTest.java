package jace.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractJaceTest;
import jace.TestUtils;
import jace.core.Debugger;

/**
 * Test class to validate the MonitorMode's breakpoint functionality.
 * This test verifies that breakpoints are correctly registered and added to the debugger.
 */
public class MonitorModeBreakpointTest extends AbstractJaceTest {
    
    // Components we'll need for testing
    private MonitorMode monitorMode;
    private JaceTerminal terminal;
    private PrintStream printStream;
    private Debugger debugger;
    
    @Before
    @Override
    public void commonSetup() {
        // First call the parent setup to initialize the emulator environment
        super.commonSetup();
        
        // Initialize test environment with all JavaFX components disabled and reliable CPU test setup
        TestUtils.setupForCpuTest();
        
        // Set up output capture with a mock PrintStream
        printStream = mock(PrintStream.class);
        
        // Create a mock terminal
        terminal = mock(JaceTerminal.class);
        when(terminal.getOutput()).thenReturn(printStream);
        
        // Create a real debugger
        debugger = new Debugger() {
            @Override
            public void updateStatus() {
                // No-op for testing
            }
        };
        
        // Create the MonitorMode instance
        monitorMode = new MonitorMode(terminal);
        
        // Use reflection to set the debugger in MonitorMode
        try {
            java.lang.reflect.Field debuggerField = MonitorMode.class.getDeclaredField("debugger");
            debuggerField.setAccessible(true);
            debuggerField.set(monitorMode, debugger);
        } catch (Exception e) {
            fail("Failed to set debugger: " + e.getMessage());
        }
    }
    
    /**
     * Test that a breakpoint is correctly added to the debugger's breakpoint list
     * and that the debugger is activated.
     */
    @Test
    public void testAddBreakpoint() {
        // Verify initial state
        assertTrue("Debugger should start with empty breakpoints", debugger.getBreakpoints().isEmpty());
        assertFalse("Debugger should not be active initially", debugger.isActive());
        
        // Add a breakpoint using reflection
        try {
            java.lang.reflect.Method addBreakpointMethod = 
                MonitorMode.class.getDeclaredMethod("addBreakpoint", int.class);
            addBreakpointMethod.setAccessible(true);
            addBreakpointMethod.invoke(monitorMode, 0xE000);
        } catch (Exception e) {
            fail("Failed to add breakpoint: " + e.getMessage());
        }
        
        // Verify breakpoint was added
        assertFalse("Debugger breakpoints should not be empty after adding", debugger.getBreakpoints().isEmpty());
        assertTrue("Breakpoint should be in the list", debugger.getBreakpoints().contains(0xE000));
        assertTrue("Debugger should be active after adding breakpoint", debugger.isActive());
    }
    
    /**
     * Test that machine code execution can be started from a specified address.
     * Using Mockito to verify the output.
     */
    @Test
    public void testExecuteCode() {
        // Reset any previous interactions with the mock
        reset(printStream);
        
        // Execute code from $0300 using reflection
        try {
            java.lang.reflect.Method executeCodeMethod = 
                MonitorMode.class.getDeclaredMethod("executeCode", int.class);
            executeCodeMethod.setAccessible(true);
            executeCodeMethod.invoke(monitorMode, 0x0300);
        } catch (Exception e) {
            fail("Failed to execute code: " + e.getMessage());
        }
        
        // Verify that the correct message was printed to the output
        verify(printStream).println(contains("Execution started at $300"));
    }
} 