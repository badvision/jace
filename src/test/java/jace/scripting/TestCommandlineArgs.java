/*
 * Copyright 2019 Brendan Robert
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
package jace.scripting;

import jace.Emulator;
import jace.apple2e.Apple2e;
import jace.apple2e.MOS65C02;
import jace.apple2e.VideoNTSC;
import jace.apple2e.VideoNTSC.VideoMode;
import jace.core.Computer;
import jace.core.RAM;
import jace.core.SoundMixer;
import jace.core.Utility;
import java.util.Arrays;
import javafx.embed.swing.JFXPanel;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Check out various command line arguments to see if they do the right thing
 */
public class TestCommandlineArgs {

    static Computer computer;
    static MOS65C02 cpu;
    static RAM ram;

    @BeforeClass
    public static void initJavaFX() {
        new JFXPanel();
    }

    @Before
    public void setup() {
        Utility.setHeadlessMode(true);
        SoundMixer.MUTE = true;
        computer = new Apple2e();
        cpu = (MOS65C02) computer.getCpu();
        ram = computer.getMemory();
        Emulator.computer = (Apple2e) computer;
        computer.pause();
        cpu.suspend();
        cpu.clearState();
    }

    static String GRAPHICS_MODE = "vid.videomode";

    @Test
    public void testVideoModes() {
        for (VideoMode mode : VideoMode.values()) {
            Emulator.instance.applyConfiguration(Arrays.asList("-" + GRAPHICS_MODE, mode.name().toLowerCase()));
            assertTrue("Should be NTSC video module", computer.video instanceof VideoNTSC);
            VideoNTSC video = (VideoNTSC) computer.video;
            assertEquals("Should have switched to " + mode.name() + " mode", mode, video.getVideoMode());

            Emulator.instance.applyConfiguration(Arrays.asList("-" + GRAPHICS_MODE, mode.name().toUpperCase()));
            assertTrue("Should be NTSC video module", computer.video instanceof VideoNTSC);
            video = (VideoNTSC) computer.video;
            assertEquals("Should have switched to " + mode.name() + " mode", mode, video.getVideoMode());
        }
    }
}
