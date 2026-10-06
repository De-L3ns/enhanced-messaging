package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.PrivateMessage;
import java.time.Instant;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

public final class PrivateMessageMapper
{
	private PrivateMessageMapper()
	{
	}

	public static PrivateMessage fromEvent(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.PRIVATECHAT && type != ChatMessageType.MODPRIVATECHAT
			&& type != ChatMessageType.PRIVATECHATOUT)
		{
			return null;
		}
		if (event.getName() == null || event.getMessage() == null)
		{
			return null;
		}
		String playerName = Text.toJagexName(Text.removeTags(event.getName()));
		if (playerName.isEmpty())
		{
			return null;
		}
		Instant timestamp = event.getTimestamp() > 0
			? Instant.ofEpochSecond(event.getTimestamp()) : Instant.now();
		String text = event.getMessageNode() == null ? null : displayText(event.getMessageNode());
		return new PrivateMessage(playerName, text == null ? Text.unescapeJagex(event.getMessage()) : text, timestamp,
			type == ChatMessageType.PRIVATECHATOUT);
	}

	public static String displayText(MessageNode node)
	{
		String formatted = node.getRuneLiteFormatMessage();
		String text = formatted == null ? node.getValue() : formatted;
		return text == null ? null : Text.unescapeJagex(text);
	}
}
