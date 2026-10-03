package com.enhancedmessaging.presentation;

import com.enhancedmessaging.EnhancedMessagingConfig;
import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class EnhancedMessagingPanel extends PluginPanel
{
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private final ConversationService conversationService;
	private final DefaultListModel<Conversation> conversationModel = new DefaultListModel<>();
	private final JList<Conversation> conversationList = new JList<>(conversationModel);
	private final JLabel conversationTitle = new JLabel("No conversation selected");
	private final JTextArea transcript = new JTextArea();
	private final JScrollPane transcriptScroll = new JScrollPane(transcript);
	private final JCheckBox retainHistory = new JCheckBox("Retain message history");
	private final JButton deleteHistory = new JButton("Delete saved history");
	private final JLabel storageStatus = new JLabel("Session history only.");
	private boolean refreshing;

	public EnhancedMessagingPanel(ConversationService conversationService, Consumer<Boolean> retentionChanged,
		Runnable deleteSavedHistory)
	{
		super(false);
		this.conversationService = conversationService;
		setLayout(new BorderLayout(0, 10));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout(0, 8));
		top.setOpaque(false);
		top.add(new JLabel("Private Messages"), BorderLayout.NORTH);
		conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		conversationList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		conversationList.setFixedCellHeight(30);
		conversationList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focused)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
				label.putClientProperty("html.disable", true);
				Conversation conversation = (Conversation) value;
				label.setText(conversation.getPlayerName() + " (" + conversation.getMessageCount() + ")");
				return label;
			}
		});
		conversationList.addListSelectionListener(event ->
		{
			if (!event.getValueIsAdjusting() && !refreshing)
			{
				showConversation(true);
			}
		});
		JScrollPane conversationsScroll = new JScrollPane(conversationList);
		conversationsScroll.setPreferredSize(new Dimension(0, 150));
		top.add(conversationsScroll, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		JPanel conversation = new JPanel(new BorderLayout(0, 8));
		conversation.setOpaque(false);
		conversationTitle.putClientProperty("html.disable", true);
		conversation.add(conversationTitle, BorderLayout.NORTH);
		transcript.setEditable(false);
		transcript.setLineWrap(true);
		transcript.setWrapStyleWord(true);
		transcript.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		transcript.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		transcript.setMargin(new java.awt.Insets(8, 8, 8, 8));
		((DefaultCaret) transcript.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		// Let the sidebar's available height determine the viewport, regardless of message count.
		transcriptScroll.setMinimumSize(new Dimension(0, 0));
		transcriptScroll.setPreferredSize(new Dimension(0, 0));
		conversation.add(transcriptScroll, BorderLayout.CENTER);
		add(conversation, BorderLayout.CENTER);

		// A wrapped text area's initial minimum height can force RuneLite to enlarge the window.
		JPanel note = new JPanel(new GridLayout(0, 1, 0, 2));
		note.setOpaque(false);
		retainHistory.setOpaque(false);
		retainHistory.setToolTipText("Save messages locally between sessions. Files are not encrypted.");
		retainHistory.addActionListener(event ->
		{
			boolean enabled = retainHistory.isSelected();
			if (enabled && JOptionPane.showConfirmDialog(this, EnhancedMessagingConfig.STORAGE_NOTICE,
				"Retain message history", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE) != JOptionPane.YES_OPTION)
			{
				retainHistory.setSelected(false);
				return;
			}
			retentionChanged.accept(enabled);
		});
		note.add(retainHistory);
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
		note.add(deleteHistory);
		storageStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		storageStatus.putClientProperty("html.disable", true);
		note.add(storageStatus);
		for (String line : new String[]{"Latest " + Conversation.MAX_MESSAGES + " messages/player.",
			"Up to " + ConversationService.MAX_CONVERSATIONS + " players."})
		{
			JLabel label = new JLabel(line);
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			note.add(label);
		}
		add(note, BorderLayout.SOUTH);
		refresh();
	}

	public void setStorageState(boolean enabled, boolean canDelete, String status)
	{
		retainHistory.setSelected(enabled);
		deleteHistory.setEnabled(canDelete);
		storageStatus.setText(status);
		storageStatus.setToolTipText(status);
	}

	public void refresh()
	{
		Conversation previous = conversationList.getSelectedValue();
		List<Conversation> conversations = conversationService.getConversations();
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
		StringBuilder content = new StringBuilder();
		if (conversation == null)
		{
			content.append("Send or receive a private message in-game to start a conversation.");
		}
		else
		{
			for (PrivateMessage message : conversation.getMessages())
			{
				content.append('[').append(TIME_FORMAT.format(message.getTimestamp())).append("] ")
					.append(message.isOutgoing() ? "You" : message.getPlayerName()).append(":\n")
					.append(message.getText()).append("\n\n");
			}
		}
		String text = content.toString();
		if (text.equals(transcript.getText()))
		{
			return;
		}
		JScrollBar scrollBar = transcriptScroll.getVerticalScrollBar();
		boolean atBottom = scrollBar.getValue() + scrollBar.getVisibleAmount() >= scrollBar.getMaximum() - 8;
		int previousScroll = scrollBar.getValue();
		transcript.setText(text);
		SwingUtilities.invokeLater(() ->
		{
			if (text.equals(transcript.getText()))
			{
				scrollBar.setValue(selectionChanged || atBottom ? scrollBar.getMaximum() : previousScroll);
			}
		});
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
