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

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import javax.swing.Icon;
import javax.swing.JLabel;

/**
 * This renders label text in white with a black outline around the letters for
 * enhanced readability.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
public class OutlinedLabel extends JLabel {

    public OutlinedLabel(Icon image) {
        super(image);
    }

    public OutlinedLabel(String text) {
        super(text);
    }

    public OutlinedLabel(Icon image, int horizontalAlignment) {
        super(image, horizontalAlignment);
    }

    public OutlinedLabel(String text, int horizontalAlignment) {
        super(text, horizontalAlignment);
    }

    public OutlinedLabel(String text, Icon icon, int horizontalAlignment) {
        super(text, icon, horizontalAlignment);
    }

    @Override
    public void paint(Graphics g) {
        String text = getText();
        Graphics2D gg = (Graphics2D) g;
        int width = getWidth();
        int height = getHeight();
//        int width = g.getClipBounds().width;
//        int height = g.getClipBounds().height;
        TextLayout layout = new TextLayout(text, getFont(), ((Graphics2D) g).getFontRenderContext());
        Shape shape = layout.getOutline(null);
        getIcon().paintIcon(this, g, (int) ((width - getIcon().getIconWidth()) / 2), 0);
        int x = (int) ((width - layout.getBounds().getWidth()) / 2);
        int y = (int) (((height - layout.getBounds().getHeight() + layout.getAscent()) / 2) + layout.getDescent());
        AffineTransform shift = AffineTransform.getTranslateInstance(x, y);
        Shape shp = shift.createTransformedShape(shape);
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setColor(getBackground());
        gg.setStroke(new BasicStroke(3.0f));
        gg.draw(shp);
        gg.setColor(getForeground());
        gg.fill(shp);
    }
}
