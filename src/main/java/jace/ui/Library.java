/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
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
