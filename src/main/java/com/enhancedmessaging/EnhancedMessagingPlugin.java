package com.enhancedmessaging;

import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.infrastructure.PrivateMessageMapper;
import com.enhancedmessaging.presentation.EnhancedMessagingPanel;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Enhanced Messaging",
	description = "View private message conversations in the sidebar",
	tags = {"chat", "private", "messages", "sidebar"}
)
public class EnhancedMessagingPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	private volatile ConversationService conversationService;
	private EnhancedMessagingPanel panel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		ConversationService session = new ConversationService();
		conversationService = session;
		SwingUtilities.invokeLater(() ->
		{
			if (conversationService != session)
			{
				return;
			}
			panel = new EnhancedMessagingPanel(session);
			navigationButton = NavigationButton.builder()
				.tooltip("Enhanced Messaging")
				.icon(EnhancedMessagingPanel.createIcon())
				.priority(7)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navigationButton);
		});
		log.debug("Enhanced Messaging started!");
	}

	@Override
	protected void shutDown()
	{
		conversationService = null;
		SwingUtilities.invokeLater(() ->
		{
			if (navigationButton != null)
			{
				clientToolbar.removeNavigation(navigationButton);
				navigationButton = null;
			}
			panel = null;
		});
		log.debug("Enhanced Messaging stopped!");
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ConversationService session = conversationService;
		PrivateMessage message = PrivateMessageMapper.fromEvent(event);
		if (session == null || message == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (conversationService == session && panel != null)
			{
				session.record(message);
				panel.refresh();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			ConversationService session = conversationService;
			SwingUtilities.invokeLater(() ->
			{
				if (session != null && conversationService == session && panel != null)
				{
					session.clear();
					panel.refresh();
				}
			});
		}
	}
}
