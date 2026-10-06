package com.enhancedmessaging.presentation;

import com.enhancedmessaging.application.AvatarService;
import com.enhancedmessaging.application.BossIconService;
import com.enhancedmessaging.domain.ConversationHistory;
import com.enhancedmessaging.application.FriendStatusService;
import com.enhancedmessaging.domain.Conversation;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Filepath;

public class EnhancedMessagingPanel extends PluginPanel
{
	private final ConversationHistory history;
	private final AvatarService avatars;
	private final FriendStatusService friends;
	private final DefaultListModel<Conversation> conversationModel = new DefaultListModel<>();
	private final JList<Conversation> conversationList = new JList<>(conversationModel);
	private final JLabel conversationTitle = new JLabel("No conversation selected");
	private final MessageTranscript transcript = new MessageTranscript();
	private final JScrollPane transcriptScroll = new JScrollPane(transcript);
	private final JButton deleteHistory = new JButton("Delete saved history");
	private final JLabel storageStatus = new JLabel("Session history only.");
	private final HierarchyListener visibilityListener = event ->
	{
		if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
		{
			setViewing(isShowing());
		}
	};
	private boolean refreshing;
	private boolean viewing;
	private boolean closed;
	private long transcriptRevision;
	private Runnable readChanged = () -> { };

	public EnhancedMessagingPanel(ConversationHistory history, AvatarService avatars,
		FriendStatusService friends, Runnable deleteSavedHistory)
	{
		super(false);
		this.history = history;
		this.avatars = avatars;
		this.friends = friends;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(createConversationList(), BorderLayout.NORTH);
		add(createConversationView(), BorderLayout.CENTER);
		add(createStorageControls(deleteSavedHistory), BorderLayout.SOUTH);
		addHierarchyListener(visibilityListener);
		refresh();
	}

	private JPanel createConversationList()
	{
		JPanel top = new JPanel(new BorderLayout(0, 8));
		top.setOpaque(false);
		JLabel title = new JLabel("Enhanced Messaging");
		title.setFont(FontManager.getRunescapeFont());
		title.setForeground(Color.YELLOW);
		top.add(title, BorderLayout.NORTH);
		conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		conversationList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		conversationList.setFixedCellHeight(36);
		conversationList.setCellRenderer(new PlayerRenderer());
		conversationList.addListSelectionListener(event ->
		{
			if (!event.getValueIsAdjusting() && !refreshing && !closed)
			{
				showConversation(true);
			}
		});
		addAvatarMenu(conversationList, event ->
		{
			int index = conversationList.locationToIndex(event.getPoint());
			return index >= 0 && conversationList.getCellBounds(index, index).contains(event.getPoint())
				? conversationModel.get(index).getPlayerName() : null;
		});
		JScrollPane conversationsScroll = new JScrollPane(conversationList);
		conversationsScroll.setPreferredSize(new Dimension(0, 112));
		conversationsScroll.setMinimumSize(new Dimension(0, 0));
		conversationsScroll.setBorder(null);
		top.add(conversationsScroll, BorderLayout.CENTER);
		return top;
	}

	private JPanel createConversationView()
	{
		JPanel conversation = new JPanel(new BorderLayout(0, 8));
		conversation.setOpaque(false);
		conversationTitle.putClientProperty("html.disable", true);
		conversationTitle.setFont(FontManager.getRunescapeFont());
		conversationTitle.setForeground(Color.YELLOW);
		conversationTitle.setIconTextGap(8);
		conversationTitle.setMinimumSize(new Dimension(0, 26));
		conversationTitle.setPreferredSize(new Dimension(0, 26));
		conversationTitle.setToolTipText("Right-click to change this player's avatar locally.");
		addAvatarMenu(conversationTitle, event ->
		{
			Conversation selected = conversationList.getSelectedValue();
			return selected == null ? null : selected.getPlayerName();
		});
		conversation.add(conversationTitle, BorderLayout.NORTH);
		transcriptScroll.setMinimumSize(new Dimension(0, 0));
		transcriptScroll.setPreferredSize(new Dimension(0, 0));
		transcriptScroll.setBorder(null);
		transcriptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		conversation.add(transcriptScroll, BorderLayout.CENTER);
		return conversation;
	}

	private JPanel createStorageControls(Runnable deleteSavedHistory)
	{
		JPanel footer = new JPanel(new GridLayout(0, 1, 0, 4));
		footer.setOpaque(false);
		storageStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		storageStatus.setFont(FontManager.getDefaultFont().deriveFont(12f));
		storageStatus.putClientProperty("html.disable", true);
		footer.add(storageStatus);
		deleteHistory.setEnabled(false);
		deleteHistory.addActionListener(event ->
		{
			if (JOptionPane.showConfirmDialog(this, "Delete saved history for this character?\n"
				+ "This also clears the conversations currently shown.\n"
				+ "New messages will be saved if retention remains enabled.", "Delete saved history",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
			{
				deleteSavedHistory.run();
			}
		});
		footer.add(deleteHistory);
		return footer;
	}

	public void setStorageState(boolean canDelete, String status)
	{
		deleteHistory.setEnabled(canDelete);
		storageStatus.setText(status);
		storageStatus.setToolTipText(status);
	}

	public void setReadChanged(Runnable readChanged)
	{
		this.readChanged = readChanged;
	}

	public void setBossIcons(BossIconService icons) { transcript.setBossIcons(icons); }

	public void refreshBossIcons()
	{
		if (!closed) { transcript.refreshBossIcons(); }
	}

	public void selectConversation(String player)
	{
		if (closed) { return; }
		refresh();
		for (int i = 0; i < conversationModel.size(); i++)
		{
			if (conversationModel.get(i).getPlayerName().equalsIgnoreCase(player))
			{
				conversationList.setSelectedIndex(i);
				conversationList.ensureIndexIsVisible(i);
				showConversation(true);
				break;
			}
		}
	}

	@Override
	public void onActivate()
	{
		setViewing(true);
	}

	@Override
	public void onDeactivate()
	{
		setViewing(false);
	}

	private void setViewing(boolean visible)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> setViewing(visible));
			return;
		}
		if (!closed)
		{
			viewing = visible;
			if (visible)
			{
				showConversation(false);
			}
		}
	}

	public void refresh()
	{
		if (closed)
		{
			return;
		}
		Conversation previous = conversationList.getSelectedValue();
		List<Conversation> conversations = history.getConversations();
		refreshing = true;
		conversationModel.clear();
		int selectedIndex = 0;
		for (int i = 0; i < conversations.size(); i++)
		{
			Conversation conversation = conversations.get(i);
			conversationModel.addElement(conversation);
			if (previous != null && previous.getPlayerName().equalsIgnoreCase(conversation.getPlayerName()))
			{
				selectedIndex = i;
			}
		}
		if (!conversations.isEmpty())
		{
			conversationList.setSelectedIndex(selectedIndex);
		}
		refreshing = false;
		showConversation(previous != conversationList.getSelectedValue());
	}

	private void showConversation(boolean selectionChanged)
	{
		Conversation conversation = conversationList.getSelectedValue();
		conversationTitle.setText(conversation == null ? "No conversation selected" : conversation.getPlayerName());
		if (viewing && conversation != null)
		{
			boolean unread = conversation.isUnread();
			conversation.markRead();
			conversationList.repaint();
			if (unread) { readChanged.run(); }
		}
		refreshAvatars();
		JScrollBar scrollBar = transcriptScroll.getVerticalScrollBar();
		if (!transcript.setMessages(conversation == null ? Collections.emptyList() : conversation.getMessages()) && !selectionChanged)
		{
			return;
		}
		long revision = ++transcriptRevision;
		SwingUtilities.invokeLater(() ->
		{
			if (!closed && revision == transcriptRevision)
			{
				transcriptScroll.validate();
				scrollBar.setValue(scrollBar.getMaximum());
			}
		});
	}

	public void refreshAvatars()
	{
		if (!closed)
		{
			Conversation selected = conversationList.getSelectedValue();
			conversationTitle.setIcon(selected == null ? null : iconFor(selected.getPlayerName()));
			conversationTitle.setToolTipText(selected == null ? null : friends.statusFor(selected.getPlayerName()).getDescription()
				+ ". Right-click to change this player's avatar locally.");
			conversationList.repaint();
		}
	}

	private AvatarIcon iconFor(String player)
	{
		return new AvatarIcon(avatars == null ? null : avatars.imageFor(player), friends.statusFor(player));
	}

	private void addAvatarMenu(Component target, Function<MouseEvent, String> playerAt)
	{
		target.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event) { popup(event); }

			@Override
			public void mouseReleased(MouseEvent event) { popup(event); }

			private void popup(MouseEvent event)
			{
				if (event.isPopupTrigger())
				{
					String player = playerAt.apply(event);
					if (player != null) { showAvatarMenu(player, target, event); }
				}
			}
		});
	}

	private void showAvatarMenu(String player, Component target, MouseEvent event)
	{
		if (closed || avatars == null || !avatars.canChange())
		{
			return;
		}
		long token = avatars.contextToken();
		JPopupMenu menu = new JPopupMenu();
		JMenu stocks = new JMenu("Stock avatar");
		avatars.getStock().forEach((id, image) ->
		{
			JMenuItem choice = new JMenuItem(id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1), new AvatarIcon(image));
			choice.addActionListener(ignored ->
			{
				if (avatars.isCurrent(token))
				{
					avatars.selectStock(player, id);
				}
			});
			stocks.add(choice);
		});
		menu.add(stocks);
		JMenuItem importImage = new JMenuItem("Import avatar...");
		importImage.setToolTipText("Local only. PNG/JPEG up to 2 MiB and 2048 × 2048 pixels; cropped to a square.");
		importImage.addActionListener(ignored ->
		{
			if (avatars.isCurrent(token))
			{
				List<Filepath> files = new Filepath.Chooser().setIsOpen().setAcceptsFiles()
					.setDialogTitle("Import local avatar (PNG/JPEG, up to 2 MiB)")
					.addExtensionFilter("PNG or JPEG image", "png", "jpg", "jpeg").showDialog(this);
				if (files != null && !files.isEmpty() && avatars.isCurrent(token))
				{
					avatars.importImage(player, files.get(0));
				}
			}
		});
		menu.add(importImage);
		JMenuItem reset = new JMenuItem("Reset avatar");
		reset.addActionListener(ignored ->
		{
			if (avatars.isCurrent(token))
			{
				avatars.reset(player);
			}
		});
		menu.add(reset);
		menu.show(target, event.getX(), event.getY());
	}

	public void close()
	{
		closed = true;
		viewing = false;
		removeHierarchyListener(visibilityListener);
	}

	private class PlayerRenderer extends JPanel implements ListCellRenderer<Conversation>
	{
		private final JLabel name = new JLabel();
		private final JLabel count = new JLabel();
		private final JLabel unreadBadge = new UnreadBadge();

		PlayerRenderer()
		{
			super(new BorderLayout(8, 0));
			setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
			name.putClientProperty("html.disable", true);
			name.setFont(FontManager.getRunescapeFont());
			name.setForeground(Color.YELLOW);
			name.setIconTextGap(8);
			count.setFont(FontManager.getDefaultFont().deriveFont(10f));
			count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			JPanel trailing = new JPanel(new BorderLayout(6, 0));
			trailing.setOpaque(false);
			trailing.add(count, BorderLayout.CENTER);
			trailing.add(unreadBadge, BorderLayout.EAST);
			add(name, BorderLayout.CENTER);
			add(trailing, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Conversation> list, Conversation value,
			int index, boolean selected, boolean focused)
		{
			setBackground(selected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			name.setText(value.getPlayerName());
			name.setIcon(iconFor(value.getPlayerName()));
			count.setText(String.valueOf(value.getMessageCount()));
			unreadBadge.setVisible(value.isUnread());
			String status = friends.statusFor(value.getPlayerName()).getDescription();
			setToolTipText(status + ". " + (value.isUnread()
				? "New messages. Open this conversation to mark it read." : "Right-click to change avatar."));
			getAccessibleContext().setAccessibleName(value.getPlayerName() + ", " + value.getMessageCount()
				+ " messages, " + status + (value.isUnread() ? ", unread" : ""));
			return this;
		}
	}

	private static class UnreadBadge extends JLabel
	{
		UnreadBadge()
		{
			super("New", JLabel.CENTER);
			setFont(FontManager.getDefaultFont().deriveFont(Font.BOLD, 10f));
			setForeground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int height = Math.min(getHeight(), getPreferredSize().height);
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillRoundRect(0, (getHeight() - height) / 2, getWidth(), height, height, height);
			}
			finally
			{
				g.dispose();
			}
			super.paintComponent(graphics);
		}
	}

	public static BufferedImage createIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(105, 190, 230));
			graphics.setStroke(new BasicStroke(1.5f));
			graphics.drawRoundRect(1, 2, 13, 9, 3, 3);
			graphics.drawLine(4, 11, 4, 14);
			graphics.drawLine(4, 14, 7, 11);
			graphics.drawLine(4, 5, 11, 5);
			graphics.drawLine(4, 8, 9, 8);
		}
		finally
		{
			graphics.dispose();
		}
		return icon;
	}
}
