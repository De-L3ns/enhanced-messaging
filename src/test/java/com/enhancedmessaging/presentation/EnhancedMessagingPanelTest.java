package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.ConversationHistory;
import com.enhancedmessaging.application.FriendStatusService;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.FriendStatus;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EnhancedMessagingPanelTest
{
	@Test
	public void widgetNavigationSelectsTheExactChatWithoutChangingHistory() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationHistory conversations = new ConversationHistory();
			conversations.record(message("Alice", "Hello", false));
			conversations.record(message("Bob", "Hi", false));
			EnhancedMessagingPanel panel = createPanel(conversations);
			panel.setReadChanged(() -> { });
			panel.refresh();
			assertEquals(2, findConversationList(panel).getModel().getSize());
			panel.selectConversation("alice");
			assertEquals("Alice", ((Conversation) findConversationList(panel).getSelectedValue()).getPlayerName());
			assertTrue(conversations.getConversations().stream().filter(c -> c.getPlayerName().equals("Alice")).findFirst().get().isUnread());
			panel.onActivate();
			assertFalse(conversations.getConversations().stream().filter(c -> c.getPlayerName().equals("Alice")).findFirst().get().isUnread());
			List<Conversation> before = conversations.getConversations();
			panel.selectConversation("Bob");
			assertEquals("Bob", ((Conversation) findConversationList(panel).getSelectedValue()).getPlayerName());
			assertTrue(transcriptText(findTranscript(panel)).contains("Hi"));
			assertEquals(before, conversations.getConversations());
			panel.close();
		});
	}

	@Test
	public void statusRefreshKeepsUnreadSelectionAndTranscript() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationHistory conversations = new ConversationHistory();
			conversations.record(message("Alice", "Hello", false));
			FriendStatusService friends = new FriendStatusService();
			friends.switchAccount("account-a");
			EnhancedMessagingPanel panel = new EnhancedMessagingPanel(conversations, null, friends, () -> { });
			JList<?> list = findConversationList(panel);
			Object selected = list.getSelectedValue();
			String transcript = transcriptText(findTranscript(panel));
			friends.update("account-a", Map.of("alice", FriendStatus.ONLINE));
			panel.refreshAvatars();
			assertTrue(renderSelected(list).getToolTipText().startsWith("Online."));
			friends.update("account-a", Map.of("alice", FriendStatus.OFFLINE));
			panel.refreshAvatars();
			assertTrue(renderSelected(list).getToolTipText().startsWith("Offline."));
			assertEquals(selected, list.getSelectedValue());
			assertEquals(transcript, transcriptText(findTranscript(panel)));
			assertTrue(conversations.getConversations().get(0).isUnread());
			friends.switchAccount("account-b");
			panel.refreshAvatars();
			assertTrue(renderSelected(list).getToolTipText().startsWith("Status unavailable."));
			panel.close();
		});
	}

	private <T> JComponent renderSelected(JList<T> list)
	{
		return (JComponent) list.getCellRenderer().getListCellRendererComponent(
			list, list.getSelectedValue(), list.getSelectedIndex(), true, false);
	}

	@Test
	public void openingAnEmptyPanelDoesNotRequireAnOversizedClient() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			EnhancedMessagingPanel panel = createPanel(new ConversationHistory());
			assertTrue("Minimum panel size: " + panel.getMinimumSize(), panel.getMinimumSize().height <= 300);
			assertTrue("Preferred panel size: " + panel.getPreferredSize(), panel.getPreferredSize().height <= 500);
		});
	}

	@Test
	public void longConversationsDoNotIncreaseTheRequiredClientHeight() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationHistory service = new ConversationHistory();
			EnhancedMessagingPanel panel = createPanel(service);
			int initialMinimumHeight = panel.getMinimumSize().height;
			int initialPreferredHeight = panel.getPreferredSize().height;
			for (int i = 0; i < Conversation.MAX_MESSAGES; i++)
			{
				service.record(message("Alice", "A long message that should wrap and scroll. ".repeat(5), false));
			}
			panel.refresh();

			assertTrue(panel.getMinimumSize().height <= initialMinimumHeight);
			assertTrue(panel.getPreferredSize().height <= initialPreferredHeight);
			panel.setSize(panel.getPreferredSize().width, 400);
			layoutChildren(panel);
			assertTrue("Transcript must fit within the sidebar", findTranscript(panel).getVisibleRect().height > 0);
		});
	}

	@Test
	public void preservesSelectedPlayerWhenAnotherConversationMovesToTheTop() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationHistory service = new ConversationHistory();
			service.record(message("Alice", "Alice's message", false));
			EnhancedMessagingPanel panel = createPanel(service);
			JList<?> list = findConversationList(panel);
			MessageTranscript transcript = findTranscript(panel);
			assertNotNull(list);
			assertNotNull(transcript);

			service.record(message("Bob", "Bob's message", false));
			panel.refresh();
			assertEquals("Alice", ((Conversation) list.getSelectedValue()).getPlayerName());
			assertTrue(transcriptText(transcript).contains("Alice's message"));
			assertFalse(transcriptText(transcript).contains("Bob's message"));

			list.setSelectedIndex(0);
			assertEquals("Bob", ((Conversation) list.getSelectedValue()).getPlayerName());
			assertTrue(transcriptText(transcript).contains("Bob's message"));
			assertFalse(transcriptText(transcript).contains("Alice's message"));

			service.record(message("Bob", "My reply", true));
			panel.refresh();
			assertFalse(transcriptText(transcript).contains("You"));
			assertTrue(transcriptText(transcript).contains("My reply"));
		});
	}

	@Test
	public void receivedMessagesScrollToTheBottomEvenWhenReadingOlderMessages() throws Exception
	{
		ConversationHistory service = new ConversationHistory();
		EnhancedMessagingPanel[] panel = new EnhancedMessagingPanel[1];
		JScrollBar[] bar = new JScrollBar[1];
		SwingUtilities.invokeAndWait(() ->
		{
			for (int i = 0; i < 40; i++) { service.record(message("Alice", "Message " + i, false)); }
			panel[0] = createPanel(service);
			panel[0].setSize(230, 400);
			layoutChildren(panel[0]);
			bar[0] = ((JScrollPane) findTranscript(panel[0]).getParent().getParent()).getVerticalScrollBar();
		});
		SwingUtilities.invokeAndWait(() ->
		{
			bar[0].setValue(0);
			assertTrue(bar[0].getMaximum() > bar[0].getVisibleAmount());
			service.record(message("Alice", "The latest incoming message", false));
			panel[0].refresh();
			layoutChildren(panel[0]);
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(bar[0].getMaximum(), bar[0].getValue() + bar[0].getVisibleAmount());
			assertTrue(transcriptText(findTranscript(panel[0])).contains("The latest incoming message"));
			panel[0].close();
		});
	}

	@Test
	public void clearingHistoryRemovesPlayersAndMessagesFromThePanel() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationHistory service = new ConversationHistory();
			service.record(message("Alice", "Private message", false));
			EnhancedMessagingPanel panel = createPanel(service);
			service.clear();
			panel.refresh();

			assertEquals(0, findConversationList(panel).getModel().getSize());
			assertFalse(transcriptText(findTranscript(panel)).contains("Private message"));
			assertTrue(transcriptText(findTranscript(panel)).contains("Send or receive a private message"));
		});
	}

	private EnhancedMessagingPanel createPanel(ConversationHistory service)
	{
		return new EnhancedMessagingPanel(service, null, new FriendStatusService(), () -> { });
	}

	private PrivateMessage message(String player, String text, boolean outgoing)
	{
		return new PrivateMessage(player, text, Instant.ofEpochSecond(100), outgoing);
	}

	private void layoutChildren(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container)
			{
				layoutChildren((Container) child);
			}
		}
	}

	private JList<?> findConversationList(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JList)
			{
				return (JList<?>) child;
			}
			if (child instanceof Container)
			{
				JList<?> list = findConversationList((Container) child);
				if (list != null)
				{
					return list;
				}
			}
		}
		return null;
	}

	private MessageTranscript findTranscript(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof MessageTranscript)
			{
				return (MessageTranscript) child;
			}
			if (child instanceof Container)
			{
				MessageTranscript area = findTranscript((Container) child);
				if (area != null)
				{
					return area;
				}
			}
		}
		return null;
	}

	private JLabel findLabel(Container parent, String text)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel && text.equals(((JLabel) child).getText()))
			{
				return (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel label = findLabel((Container) child, text);
				if (label != null)
				{
					return label;
				}
			}
		}
		return null;
	}

	private String transcriptText(Container parent)
	{
		StringBuilder text = new StringBuilder();
		for (Component child : parent.getComponents())
		{
			if (child instanceof JTextArea)
			{
				text.append(((JTextArea) child).getText()).append('\n');
			}
			else if (child instanceof JLabel)
			{
				text.append(((JLabel) child).getText()).append('\n');
			}
			else if (child instanceof Container)
			{
				text.append(transcriptText((Container) child));
			}
		}
		return text.toString();
	}

	@Test
	public void unreadOnlyClearsWhenTheSelectedConversationIsVisible() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationHistory service = new ConversationHistory();
			service.record(message("Alice", "Arrived while hidden", false));
			EnhancedMessagingPanel panel = createPanel(service);
			Conversation alice = service.getConversations().get(0);
			JList<?> list = findConversationList(panel);
			JComponent row = renderSelected(list);
			row.setSize(220, 36);
			layoutChildren(row);
			JLabel badge = findLabel(row, "New");
			assertTrue(badge.isVisible());
			assertTrue("New pill must fit the right side of the row",
				SwingUtilities.convertPoint(badge, 0, 0, row).x > row.getWidth() / 2
					&& badge.getWidth() >= badge.getPreferredSize().width);
			assertTrue(alice.isUnread());
			panel.onActivate();
			assertFalse(alice.isUnread());
			assertFalse(findLabel(renderSelected(list), "New").isVisible());
			service.record(message("Bob", "Another conversation", false));
			panel.refresh();
			Conversation bob = service.getConversations().get(0);
			assertTrue(bob.isUnread());
			findConversationList(panel).setSelectedIndex(0);
			assertFalse(bob.isUnread());
			service.record(message("Bob", "Already viewing Bob", false));
			panel.refresh();
			assertFalse(bob.isUnread());
			assertFalse(findLabel(renderSelected(list), "New").isVisible());
			panel.onDeactivate();
			service.record(message("Bob", "Panel hidden again", false));
			panel.refresh();
			assertTrue(bob.isUnread());
			assertTrue(findLabel(renderSelected(list), "New").isVisible());
			panel.close();
		});
	}
}
