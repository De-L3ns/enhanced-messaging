package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EnhancedMessagingPanelTest
{
	@Test
	public void openingAnEmptyPanelDoesNotRequireAnOversizedClient() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			EnhancedMessagingPanel panel = createPanel(new ConversationService());
			assertTrue("Minimum panel size: " + panel.getMinimumSize(), panel.getMinimumSize().height <= 300);
			assertTrue("Preferred panel size: " + panel.getPreferredSize(), panel.getPreferredSize().height <= 500);
		});
	}

	@Test
	public void longConversationsDoNotIncreaseTheRequiredClientHeight() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationService service = new ConversationService();
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
			ConversationService service = new ConversationService();
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
			assertTrue(transcriptText(transcript).contains("You"));
			assertTrue(transcriptText(transcript).contains("My reply"));
		});
	}

	@Test
	public void clearingHistoryRemovesPlayersAndMessagesFromThePanel() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationService service = new ConversationService();
			service.record(message("Alice", "Private message", false));
			EnhancedMessagingPanel panel = createPanel(service);
			service.clear();
			panel.refresh();

			assertEquals(0, findConversationList(panel).getModel().getSize());
			assertFalse(transcriptText(findTranscript(panel)).contains("Private message"));
			assertTrue(transcriptText(findTranscript(panel)).contains("Send or receive a private message"));
		});
	}

	private EnhancedMessagingPanel createPanel(ConversationService service)
	{
		return new EnhancedMessagingPanel(service, null, () -> { });
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
			ConversationService service = new ConversationService();
			service.record(message("Alice", "Arrived while hidden", false));
			EnhancedMessagingPanel panel = createPanel(service);
			Conversation alice = service.getConversations().get(0);
			assertTrue(alice.isUnread());
			panel.onActivate();
			assertFalse(alice.isUnread());
			service.record(message("Bob", "Another conversation", false));
			panel.refresh();
			Conversation bob = service.getConversations().get(0);
			assertTrue(bob.isUnread());
			findConversationList(panel).setSelectedIndex(0);
			assertFalse(bob.isUnread());
			service.record(message("Bob", "Already viewing Bob", false));
			panel.refresh();
			assertFalse(bob.isUnread());
			panel.onDeactivate();
			service.record(message("Bob", "Panel hidden again", false));
			panel.refresh();
			assertTrue(bob.isUnread());
			panel.close();
		});
	}
}
