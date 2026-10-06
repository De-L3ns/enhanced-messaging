package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.PrivateMessage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;

// Client-thread only. Read command nodes while RuneLite resolves their display text.
public class ChatCommandMessages
{
	private static final int MAX_PENDING = 100;
	private final Map<Integer, Pending> pending = new LinkedHashMap<>();
	private String account;

	public void switchAccount(String key)
	{
		if (!Objects.equals(account, key))
		{
			account = key;
			clear();
		}
	}

	public void track(ChatMessage event, PrivateMessage message)
	{
		MessageNode node = event.getMessageNode();
		if (account == null || node == null || !event.getMessage().startsWith("!") || !message.getText().startsWith("!"))
		{
			return;
		}
		pending.put(node.getId(), new Pending(node, message));
		if (pending.size() > MAX_PENDING) { pending.remove(pending.keySet().iterator().next()); }
	}

	public List<PrivateMessage> poll()
	{
		List<PrivateMessage> updates = new ArrayList<>();
		Iterator<Map.Entry<Integer, Pending>> iterator = pending.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Integer, Pending> entry = iterator.next();
			Pending command = entry.getValue();
			// RuneLite reuses nodes when old chat lines leave its buffer.
			if (command.node.getId() != entry.getKey()) { iterator.remove(); continue; }
			String text = PrivateMessageMapper.displayText(command.node);
			if (text != null && !text.equals(command.message.getText()))
			{
				updates.add(new PrivateMessage(command.message.getId(), command.message.getPlayerName(), text,
					command.message.getTimestamp(), command.message.isOutgoing()));
				iterator.remove();
			}
		}
		return updates;
	}

	public void clear() { pending.clear(); }

	private static class Pending
	{
		private final MessageNode node;
		private final PrivateMessage message;

		private Pending(MessageNode node, PrivateMessage message)
		{
			this.node = node;
			this.message = message;
		}
	}
}
