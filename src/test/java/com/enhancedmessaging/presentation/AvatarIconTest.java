package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.FriendStatus;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AvatarIconTest
{
	@Test
	public void paintsOnlineAndOfflineOrbs()
	{
		BufferedImage online = paint(FriendStatus.ONLINE);
		BufferedImage offline = paint(FriendStatus.OFFLINE);
		assertEquals(new Color(70, 190, 90).getRGB(), online.getRGB(21, 21));
		assertEquals(Color.GRAY.getRGB(), offline.getRGB(21, 21));
		assertNotEquals(online.getRGB(21, 21), paint(FriendStatus.UNKNOWN).getRGB(21, 21));
	}

	@Test
	public void avatarHasNoUnreadDotAndUnknownStatusHasNoOrb()
	{
		assertEquals(0, paint(FriendStatus.ONLINE).getRGB(22, 3) >>> 24);
		assertEquals(0, paint(FriendStatus.UNKNOWN).getRGB(25, 21) >>> 24);
	}

	private BufferedImage paint(FriendStatus status)
	{
		BufferedImage output = new BufferedImage(26, 26, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = output.createGraphics();
		try
		{
			new AvatarIcon(null, status).paintIcon(null, graphics, 0, 0);
		}
		finally
		{
			graphics.dispose();
		}
		return output;
	}
}
