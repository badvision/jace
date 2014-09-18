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
package jace.hardware.massStorage;

import jace.core.Computer;
import jace.core.RAM;
import java.io.IOException;

/**
 * Generic representation of a mass storage disk, either an image or a virtual volume.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public interface IDisk {
    public static int BLOCK_SIZE = 512;
    public static int MAX_BLOCK = 65535;

    public void mliFormat() throws IOException;
    public void mliRead(int block, int bufferAddress, RAM memory) throws IOException;
    public void mliWrite(int block, int bufferAddress, RAM memory) throws IOException;
    public void boot0(int slot, Computer computer) throws IOException;

    // Return size in 512k blocks
    public int getSize();
    
    public void eject();
    public boolean isWriteProtected();

}
