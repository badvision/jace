package jace.hardware;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import java.net.Socket;
import java.io.IOException;

public class CardSSCInitializationTest {
    
    private CardSSC ssc;
    
    @Before
    public void setUp() {
        // Clean setup for each test
    }
    
    @Test
    public void testSSCStartsListeningImmediatelyAfterResume() throws InterruptedException {
        System.out.println("Testing SSC immediate port listening after resume...");
        
        // Create SSC card and properly initialize it (simulating attach->resume)
        ssc = new CardSSC();
        ssc.setSlot(2); // Now handles headless mode gracefully
        
        // This should start the network listener
        ssc.resume();
        
        // Give the network listener thread a moment to start
        Thread.sleep(1000);
        
        // Test if port 1977 is listening by attempting to connect
        boolean portIsListening = false;
        try (Socket testSocket = new Socket("localhost", 1977)) {
            portIsListening = testSocket.isConnected();
            System.out.println("✅ Successfully connected to SSC port 1977");
        } catch (IOException e) {
            System.out.println("❌ Failed to connect to SSC port 1977: " + e.getMessage());
        }
        
        // Clean up
        if (ssc != null) {
            ssc.suspend();
        }
        
        assertTrue("SSC should start listening on port 1977 immediately after resume", portIsListening);
    }
    
    @Test
    public void testSSCNetworkFunctionality() throws InterruptedException, IOException {
        System.out.println("Testing SSC network functionality...");
        
        // Create SSC card and start it
        ssc = new CardSSC();
        ssc.setSlot(2); // Now handles headless mode gracefully  
        ssc.resume();
        
        // Give the network listener thread a moment to start
        Thread.sleep(500);
        
        // Test network connection
        try (Socket testSocket = new Socket("localhost", 1977)) {
            assertTrue("Should be able to connect to SSC port", testSocket.isConnected());
            
            // Test sending data to the SSC
            testSocket.getOutputStream().write("Hello SSC!".getBytes());
            testSocket.getOutputStream().flush();
            
            // Give SSC a moment to process the input
            Thread.sleep(100);
            
            System.out.println("✅ Successfully sent data to SSC");
            
        } catch (IOException e) {
            fail("Failed to test SSC network functionality: " + e.getMessage());
        } finally {
            // Clean up
            if (ssc != null) {
                ssc.suspend();
            }
        }
    }
}