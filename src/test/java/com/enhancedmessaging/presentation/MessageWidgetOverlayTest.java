package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.WidgetOptions;
import com.enhancedmessaging.application.WidgetView;
import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.WidgetChatMode;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.*;

public class MessageWidgetOverlayTest
{
	private final AtomicBoolean visible = new AtomicBoolean(true);
	private final MessageWidgetOverlay overlay = new MessageWidgetOverlay(null, visible::get, () -> true, () -> 500);
	private final List<MessageWidgetOverlay.Action> actions = new ArrayList<>();
	private final MessageWidgetMouseListener mouse = new MessageWidgetMouseListener(overlay, actions::add);
	private final JPanel eventSource = new JPanel();

	@Test
	public void rowAndPinClicksHaveSeparateActionsAndDoNotReachTheGame()
	{
		publish(true, true);
		MouseEvent down = event(MouseEvent.MOUSE_PRESSED, 75, 110, 0);
		mouse.mousePressed(down);
		assertTrue(down.isConsumed());
		MouseEvent up = event(MouseEvent.MOUSE_RELEASED, 75, 110, 0);
		mouse.mouseReleased(up);
		assertTrue(up.isConsumed());
		MouseEvent clicked = event(MouseEvent.MOUSE_CLICKED, 75, 110, 0);
		mouse.mouseClicked(clicked);
		assertTrue(clicked.isConsumed());
		assertEquals("Alice", actions.get(0).getPlayer());
		assertFalse(actions.get(0).isPin());
		mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 267, 85, 0));
		mouse.mouseReleased(event(MouseEvent.MOUSE_RELEASED, 267, 85, 0));
		assertTrue(actions.get(1).isPin());
		assertEquals(7, actions.get(1).getContextToken());
	}

	@Test
	public void passiveRowsStillAllowPinsButNotBlankSpaceOrOverlayDragGestures()
	{
		publish(false, true);
		assertNull(overlay.actionAt(new Point(75, 110)));
		assertNotNull(overlay.actionAt(new Point(267, 85)));
		assertNull(overlay.actionAt(new Point(51, 61)));
		MouseEvent drag = event(MouseEvent.MOUSE_PRESSED, 267, 85, InputEvent.ALT_DOWN_MASK);
		mouse.mousePressed(drag);
		assertFalse(drag.isConsumed());
		publish(true, false);
		assertNull(overlay.actionAt(new Point(267, 85)));
		assertNotNull(overlay.actionAt(new Point(75, 110)));
	}

	@Test
	public void leavingTheRowOrHidingTheWidgetCancelsTheAction()
	{
		publish(true, true);
		mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 75, 110, 0));
		MouseEvent outside = event(MouseEvent.MOUSE_RELEASED, 500, 400, 0);
		mouse.mouseReleased(outside);
		assertTrue(outside.isConsumed());
		assertTrue(actions.isEmpty());
		mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 75, 110, 0));
		overlay.publish(null);
		mouse.mouseReleased(event(MouseEvent.MOUSE_RELEASED, 75, 110, 0));
		assertTrue(actions.isEmpty());
		publish(true, true);
		visible.set(false);
		assertNull(render(overlay));
		assertNull(overlay.actionAt(new Point(75, 110)));
	}

	@Test
	public void changingThePlayerAccountOrActionBeforeReleaseCancelsTheClick()
	{
		for (int change = 0; change < 3; change++)
		{
			publish(true, true);
			mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 75, 110, 0));
			WidgetView.Chat chat = new WidgetView.Chat(change == 0 ? "Bob" : "Alice", false, true,
				FriendStatus.ONLINE, null, List.of(message()));
			overlay.publish(new WidgetView(options(true), List.of(chat), change == 1 ? 8 : 7, true, ""));
			render(overlay);
			mouse.mouseReleased(event(MouseEvent.MOUSE_RELEASED, change == 2 ? 267 : 75, change == 2 ? 85 : 110, 0));
			assertTrue(actions.isEmpty());
		}
	}

	@Test
	public void largeWidgetsFitTheCanvasAndClippedRowsCannotBeClicked()
	{
		MessageWidgetOverlay small = new MessageWidgetOverlay(null, () -> true, () -> true, () -> 250);
		List<WidgetView.Chat> rows = new ArrayList<>();
		for (int i = 0; i < 10; i++)
		{
			rows.add(new WidgetView.Chat("Player " + i, false, true, FriendStatus.ONLINE, null,
				List.of(message(), message(), message())));
		}
		small.publish(new WidgetView(options(true), rows, 7, true, ""));
		small.setBounds(new Rectangle(0, 0, 240, 0));
		Dimension size = render(small);
		assertTrue(size.height <= 230);
		assertNotNull(small.actionAt(new Point(20, 50)));
		assertNull(small.actionAt(new Point(20, 210)));
	}

	@Test
	public void nativeResizeReflowsPreviewsUpdatesPinBoundsAndResetRestoresConfiguredWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			publish(true, true);
			assertTrue(overlay.isResizable());
			// The first chat starts immediately below the padding, with no title area.
			assertNotNull(overlay.actionAt(new Point(75, 75)));
			overlay.setPreferredSize(new Dimension(360, 180));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			Dimension size = render(overlay);
			assertEquals(new Dimension(360, 180), size);
			overlay.getBounds().setSize(size);
			assertNotNull(overlay.actionAt(new Point(387, 85)));
			assertTrue(overlay.actionAt(new Point(387, 85)).isPin());
			assertFalse(overlay.actionAt(new Point(267, 85)).isPin());
			overlay.setPreferredSize(null);
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(240, render(overlay).width);
			assertTrue(overlay.actionAt(new Point(267, 85)).isPin());
		});
	}

	@Test
	public void resizingHeightHidesOnlyWholeRowsAndCannotReviveDisabledWidgets() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			List<WidgetView.Chat> rows = List.of(
				new WidgetView.Chat("Alice", false, true, FriendStatus.ONLINE, null, List.of(message())),
				new WidgetView.Chat("Bob", false, true, FriendStatus.OFFLINE, null, List.of(message())));
			overlay.publish(new WidgetView(options(true), rows, 7, true, ""));
			overlay.setBounds(new Rectangle(0, 0, 240, 0));
			overlay.setPreferredSize(new Dimension(240, 110));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = new BufferedImage(240, 110, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try { assertEquals(110, overlay.render(graphics).height); } finally { graphics.dispose(); }
			assertEquals("A hidden chat must not leave a partial row above the footer",
				0, image.getRGB(10, 83));
			assertEquals("The outer padding must remain transparent", 0, image.getRGB(0, 20));
			assertEquals("Resizing taller must not add an outer background", 0, image.getRGB(230, 109));
			assertNotNull(overlay.actionAt(new Point(20, 20)));
			assertNull(overlay.actionAt(new Point(20, 95)));
			overlay.setPreferredSize(new Dimension(1, 1));
			assertEquals(new Dimension(180, 60), overlay.getPreferredSize());
			overlay.publish(null);
		});
		SwingUtilities.invokeAndWait(() -> assertNull(render(overlay)));
	}

	@Test
	public void shrinkingIntoBottomPaddingKeepsEveryChatThatStillFits() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			// A two-line preview ends at y=76; the remaining eight pixels are padding.
			overlay.publish(new WidgetView(options(true), List.of(new WidgetView.Chat("Alice", false, true,
				FriendStatus.ONLINE, null, List.of(message()))), 7, true, ""));
			overlay.setBounds(new Rectangle(0, 0, 240, 0));
			assertEquals(84, render(overlay).height);
			overlay.setPreferredSize(new Dimension(240, 76));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = new BufferedImage(240, 76, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try { assertEquals(76, overlay.render(graphics).height); } finally { graphics.dispose(); }
			assertNotNull(overlay.actionAt(new Point(20, 20)));
			assertNotNull(overlay.actionAt(new Point(20, 75)));
			assertEquals("The footer must not replace a chat that fits",
				net.runelite.client.ui.ColorScheme.MEDIUM_GRAY_COLOR.getRGB(), image.getRGB(10, 70));
		});
	}

	@Test
	public void footerDoesNotDisplaceAChatWhenThereAreMoreChatsBelowIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			overlay.publish(new WidgetView(options(true), List.of(
				new WidgetView.Chat("Alice", false, true, FriendStatus.ONLINE, null, List.of(message())),
				new WidgetView.Chat("Bob", false, true, FriendStatus.OFFLINE, null, List.of(message()))), 7, true, ""));
			overlay.setBounds(new Rectangle(0, 0, 240, 0));
			overlay.setPreferredSize(new Dimension(240, 90));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = new BufferedImage(240, 90, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try { assertEquals(90, overlay.render(graphics).height); } finally { graphics.dispose(); }
			assertNotNull(overlay.actionAt(new Point(20, 75)));
			assertNull(overlay.actionAt(new Point(20, 85)));
			assertEquals("No partial second chat should remain visible",
				0, image.getRGB(10, 83));
		});
	}

	@Test
	public void previewsWrapAtMostTwoLinesWithoutSplittingSurrogatePairs()
	{
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setFont(net.runelite.client.ui.FontManager.getDefaultFont());
			for (String text : List.of("A long message ".repeat(50), "a".repeat(200), "\ud83d\ude00".repeat(100), "<html> literal text\nnext line"))
			{
				List<String> lines = MessageWidgetOverlay.wrap(text, g.getFontMetrics(), 120);
				assertTrue(lines.size() <= 2);
				for (String line : lines)
				{
					assertTrue(g.getFontMetrics().stringWidth(line) <= 120);
					assertFalse(!line.isEmpty() && Character.isHighSurrogate(line.charAt(line.length() - 1)));
				}
			}
		}
		finally { g.dispose(); }
	}

	private void publish(boolean clickToOpen, boolean canPin)
	{
		overlay.publish(new WidgetView(options(clickToOpen), List.of(new WidgetView.Chat("Alice", false, true,
			FriendStatus.ONLINE, null, List.of(message()))), 7, canPin, ""));
		overlay.setBounds(new Rectangle(50, 60, 240, 0));
		Dimension size = render(overlay);
		overlay.getBounds().setSize(size);
	}
	private WidgetOptions options(boolean click)
	{
		return new WidgetOptions(true, 10, 3, WidgetChatMode.PINNED_AND_RECENT, true, true, true, 240, click);
	}
	private PrivateMessage message()
	{
		return new PrivateMessage("Alice", "A very long message that needs a bounded preview. ".repeat(10), Instant.ofEpochSecond(100), false);
	}
	private Dimension render(MessageWidgetOverlay target)
	{
		BufferedImage image = new BufferedImage(400, 1600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try { return target.render(g); } finally { g.dispose(); }
	}
	private MouseEvent event(int type, int x, int y, int modifiers)
	{
		// Unit-test events are called directly on our listener; never dispatched to a game client.
		return new MouseEvent(eventSource, type, 0, modifiers, x, y, 1, false, MouseEvent.BUTTON1);
	}
}
