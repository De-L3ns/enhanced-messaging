package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
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
	public void rowClicksDoNotReachTheGame()
	{
		publish(true);
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
		mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 267, 85, 0));
		mouse.mouseReleased(event(MouseEvent.MOUSE_RELEASED, 267, 85, 0));
		assertEquals(7, actions.get(1).getContextToken());
	}

	@Test
	public void passiveRowsAndBlankSpaceDoNotConsumeClicksAndOverlayDragGesturesArePreserved()
	{
		publish(false);
		assertNull(overlay.actionAt(new Point(75, 110)));
		assertNull(overlay.actionAt(new Point(267, 85)));
		assertNull(overlay.actionAt(new Point(51, 61)));
		MouseEvent drag = event(MouseEvent.MOUSE_PRESSED, 267, 85, InputEvent.ALT_DOWN_MASK);
		mouse.mousePressed(drag);
		assertFalse(drag.isConsumed());
		publish(true);
		assertNotNull(overlay.actionAt(new Point(267, 85)));
		assertNotNull(overlay.actionAt(new Point(75, 110)));
	}

	@Test
	public void leavingTheRowOrHidingTheWidgetCancelsTheAction()
	{
		publish(true);
		mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 75, 110, 0));
		MouseEvent outside = event(MouseEvent.MOUSE_RELEASED, 500, 400, 0);
		mouse.mouseReleased(outside);
		assertTrue(outside.isConsumed());
		assertTrue(actions.isEmpty());
		mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 75, 110, 0));
		overlay.publish(null);
		mouse.mouseReleased(event(MouseEvent.MOUSE_RELEASED, 75, 110, 0));
		assertTrue(actions.isEmpty());
		publish(true);
		visible.set(false);
		assertNull(render(overlay));
		assertNull(overlay.actionAt(new Point(75, 110)));
	}

	@Test
	public void changingThePlayerOrAccountBeforeReleaseCancelsTheClick()
	{
		for (int change = 0; change < 2; change++)
		{
			publish(true);
			mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 75, 110, 0));
			WidgetView.Chat chat = new WidgetView.Chat(change == 0 ? "Bob" : "Alice", true,
				FriendStatus.ONLINE, null, List.of(message()));
			overlay.publish(new WidgetView(options(true), List.of(chat), change == 1 ? 8 : 7));
			render(overlay);
			mouse.mouseReleased(event(MouseEvent.MOUSE_RELEASED, 75, 110, 0));
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
			rows.add(new WidgetView.Chat("Player " + i, true, FriendStatus.ONLINE, null,
				List.of(message(), message(), message())));
		}
		small.publish(new WidgetView(options(true), rows, 7));
		small.setBounds(new Rectangle(0, 0, 240, 0));
		Dimension size = render(small);
		assertTrue(size.height <= 230);
		assertNotNull(small.actionAt(new Point(20, 50)));
		assertNull(small.actionAt(new Point(20, 210)));
	}

	@Test
	public void nativeResizeReflowsPreviewsUpdatesRowBoundsAndResetRestoresDefaultWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			publish(true);
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
			assertEquals("Alice", overlay.actionAt(new Point(387, 85)).getPlayer());
			assertNotNull(overlay.actionAt(new Point(267, 85)));
			overlay.setPreferredSize(null);
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(240, render(overlay).width);
			assertNotNull(overlay.actionAt(new Point(267, 85)));
		});
	}

	@Test
	public void resizingHeightHidesOnlyWholeRowsAndCannotReviveDisabledWidgets() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			List<WidgetView.Chat> rows = List.of(
				new WidgetView.Chat("Alice", true, FriendStatus.ONLINE, null, List.of(message())),
				new WidgetView.Chat("Bob", true, FriendStatus.OFFLINE, null, List.of(message())));
			overlay.publish(new WidgetView(options(true), rows, 7));
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
			// A two-line preview ends at y=60; the rest is spacing and padding.
			overlay.publish(new WidgetView(options(true), List.of(new WidgetView.Chat("Alice", true,
				FriendStatus.ONLINE, null, List.of(message()))), 7));
			overlay.setBounds(new Rectangle(0, 0, 240, 0));
			assertEquals(69, render(overlay).height);
			overlay.setPreferredSize(new Dimension(240, 60));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = new BufferedImage(240, 60, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try { assertEquals(60, overlay.render(graphics).height); } finally { graphics.dispose(); }
			assertNotNull(overlay.actionAt(new Point(20, 20)));
			assertNotNull(overlay.actionAt(new Point(20, 59)));
			assertTrue("The footer must not replace a chat that fits", (image.getRGB(6, 55) >>> 24) > 0);
		});
	}

	@Test
	public void footerDoesNotDisplaceAChatWhenThereAreMoreChatsBelowIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			overlay.publish(new WidgetView(options(true), List.of(
				new WidgetView.Chat("Alice", true, FriendStatus.ONLINE, null, List.of(message())),
				new WidgetView.Chat("Bob", true, FriendStatus.OFFLINE, null, List.of(message()))), 7));
			overlay.setBounds(new Rectangle(0, 0, 240, 0));
			overlay.setPreferredSize(new Dimension(240, 90));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = new BufferedImage(240, 90, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try { assertEquals(90, overlay.render(graphics).height); } finally { graphics.dispose(); }
			assertNotNull(overlay.actionAt(new Point(20, 59)));
			assertNull(overlay.actionAt(new Point(20, 85)));
			assertEquals("No partial second chat should remain visible",
				0, image.getRGB(10, 83));
		});
	}

	@Test
	public void compactModeDrawsOnlyAvatarsAndUnreadBadges() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			overlay.publish(new WidgetView(compactOptions(true, true), List.of(
				new WidgetView.Chat("Alice", true, FriendStatus.ONLINE, null, List.of(message())),
				new WidgetView.Chat("Bob", false, FriendStatus.OFFLINE, null, List.of(message()))), 7));
			overlay.setBounds(new Rectangle(0, 0, 76, 0));
			BufferedImage image = new BufferedImage(76, 80, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try { assertEquals(new Dimension(76, 80), overlay.render(graphics)); } finally { graphics.dispose(); }
			assertTrue(overlay.isResizable());
			assertTrue(overlay.isMovable());
			assertNotEquals(0, image.getRGB(16, 16));
			assertNotEquals("Unread chats display a New pill", 0, image.getRGB(36, 18));
			assertEquals("Read chats have no pill or name", 0, image.getRGB(36, 54));
			assertEquals("Compact mode has no chat background", 0, image.getRGB(1, 20));
			assertEquals("Rows stay separated", 0, image.getRGB(16, 38));
			assertEquals("Alice", overlay.actionAt(new Point(16, 16)).getPlayer());
			assertEquals("Bob", overlay.actionAt(new Point(16, 54)).getPlayer());
			assertNull(overlay.actionAt(new Point(16, 38)));
		});
	}

	@Test
	public void compactUnreadCanBeClearedAndClicksCanBeDisabled() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			overlay.setBounds(new Rectangle(0, 0, 76, 44));
			for (boolean unread : List.of(true, false))
			{
				overlay.publish(new WidgetView(compactOptions(true, true), List.of(
					new WidgetView.Chat("Alice", unread, FriendStatus.ONLINE, null, List.of())), 7));
				BufferedImage image = new BufferedImage(76, 44, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = image.createGraphics();
				try { overlay.render(graphics); } finally { graphics.dispose(); }
				assertEquals(unread, image.getRGB(36, 18) != 0);
			}
			overlay.publish(new WidgetView(compactOptions(true, false), List.of(
				new WidgetView.Chat("Alice", true, FriendStatus.ONLINE, null, List.of())), 7));
			BufferedImage badgeHidden = new BufferedImage(76, 44, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = badgeHidden.createGraphics();
			try { overlay.render(graphics); } finally { graphics.dispose(); }
			assertEquals("Show New indicator still applies in compact mode", 0, badgeHidden.getRGB(36, 18));
			overlay.publish(new WidgetView(compactOptions(false, true), List.of(
				new WidgetView.Chat("Alice", true, FriendStatus.ONLINE, null, List.of())), 7));
			render(overlay);
			MouseEvent click = event(MouseEvent.MOUSE_PRESSED, 16, 16, 0);
			mouse.mousePressed(click);
			assertFalse(click.isConsumed());
			assertNull(overlay.actionAt(new Point(16, 16)));
		});
	}

	@Test
	public void emptyCompactModeHidesAndSwitchingBackRestoresRegularRows() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			overlay.publish(new WidgetView(compactOptions(true, true), List.of(), 7));
			assertNull(render(overlay));
			overlay.publish(new WidgetView(compactOptions(true, true), List.of(
				new WidgetView.Chat("Alice", true, FriendStatus.ONLINE, null, List.of())), 7));
			assertEquals(76, render(overlay).width);
			publish(true);
			assertTrue(overlay.isResizable());
			assertEquals(240, render(overlay).width);
			assertNotNull(overlay.actionAt(new Point(75, 110)));
		});
	}

	private WidgetOptions compactOptions(boolean click, boolean unread)
	{
		return new WidgetOptions(true, 10, 3, true, false, true, unread, WidgetUnreadStyle.PILL, click);
	}

	@Test
	public void resizingCompactGridReflowsFourChatsAndUpdatesEveryClickTarget() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			overlay.publish(new WidgetView(compactOptions(true, true), fourChats(), 7));
			overlay.setBounds(new Rectangle(0, 0, 76, 152));
			overlay.setPreferredSize(new Dimension(148, 80));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(new Dimension(148, 80), render(overlay));
			assertEquals("Alice", overlay.actionAt(new Point(20, 20)).getPlayer());
			assertEquals("Bob", overlay.actionAt(new Point(92, 20)).getPlayer());
			assertEquals("Carol", overlay.actionAt(new Point(20, 56)).getPlayer());
			assertEquals("Dave", overlay.actionAt(new Point(92, 56)).getPlayer());
			assertNull(overlay.actionAt(new Point(74, 20)));
			assertNull(overlay.actionAt(new Point(20, 38)));
			overlay.setPreferredSize(new Dimension(292, 44));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(new Dimension(292, 44), render(overlay));
			for (int i = 0; i < fourChats().size(); i++)
			{
				assertEquals(fourChats().get(i).getPlayer(), overlay.actionAt(new Point(20 + i * 72, 20)).getPlayer());
			}
			mouse.mousePressed(event(MouseEvent.MOUSE_PRESSED, 236, 20, 0));
			mouse.mouseReleased(event(MouseEvent.MOUSE_RELEASED, 236, 20, 0));
			assertEquals("Dave", actions.get(0).getPlayer());
			overlay.setPreferredSize(new Dimension(76, 152));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(new Dimension(76, 152), render(overlay));
			assertEquals("Dave", overlay.actionAt(new Point(20, 128)).getPlayer());
		});
	}

	@Test
	public void shortCompactGridHidesWholeRowsWithoutClickableInvisibleChats() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			overlay.publish(new WidgetView(compactOptions(true, true), fourChats(), 7));
			overlay.setBounds(new Rectangle(0, 0, 148, 40));
			overlay.setPreferredSize(new Dimension(148, 40));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(new Dimension(148, 40), render(overlay));
			assertEquals("Bob", overlay.actionAt(new Point(92, 20)).getPlayer());
			assertNull(overlay.actionAt(new Point(20, 56)));
			assertNull(overlay.actionAt(new Point(92, 56)));
		});
	}

	@Test
	public void glowUsesSmallerCellsAndRestoresSavedCompactSize() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			// RuneLite restores size before the first account snapshot is published.
			overlay.setPreferredSize(new Dimension(76, 80));
			overlay.publish(new WidgetView(glowOptions(true, true), fourChats(), 7));
			overlay.setBounds(new Rectangle(0, 0, 76, 80));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(new Dimension(76, 80), render(overlay));
			assertEquals("Bob", overlay.actionAt(new Point(56, 20)).getPlayer());
			assertEquals("Dave", overlay.actionAt(new Point(56, 56)).getPlayer());
			overlay.setPreferredSize(new Dimension(148, 44));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(new Dimension(148, 44), render(overlay));
			assertEquals("Dave", overlay.actionAt(new Point(128, 20)).getPlayer());
		});
	}

	@Test
	public void glowingAvatarsReplacePillsAndClearOnReadInBothLayouts() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			for (boolean compact : List.of(true, false))
			{
				int haloX = 5;
				int haloY = compact ? 20 : 17;
				int plain = 0;
				for (boolean unread : List.of(false, true))
				{
					overlay.publish(new WidgetView(glowOptions(compact, true), List.of(
						new WidgetView.Chat("Alice", unread, FriendStatus.UNKNOWN, null, List.of())), 7));
					BufferedImage image = new BufferedImage(240, 100, BufferedImage.TYPE_INT_ARGB);
					Graphics2D graphics = image.createGraphics();
					try { overlay.render(graphics); } finally { graphics.dispose(); }
					if (!unread) { plain = image.getRGB(haloX, haloY); }
					assertEquals(unread, image.getRGB(haloX, haloY) != plain);
					assertEquals("Glow must not draw a New pill", 0, image.getRGB(compact ? 36 : 210, 20));
				}
			}
			overlay.publish(new WidgetView(glowOptions(true, false), List.of(
				new WidgetView.Chat("Alice", true, FriendStatus.UNKNOWN, null, List.of())), 7));
			BufferedImage disabled = new BufferedImage(40, 44, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = disabled.createGraphics();
			try { overlay.render(graphics); } finally { graphics.dispose(); }
			assertEquals("Show New indicator disables glow too", 0, disabled.getRGB(5, 20));
		});
	}

	private WidgetOptions glowOptions(boolean compact, boolean unread)
	{
		return new WidgetOptions(true, 4, 0, compact, false, false, unread, WidgetUnreadStyle.GLOW, true);
	}

	private List<WidgetView.Chat> fourChats()
	{
		List<WidgetView.Chat> chats = new ArrayList<>();
		for (String player : List.of("Alice", "Bob", "Carol", "Dave"))
		{
			chats.add(new WidgetView.Chat(player, true, FriendStatus.UNKNOWN, null, List.of()));
		}
		return chats;
	}

	@Test
	public void shortRegularChatsHaveCompactTranslucentBackdropsAndUnusedSpaceIsPassive()
	{
		WidgetOptions options = new WidgetOptions(true, 3, 1, false, true, true, false, WidgetUnreadStyle.PILL, true);
		PrivateMessage shortMessage = new PrivateMessage("Alice", "Hello", Instant.ofEpochSecond(100), false);
		overlay.publish(new WidgetView(options, List.of(
			new WidgetView.Chat("Alice", false, FriendStatus.UNKNOWN, null, List.of(shortMessage)),
			new WidgetView.Chat("Bob", false, FriendStatus.UNKNOWN, null, List.of(shortMessage))), 7));
		overlay.setBounds(new Rectangle(0, 0, 240, 0));
		BufferedImage image = new BufferedImage(240, 160, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		Dimension dimensions;
		try { dimensions = overlay.render(graphics); } finally { graphics.dispose(); }
		assertTrue("Two short chats should occupy less height", dimensions.height < 110);
		MessageWidgetOverlay.Action first = overlay.actionAt(new Point(10, 15));
		assertNotNull(first);
		assertTrue("Short content must not fill a wide grey rectangle", first.getBounds().width < 150);
		int alpha = image.getRGB(6, 35) >>> 24;
		assertTrue("Terrain should show through the backdrop", alpha > 0 && alpha < 200);
		assertEquals("The rest of the dragged width stays transparent", 0, image.getRGB(220, 35));
		assertNull(overlay.actionAt(new Point(220, 35)));
		assertNull(overlay.actionAt(new Point(10, 48)));
		assertNotNull(overlay.actionAt(new Point(10, 60)));
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

	private void publish(boolean clickToOpen)
	{
		overlay.publish(new WidgetView(options(clickToOpen), List.of(new WidgetView.Chat("Alice", true,
			FriendStatus.ONLINE, null, List.of(message()))), 7));
		overlay.setBounds(new Rectangle(50, 60, 240, 0));
		Dimension size = render(overlay);
		overlay.getBounds().setSize(size);
	}
	private WidgetOptions options(boolean click)
	{
		return new WidgetOptions(true, 10, 3, false, true, true, true, WidgetUnreadStyle.PILL, click);
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
