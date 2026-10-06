package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.application.BossIconService;
import com.enhancedmessaging.application.BossIconSource;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
			assertEquals(FontManager.getDefaultFont().deriveFont(12f), body.getFont());
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
			assertEquals("Both directions use a consistent column", transcript.getComponent(1).getX(), transcript.getComponent(2).getX());
			assertEquals(transcript.getComponent(1).getWidth(), transcript.getComponent(2).getWidth());
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
	public void resolvingACommandUpdatesItsCachedBoxAndRemeasuresWrappedText() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			PrivateMessage raw = new PrivateMessage("Alice", "!kc zulrah", Instant.now(), false);
			MessageTranscript transcript = new MessageTranscript();
			transcript.setSize(120, 1000);
			transcript.setMessages(List.of(raw));
			int initialHeight = transcript.getPreferredSize().height;
			JTextArea originalBody = body(transcript);
			String resolved = "Zulrah: 42 killed. More resolved command detail. ".repeat(10);
			PrivateMessage updated = new PrivateMessage(raw.getId(), raw.getPlayerName(), resolved, raw.getTimestamp(), raw.isOutgoing());
			assertTrue(transcript.setMessages(List.of(updated)));
			assertEquals(resolved, originalBody.getText());
			assertTrue(originalBody == body(transcript));
			assertTrue(transcript.getPreferredSize().height > initialHeight);
			assertEquals(2, transcript.getComponentCount());
		});
	}

	@Test
	public void resolvedCommandBecomesABossCardAndDelayedIconDoesNotChangeItsHeight() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			MessageTranscript transcript = new MessageTranscript();
			CompletableFuture<BufferedImage> loaded = new CompletableFuture<>();
			BossIconService icons = new BossIconService(iconSource(loaded), Runnable::run, transcript::refreshBossIcons);
			transcript.setBossIcons(icons);
			transcript.setSize(220, 1000);
			PrivateMessage command = new PrivateMessage("Alice", "!kc vardorvis", Instant.now(), false);
			transcript.setMessages(List.of(command));
			assertEquals("!kc vardorvis", body(transcript).getText());
			PrivateMessage result = new PrivateMessage(command.getId(), "Alice", "Vardorvis kill count: 1,401", command.getTimestamp(), false);
			assertTrue(transcript.setMessages(List.of(result)));
			assertTrue(hasText(transcript, "Vardorvis"));
			assertTrue(hasText(transcript, "Kill count: 1,401"));
			JLabel icon = findBossIcon(transcript);
			assertNotNull(icon);
			assertNull(icon.getIcon());
			int height = transcript.getPreferredSize().height;
			loaded.complete(new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB));
			assertNotNull(icon.getIcon());
			assertEquals(25, icon.getIcon().getIconWidth());
			assertEquals(height, transcript.getPreferredSize().height);
			assertEquals(2, transcript.getComponentCount());
			icons.close();
		});
	}

	@Test
	public void unknownBossesRemainPlainAndCardsCanReturnToOrdinaryMessages() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			MessageTranscript transcript = new MessageTranscript();
			BossIconService icons = new BossIconService(iconSource(CompletableFuture.completedFuture(null)),
				Runnable::run, transcript::refreshBossIcons);
			transcript.setBossIcons(icons);
			PrivateMessage unknown = new PrivateMessage("Alice", "Unknown boss kill count: 42", Instant.now(), false);
			transcript.setMessages(List.of(unknown));
			assertEquals(unknown.getText(), body(transcript).getText());
			PrivateMessage card = new PrivateMessage(unknown.getId(), "Alice", "Vardorvis kill count: 42", unknown.getTimestamp(), false);
			transcript.setMessages(List.of(card));
			assertTrue(hasText(transcript, "Kill count: 42"));
			transcript.setMessages(List.of(new PrivateMessage(unknown.getId(), "Alice", "Thanks!", unknown.getTimestamp(), false)));
			assertEquals("Thanks!", body(transcript).getText());
			assertFalse(hasText(transcript, "Vardorvis"));
			icons.close();
		});
	}

	@Test
	public void restoredBossCardsWrapWithinANarrowSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			MessageTranscript transcript = new MessageTranscript();
			transcript.setMessages(List.of(new PrivateMessage("Alice", "Chambers of Xeric: Challenge Mode kill count: 1,401", Instant.now(), true)));
			transcript.setSize(220, 1000);
			int wide = transcript.getPreferredSize().height;
			transcript.setSize(120, 1000);
			assertTrue(transcript.getPreferredSize().height > wide);
			layout(transcript);
			assertTrue(hasText(transcript, "Chambers of Xeric: Challenge Mode"));
			assertEquals(0, transcript.getMinimumSize().width);
			assertEquals(MessageStyle.OUTGOING_BACKGROUND, transcript.getComponent(1).getBackground());
		});
	}

	private BossIconSource iconSource(CompletableFuture<BufferedImage> loaded)
	{
		return new BossIconSource()
		{
			public String canonicalName(String name) { return name.equals("Vardorvis") ? name : null; }
			public CompletableFuture<BufferedImage> load(String name) { return loaded; }
		};
	}

	private JLabel findBossIcon(Container parent)
	{
		for (Component component : parent.getComponents())
		{
			if (component instanceof JLabel && "Vardorvis".equals(((JLabel) component).getToolTipText())) { return (JLabel) component; }
			if (component instanceof Container)
			{
				JLabel label = findBossIcon((Container) component);
				if (label != null) { return label; }
			}
		}
		return null;
	}

	private boolean hasText(Container parent, String text)
	{
		return Arrays.stream(parent.getComponents()).anyMatch(component ->
			component instanceof JTextArea && text.equals(((JTextArea) component).getText())
				|| component instanceof Container && hasText((Container) component, text));
	}

	@Test
	public void longParagraphsAndUnbrokenTextUseTheFullColumnWithCompactFooterTimes() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			MessageTranscript transcript = new MessageTranscript();
			Instant time = LocalDate.of(2026, 10, 6).atTime(14, 22).atZone(ZoneId.systemDefault()).toInstant();
			String paragraph = "A longer message with paragraphs that should be easy to read. ".repeat(5) + "\n\n" + "x".repeat(160);
			transcript.setMessages(List.of(
				new PrivateMessage("Alice", paragraph, time, false),
				new PrivateMessage("Alice", "A short reply", time.plusSeconds(10), true)));
			transcript.setSize(200, 1000);
			layout(transcript);
			Component incoming = transcript.getComponent(1);
			Component outgoing = transcript.getComponent(2);
			assertEquals(incoming.getX(), outgoing.getX());
			assertEquals(incoming.getWidth(), outgoing.getWidth());
			assertTrue("Paragraphs must use almost the entire transcript width", incoming.getWidth() >= 190);
			assertEquals(paragraph, body(transcript).getText());
			assertTrue(body(transcript).getHeight() >= body(transcript).getPreferredSize().height);
			assertTrue(hasLabel((Container) incoming, "14:22"));
			assertFalse(hasLabel((Container) incoming, "14:22:00"));
			Container footer = (Container) ((java.awt.BorderLayout) ((Container) incoming).getLayout()).getLayoutComponent(java.awt.BorderLayout.SOUTH);
			assertTrue("The timestamp belongs below the message", footer.getY() >= body(transcript).getY() + body(transcript).getHeight());
			assertTrue("A one-line reply should stay compact", outgoing.getHeight() <= 40);
		});
	}

	@Test
	public void resizingReflowsTheSameMessageBetweenInlineAndFooterTimestamps() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			MessageTranscript transcript = new MessageTranscript();
			String text = "A somewhat longer reply that still fits on one wide row.";
			transcript.setMessages(List.of(new PrivateMessage("Alice", text, Instant.now(), false)));
			transcript.setSize(500, 1000);
			layout(transcript);
			Container message = (Container) transcript.getComponent(1);
			java.awt.BorderLayout layout = (java.awt.BorderLayout) message.getLayout();
			assertNotNull(layout.getLayoutComponent(java.awt.BorderLayout.EAST));
			int wideHeight = message.getHeight();
			transcript.setSize(120, 1000);
			layout(transcript);
			assertNotNull(layout.getLayoutComponent(java.awt.BorderLayout.SOUTH));
			assertTrue(message.getHeight() > wideHeight);
			assertEquals(text, body(transcript).getText());
			transcript.setSize(500, 1000);
			layout(transcript);
			assertNotNull(layout.getLayoutComponent(java.awt.BorderLayout.EAST));
			assertEquals(wideHeight, message.getHeight());
		});
	}

	@Test
	public void directionsHaveOppositeAccentEdgesAndSmallerMessageFonts() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			MessageTranscript transcript = new MessageTranscript();
			transcript.setMessages(List.of(
				new PrivateMessage("Alice", "Incoming", Instant.now(), false),
				new PrivateMessage("Alice", "Outgoing", Instant.now(), true)));
			transcript.setSize(200, 1000);
			layout(transcript);
			assertEquals(12, body(transcript).getFont().getSize());
			for (int i = 1; i <= 2; i++)
			{
				Component row = transcript.getComponent(i);
				BufferedImage image = new BufferedImage(row.getWidth(), row.getHeight(), BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D graphics = image.createGraphics();
				try { row.paint(graphics); } finally { graphics.dispose(); }
				if (i == 1)
				{
					assertEquals(MessageStyle.INCOMING_ACCENT.getRGB(), image.getRGB(0, row.getHeight() / 2));
					assertEquals(MessageStyle.INCOMING_BACKGROUND.getRGB(), image.getRGB(row.getWidth() - 1, row.getHeight() / 2));
				}
				else
				{
					assertEquals(MessageStyle.OUTGOING_BACKGROUND.getRGB(), image.getRGB(0, row.getHeight() / 2));
					assertEquals(MessageStyle.OUTGOING_ACCENT.getRGB(), image.getRGB(row.getWidth() - 1, row.getHeight() / 2));
				}
				Container time = (Container) ((java.awt.BorderLayout) ((Container) row).getLayout()).getLayoutComponent(java.awt.BorderLayout.EAST);
				JLabel label = (JLabel) time.getComponent(0);
				assertNotNull(label.getIcon());
				assertEquals(i == 1 ? "Received" : "Sent", label.getToolTipText());
			}
			transcript.setMessages(List.of(new PrivateMessage("Alice", "Vardorvis kill count: 1,401", Instant.now(), true)));
			assertEquals(12, textAreaWithText(transcript, "Kill count: 1,401").getFont().getSize());
		});
	}

	private JTextArea textAreaWithText(Container parent, String text)
	{
		for (Component component : parent.getComponents())
		{
			if (component instanceof JTextArea && text.equals(((JTextArea) component).getText())) { return (JTextArea) component; }
			if (component instanceof Container)
			{
				JTextArea found = textAreaWithText((Container) component, text);
				if (found != null) { return found; }
			}
		}
		return null;
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
