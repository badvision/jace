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

package jace.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Nothing yet, but will be a software librarian of sorts one day...
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class Library {
    public static void main (String... args) {
        try {
            // Type: a = ascii, d = directory listing, i = image (binary)
            URL url = new URL("ftp://anonymous:jaceuser@ftp.apple.asimov.net/pub/apple_II/images/;type=d");
            URLConnection urlc = url.openConnection();
            InputStream is = urlc.getInputStream(); // To upload
            byte[] buffer = new byte[4096];
            int bytesRead = is.read(buffer);
            while (bytesRead > 0) {
                String s = new String(buffer, 0, bytesRead);
                System.out.println(s);
                bytesRead = is.read(buffer);
            }
        } catch (IOException ex) {
            Logger.getLogger(Library.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
