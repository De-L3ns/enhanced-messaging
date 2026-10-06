package com.enhancedmessaging.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import lombok.Getter;

public class Conversation
{
	public static final int MAX_MESSAGES = 500;

	@Getter
	private final String playerName;
	private final Deque<PrivateMessage> messages = new ArrayDeque<>();
	@Getter
	private boolean unread;

	public Conversation(String playerName)
	{
		this.playerName = playerName;
	}

	public void add(PrivateMessage message)
	{
		if (messages.size() == MAX_MESSAGES)
		{
			messages.removeFirst();
		}
		messages.addLast(message);
	}

	public List<PrivateMessage> getMessages()
	{
		return Collections.unmodifiableList(new ArrayList<>(messages));
	}

	public boolean updateText(String id, String text)
	{
		List<PrivateMessage> updated = new ArrayList<>(messages);
		for (int i = 0; i < updated.size(); i++)
		{
			PrivateMessage original = updated.get(i);
			if (original.getId().equals(id))
			{
				if (original.getText().equals(text)) { return false; }
				updated.set(i, new PrivateMessage(original.getId(), original.getPlayerName(), text,
					original.getTimestamp(), original.isOutgoing()));
				messages.clear();
				messages.addAll(updated);
				return true;
			}
		}
		return false;
	}

	public int getMessageCount()
	{
		return messages.size();
	}

	public List<PrivateMessage> getRecentMessages(int count)
	{
		List<PrivateMessage> recent = new ArrayList<>();
		java.util.Iterator<PrivateMessage> iterator = messages.descendingIterator();
		while (iterator.hasNext() && recent.size() < count)
		{
			recent.add(iterator.next());
		}
		Collections.reverse(recent);
		return Collections.unmodifiableList(recent);
	}

	public void markUnread()
	{
		unread = true;
	}

	public void markRead()
	{
		unread = false;
	}
}
