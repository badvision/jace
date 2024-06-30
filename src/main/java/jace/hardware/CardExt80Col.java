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

package jace.hardware;

import jace.apple2e.RAM128k;
import jace.core.PagedMemory;
import jace.state.Stateful;

/**
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Stateful
public class CardExt80Col extends RAM128k {
    @Stateful
    public PagedMemory auxMemory;
    @Stateful
    public PagedMemory auxLanguageCard;
    @Stateful
    public PagedMemory auxLanguageCard2;

    @Override
    public String getName() {
        return "Extended 80-col card (128kb)";
    }

    @Override
    public String getShortName() {
        return "128kb";
    }

    public CardExt80Col() {
        super();
        auxMemory = new PagedMemory(0xc000, PagedMemory.Type.RAM);
        auxLanguageCard = new PagedMemory(0x3000, PagedMemory.Type.LANGUAGE_CARD);
        auxLanguageCard2 = new PagedMemory(0x1000, PagedMemory.Type.LANGUAGE_CARD);
        initMemoryPattern(auxMemory);
     }
    
    // This is redundant here, but necessary for Ramworks
    @Override
    public PagedMemory getAuxVideoMemory() {
        return auxMemory;
    }

    /**
     * @return the auxMemory
     */
    @Override
    public PagedMemory getAuxMemory() {
        return auxMemory;
    }

    /**
     * @return the auxLanguageCard
     */
    @Override
    public PagedMemory getAuxLanguageCard() {
        return auxLanguageCard;
    }

    /**
     * @return the auxLanguageCard2
     */
    @Override
    public PagedMemory getAuxLanguageCard2() {
        return auxLanguageCard2;
    }
    
    @Override
    public void reconfigure() {
        // Do nothing
    }

    @Override
    public void attach() {
        // Nothing to do...
    }
}
