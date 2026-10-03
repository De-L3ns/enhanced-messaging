package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import javax.swing.JList;
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
			EnhancedMessagingPanel panel = new EnhancedMessagingPanel(new ConversationService());
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
			EnhancedMessagingPanel panel = new EnhancedMessagingPanel(service);
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
			EnhancedMessagingPanel panel = new EnhancedMessagingPanel(service);
			JList<?> list = findConversationList(panel);
			JTextArea transcript = findTranscript(panel);
			assertNotNull(list);
			assertNotNull(transcript);

			service.record(message("Bob", "Bob's message", false));
			panel.refresh();
			assertEquals("Alice", ((Conversation) list.getSelectedValue()).getPlayerName());
			assertTrue(transcript.getText().contains("Alice's message"));
			assertFalse(transcript.getText().contains("Bob's message"));

			list.setSelectedIndex(0);
			assertEquals("Bob", ((Conversation) list.getSelectedValue()).getPlayerName());
			assertTrue(transcript.getText().contains("Bob's message"));
			assertFalse(transcript.getText().contains("Alice's message"));

			service.record(message("Bob", "My reply", true));
			panel.refresh();
			assertTrue(transcript.getText().contains("You:\nMy reply"));
		});
	}

	@Test
	public void clearingHistoryRemovesPlayersAndMessagesFromThePanel() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConversationService service = new ConversationService();
			service.record(message("Alice", "Private message", false));
			EnhancedMessagingPanel panel = new EnhancedMessagingPanel(service);
			service.clear();
			panel.refresh();

			assertEquals(0, findConversationList(panel).getModel().getSize());
			assertFalse(findTranscript(panel).getText().contains("Private message"));
			assertTrue(findTranscript(panel).getText().contains("Send or receive a private message"));
		});
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

	private JTextArea findTranscript(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JTextArea && child.isFocusable())
			{
				return (JTextArea) child;
			}
			if (child instanceof Container)
			{
				JTextArea area = findTranscript((Container) child);
				if (area != null)
				{
					return area;
				}
			}
		}
		return null;
	}
}
