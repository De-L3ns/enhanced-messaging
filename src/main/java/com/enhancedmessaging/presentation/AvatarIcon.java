package com.enhancedmessaging.presentation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import net.runelite.client.ui.ColorScheme;

final class AvatarIcon implements Icon
{
	private final BufferedImage image;
	private final boolean unread;

	AvatarIcon(BufferedImage image, boolean unread)
	{
		this.image = image;
		this.unread = unread;
	}

	@Override
	public void paintIcon(Component component, Graphics graphics, int x, int y)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.translate(x, y);
			java.awt.Shape originalClip = g.getClip();
			g.clip(new java.awt.geom.Ellipse2D.Double(0, 2, 24, 24));
			g.setColor(new Color(85, 85, 85));
			g.fillRect(0, 2, 24, 24);
			if (image == null)
			{
				g.setColor(Color.LIGHT_GRAY);
				g.fillOval(8, 6, 8, 8);
				g.fillOval(4, 15, 16, 14);
			}
			else
			{
				g.drawImage(image, 0, 2, 24, 24, null);
			}
			g.setClip(originalClip);
			if (unread)
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillOval(19, 0, 7, 7);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	@Override
	public int getIconWidth()
	{
		return 26;
	}

	@Override
	public int getIconHeight()
	{
		return 26;
	}
}
