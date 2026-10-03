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

	public void record(PrivateMessage message)
	{
		String key = message.getPlayerName().toLowerCase(Locale.ROOT);
		Conversation conversation = conversations.remove(key);
		if (conversation == null)
		{
			conversation = new Conversation(message.getPlayerName());
		}
		conversation.add(message);
		conversations.put(key, conversation);
		if (conversations.size() > MAX_CONVERSATIONS)
		{
			conversations.remove(conversations.keySet().iterator().next());
		}
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
		Set<String> seen = new HashSet<>();
		clear();
		for (PrivateMessage message : saved)
		{
			if (seen.add(message.getId()))
			{
				record(message);
			}
		}
		for (PrivateMessage message : current)
		{
			if (seen.add(message.getId()))
			{
				record(message);
			}
		}
	}
}
