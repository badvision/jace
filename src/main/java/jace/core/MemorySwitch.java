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

/**
 * Represents the different memory bank switching flags for Apple //e memory access.
 * These flags can be used to explicitly control which memory bank is accessed
 * during read and write operations.
 * 
 * It is worth noting that if no switches are provided, then the default behavior
 * is to assume main memory and rom are being used.
 * 
 * AUX_ZP controls zero page but also language card 1 and 2.
 * AUX controls all other memory including ZP.
 * It is possible to use LC2 by itself, LC1 by itself, or both.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public enum MemorySwitch {
    // Special flag for wildcard matching
    ANY,        // Match any memory configuration
    
    // Core memory bank selection switches
    AUX_ZP,     // Use auxiliary zero page (0000-01FF)
    AUX,        // Use auxiliary memory
    
    // Language card bank selection switches
    AUX_LC,     // Use auxiliary language card
    LC1,        // Language card bank 1 enabled
    LC2         // Language card bank 2 enabled (overrides LC1)
} 