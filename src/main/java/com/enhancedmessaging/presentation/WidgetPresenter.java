package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.AvatarService;
import com.enhancedmessaging.application.FriendStatusService;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.ConversationHistory;
import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WidgetPresenter
{
	private final ConversationHistory conversations;
	private final AvatarService avatars;
	private final FriendStatusService friends;
	private String account;
	private long generation;

	public WidgetPresenter(ConversationHistory conversations, AvatarService avatars, FriendStatusService friends)
	{
		this.conversations = conversations;
		this.avatars = avatars;
		this.friends = friends;
	}

	public void switchAccount(String key)
	{
		if (!Objects.equals(account, key)) { account = key; generation++; }
	}

	public boolean isCurrent(long token)
	{
		return account != null && generation == token;
	}

	// Build on state changes on the UI thread, never while rendering the overlay.
	public WidgetView snapshot(WidgetOptions options)
	{
		List<Conversation> selected = new ArrayList<>();
		Conversation latestIncoming = conversations.getLatestIncomingConversation();
		if (latestIncoming != null) { selected.add(latestIncoming); }
		for (Conversation chat : conversations.getConversations())
		{
			if (chat != latestIncoming && chat.getMessageCount() > 0) { selected.add(chat); }
		}
		List<WidgetView.Chat> rows = new ArrayList<>();
		int limit = Math.max(1, Math.min(10, options.getChatCount()));
		int previews = options.isLowFootprint() ? 0 : Math.max(0, Math.min(3, options.getPreviewCount()));
		for (Conversation chat : selected)
		{
			if (rows.size() == limit)
			{
				break;
			}
			List<PrivateMessage> messages = previews == 0 ? List.of() : chat.getRecentMessages(previews);
			String player = chat.getPlayerName();
			rows.add(new WidgetView.Chat(player, chat.isUnread(), options.isLowFootprint() ? FriendStatus.UNKNOWN : friends.statusFor(player),
				options.showsAvatars() && avatars != null ? avatars.imageFor(player) : null, messages));
		}
		return new WidgetView(options, List.copyOf(rows), generation);
	}
}
