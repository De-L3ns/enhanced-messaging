package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.BossKillCount;
import com.enhancedmessaging.application.BossIconService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Insets;
import java.awt.Graphics;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.text.DefaultCaret;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

// A width-tracking viewport keeps wrapped messages from increasing RuneLite's minimum window size.
final class MessageTranscript extends JPanel implements Scrollable
{
	private static final int OUTER_PADDING = 4;
	private static final int MESSAGE_SPACING = 4;
	private static final int CONTENT_GAP = 2;
	private static final int INLINE_TIME_GAP = 8;
	private static final int HORIZONTAL_PADDING = 12;
	private static final int VERTICAL_PADDING = 9;
	private static final Font BODY_FONT = FontManager.getDefaultFont().deriveFont(12f);
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
		.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
	private List<PrivateMessage> shown = Collections.emptyList();
	private Map<String, MessageBox> boxes = new LinkedHashMap<>();
	private final JTextArea empty = textArea("Send or receive a private message in-game to start a conversation.");
	private BossIconService bossIcons;

	MessageTranscript()
	{
		setLayout(null);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		add(empty);
	}

	void setBossIcons(BossIconService icons)
	{
		bossIcons = icons;
		refreshBossIcons();
	}

	void refreshBossIcons()
	{
		boxes.values().forEach(MessageBox::refreshIcon);
		repaint();
	}

	boolean setMessages(List<PrivateMessage> messages)
	{
		if (shown.equals(messages))
		{
			return false;
		}
		shown = messages;
		Map<String, MessageBox> next = new LinkedHashMap<>();
		removeAll();
		if (messages.isEmpty())
		{
			add(empty);
		}
		LocalDate previousDate = null;
		for (PrivateMessage message : messages)
		{
			LocalDate date = message.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
			if (!date.equals(previousDate))
			{
				JLabel separator = new JLabel(DATE_FORMAT.format(date), SwingConstants.CENTER);
				separator.setFont(FontManager.getDefaultFont().deriveFont(10f));
				separator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				separator.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
				add(separator);
				previousDate = date;
			}
			MessageBox box = boxes.get(message.getId());
			if (box == null)
			{
				box = new MessageBox(message);
			}
			else { box.updateText(message.getText()); }
			next.put(message.getId(), box);
			add(box);
		}
		boxes = next;
		revalidate();
		repaint();
		return true;
	}

	@Override
	public void doLayout()
	{
		layoutMessages(getWidth(), true);
	}

	private int layoutMessages(int width, boolean apply)
	{
		int available = Math.max(40, width - OUTER_PADDING * 2);
		int y = OUTER_PADDING;
		for (Component component : getComponents())
		{
			int boxWidth = available;
			int height;
			if (component == empty)
			{
				empty.setSize(boxWidth, Short.MAX_VALUE);
				height = empty.getPreferredSize().height;
			}
			else if (component instanceof MessageBox)
			{
				height = ((MessageBox) component).heightFor(boxWidth);
			}
			else
			{
				height = component.getPreferredSize().height;
			}
			if (apply)
			{
				component.setBounds(OUTER_PADDING, y, boxWidth, height);
			}
			y += height + (component instanceof JLabel ? 8 : MESSAGE_SPACING);
		}
		return y;
	}

	@Override
	public Dimension getPreferredSize()
	{
		int width = getParent() instanceof JViewport ? ((JViewport) getParent()).getExtentSize().width : getWidth();
		if (width <= 0)
		{
			width = PluginPanel.PANEL_WIDTH - 20;
		}
		return new Dimension(width, layoutMessages(width, false));
	}

	@Override
	public Dimension getMinimumSize()
	{
		return new Dimension(0, 0);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return new Dimension(0, 0);
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
	{
		return Math.max(16, visible.height - 16);
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}

	private static JTextArea textArea(String text)
	{
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setOpaque(false);
		area.setBorder(null);
		area.setMargin(new Insets(0, 0, 0, 0));
		area.setFont(BODY_FONT);
		area.setForeground(ColorScheme.TEXT_COLOR);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		return area;
	}

	private class MessageBox extends JPanel
	{
		private final boolean outgoing;
		private final JPanel metadata = new JPanel(new BorderLayout(6, 0));
		private final JTextArea body;
		private final JPanel bossContent = new JPanel(new BorderLayout(8, 0));
		private final JLabel bossIcon = new JLabel();
		private final JTextArea bossName = textArea("");
		private final JTextArea bossCount = textArea("");
		private BossKillCount killCount;
		private String shownText;
		private int measuredWidth = -1;
		private int measuredHeight;
		private boolean inlineTime;

		MessageBox(PrivateMessage message)
		{
			super(new BorderLayout(INLINE_TIME_GAP, CONTENT_GAP));
			outgoing = message.isOutgoing();
			setBackground(outgoing ? MessageStyle.OUTGOING_BACKGROUND : MessageStyle.INCOMING_BACKGROUND);
			setToolTipText(outgoing ? "Outgoing message" : "Incoming message");
			setBorder(BorderFactory.createEmptyBorder(5, 6, 4, 6));
			metadata.setOpaque(false);
			JLabel time = new JLabel(TIME_FORMAT.format(message.getTimestamp()), SwingConstants.RIGHT);
			time.setFont(FontManager.getDefaultFont().deriveFont(10f));
			time.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			time.setIcon(new MessageDirectionIcon(outgoing));
			time.setIconTextGap(4);
			time.setToolTipText(outgoing ? "Sent" : "Received");
			metadata.add(time, BorderLayout.EAST);
			add(metadata, BorderLayout.SOUTH);
			body = textArea("");
			bossContent.setOpaque(false);
			bossIcon.setPreferredSize(new Dimension(32, 32));
			bossIcon.setMinimumSize(new Dimension(32, 32));
			bossIcon.setVerticalAlignment(SwingConstants.TOP);
			bossIcon.setHorizontalAlignment(SwingConstants.CENTER);
			bossName.setFont(FontManager.getRunescapeSmallFont());
			bossName.setForeground(Color.YELLOW);
			bossCount.setFont(BODY_FONT.deriveFont(Font.BOLD));
			JPanel details = new JPanel(new BorderLayout(0, 2));
			details.setOpaque(false);
			details.add(bossName, BorderLayout.NORTH);
			details.add(bossCount, BorderLayout.CENTER);
			bossContent.add(bossIcon, BorderLayout.WEST);
			bossContent.add(details, BorderLayout.CENTER);
			updateText(message.getText());
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			graphics.setColor(outgoing ? MessageStyle.OUTGOING_ACCENT : MessageStyle.INCOMING_ACCENT);
			graphics.fillRect(outgoing ? getWidth() - 3 : 0, 0, 3, getHeight());
		}

		void updateText(String text)
		{
			if (!text.equals(shownText))
			{
				shownText = text;
				body.setText(text);
				killCount = BossKillCount.fromText(text);
				if (killCount != null && bossIcons != null && !bossIcons.supports(killCount.getBoss())) { killCount = null; }
				if (killCount == null)
				{
					remove(bossContent);
					add(body, BorderLayout.CENTER);
				}
				else
				{
					remove(body);
					bossName.setText(killCount.getBoss());
					bossIcon.setToolTipText(killCount.getBoss());
					bossCount.setText("Kill count: " + killCount.getCount());
					add(bossContent, BorderLayout.CENTER);
					refreshIcon();
				}
				measuredWidth = -1;
			}
		}

		void refreshIcon()
		{
			if (killCount != null && bossIcons != null)
			{
				java.awt.image.BufferedImage image = bossIcons.imageFor(killCount.getBoss());
				bossIcon.setIcon(image == null ? null : new ImageIcon(image));
			}
		}

		int heightFor(int width)
		{
			if (measuredWidth != width)
			{
				int contentWidth = Math.max(1, width - HORIZONTAL_PADDING);
				boolean fitsInline = killCount == null && !shownText.contains("\n") && !shownText.contains("\r")
					&& !shownText.contains("\t") && body.getFontMetrics(body.getFont()).stringWidth(shownText)
						+ metadata.getPreferredSize().width + INLINE_TIME_GAP <= contentWidth;
				if (inlineTime != fitsInline)
				{
					inlineTime = fitsInline;
					remove(metadata);
					add(metadata, inlineTime ? BorderLayout.EAST : BorderLayout.SOUTH);
				}
				int contentHeight;
				if (killCount == null)
				{
					body.setSize(Math.max(1, contentWidth - (inlineTime ? metadata.getPreferredSize().width + INLINE_TIME_GAP : 0)), Short.MAX_VALUE);
					contentHeight = body.getPreferredSize().height;
				}
				else
				{
					int textWidth = Math.max(1, width - HORIZONTAL_PADDING - 32 - 8);
					bossName.setSize(textWidth, Short.MAX_VALUE);
					bossCount.setSize(textWidth, Short.MAX_VALUE);
					contentHeight = Math.max(32, bossName.getPreferredSize().height + 2 + bossCount.getPreferredSize().height);
				}
				measuredHeight = VERTICAL_PADDING + (inlineTime ? Math.max(metadata.getPreferredSize().height, contentHeight)
					: metadata.getPreferredSize().height + CONTENT_GAP + contentHeight);
				measuredWidth = width;
			}
			return measuredHeight;
		}
	}
}
