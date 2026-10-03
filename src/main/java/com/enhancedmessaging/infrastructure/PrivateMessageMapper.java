package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.PrivateMessage;
import java.time.Instant;
import net.runelite.api.ChatMessageType;
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
		return new PrivateMessage(playerName, Text.unescapeJagex(event.getMessage()), timestamp,
			type == ChatMessageType.PRIVATECHATOUT);
	}
}
