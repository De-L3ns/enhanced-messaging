package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.PrivateMessage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChatCommandMessagesTest
{
	private final ChatCommandMessages commands = new ChatCommandMessages();

	@Before
	public void setUp() { commands.switchAccount("a"); }

	@Test
	public void delayedIncomingAndOutgoingResponsesKeepTheirOriginalIdentityAndMetadata()
	{
		for (ChatMessageType type : List.of(ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT))
		{
			TestMessageNode node = new TestMessageNode(1, type, "!kc zulrah");
			ChatMessage event = event(node);
			PrivateMessage original = PrivateMessageMapper.fromEvent(event);
			commands.track(event, original);
			assertTrue(commands.poll().isEmpty());
			node.setRuneLiteFormatMessage("<colNORMAL>Zulrah: <colHIGHLIGHT>42<colNORMAL> killed");
			List<PrivateMessage> updates = commands.poll();
			assertEquals(1, updates.size());
			PrivateMessage updated = updates.get(0);
			assertEquals("Zulrah: 42 killed", updated.getText());
			assertEquals(original.getId(), updated.getId());
			assertEquals(original.getPlayerName(), updated.getPlayerName());
			assertEquals(original.getTimestamp(), updated.getTimestamp());
			assertEquals(original.isOutgoing(), updated.isOutgoing());
			assertEquals("!kc zulrah", original.getText());
			assertTrue(commands.poll().isEmpty());
		}
	}

	@Test
	public void recycledChatNodesCannotChangeTheOriginalMessage()
	{
		TestMessageNode node = track(1, "!kc zulrah");
		node.setId(999);
		node.setRuneLiteFormatMessage("Another player's message");
		assertTrue(commands.poll().isEmpty());
		node.setId(1);
		assertTrue(commands.poll().isEmpty());
	}

	@Test
	public void accountChangesLogoutAndCleanupDiscardPendingNodes()
	{
		TestMessageNode oldAccount = track(1, "!kc zulrah");
		commands.switchAccount("b");
		oldAccount.setRuneLiteFormatMessage("Zulrah: 42 killed");
		assertTrue(commands.poll().isEmpty());
		TestMessageNode loggedOut = track(2, "!lvl attack");
		commands.switchAccount(null);
		loggedOut.setRuneLiteFormatMessage("Attack: 99");
		assertTrue(commands.poll().isEmpty());
		commands.switchAccount("a");
		TestMessageNode closed = track(3, "!kc jad");
		commands.clear();
		closed.setRuneLiteFormatMessage("Jad: 1 killed");
		assertTrue(commands.poll().isEmpty());
	}

	@Test
	public void ordinaryMessagesAndAlreadyResolvedCommandsAreNotWatched()
	{
		TestMessageNode ordinary = track(1, "Hello");
		ordinary.setRuneLiteFormatMessage("Changed");
		TestMessageNode resolved = new TestMessageNode(2, ChatMessageType.PRIVATECHAT, "!kc zulrah");
		resolved.setRuneLiteFormatMessage("Zulrah: 42 killed");
		commands.track(event(resolved), PrivateMessageMapper.fromEvent(event(resolved)));
		resolved.setRuneLiteFormatMessage("Zulrah: 43 killed");
		assertTrue(commands.poll().isEmpty());
	}

	@Test
	public void directNodeValueUpdatesAreAlsoCaptured()
	{
		TestMessageNode node = track(1, "!kc zulrah");
		node.setValue("Zulrah: 42 killed");
		assertEquals("Zulrah: 42 killed", commands.poll().get(0).getText());
	}

	@Test
	public void unresolvedCommandsAreBoundedWithoutScanningChatHistory()
	{
		List<TestMessageNode> nodes = new ArrayList<>();
		for (int i = 0; i < 101; i++) { nodes.add(track(i, "!kc zulrah")); }
		for (int i = 0; i < nodes.size(); i++) { nodes.get(i).setRuneLiteFormatMessage("Resolved " + i); }
		List<PrivateMessage> updates = commands.poll();
		assertEquals(100, updates.size());
		assertEquals("Resolved 1", updates.get(0).getText());
		assertTrue(commands.poll().isEmpty());
	}

	private TestMessageNode track(int id, String text)
	{
		TestMessageNode node = new TestMessageNode(id, ChatMessageType.PRIVATECHAT, text);
		ChatMessage event = event(node);
		commands.track(event, PrivateMessageMapper.fromEvent(event));
		return node;
	}

	private ChatMessage event(TestMessageNode node)
	{
		return new ChatMessage(node, node.getType(), node.getName(), node.getValue(), "", node.getTimestamp());
	}
}
