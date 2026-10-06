package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.image.BufferedImage;
import java.util.List;
import lombok.Value;

// Immutable display data; image pixels are shared from the read-only avatar cache.
@Value
public class WidgetView
{
	WidgetOptions options;
	List<Chat> chats;
	long contextToken;

	@Value
	public static class Chat
	{
		String player;
		boolean unread;
		FriendStatus status;
		BufferedImage avatar;
		List<PrivateMessage> messages;
	}
}
