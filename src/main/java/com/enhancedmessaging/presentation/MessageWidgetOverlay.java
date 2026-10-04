package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.WidgetView;
import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.WidgetChatMode;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import lombok.Value;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class MessageWidgetOverlay extends Overlay
{
	private static final Font BODY = FontManager.getDefaultFont().deriveFont(12f);
	private static final Font SMALL = FontManager.getDefaultFont().deriveFont(10f);
	private static final Font BADGE = SMALL.deriveFont(Font.BOLD);
	private static final int FOOTER_HEIGHT = 22;
	private final BooleanSupplier visible;
	private final BooleanSupplier interactive;
	private final IntSupplier canvasHeight;
	private volatile Frame frame;
	private volatile Interaction interaction;
	private volatile Dimension resizedSize;
	private final AtomicBoolean resizeQueued = new AtomicBoolean();
	// Only the UI thread accesses this view or builds image pixels.
	private WidgetView view;

	public MessageWidgetOverlay(Plugin plugin, BooleanSupplier visible, BooleanSupplier interactive, IntSupplier canvasHeight)
	{
		super(plugin);
		this.visible = visible;
		this.interactive = interactive;
		this.canvasHeight = canvasHeight;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setResizable(true);
		setMinimumSize(60);
	}

	@Override
	public void setPreferredSize(Dimension size)
	{
		Dimension bounded = size == null ? null : new Dimension(Math.max(180, Math.min(360, size.width)),
			Math.max(60, Math.min(1600, size.height)));
		super.setPreferredSize(bounded);
		resizedSize = bounded;
		clearInteraction();
		if (resizeQueued.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(() ->
			{
				resizeQueued.set(false);
				if (view != null) { frame = build(view); }
			});
		}
	}

	// Build pixels and hit areas once on the UI thread, not every game frame.
	public void publish(WidgetView view)
	{
		clearInteraction();
		this.view = view == null || !view.getOptions().isEnabled() ? null : view;
		frame = view == null || !view.getOptions().isEnabled() ? null : build(view);
	}

	public void clearInteraction()
	{
		interaction = null;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Frame current = frame;
		if (current == null || !visible.getAsBoolean())
		{
			clearInteraction();
			return null;
		}
		int available = Math.max(70, canvasHeight.getAsInt() - 20);
		Dimension size = resizedSize;
		if (size != null) { available = Math.min(available, size.height); }
		int rows = 0;
		int contentBottom = 8;
		for (Row row : current.rows)
		{
			int bottom = row.bounds.y + row.bounds.height;
			if (bottom > available)
			{
				break;
			}
			rows++;
			contentBottom = bottom;
		}
		boolean hiddenChats = rows < current.rows.size();
		// The footer uses spare space; it must never displace a chat that fits.
		boolean footer = hiddenChats && available - contentBottom >= FOOTER_HEIGHT;
		int naturalHeight = hiddenChats ? contentBottom + 8 + (footer ? FOOTER_HEIGHT : 0) : current.image.getHeight();
		int height = size == null ? Math.min(naturalHeight, available) : available;
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.clipRect(0, 0, current.image.getWidth(), height);
			int contentHeight = hiddenChats ? contentBottom : Math.min(current.image.getHeight(), height);
			g.drawImage(current.image, 0, 0, current.image.getWidth(), contentHeight,
				0, 0, current.image.getWidth(), contentHeight, null);
			if (footer)
			{
				g.setFont(SMALL);
				g.setColor(ColorScheme.TEXT_COLOR);
				g.drawString("More chats in sidebar", 10, height - 8);
			}
		}
		finally
		{
			g.dispose();
		}
		interaction = frame == current && (size == null || size.width == current.image.getWidth())
			&& interactive.getAsBoolean() ? new Interaction(current, rows, height) : null;
		return new Dimension(current.image.getWidth(), height);
	}

	public Action actionAt(Point point)
	{
		Interaction current = interaction;
		if (current == null)
		{
			return null;
		}
		Rectangle overlay = new Rectangle(getBounds());
		int x = point.x - overlay.x;
		int y = point.y - overlay.y;
		if (x < 0 || x >= current.frame.image.getWidth() || y < 0 || y >= current.height)
		{
			return null;
		}
		for (int i = 0; i < current.count; i++)
		{
			Row row = current.frame.rows.get(i);
			if (row.bounds.contains(x, y))
			{
				boolean pin = row.pin.contains(x, y);
				if (pin ? !current.frame.view.isCanPin() : !current.frame.view.getOptions().isClickToOpen())
				{
					return null;
				}
				Rectangle bounds = new Rectangle(pin ? row.pin : row.bounds);
				bounds.translate(overlay.x, overlay.y);
				return new Action(row.player, pin, current.frame.view.getContextToken(), bounds);
			}
		}
		return null;
	}

	private Frame build(WidgetView view)
	{
		Dimension size = resizedSize;
		int width = size == null ? Math.max(180, Math.min(360, view.getOptions().getWidth())) : size.width;
		BufferedImage work = new BufferedImage(width, 1600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = work.createGraphics();
		List<Row> rows = new ArrayList<>();
		int y = 8;
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (WidgetView.Chat chat : view.getChats())
			{
				List<PreviewLine> previews = new ArrayList<>();
				g.setFont(BODY);
				for (PrivateMessage message : chat.getMessages())
				{
					for (String line : wrap(message.getText(), g.getFontMetrics(), width - 32))
					{
						previews.add(new PreviewLine(line, message.isOutgoing()));
					}
				}
				if (chat.getMessages().isEmpty() && view.getOptions().getPreviewCount() > 0)
				{
					for (String line : wrap("No messages this session.", g.getFontMetrics(), width - 32))
					{
						previews.add(new PreviewLine(line, false));
					}
				}
				int height = 36 + previews.size() * 16;
				g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
				g.fillRect(8, y, width - 16, height);
				int nameX = 14;
				FriendStatus status = view.getOptions().isStatus() ? chat.getStatus() : FriendStatus.UNKNOWN;
				if (view.getOptions().isAvatars())
				{
					new AvatarIcon(chat.getAvatar(), status).paintIcon(null, g, nameX, y + 4);
					nameX += 32;
				}
				else if (status != FriendStatus.UNKNOWN)
				{
					g.setColor(status == FriendStatus.ONLINE ? new Color(70, 190, 90) : Color.GRAY);
					g.fillOval(nameX, y + 15, 7, 7);
					nameX += 13;
				}
				Rectangle pin = new Rectangle(width - 34, y + 4, 22, 28);
				g.setColor(!view.isCanPin() ? Color.DARK_GRAY : chat.isPinned() ? new Color(255, 207, 86) : Color.GRAY);
				g.setStroke(new java.awt.BasicStroke(1.3f));
				java.awt.Polygon diamond = new java.awt.Polygon(
					new int[]{width - 23, width - 18, width - 23, width - 28},
					new int[]{y + 12, y + 17, y + 22, y + 17}, 4);
				if (chat.isPinned()) { g.fillPolygon(diamond); } else { g.drawPolygon(diamond); }
				int nameEnd = pin.x - 5;
				if (view.getOptions().isUnread() && chat.isUnread())
				{
					g.setFont(BADGE);
					int badgeWidth = g.getFontMetrics().stringWidth("New") + 14;
					nameEnd -= badgeWidth + 6;
					g.setColor(ColorScheme.BRAND_ORANGE);
					g.fillRoundRect(nameEnd, y + 9, badgeWidth, 18, 18, 18);
					g.setColor(ColorScheme.DARKER_GRAY_COLOR);
					g.drawString("New", nameEnd + 7, y + 22);
					nameEnd -= 6;
				}
				g.setFont(FontManager.getRunescapeFont());
				g.setColor(Color.YELLOW);
				g.drawString(ellipsize(chat.getPlayer(), g.getFontMetrics(), nameEnd - nameX), nameX, y + 22);
				g.setFont(BODY);
				g.setColor(ColorScheme.TEXT_COLOR);
				int lineY = y + 46;
				for (PreviewLine line : previews)
				{
					g.setColor(line.outgoing ? MessageStyle.OUTGOING_TEXT : ColorScheme.TEXT_COLOR);
					g.drawString(line.text, 16, lineY);
					lineY += 16;
				}
				rows.add(new Row(chat.getPlayer(), new Rectangle(8, y, width - 16, height), pin));
				y += height + 5;
			}
			g.setFont(BODY);
			g.setColor(ColorScheme.TEXT_COLOR);
			if (rows.isEmpty())
			{
				String hint = view.getOptions().getMode() == WidgetChatMode.PINNED_ONLY
					? "Pin a chat from the sidebar." : "Send or receive a private message.";
				for (String line : wrap(hint, g.getFontMetrics(), width - 20))
				{
					g.drawString(line, 10, y + 16);
					y += 16;
				}
				y += 11;
			}
			if (!view.getStatus().isEmpty())
			{
				g.setFont(SMALL);
				g.drawString(ellipsize(view.getStatus(), g.getFontMetrics(), width - 20), 10, y + 12);
				y += 20;
			}
		}
		finally
		{
			g.dispose();
		}
		BufferedImage image = new BufferedImage(width, y + 3, BufferedImage.TYPE_INT_ARGB);
		Graphics2D copy = image.createGraphics();
		try { copy.drawImage(work, 0, 0, null); } finally { copy.dispose(); }
		return new Frame(image, List.copyOf(rows), view);
	}

	static List<String> wrap(String text, FontMetrics metrics, int width)
	{
		String remaining = text.replaceAll("\\s+", " ").trim();
		List<String> lines = new ArrayList<>();
		if (metrics.stringWidth(remaining) <= width)
		{
			return List.of(remaining);
		}
		int end = fittingEnd(remaining, metrics, width);
		int space = remaining.lastIndexOf(' ', end);
		if (space > 0) { end = space; }
		lines.add(remaining.substring(0, end));
		lines.add(ellipsize(remaining.substring(end).trim(), metrics, width));
		return lines;
	}

	private static String ellipsize(String text, FontMetrics metrics, int width)
	{
		if (width <= 0) { return ""; }
		if (metrics.stringWidth(text) <= width) { return text; }
		String dots = "...";
		return text.substring(0, fittingEnd(text, metrics, Math.max(0, width - metrics.stringWidth(dots)))) + dots;
	}

	private static int fittingEnd(String text, FontMetrics metrics, int width)
	{
		int low = 0;
		int high = text.length();
		while (low < high)
		{
			int mid = (low + high + 1) / 2;
			if (metrics.stringWidth(text.substring(0, mid)) <= width) { low = mid; } else { high = mid - 1; }
		}
		// Do not split a UTF-16 surrogate pair.
		if (low > 0 && low < text.length() && Character.isHighSurrogate(text.charAt(low - 1))) { low--; }
		return low;
	}

	@Value
	private static class PreviewLine
	{
		String text;
		boolean outgoing;
	}

	@Value
	public static class Action
	{
		String player;
		boolean pin;
		long contextToken;
		Rectangle bounds;
	}

	@Value
	private static class Row
	{
		String player;
		Rectangle bounds;
		Rectangle pin;
	}

	@Value
	private static class Frame
	{
		BufferedImage image;
		List<Row> rows;
		WidgetView view;
	}

	@Value
	private static class Interaction
	{
		Frame frame;
		int count;
		int height;
	}
}
