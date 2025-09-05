/*
 * Copyright 2023 org.badvision.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jace;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.apple2e.RAM128k;
import jace.core.CPU;
import jace.core.Computer;
import jace.core.Device;
import jace.core.PagedMemory;
import jace.core.RAM;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import jace.core.Video;
import jace.core.VideoWriter;
import jace.ide.HeadlessProgram;
import jace.ide.Program;
import javafx.scene.image.WritableImage;

/**
 * Utility methods for test cases.
 * Contains methods for setting up the test environment, creating mock components,
 * and managing the emulator state during tests.
 *
 * @author brobert
 */
public class TestUtils {
    private static final Logger LOG = Logger.getLogger(TestUtils.class.getName());
    
    private static final String VERBOSE_PROPERTY = "jace.test.verbose";
    private static final boolean VERBOSE_MODE = Boolean.getBoolean(VERBOSE_PROPERTY);
    
    private TestUtils() {
        // Utility class has no constructor
    }

    public static void initComputer() {
        Utility.setHeadlessMode(true);
        Emulator.withComputer(Computer::reconfigure);
    }

    public static class FakeRAM extends RAM128k {
        PagedMemory fakeMemory = new PagedMemory(0x0, PagedMemory.Type.RAM);
        byte[] memory = new byte[65536];
        public byte read(int address, TYPE eventType, boolean triggerEvent, boolean requireSyncronization) {
            return memory[address & 0x0ffff];
        }

        public byte readRaw(int address) {
            return memory[address & 0x0ffff];
        }

        public void write(int address, byte value, boolean triggerEvent, boolean requireSyncronization) {
            memory[address & 0x0ffff] = value;
        }

        @Override
        public String getName() {
            return "Fake ram";
        }

        @Override
        public String getShortName() {
            return "ram";
        }

        @Override
        public void reconfigure() {
            // Nothing needed
        }

        @Override
        public void configureActiveMemory() {
            // Nothing needed
        }

        @Override
        protected void loadRom(String path) throws IOException {
            // Nothing needed
        }

        @Override
        public void attach() {
            // Nothing needed
        }

        @Override
        public void performExtendedCommand(int i) {
            // Nothing needed
        }

        @Override
        public String getState() {
            return "";
        }

        @Override
        public void resetState() {
        }
        @Override
        public PagedMemory getAuxVideoMemory() {
            return fakeMemory;
        }
        @Override
        public PagedMemory getAuxMemory() {
            return fakeMemory;
        }
        @Override
        public PagedMemory getAuxLanguageCard() {
            return fakeMemory;
        }
        @Override
        public PagedMemory getAuxLanguageCard2() {
            return fakeMemory;
        }

        @Override
        public void dumpMemoryMap() {
            // No-op for fake RAM used in tests
        }
    }

    public static void clearFakeRam(RAM ram) {
        if (ram instanceof FakeRAM fakeRam) {
            Arrays.fill(fakeRam.memory, (byte) 0);
        }
    }

    public static RAM initFakeRam() {
        FakeRAM ram = new FakeRAM();
        
        // Initialize memory to zero
        Arrays.fill(ram.memory, (byte) 0);
        
        // Set up the CPU to use this memory
        Emulator.withComputer(c -> {
            c.setMemory(ram);
            c.getCpu().setMemory(ram);
        });
        
        return ram;
    }

    public static void assemble(String code, int addr) throws Exception {
        runAssemblyCode(code, addr, 0);
    }
    
    public static void runAssemblyCode(String code, int ticks) throws Exception {
        runAssemblyCode(code, 0x6000, ticks);
    }
    
    public static void runAssemblyCode(String code, int addr, int ticks) throws Exception {
        CPU cpu = Emulator.withComputer(c->c.getCpu(), null);
        HeadlessProgram program = new HeadlessProgram(Program.DocumentType.assembly);
        program.setValue("*=$"+Integer.toHexString(addr)+"\n "+code+"\n NOP\n RTS");
        program.execute();
        if (ticks > 0) {                
            cpu.resume();
            for (int i=0; i < ticks; i++) {
                cpu.doTick();
            }
            cpu.suspend();
        }
    }

    public static Device createSimpleDevice(Runnable r, String name) {
        return new Device() {
            @Override
            public void tick() {
                r.run();
            }
            
            @Override
            public String getShortName() {
                return name;
            }
            
            @Override
            public void reconfigure() {
            }
            
            @Override
            protected String getDeviceName() {
                return name;
            }
        };
    }

    /**
     * Base class for all mock video implementations.
     * Contains common methods that are reused across all mock video types.
     */
    public static abstract class BaseMockVideo extends jace.core.Video {
        private byte floatingBus = 0;
        
        public BaseMockVideo() {
            super();
        }
        
        @Override
        public void doPostDraw() {
            // No-op for testing
        }
        
        @Override
        public void vblankStart() {
            // No-op for testing
        }
        
        @Override
        public void vblankEnd() {
            // No-op for testing
        }
        
        @Override
        public void hblankStart(javafx.scene.image.WritableImage screen, int y, boolean isDirty) {
            // No-op for testing - ignore screen parameter for headless operation
        }
        
        @Override
        public byte getFloatingBus() {
            return floatingBus;
        }
        
        public void setFloatingBus(byte value) {
            this.floatingBus = value;
        }
        
        @Override
        public void tick() {
            // No-op for testing - prevent any actual video processing
        }
        
        @Override
        public void configureVideoMode() {
            // No-op for testing, subclasses can override if needed
        }
        
        @Override
        public WritableImage getFrameBuffer() {
            // Return a minimal frame buffer for headless tests
            return new WritableImage(1, 1);
        }
        
        protected void showBW(WritableImage screen, int x, int y, int dhgrWord) {
            // No-op for testing
        }
        
        protected void showDhgr(WritableImage screen, int x, int y, int dhgrWord) {
            // No-op for testing
        }
        
        protected void displayLores(WritableImage screen, int xOffset, int y, int rowAddress) {
            // No-op for testing
        }
        
        protected void displayDoubleLores(WritableImage screen, int xOffset, int y, int rowAddress) {
            // No-op for testing
        }
    }

    /**
     * A simple mock Video implementation for testing.
     * This prevents NPEs when accessing the video component or floating bus.
     */
    public static class MockVideo extends Video {
        public MockVideoWriter mockWriter;
        
        public MockVideo() {
            super();
            mockWriter = new MockVideoWriter();
            setCurrentWriter(mockWriter);
        }
        
        @Override
        public void attach() {
            // Do nothing in the mock implementation
        }
        
        @Override
        public void detach() {
            // Do nothing in the mock implementation
        }
        
        @Override
        public byte getFloatingBus() {
            // Return a fixed value for predictable tests
            return (byte) 0xEA;  // NOP instruction for predictable tests
        }
        
        @Override
        public void vblankEnd() {
            // Do nothing
        }
        
        @Override
        public void hblankStart(WritableImage screen, int y, boolean isDirty) {
            // Do nothing
        }
        
        @Override
        public void configureVideoMode() {
            // Do nothing
        }
        
        @Override
        public void doPostDraw() {
            // Do nothing
        }
        
        @Override
        public String getDeviceName() {
            return "MockVideo";
        }
    }
    
    /**
     * A mock implementation of VideoWriter for tests
     */
    public static class MockVideoWriter extends VideoWriter {
        @Override
        public void displayByte(WritableImage screen, int xOffset, int y, int yTextOffset, int yGraphicsOffset) {
            // Do nothing in mock implementation
        }
        
        @Override
        public int getYOffset(int y) {
            // Return a reasonable default offset
            return 0x0400; // Text page 1 base address
        }
    }
    
    /**
     * A specialized mock for NTSC video tests
     */
    public static class MockVideoNTSC extends jace.apple2e.VideoNTSC {
        @Override
        public void doPostDraw() {
            // Do nothing in mock implementation
        }
        
        @Override
        public void vblankEnd() {
            // Do nothing in mock implementation
        }
        
        @Override
        public void hblankStart(WritableImage screen, int y, boolean isDirty) {
            // Do nothing in mock implementation
        }
        
        @Override
        public byte getFloatingBus() {
            // Return a fixed value for predictable tests
            return (byte) 0xEA;  // NOP instruction for predictable tests
        }
        
        @Override
        public String getDeviceName() {
            return "MockVideoNTSC";
        }
    }
    
    /**
     * A specialized mock for DHGR video tests
     */
    public static class MockVideoDHGR extends jace.apple2e.VideoDHGR {
        @Override
        public void doPostDraw() {
            // Do nothing in mock implementation
        }
        
        @Override
        public void vblankEnd() {
            // Do nothing in mock implementation
        }
        
        @Override
        public void hblankStart(WritableImage screen, int y, boolean isDirty) {
            // Do nothing in mock implementation
        }
        
        @Override
        public byte getFloatingBus() {
            // Return a fixed value for predictable tests
            return (byte) 0xEA;  // NOP instruction for predictable tests
        }
        
        @Override
        public String getDeviceName() {
            return "MockVideoDHGR";
        }
    }
    
    /**
     * Sets up a mock video device for tests to prevent NPEs when accessing the
     * floating bus.
     *
     * @param <T> The type of Video implementation
     * @param videoClass The class of the Video implementation to create and set up
     */
    public static <T extends Video> void setupMockVideo(Class<T> videoClass) {
        try {
            // Create a new mock video instance
            T videoInstance = videoClass.getDeclaredConstructor().newInstance();

            // Configure and set the video in the computer
            Emulator.withComputer(computer -> {
                // Suspend the computer during setup
                computer.getMotherboard().suspend();
                try {
                    // Attach the video
                    computer.setVideo(videoInstance);
                    videoInstance.attach();
                } finally {
                    // Resume the computer
                    computer.getMotherboard().resume();
                }
            });

            // Log successful setup
            if (VERBOSE_MODE) {
                LOG.info("Mock video initialized successfully: " + videoClass.getSimpleName());
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error setting up mock video: " + e.getMessage(), e);
            throw new RuntimeException("Failed to set up mock video", e);
        }
    }
    
    /**
     * Sets up a standard mock video device for tests.
     */
    public static void setupMockVideo() {
        setupMockVideo(MockVideo.class);
    }
    
    /**
     * Sets up a specialized NTSC mock video for NTSC-specific tests.
     * Use this method instead of setupMockVideo() for tests that need
     * an actual VideoNTSC implementation.
     */
    public static void setupMockVideoNTSC() {
        setupMockVideo(MockVideoNTSC.class);
    }
    
    /**
     * Sets up a specialized DHGR mock video for DHGR-specific tests.
     * Use this method instead of setupMockVideo() for tests that need
     * an actual VideoDHGR implementation.
     */
    public static void setupMockVideoDHGR() {
        setupMockVideo(MockVideoDHGR.class);
    }
    
    /**
     * Configure the test environment to ensure it's set up for headless operation.
     * This sets various system properties to prevent JavaFX initialization
     * and places the application in test mode.
     */
    public static void configureTestEnvironment() {
        // Set system properties to disable JavaFX
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        
        // Set test mode flag
        System.setProperty("jace.test", "true");
        Utility.setTestMode(true);
        
        // Prevent JaceApplication from initializing JavaFX toolkit
        JaceApplication.setupForTesting(true);
        
        LOG.fine("Test environment configured for headless mode");
    }
    
    /**
     * Sets up the computer for CPU tests.
     * This includes creating essential components, configuring the motherboard,
     * setting up mock video, and clearing any cards/peripherals.
     */
    public static void setupForCpuTest() {
        // Configure test environment first
        configureTestEnvironment();
        
        try {
            // Initialize emulator explicitly for CPU tests
            Emulator.resetForTesting();
            
            // Create bare minimum computer setup for CPU testing
            Emulator.withComputer(c -> {
                if (VERBOSE_MODE) {
                    LOG.info("CPU Test Setup - Creating essential components");
                }
                
                // Replace any existing RAM with FakeRAM to avoid bank switching issues
                FakeRAM ram = new FakeRAM();
                c.setMemory(ram);
                
                // Suspend the computer's motherboard before making changes
                // This prevents any timing issues during setup
                c.getMotherboard().suspend();
                
                try {                    
                    // Create and set up mock video to prevent NPEs
                    MockVideo mockVideo = new MockVideo();
                    
                    // Set the video before attaching to ensure consistent state
                    c.setVideo(mockVideo);
                    
                    // Now attach the video to register listeners
                    mockVideo.attach();
                    
                    // Double-check the video writer is properly set
                    mockVideo.setCurrentWriter(mockVideo.mockWriter);
                    
                    // Verify the video is properly initialized
                    if (c.getVideo() == null) {
                        throw new IllegalStateException("Video is null after setting it");
                    }
                    
                    if (VERBOSE_MODE) {
                        LOG.info("CPU Test Setup - Mock video initialized: " + c.getVideo().getClass().getSimpleName());
                    }
                    
                    // Disable all cards and peripherals
                    // In CPU tests we don't need any cards or peripherals
                    for (int slot = 1; slot <= 7; slot++) {
                        c.getMemory().removeCard(slot);
                    }
                    
                    // Final verification of video state
                    if (c.getVideo() == null || !(c.getVideo() instanceof MockVideo)) {
                        throw new IllegalStateException("Mock video not properly initialized after setup");
                    }
                    
                    // Verify floating bus access works
                    try {
                        byte floatingBus = c.getVideo().getFloatingBus();
                        if (VERBOSE_MODE) {
                            LOG.info("CPU Test Setup - Floating bus test successful (value: " + floatingBus + ")");
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "CPU Test Setup - Floating bus access failed: " + e.getMessage(), e);
                        throw e;
                    }
                } finally {
                    // Resume the motherboard with our modified configuration
                    // Note: The motherboard will immediately be suspended again in the next step
                    c.getMotherboard().resume();
                }
                
                // Configure the computer without reconfiguration
                // This ensures a clean, suspended state
                c.getMotherboard().suspend();
            });
            
            // Verify the video setup after all initialization
            Emulator.withComputer(c -> {
                if (c.getVideo() == null) {
                    throw new IllegalStateException("Video is null after CPU test setup - this should never happen");
                }
                if (VERBOSE_MODE) {
                    LOG.info("CPU Test Setup - Final verification successful, video = " + c.getVideo().getClass().getSimpleName());
                }
            });
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "ERROR setting up CPU test environment: " + e.getMessage(), e);
            throw new RuntimeException("Failed to set up CPU test environment", e);
        }
    }
}
