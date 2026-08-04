/**
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.core;

import static jace.TestUtils.initComputer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jace.Emulator;
import jace.apple2e.RAM128k;
import jace.apple2e.SoftSwitches;
import jace.hardware.CardSSC;

/**
 * Installing or removing a card must invalidate the memoized page maps.
 *
 * RAM128k.configureActiveMemory() memoizes built page maps in a HashMap keyed on
 * a string built from the soft switch state. That key does not encode which
 * cards are installed, but a card changes how $C100-$C7FF maps -- so a map
 * cached before a card change is stale afterwards even though the switches are
 * bit-for-bit identical.
 *
 * There were two halves to the bug, and a test that only covers the first will
 * still pass with the second present:
 *
 *   1. addCard()/removeCard() did not clear the cache at all.
 *   2. Clearing the cache alone is insufficient, because
 *      configureActiveMemory() early-returns when the switch state is unchanged.
 *      After a bare memoryConfigurations.clear() it would return without
 *      rebuilding, leaving activeRead/activeWrite pointing at the discarded
 *      maps. The `state` guard has to be reset too.
 *
 * cardChangeRebuildsEvenWhenSwitchesAreUnchanged is the one that catches (2):
 * it holds every soft switch constant across the card change, so the early
 * return is guaranteed to trigger unless the guard was reset.
 *
 * @author brobert
 */
public class CardChangeInvalidatesMemoryMapTest {

    /** Slot 2 ROM space. The SSC serves $C200-$C2FF through RAMListeners. */
    private static final int SLOT2_ROM = 0xC200;
    private static final int SLOT = 2;

    private static RAM128k ram;

    @BeforeClass
    public static void setupClass() {
        initComputer();
    }

    @Before
    public void setup() {
        ram = (RAM128k) Emulator.withComputer(c -> c.getMemory(), null);
        assertNotNull("test needs a RAM128k instance", ram);
        // Slot ROM must be mapped in for $C200 to reach the card at all.
        SoftSwitches.CXROM.getSwitch().setState(false);
        ram.configureActiveMemory();
    }

    /**
     * The core contract: after addCard(), $C200 must read the card's ROM rather
     * than whatever was mapped there before.
     */
    @Test
    public void addCardRemapsSlotRomSpace() {
        ram.removeCard(SLOT);
        byte withoutCard = ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false);

        ram.addCard(new CardSSC(), SLOT);
        byte withCard = ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false);

        assertNotEquals("installing a card must change what $C200 reads; "
                + "an unchanged value means a stale page map was reused",
                withoutCard, withCard);
    }

    /**
     * This is the test that fails if only memoryConfigurations.clear() is done
     * without resetting the `state` guard.
     *
     * Every soft switch is identical before and after the card change, so
     * configureActiveMemory()'s early return fires unless invalidation reset the
     * guard -- in which case activeRead still points at the pre-card map and
     * $C200 reads the stale value.
     */
    @Test
    public void cardChangeRebuildsEvenWhenSwitchesAreUnchanged() {
        ram.removeCard(SLOT);

        String readConfigBefore = ram.getReadConfiguration();
        String writeConfigBefore = ram.getWriteConfiguration();
        byte withoutCard = ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false);

        ram.addCard(new CardSSC(), SLOT);

        // Precondition: the switch state genuinely did not change, so this test
        // is actually exercising the early-return path it claims to.
        assertEquals("read configuration must be unchanged for this test to be "
                + "exercising the early-return path",
                readConfigBefore, ram.getReadConfiguration());
        assertEquals("write configuration must be unchanged for this test to be "
                + "exercising the early-return path",
                writeConfigBefore, ram.getWriteConfiguration());

        assertNotEquals("$C200 still reads the pre-card value even though a card "
                + "was installed: the memoized page map was not rebuilt because "
                + "configureActiveMemory() early-returned on an unchanged switch "
                + "state. Invalidation must reset the state guard, not just clear "
                + "the map.",
                withoutCard, ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false));
    }

    /**
     * Removing a card must unmap its ROM, which is the same bug in the other
     * direction: removeCard() previously did not invalidate or reconfigure.
     */
    @Test
    public void removeCardUnmapsSlotRomSpace() {
        ram.addCard(new CardSSC(), SLOT);
        byte withCard = ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false);

        ram.removeCard(SLOT);
        byte withoutCard = ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false);

        assertNotEquals("removing a card must change what $C200 reads; "
                + "an unchanged value means the card's ROM is still mapped",
                withCard, withoutCard);
    }

    /**
     * Re-installing the same card type must still produce a working mapping.
     * Guards the memoization from caching a map keyed on a state that recurs.
     */
    @Test
    public void repeatedCardChangesStayCorrect() {
        ram.addCard(new CardSSC(), SLOT);
        byte firstInstall = ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false);

        ram.removeCard(SLOT);
        ram.addCard(new CardSSC(), SLOT);
        byte secondInstall = ram.read(SLOT2_ROM, RAMEvent.TYPE.READ_DATA, false, false);

        assertEquals("re-installing the same card type must map the same ROM byte",
                firstInstall, secondInstall);
    }
}
