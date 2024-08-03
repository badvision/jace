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
package jace.apple2e;

import static jace.TestUtils.initComputer;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.ProgramException;
import jace.TestProgram;
import jace.core.SoundMixer;


/**
 * More advanced cycle counting tests.  These help ensure CPU runs correctly so things
 * like vapor lock and speaker sound work as expected.
 * @author brobert
 */
public class CycleCountTest {

    @BeforeClass
    public static void setupClass() {
        initComputer();
        SoundMixer.MUTE = true;
    }

    /**
     * Composite test which ensures the speaker beep is the right pitch.
     * Test that the wait routine for beep cycles correctly.
     * Calling WAIT with A=#$c (12) should take 535 cycles
     * according to the tech ref notes: =1/2*(26+27*A+5*A^2) where A = 12 (0x0c)
     * The BELL routine has an additional 12 cycles per iteration plus 1 extra cycle in the first iteration.
     * e.g. 2 iterations take 1093 cycles
     * 
     * @throws ProgramException 
     */
    @Test
    public void testDirectBeeperCycleCount() throws ProgramException {
        new TestProgram("""
            SPKR = $C030
                    jmp BELL
            WAIT    sec
            WAIT2   pha
            WAIT3   sbc #$01
                    bne WAIT3
                    pla
                    sbc #$01
                    bne WAIT2
                    rts
            BELL    +markTimer
                    ldy #$02
            BELL2   lda #$0c
                    jsr WAIT
                    lda SPKR
                    dey
                    bne BELL2
            """, 1093).run();
    }    
    
}
