package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
