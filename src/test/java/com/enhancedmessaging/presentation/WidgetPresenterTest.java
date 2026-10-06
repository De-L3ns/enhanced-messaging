package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.AvatarService;
import com.enhancedmessaging.application.AvatarStorage;
import com.enhancedmessaging.application.FriendStatusService;
import com.enhancedmessaging.domain.ConversationHistory;
import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class WidgetPresenterTest
{
	private final ConversationHistory conversations = new ConversationHistory();
	private final FriendStatusService friends = new FriendStatusService();
	private final WidgetPresenter widget = new WidgetPresenter(conversations, null, friends);

	@Before
	public void setUp() { widget.switchAccount("a"); }

	@Test
	public void latestSenderLeadsRecentActivityWithoutDuplicates()
	{
		record("Alice", "Old", false);
		record("Bob", "Recent", false);
		record("Carol", "Newest", false);
		record("Alice", "Outgoing elsewhere", true);
		assertEquals(List.of("Carol", "Alice", "Bob"), names(widget.snapshot(options(3, 1, false))));
		record("ALICE", "A newer incoming message", false);
		assertEquals(List.of("Alice", "Carol", "Bob"), names(widget.snapshot(options(10, 1, false))));
	}

	@Test
	public void oneChatFollowsTheLatestSenderInBothLayouts()
	{
		for (boolean compact : List.of(false, true))
		{
			record("Alice", "First incoming", false);
			record("Bob", "New incoming from Bob", false);
			WidgetView.Chat latest = widget.snapshot(options(1, 1, compact)).getChats().get(0);
			assertEquals("Bob", latest.getPlayer());
			assertTrue(latest.isUnread());
			if (!compact) { assertEquals("New incoming from Bob", latest.getMessages().get(0).getText()); }
			record("Alice", "Outgoing elsewhere", true);
			assertEquals(List.of("Bob"), names(widget.snapshot(options(1, 1, compact))));
			conversations.getLatestIncomingConversation().markRead();
			assertEquals(List.of("Bob"), names(widget.snapshot(options(1, 1, compact))));
			assertFalse(widget.snapshot(options(1, 1, compact)).getChats().get(0).isUnread());
		}
	}

	@Test
	public void historyRestoreDoesNotStealLivePriorityAndClearResetsIt()
	{
		PrivateMessage saved = new PrivateMessage("Carol", "Saved incoming", Instant.ofEpochSecond(200), false);
		conversations.mergeSavedHistory(List.of(saved));
		assertEquals(List.of("Carol"), names(widget.snapshot(options(1, 1, false))));
		assertFalse(widget.snapshot(options(1, 1, false)).getChats().get(0).isUnread());
		record("Bob", "Live incoming", false);
		conversations.mergeSavedHistory(List.of(saved));
		assertEquals(List.of("Bob"), names(widget.snapshot(options(1, 1, false))));
		conversations.clear();
		assertNull(conversations.getLatestIncomingConversation());
		assertTrue(widget.snapshot(options(1, 1, false)).getChats().isEmpty());
	}

	@Test
	public void previewsAreBoundedChronologicalAndNeverMarkMessagesRead()
	{
		for (int i = 0; i < 20; i++) { record("Alice", "Message " + i, i % 2 == 0); }
		WidgetView.Chat chat = widget.snapshot(options(3, 3, false)).getChats().get(0);
		assertEquals(List.of("Message 17", "Message 18", "Message 19"), chat.getMessages().stream().map(PrivateMessage::getText).collect(Collectors.toList()));
		assertTrue(chat.getMessages().get(1).isOutgoing());
		assertTrue(chat.isUnread());
		assertTrue(conversations.getConversations().get(0).isUnread());
		assertTrue(widget.snapshot(options(3, 0, false)).getChats().get(0).getMessages().isEmpty());
		record("Alice", "Another", false);
		assertEquals("Message 19", chat.getMessages().get(2).getText());
	}

	@Test
	public void resolvedCommandsRefreshPreviewsWithoutDuplicatingTheMessage()
	{
		PrivateMessage command = new PrivateMessage("Alice", "!kc zulrah", Instant.ofEpochSecond(100), false);
		conversations.record(command);
		WidgetView before = widget.snapshot(options(1, 1, false));
		conversations.updateMessage(new PrivateMessage(command.getId(), "Alice", "Zulrah: 42 killed", command.getTimestamp(), false));
		WidgetView.Chat after = widget.snapshot(options(1, 1, false)).getChats().get(0);
		assertEquals("Zulrah: 42 killed", after.getMessages().get(0).getText());
		assertEquals(command.getId(), after.getMessages().get(0).getId());
		assertEquals(1, after.getMessages().size());
		assertTrue(after.isUnread());
		assertEquals("!kc zulrah", before.getChats().get(0).getMessages().get(0).getText());
	}

	@Test
	public void accountChangesRejectOldClicksIncludingAfterLoggingBackIntoTheSameAccount()
	{
		long token = widget.snapshot(options(1, 1, false)).getContextToken();
		assertTrue(widget.isCurrent(token));
		widget.switchAccount("a");
		assertTrue(widget.isCurrent(token));
		widget.switchAccount(null);
		assertFalse(widget.isCurrent(token));
		widget.switchAccount("a");
		assertFalse(widget.isCurrent(token));
		assertTrue(widget.isCurrent(widget.snapshot(options(1, 1, true)).getContextToken()));
	}

	@Test
	public void compactSnapshotsAlwaysLoadAvatarsButOmitPreviews()
	{
		BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
		AvatarStorage storage = new AvatarStorage()
		{
			public CompletableFuture<Map<String, BufferedImage>> stock() { return CompletableFuture.completedFuture(Map.of("default", image)); }
			public CompletableFuture<BufferedImage> load(String account, String player) { return CompletableFuture.completedFuture(image); }
			public CompletableFuture<BufferedImage> selectStock(String account, String player, String id) { throw new UnsupportedOperationException(); }
			public CompletableFuture<BufferedImage> importImage(String account, String player, Filepath file) { throw new UnsupportedOperationException(); }
			public CompletableFuture<Void> reset(String account, String player) { throw new UnsupportedOperationException(); }
		};
		AvatarService avatars = new AvatarService(storage, Runnable::run, () -> { }, message -> fail(message));
		avatars.switchAccount("a");
		WidgetPresenter withAvatars = new WidgetPresenter(conversations, avatars, friends);
		record("Alice", "Private preview text", false);
		assertNull(withAvatars.snapshot(options(1, 3, false)).getChats().get(0).getAvatar());
		WidgetView.Chat compact = withAvatars.snapshot(options(1, 3, true)).getChats().get(0);
		assertSame(image, compact.getAvatar());
		assertTrue(compact.getMessages().isEmpty());
		assertTrue(compact.isUnread());
		WidgetOptions glow = new WidgetOptions(true, 1, 3, false, false, true, true, WidgetUnreadStyle.GLOW, true);
		assertSame("Glow requires an avatar even when regular avatars are disabled", image,
			withAvatars.snapshot(glow).getChats().get(0).getAvatar());
	}

	private WidgetOptions options(int count, int previews, boolean compact)
	{
		return new WidgetOptions(true, count, previews, compact, false, true, true, WidgetUnreadStyle.PILL, true);
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
