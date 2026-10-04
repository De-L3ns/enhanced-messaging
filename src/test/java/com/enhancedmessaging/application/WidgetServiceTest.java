package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.WidgetChatMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.*;

public class WidgetServiceTest
{
	private final ConversationService conversations = new ConversationService();
	private final FriendStatusService friends = new FriendStatusService();
	private final PinService pins = new PinService(new PinStorage()
	{
		public CompletableFuture<List<String>> load(String account) { return CompletableFuture.completedFuture(List.of()); }
		public CompletableFuture<Void> save(String account, List<String> players) { return CompletableFuture.completedFuture(null); }
	}, Runnable::run, () -> { }, message -> fail(message));
	private final WidgetService widget = new WidgetService(conversations, pins, null, friends);

	@Test
	public void latestSenderComesFirstAndRemainingSlotsUsePinsAndRecentActivityWithoutDuplicates()
	{
		pins.switchAccount("a");
		pins.toggle("Alice");
		pins.toggle("Empty chat");
		assertEquals(List.of("Alice", "Empty chat"), names(widget.snapshot(options(3, 1, WidgetChatMode.PINNED_AND_RECENT))));
		record("Alice", "Old", false);
		record("Bob", "Recent", false);
		record("Carol", "Newest", false);
		assertEquals(List.of("Carol", "Alice", "Empty chat"), names(widget.snapshot(options(3, 1, WidgetChatMode.PINNED_AND_RECENT))));
		assertEquals(List.of("Carol"), names(widget.snapshot(options(1, 1, WidgetChatMode.PINNED_ONLY))));
		assertEquals(List.of("Carol", "Alice", "Empty chat"), names(widget.snapshot(options(10, 1, WidgetChatMode.PINNED_ONLY))));
		assertEquals(2, pins.getPlayers().size());
		record("ALICE", "A newer message from a pinned player", false);
		assertEquals(List.of("Alice", "Empty chat", "Carol", "Bob"),
			names(widget.snapshot(options(10, 1, WidgetChatMode.PINNED_AND_RECENT))));
	}

	@Test
	public void oneChatFollowsTheLatestSenderEvenAheadOfPinsAndOutgoingActivity()
	{
		pins.switchAccount("a");
		pins.toggle("Alice");
		for (WidgetChatMode mode : WidgetChatMode.values())
		{
			record("Alice", "First incoming", false);
			assertEquals(List.of("Alice"), names(widget.snapshot(options(1, 1, mode))));
			record("Bob", "New incoming from Bob", false);
			WidgetView.Chat latest = widget.snapshot(options(1, 1, mode)).getChats().get(0);
			assertEquals("Bob", latest.getPlayer());
			assertEquals("New incoming from Bob", latest.getMessages().get(0).getText());
			assertTrue(latest.isUnread());
			assertFalse(latest.isPinned());
			record("Alice", "Outgoing elsewhere", true);
			assertEquals(List.of("Bob"), names(widget.snapshot(options(1, 1, mode))));
			conversations.getLatestIncomingConversation().markRead();
			assertEquals(List.of("Bob"), names(widget.snapshot(options(1, 1, mode))));
			assertFalse(widget.snapshot(options(1, 1, mode)).getChats().get(0).isUnread());
			record("ALICE", "Newest incoming", false);
			assertEquals(List.of("Alice"), names(widget.snapshot(options(1, 1, mode))));
			assertEquals(List.of("Alice"), pins.getPlayers());
		}
	}

	@Test
	public void historyRestoreDoesNotStealLivePriorityAndClearResetsIt()
	{
		pins.switchAccount("a");
		pins.toggle("Alice");
		PrivateMessage saved = new PrivateMessage("Carol", "Saved incoming", Instant.ofEpochSecond(200), false);
		conversations.mergeSavedHistory(List.of(saved));
		assertEquals(List.of("Alice"), names(widget.snapshot(options(1, 1, WidgetChatMode.PINNED_AND_RECENT))));
		record("Bob", "Live incoming", false);
		conversations.mergeSavedHistory(List.of(saved));
		assertEquals(List.of("Bob"), names(widget.snapshot(options(1, 1, WidgetChatMode.PINNED_AND_RECENT))));
		conversations.clear();
		assertNull(conversations.getLatestIncomingConversation());
		assertEquals(List.of("Alice"), names(widget.snapshot(options(1, 1, WidgetChatMode.PINNED_AND_RECENT))));
	}

	@Test
	public void previewsAreBoundedChronologicalAndNeverMarkMessagesRead()
	{
		pins.switchAccount("a");
		for (int i = 0; i < 20; i++) { record("Alice", "Message " + i, i % 2 == 0); }
		WidgetView.Chat chat = widget.snapshot(options(3, 3, WidgetChatMode.PINNED_AND_RECENT)).getChats().get(0);
		assertEquals(List.of("Message 17", "Message 18", "Message 19"), chat.getMessages().stream().map(PrivateMessage::getText).collect(Collectors.toList()));
		assertTrue(chat.getMessages().get(1).isOutgoing());
		assertTrue(chat.isUnread());
		assertTrue(conversations.getConversations().get(0).isUnread());
		assertTrue(widget.snapshot(options(3, 0, WidgetChatMode.PINNED_AND_RECENT)).getChats().get(0).getMessages().isEmpty());
		record("Alice", "Another", false);
		assertEquals(3, chat.getMessages().size());
		assertEquals("Message 19", chat.getMessages().get(2).getText());
	}

	@Test
	public void emptyPinnedContactsStillShowStatusWithoutAddingHistory()
	{
		pins.switchAccount("a");
		pins.toggle("Alice");
		friends.switchAccount("a");
		friends.update("a", Map.of("alice", FriendStatus.ONLINE));
		WidgetView.Chat chat = widget.snapshot(options(3, 3, WidgetChatMode.PINNED_ONLY)).getChats().get(0);
		assertEquals(FriendStatus.ONLINE, chat.getStatus());
		assertFalse(chat.isUnread());
		assertTrue(chat.getMessages().isEmpty());
		assertTrue(conversations.isEmpty());
		pins.switchAccount(null);
		assertTrue(widget.snapshot(options(3, 3, WidgetChatMode.PINNED_ONLY)).getChats().isEmpty());
	}

	private WidgetOptions options(int count, int previews, WidgetChatMode mode)
	{
		return new WidgetOptions(true, count, previews, mode, false, true, true, 240, true);
	}
	private List<String> names(WidgetView view)
	{
		return view.getChats().stream().map(WidgetView.Chat::getPlayer).collect(Collectors.toList());
	}
	private void record(String player, String text, boolean outgoing)
	{
		conversations.record(new PrivateMessage(player, text, Instant.ofEpochSecond(100), outgoing));
	}
}
