package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// The plugin and panel access session history only on the Swing event dispatch thread.
public class ConversationService
{
	public static final int MAX_CONVERSATIONS = 100;

	private final Map<String, Conversation> conversations = new LinkedHashMap<>();
	private String latestIncomingKey;

	public void record(PrivateMessage message)
	{
		record(message, true);
	}

	private void record(PrivateMessage message, boolean live)
	{
		String key = message.getPlayerName().toLowerCase(Locale.ROOT);
		Conversation conversation = conversations.remove(key);
		if (conversation == null)
		{
			conversation = new Conversation(message.getPlayerName());
		}
		conversation.add(message);
		if (live && !message.isOutgoing())
		{
			conversation.markUnread();
			latestIncomingKey = key;
		}
		conversations.put(key, conversation);
		if (conversations.size() > MAX_CONVERSATIONS)
		{
			conversations.remove(conversations.keySet().iterator().next());
		}
		if (!conversations.containsKey(latestIncomingKey)) { latestIncomingKey = null; }
	}

	public Conversation getLatestIncomingConversation()
	{
		return conversations.get(latestIncomingKey);
	}

	public boolean updateMessage(PrivateMessage message)
	{
		Conversation conversation = conversations.get(message.getPlayerName().toLowerCase(Locale.ROOT));
		return conversation != null && conversation.updateText(message.getId(), message.getText());
	}

	public List<Conversation> getConversations()
	{
		List<Conversation> result = new ArrayList<>(conversations.values());
		Collections.reverse(result);
		return Collections.unmodifiableList(result);
	}

	public void clear()
	{
		conversations.clear();
		latestIncomingKey = null;
	}

	public boolean isEmpty()
	{
		return conversations.isEmpty();
	}

	public List<PrivateMessage> snapshot()
	{
		List<PrivateMessage> messages = new ArrayList<>();
		for (Conversation conversation : conversations.values())
		{
			messages.addAll(conversation.getMessages());
		}
		return Collections.unmodifiableList(messages);
	}

	public void mergeSavedHistory(List<PrivateMessage> saved)
	{
		List<PrivateMessage> current = snapshot();
		Map<String, PrivateMessage> currentById = new LinkedHashMap<>();
		current.forEach(message -> currentById.put(message.getId(), message));
		String latestIncoming = latestIncomingKey;
		Set<String> unread = new HashSet<>();
		conversations.forEach((key, conversation) ->
		{
			if (conversation.isUnread())
			{
				unread.add(key);
			}
		});
		Set<String> seen = new HashSet<>();
		clear();
		for (PrivateMessage message : saved)
		{
			if (seen.add(message.getId()))
			{
				record(currentById.getOrDefault(message.getId(), message), false);
			}
		}
		for (PrivateMessage message : current)
		{
			if (seen.add(message.getId()))
			{
				record(message, false);
			}
		}
		// Restoring saved messages must not displace a live sender or invent a new one.
		latestIncomingKey = conversations.containsKey(latestIncoming) ? latestIncoming : null;
		unread.forEach(key ->
		{
			Conversation conversation = conversations.get(key);
			if (conversation != null)
			{
				conversation.markUnread();
			}
		});
	}
}
