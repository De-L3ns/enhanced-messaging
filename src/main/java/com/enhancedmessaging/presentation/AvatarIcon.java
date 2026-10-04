package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.FriendStatus;
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
	private final FriendStatus status;

	AvatarIcon(BufferedImage image)
	{
		this(image, FriendStatus.UNKNOWN);
	}

	AvatarIcon(BufferedImage image, FriendStatus status)
	{
		this.image = image;
		this.status = status;
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
			if (status != FriendStatus.UNKNOWN)
			{
				g.setColor(ColorScheme.DARK_GRAY_COLOR);
				g.fillOval(17, 17, 9, 9);
				g.setColor(status == FriendStatus.ONLINE ? new Color(70, 190, 90) : Color.GRAY);
				g.fillOval(18, 18, 7, 7);
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
