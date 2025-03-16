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

package jace;

import jace.terminal.HeadlessTerminal;

/**
 * Main entry point for the Jace application.
 * This class determines whether to launch in GUI mode or terminal mode
 * based on command line arguments.
 */
public class JaceLauncher {
    
    /**
     * Main entry point for the application.
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        System.out.println("JaceLauncher starting with args: " + String.join(", ", args));
        
        // Check if Terminal mode is requested via command line arguments
        for (String arg : args) {
            System.out.println("Checking arg: " + arg);
            if (arg.equalsIgnoreCase("--terminal")) {
                // Launch in Terminal mode
                System.out.println("*** Starting Jace in terminal mode... ***");
                HeadlessTerminal terminal = new HeadlessTerminal();
                terminal.run();
                System.exit(0);
                return;
            }
        }
        
        // Launch in normal GUI mode by passing control to the JavaFX Application
        System.out.println("Starting Jace in GUI mode...");
        JaceApplication.launch(JaceApplication.class, args);
    }
} 