package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.PrivateMessage;
import java.time.Instant;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PrivateMessageMapperTest
{
	@Test
	public void capturesOnlyPrivateMessageTypes()
	{
		for (ChatMessageType type : ChatMessageType.values())
		{
			PrivateMessage message = PrivateMessageMapper.fromEvent(event(type, "Alice", "Hello", 100));
			if (type == ChatMessageType.PRIVATECHAT || type == ChatMessageType.MODPRIVATECHAT
				|| type == ChatMessageType.PRIVATECHATOUT)
			{
				assertNotNull(type.name(), message);
				assertEquals(type == ChatMessageType.PRIVATECHATOUT, message.isOutgoing());
			}
			else
			{
				assertNull(type.name(), message);
			}
		}
	}

	@Test
	public void removesFormattingAndPreservesEscapedMessageCharacters()
	{
		PrivateMessage message = PrivateMessageMapper.fromEvent(event(ChatMessageType.MODPRIVATECHAT,
			"<img=0><col=ffffff>Alice\u00a0Smith</col>",
			"<col=ff0000>Hello <lt>friend<gt> <at> home<nbh>now</col>", 100));

		assertEquals("Alice Smith", message.getPlayerName());
		assertEquals("Hello <friend> @ home-now", message.getText());
		assertEquals(Instant.ofEpochSecond(100), message.getTimestamp());
		assertFalse(message.isOutgoing());
	}

	@Test
	public void ignoresMissingNamesAndMissingMessageText()
	{
		assertNull(PrivateMessageMapper.fromEvent(event(ChatMessageType.PRIVATECHAT, null, "Hello", 100)));
		assertNull(PrivateMessageMapper.fromEvent(event(ChatMessageType.PRIVATECHAT, "<img=0> ", "Hello", 100)));
		assertNull(PrivateMessageMapper.fromEvent(event(ChatMessageType.PRIVATECHAT, "Alice", null, 100)));
	}

	@Test
	public void capturesFormattedCommandsWhenRuneLiteHasAlreadyResolvedThem()
	{
		TestMessageNode node = new TestMessageNode(1, ChatMessageType.PRIVATECHAT, "!kc zulrah");
		node.setRuneLiteFormatMessage("<colNORMAL>Zulrah: <colHIGHLIGHT>42<colNORMAL> killed");
		ChatMessage event = event(ChatMessageType.PRIVATECHAT, "Alice", "!kc zulrah", 100);
		event.setMessageNode(node);
		assertEquals("Zulrah: 42 killed", PrivateMessageMapper.fromEvent(event).getText());
		node.setRuneLiteFormatMessage(null);
		node.setValue("Zulrah: 43 killed");
		assertEquals("Zulrah: 43 killed", PrivateMessageMapper.fromEvent(event).getText());
		node.setValue(null);
		assertEquals("!kc zulrah", PrivateMessageMapper.fromEvent(event).getText());
	}

	@Test
	public void usesCaptureTimeIfTheEventHasNoTimestamp()
	{
		Instant before = Instant.now();
		PrivateMessage message = PrivateMessageMapper.fromEvent(event(ChatMessageType.PRIVATECHAT,
			"Alice", "Hello", 0));
		Instant after = Instant.now();

		assertTrue(!message.getTimestamp().isBefore(before));
		assertTrue(!message.getTimestamp().isAfter(after));
	}

	private ChatMessage event(ChatMessageType type, String name, String message, int timestamp)
	{
		return new ChatMessage(null, type, name, message, "", timestamp);
	}
}
