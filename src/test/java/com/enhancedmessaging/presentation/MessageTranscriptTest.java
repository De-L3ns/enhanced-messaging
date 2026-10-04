package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Arrays;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageTranscriptTest
{
	@Test
	public void narrowerViewsWrapTheCompleteMessageInsteadOfClippingIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			String text = "A message with enough words to wrap across many lines in a narrow sidebar. ".repeat(8);
			MessageTranscript transcript = new MessageTranscript();
			transcript.setMessages(List.of(new PrivateMessage("Alice", text, Instant.now(), false)));
			transcript.setSize(200, 1000);
			int wideHeight = transcript.getPreferredSize().height;
			transcript.setSize(120, 1000);
			int narrowHeight = transcript.getPreferredSize().height;
			assertTrue(narrowHeight > wideHeight);
			layout(transcript);
			JTextArea body = body(transcript);
			assertEquals(text, body.getText());
			assertTrue(body.getHeight() >= body.getPreferredSize().height);
			assertEquals(FontManager.getDefaultFont(), body.getFont());
			assertEquals(ColorScheme.TEXT_COLOR, body.getForeground());
		});
	}

	@Test
	public void datesGroupMessagesByLocalDayAndDirectionsUseColourWithoutSenderLabels() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Instant late = LocalDate.of(2026, 10, 4).atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant();
			PrivateMessage incoming = new PrivateMessage("Alice", "Incoming", late, false);
			PrivateMessage outgoing = new PrivateMessage("Alice", "Outgoing", late.plusSeconds(10), true);
			PrivateMessage nextDay = new PrivateMessage("Alice", "Next day", late.plusSeconds(120), false);
			MessageTranscript transcript = new MessageTranscript();
			transcript.setMessages(List.of(incoming, outgoing, nextDay));
			assertEquals(5, transcript.getComponentCount());
			assertEquals("4 Oct 2026", ((JLabel) transcript.getComponent(0)).getText());
			assertEquals("5 Oct 2026", ((JLabel) transcript.getComponent(3)).getText());
			assertEquals(MessageStyle.INCOMING_BACKGROUND, transcript.getComponent(1).getBackground());
			assertEquals(MessageStyle.OUTGOING_BACKGROUND, transcript.getComponent(2).getBackground());
			assertEquals("Outgoing message", ((JPanel) transcript.getComponent(2)).getToolTipText());
			assertFalse(hasLabel(transcript, "Alice"));
			assertFalse(hasLabel(transcript, "You"));
			transcript.setSize(200, 1000);
			layout(transcript);
			assertTrue(transcript.getComponent(2).getX() > transcript.getComponent(1).getX());
			transcript.setMessages(List.of(nextDay));
			assertEquals(2, transcript.getComponentCount());
			assertEquals("5 Oct 2026", ((JLabel) transcript.getComponent(0)).getText());
		});
	}

	private boolean hasLabel(Container parent, String text)
	{
		return Arrays.stream(parent.getComponents()).anyMatch(component ->
			component instanceof JLabel && text.equals(((JLabel) component).getText())
				|| component instanceof Container && hasLabel((Container) component, text));
	}

	@Test
	public void messagesRenderMarkupLiterally() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			MessageTranscript transcript = new MessageTranscript();
			transcript.setMessages(List.of(new PrivateMessage("Alice", "<html>literal text</html>", Instant.now(), false)));
			assertEquals("<html>literal text</html>", body(transcript).getText());
		});
	}

	private void layout(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}

	private JTextArea body(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JTextArea)
			{
				return (JTextArea) child;
			}
			if (child instanceof Container)
			{
				JTextArea found = body((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
