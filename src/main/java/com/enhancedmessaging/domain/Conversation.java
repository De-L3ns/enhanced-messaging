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

	public int getMessageCount()
	{
		return messages.size();
	}
}
