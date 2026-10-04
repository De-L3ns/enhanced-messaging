package com.enhancedmessaging.presentation;

import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
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
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());
	private List<PrivateMessage> shown = Collections.emptyList();
	private Map<String, MessageBox> boxes = new LinkedHashMap<>();
	private final JTextArea empty = textArea("Send or receive a private message in-game to start a conversation.");

	MessageTranscript()
	{
		setLayout(null);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		add(empty);
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
		for (PrivateMessage message : messages)
		{
			MessageBox box = boxes.get(message.getId());
			if (box == null)
			{
				box = new MessageBox(message);
			}
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
		int available = Math.max(40, width - 16);
		int y = 8;
		for (Component component : getComponents())
		{
			boolean outgoing = component instanceof MessageBox && ((MessageBox) component).outgoing;
			int boxWidth = component == empty ? available : Math.round(available * (outgoing ? .88f : .94f));
			int height;
			if (component == empty)
			{
				empty.setSize(boxWidth, Short.MAX_VALUE);
				height = empty.getPreferredSize().height;
			}
			else
			{
				height = ((MessageBox) component).heightFor(boxWidth);
			}
			if (apply)
			{
				component.setBounds(outgoing ? width - 8 - boxWidth : 8, y, boxWidth, height);
			}
			y += height + 8;
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
		area.setFont(FontManager.getDefaultFont());
		area.setForeground(ColorScheme.TEXT_COLOR);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		return area;
	}

	private static class MessageBox extends JPanel
	{
		private final boolean outgoing;
		private final JPanel metadata = new JPanel(new BorderLayout(6, 0));
		private final JTextArea body;
		private int measuredWidth = -1;
		private int measuredHeight;

		MessageBox(PrivateMessage message)
		{
			super(new BorderLayout(0, 4));
			outgoing = message.isOutgoing();
			setBackground(outgoing ? new Color(59, 59, 59) : new Color(50, 50, 50));
			setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
			metadata.setOpaque(false);
			JLabel name = new JLabel(outgoing ? "You" : message.getPlayerName());
			name.putClientProperty("html.disable", true);
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(Color.YELLOW);
			JLabel time = new JLabel(TIME_FORMAT.format(message.getTimestamp()), SwingConstants.RIGHT);
			time.setFont(FontManager.getDefaultFont().deriveFont(10f));
			time.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			metadata.add(name, BorderLayout.CENTER);
			metadata.add(time, BorderLayout.EAST);
			add(metadata, BorderLayout.NORTH);
			body = textArea(message.getText());
			add(body, BorderLayout.CENTER);
		}

		int heightFor(int width)
		{
			if (measuredWidth != width)
			{
				body.setSize(Math.max(1, width - 16), Short.MAX_VALUE);
				measuredHeight = 14 + metadata.getPreferredSize().height + 4 + body.getPreferredSize().height;
				measuredWidth = width;
			}
			return measuredHeight;
		}
	}
}
