package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.WidgetUnreadStyle;
import lombok.Value;

@Value
public class WidgetOptions
{
	boolean enabled;
	int chatCount;
	int previewCount;
	boolean lowFootprint;
	boolean avatars;
	boolean status;
	boolean unread;
	WidgetUnreadStyle unreadStyle;
	boolean clickToOpen;

	public boolean usesGlow()
	{
		return unread && unreadStyle == WidgetUnreadStyle.GLOW;
	}

	public boolean showsAvatars()
	{
		return lowFootprint || avatars || usesGlow();
	}
}
