package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.infrastructure.PrivateMessageMapper;
import java.time.Instant;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationServiceTest
{
	@Test
	public void groupsIncomingAndOutgoingMessagesDespiteNameFormatting()
	{
		ConversationService service = new ConversationService();
		service.record(PrivateMessageMapper.fromEvent(new ChatMessage(null, ChatMessageType.PRIVATECHAT,
			"<img=2>Alice\u00a0Smith", "Hello", "", 100)));
		service.record(PrivateMessageMapper.fromEvent(new ChatMessage(null, ChatMessageType.PRIVATECHATOUT,
			"alice_smith", "Hi back", "", 101)));

		assertEquals(1, service.getConversations().size());
		Conversation conversation = service.getConversations().get(0);
		assertEquals("Alice Smith", conversation.getPlayerName());
		assertEquals(2, conversation.getMessageCount());
		assertEquals("Hello", conversation.getMessages().get(0).getText());
		assertFalse(conversation.getMessages().get(0).isOutgoing());
		assertEquals("Hi back", conversation.getMessages().get(1).getText());
		assertTrue(conversation.getMessages().get(1).isOutgoing());
	}

	@Test
	public void keepsPlayersSeparateAndOrdersByRecentActivity()
	{
		ConversationService service = new ConversationService();
		service.record(message("Alice", "First"));
		service.record(message("Bob", "Second"));
		service.record(message("Alice", "Third"));

		assertEquals("Alice", service.getConversations().get(0).getPlayerName());
		assertEquals("Bob", service.getConversations().get(1).getPlayerName());
		assertEquals(2, service.getConversations().get(0).getMessageCount());
		assertEquals("Second", service.getConversations().get(1).getMessages().get(0).getText());
	}

	@Test
	public void keepsRepeatedIdenticalMessages()
	{
		ConversationService service = new ConversationService();
		service.record(message("Alice", "Hello"));
		service.record(message("Alice", "Hello"));

		assertEquals(2, service.getConversations().get(0).getMessageCount());
	}

	@Test
	public void retainsOnlyTheLatestMessagesInChronologicalOrder()
	{
		ConversationService service = new ConversationService();
		for (int i = 0; i < Conversation.MAX_MESSAGES + 2; i++)
		{
			service.record(message("Alice", "Message " + i));
		}

		List<PrivateMessage> messages = service.getConversations().get(0).getMessages();
		assertEquals(Conversation.MAX_MESSAGES, messages.size());
		assertEquals("Message 2", messages.get(0).getText());
		assertEquals("Message " + (Conversation.MAX_MESSAGES + 1), messages.get(messages.size() - 1).getText());
	}

	@Test
	public void evictsTheLeastRecentlyActivePlayerAtTheConversationLimit()
	{
		ConversationService service = new ConversationService();
		for (int i = 0; i < ConversationService.MAX_CONVERSATIONS; i++)
		{
			service.record(message("Player " + i, "Hello"));
		}
		service.record(message("Player 0", "Still active"));
		service.record(message("New player", "Hello"));

		assertEquals(ConversationService.MAX_CONVERSATIONS, service.getConversations().size());
		assertFalse(service.getConversations().stream().anyMatch(c -> c.getPlayerName().equals("Player 1")));
		assertTrue(service.getConversations().stream().anyMatch(c -> c.getPlayerName().equals("Player 0")));
		assertEquals("New player", service.getConversations().get(0).getPlayerName());
	}

	@Test
	public void clearStartsAnEmptySession()
	{
		ConversationService service = new ConversationService();
		service.record(message("Alice", "Old session"));
		service.clear();

		assertTrue(service.getConversations().isEmpty());
		service.record(message("Alice", "New session"));
		assertEquals(1, service.getConversations().get(0).getMessageCount());
		assertEquals("New session", service.getConversations().get(0).getMessages().get(0).getText());
	}

	@Test
	public void mergingKeepsGenuineRepeatedMessagesWhileDeduplicatingPreviouslyLoadedIds()
	{
		ConversationService service = new ConversationService();
		PrivateMessage saved = message("Alice", "Hello");
		PrivateMessage fresh = message("Alice", "Hello");
		service.record(saved);
		service.record(fresh);
		service.mergeSavedHistory(List.of(saved));

		assertEquals(List.of(saved, fresh), service.snapshot());
	}

	@Test
	public void mergingSavedHistoryStillEnforcesTheMessageLimit()
	{
		ConversationService saved = new ConversationService();
		for (int i = 0; i < Conversation.MAX_MESSAGES; i++)
		{
			saved.record(message("Alice", "Saved " + i));
		}
		ConversationService current = new ConversationService();
		PrivateMessage fresh = message("Alice", "Fresh message");
		current.record(fresh);
		current.mergeSavedHistory(saved.snapshot());

		assertEquals(Conversation.MAX_MESSAGES, current.snapshot().size());
		assertEquals("Saved 1", current.snapshot().get(0).getText());
		assertEquals(fresh, current.snapshot().get(Conversation.MAX_MESSAGES - 1));
	}

	@Test
	public void mergingSavedHistoryStillEnforcesTheConversationLimit()
	{
		ConversationService saved = new ConversationService();
		for (int i = 0; i < ConversationService.MAX_CONVERSATIONS; i++)
		{
			saved.record(message("Player " + i, "Saved"));
		}
		ConversationService current = new ConversationService();
		current.record(message("New player", "Fresh"));
		current.mergeSavedHistory(saved.snapshot());

		assertEquals(ConversationService.MAX_CONVERSATIONS, current.getConversations().size());
		assertEquals("New player", current.getConversations().get(0).getPlayerName());
		assertFalse(current.getConversations().stream().anyMatch(c -> c.getPlayerName().equals("Player 0")));
	}

	private PrivateMessage message(String playerName, String text)
	{
		return new PrivateMessage(playerName, text, Instant.ofEpochSecond(100), false);
	}
}
