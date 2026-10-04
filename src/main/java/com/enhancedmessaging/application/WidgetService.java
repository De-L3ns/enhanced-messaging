package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.WidgetChatMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WidgetService
{
	private final ConversationService conversations;
	private final PinService pins;
	private final AvatarService avatars;
	private final FriendStatusService friends;

	public WidgetService(ConversationService conversations, PinService pins, AvatarService avatars, FriendStatusService friends)
	{
		this.conversations = conversations;
		this.pins = pins;
		this.avatars = avatars;
		this.friends = friends;
	}

	// Build on state changes on the UI thread, never while rendering the overlay.
	public WidgetView snapshot(WidgetOptions options)
	{
		List<Conversation> recent = conversations.getConversations();
		Map<String, Conversation> known = new LinkedHashMap<>();
		recent.forEach(chat -> known.put(chat.getPlayerName().toLowerCase(Locale.ROOT), chat));
		Map<String, Conversation> selected = new LinkedHashMap<>();
		Conversation latestIncoming = conversations.getLatestIncomingConversation();
		if (latestIncoming != null)
		{
			selected.put(latestIncoming.getPlayerName().toLowerCase(Locale.ROOT), latestIncoming);
		}
		for (String player : pins.getPlayers())
		{
			String key = player.toLowerCase(Locale.ROOT);
			selected.putIfAbsent(key, known.getOrDefault(key, new Conversation(player)));
		}
		if (options.getMode() == WidgetChatMode.PINNED_AND_RECENT)
		{
			recent.stream().filter(chat -> chat.getMessageCount() > 0)
				.forEach(chat -> selected.putIfAbsent(chat.getPlayerName().toLowerCase(Locale.ROOT), chat));
		}
		List<WidgetView.Chat> rows = new ArrayList<>();
		int limit = Math.max(1, Math.min(10, options.getChatCount()));
		int previews = Math.max(0, Math.min(3, options.getPreviewCount()));
		for (Conversation chat : selected.values())
		{
			if (rows.size() == limit)
			{
				break;
			}
			List<PrivateMessage> messages = chat.getRecentMessages(previews);
			String player = chat.getPlayerName();
			rows.add(new WidgetView.Chat(player, pins.isPinned(player), chat.isUnread(), friends.statusFor(player),
				options.isAvatars() && avatars != null ? avatars.imageFor(player) : null, messages));
		}
		return new WidgetView(options, List.copyOf(rows), pins.contextToken(), pins.canChange(), pins.getStatus());
	}
}
