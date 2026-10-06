package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.WidgetView;
import com.enhancedmessaging.application.WidgetOptions;
import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.WidgetUnreadStyle;
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
	private static final int DEFAULT_WIDTH = 240;
	private final BooleanSupplier visible;
	private final BooleanSupplier interactive;
	private final IntSupplier canvasHeight;
	private volatile Frame frame;
	private volatile Interaction interaction;
	private volatile Dimension resizedSize;
	private volatile WidgetOptions layoutOptions;
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
		WidgetOptions options = layoutOptions;
		int minWidth = options == null ? 32 : minimumWidth(options);
		int maxWidth = options == null || options.isLowFootprint() ? 724 : 360;
		int minHeight = options == null || options.isLowFootprint() ? 40 : 60;
		Dimension bounded = size == null ? null : new Dimension(Math.max(minWidth, Math.min(maxWidth, size.width)),
			Math.max(minHeight, Math.min(1600, size.height)));
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
		if (this.view != null)
		{
			layoutOptions = this.view.getOptions();
			setMinimumSize(layoutOptions.isLowFootprint() ? 32 : 60);
		}
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
		if (current == null || !visible.getAsBoolean()
			|| current.view.getOptions().isLowFootprint() && current.rows.isEmpty())
		{
			clearInteraction();
			return null;
		}
		int available = Math.max(70, canvasHeight.getAsInt() - 20);
		Dimension size = resizedSize;
		if (size != null)
		{
			available = Math.min(available, Math.max(current.view.getOptions().isLowFootprint() ? 40 : 60, size.height));
		}
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
		boolean footer = hiddenChats && !current.view.getOptions().isLowFootprint()
			&& available - contentBottom >= FOOTER_HEIGHT;
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
		interaction = frame == current && widthFor(current.view.getOptions()) == current.image.getWidth()
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
				if (!current.frame.view.getOptions().isClickToOpen())
				{
					return null;
				}
				Rectangle bounds = new Rectangle(row.bounds);
				bounds.translate(overlay.x, overlay.y);
				return new Action(row.player, current.frame.view.getContextToken(), bounds);
			}
		}
		return null;
	}

	private Frame build(WidgetView view)
	{
		if (view.getOptions().isLowFootprint()) { return buildCompact(view); }
		int width = widthFor(view.getOptions());
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
				if (view.getOptions().showsAvatars())
				{
					paintAvatar(g, chat, nameX, y + 4, status, view.getOptions());
					nameX += 32;
				}
				else if (status != FriendStatus.UNKNOWN)
				{
					g.setColor(status == FriendStatus.ONLINE ? new Color(70, 190, 90) : Color.GRAY);
					g.fillOval(nameX, y + 15, 7, 7);
					nameX += 13;
				}
				int nameEnd = width - 18;
				if (view.getOptions().isUnread() && !view.getOptions().usesGlow() && chat.isUnread())
				{
					g.setFont(BADGE);
					int badgeWidth = g.getFontMetrics().stringWidth("New") + 14;
					nameEnd -= badgeWidth + 6;
					paintBadge(g, nameEnd, y + 9);
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
				rows.add(new Row(chat.getPlayer(), new Rectangle(8, y, width - 16, height)));
				y += height + 5;
			}
			g.setFont(BODY);
			g.setColor(ColorScheme.TEXT_COLOR);
			if (rows.isEmpty())
			{
				String hint = "Send or receive a private message.";
				for (String line : wrap(hint, g.getFontMetrics(), width - 20))
				{
					g.drawString(line, 10, y + 16);
					y += 16;
				}
				y += 11;
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

	private Frame buildCompact(WidgetView view)
	{
		int width = widthFor(view.getOptions());
		int stride = compactStride(view.getOptions());
		int columns = Math.max(1, (width - 4) / stride);
		int gridRows = (view.getChats().size() + columns - 1) / columns;
		BufferedImage image = new BufferedImage(width, gridRows * 36 + 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		List<Row> rows = new ArrayList<>();
		int index = 0;
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (WidgetView.Chat chat : view.getChats())
			{
				int x = 4 + index % columns * stride;
				int y = 4 + index / columns * 36;
				paintAvatar(g, chat, x + 4, y + 2, FriendStatus.UNKNOWN, view.getOptions());
				if (view.getOptions().isUnread() && !view.getOptions().usesGlow() && chat.isUnread())
				{
					paintBadge(g, x + 30, y + 7);
				}
				rows.add(new Row(chat.getPlayer(), new Rectangle(x, y, stride - 4, 32)));
				index++;
			}
		}
		finally { g.dispose(); }
		return new Frame(image, List.copyOf(rows), view);
	}

	private int widthFor(WidgetOptions options)
	{
		Dimension size = resizedSize;
		int defaultWidth = options.isLowFootprint() ? minimumWidth(options) : DEFAULT_WIDTH;
		int width = size == null ? defaultWidth : size.width;
		return Math.max(minimumWidth(options), Math.min(options.isLowFootprint() ? 724 : 360, width));
	}

	private static int minimumWidth(WidgetOptions options)
	{
		return options.isLowFootprint() ? compactStride(options) + 4 : 180;
	}

	private static int compactStride(WidgetOptions options)
	{
		return options.getUnreadStyle() == WidgetUnreadStyle.GLOW ? 36 : 72;
	}

	private static void paintBadge(Graphics2D g, int x, int y)
	{
		g.setFont(BADGE);
		int width = g.getFontMetrics().stringWidth("New") + 14;
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.fillRoundRect(x, y, width, 18, 18, 18);
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.drawString("New", x + 7, y + 13);
	}

	private static void paintAvatar(Graphics2D g, WidgetView.Chat chat, int x, int y, FriendStatus status, WidgetOptions options)
	{
		if (options.usesGlow() && chat.isUnread())
		{
			int[] opacity = {24, 40, 80, 150};
			for (int i = 0; i < opacity.length; i++)
			{
				g.setColor(new Color(255, 207, 86, opacity[i]));
				g.fillOval(x - 4 + i, y - 2 + i, 32 - i * 2, 32 - i * 2);
			}
		}
		new AvatarIcon(chat.getAvatar(), status).paintIcon(null, g, x, y);
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
		long contextToken;
		Rectangle bounds;
	}

	@Value
	private static class Row
	{
		String player;
		Rectangle bounds;
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
