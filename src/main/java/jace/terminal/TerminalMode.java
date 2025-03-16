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

package jace.terminal;

/**
 * Interface defining the contract for different Terminal modes
 */
public interface TerminalMode {
    /**
     * Get the name of this mode
     * @return Mode name
     */
    String getName();
    
    /**
     * Get the command prompt for this mode
     * @return Command prompt string
     */
    String getPrompt();
    
    /**
     * Process a command in this mode
     * @param command Command to process
     * @return true if command was processed, false otherwise
     */
    boolean processCommand(String command);
    
    /**
     * Print help information for this mode
     */
    void printHelp();
    
    /**
     * Print help for a specific command
     * @param command Command to provide detailed help for
     * @return true if help was provided, false if command was not found
     */
    default boolean printCommandHelp(String command) {
        // Default implementation returns false, indicating no specific help
        return false;
    }
} 