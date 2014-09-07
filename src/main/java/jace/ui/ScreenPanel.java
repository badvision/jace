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

import jace.core.Computer;
import java.awt.Color;
import java.awt.Graphics;
import javax.swing.JPanel;

/**
 * Simple panel which has a navy blue background and uses the video class to
 * render its contents.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class ScreenPanel extends JPanel {

    public ScreenPanel() {
        setBackground(new Color(0, 0, 64));
        setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
        if (Computer.getComputer() != null) {
            Computer.getComputer().getVideo().forceRefresh();
        }
    }
}
