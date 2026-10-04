package com.enhancedmessaging;

import com.enhancedmessaging.application.AvatarService;
import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.application.HistoryCoordinator;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.infrastructure.AsyncHistoryStorage;
import com.enhancedmessaging.infrastructure.JsonHistoryRepository;
import com.enhancedmessaging.infrastructure.LocalAvatarStorage;
import com.enhancedmessaging.infrastructure.PrivateMessageMapper;
import com.enhancedmessaging.presentation.EnhancedMessagingPanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Enhanced Messaging",
	internalName = "enhanced-messaging",
	description = "View private message conversations in the sidebar",
	tags = {"chat", "private", "messages", "sidebar"}
)
public class EnhancedMessagingPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EnhancedMessagingConfig config;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private ScheduledExecutorService scheduler;

	private AsyncHistoryStorage storage;
	private LocalAvatarStorage avatarStorage;
	private volatile Session activeSession;

	@Provides
	EnhancedMessagingConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(EnhancedMessagingConfig.class);
	}

	@Override
	protected void startUp()
	{
		if (storage == null)
		{
			storage = new AsyncHistoryStorage(new JsonHistoryRepository(this::getPluginDirectory, gson),
				httpClient.dispatcher().executorService());
			avatarStorage = new LocalAvatarStorage(this::getPluginDirectory, httpClient.dispatcher().executorService());
		}
		Session session = new Session();
		activeSession = session;
		SwingUtilities.invokeLater(() ->
		{
			if (activeSession != session)
			{
				return;
			}
			session.avatars = new AvatarService(avatarStorage, SwingUtilities::invokeLater, () ->
			{
				if (session.panel != null)
				{
					session.panel.refreshAvatars();
				}
			}, message ->
			{
				if (activeSession == session)
				{
					JOptionPane.showMessageDialog(session.panel, message, "Avatar", JOptionPane.ERROR_MESSAGE);
				}
			});
			session.panel = new EnhancedMessagingPanel(session.conversations, session.avatars,
				() -> session.history.deleteHistory());
			session.history = new HistoryCoordinator(session.conversations, storage, scheduler,
				SwingUtilities::invokeLater, () ->
				{
					session.panel.setStorageState(session.history.canDelete(),
						session.history.getStatus());
				}, session.panel::refresh);
			session.history.setRetentionEnabled(config.retainHistory());
			session.navigationButton = NavigationButton.builder()
				.tooltip("Enhanced Messaging")
				.icon(EnhancedMessagingPanel.createIcon())
				.priority(7)
				.panel(session.panel)
				.build();
			clientToolbar.addNavigation(session.navigationButton);
			clientThread.invoke(() -> synchronizeAccount(session));
		});
		log.debug("Enhanced Messaging started!");
	}

	@Override
	protected void shutDown()
	{
		Session session = activeSession;
		activeSession = null;
		SwingUtilities.invokeLater(() ->
		{
			if (session != null && session.history != null)
			{
				closeSession(session);
				clientToolbar.removeNavigation(session.navigationButton);
			}
		});
		log.debug("Enhanced Messaging stopped!");
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		Session session = activeSession;
		PrivateMessage message = PrivateMessageMapper.fromEvent(event);
		if (session == null || message == null)
		{
			return;
		}
		String account = configManager.getRSProfileKey();
		session.requestedAccount = account;
		queue(session, current ->
		{
			switchAccount(current, account);
			current.history.record(message);
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			Session session = activeSession;
			if (session != null)
			{
				session.requestedAccount = null;
					queue(session, current ->
					{
						current.avatars.switchAccount(null);
						current.history.logout();
					});
			}
		}
		else if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			synchronizeAccount(activeSession);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		synchronizeAccount(activeSession);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		clientThread.invoke(() -> synchronizeAccount(activeSession));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (EnhancedMessagingConfig.GROUP.equals(event.getGroup())
			&& EnhancedMessagingConfig.RETAIN_HISTORY.equals(event.getKey()))
		{
			updateRetention();
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		updateRetention();
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		Session session = activeSession;
		if (session != null)
		{
			// RuneLite waits for this future on its shutdown worker, never on the client or UI thread.
			event.waitFor(CompletableFuture.runAsync(() ->
			{
				if (session.history != null)
				{
					closeSession(session);
				}
			}, SwingUtilities::invokeLater).thenCompose(ignored ->
				CompletableFuture.allOf(storage.drain(), avatarStorage.drain())));
		}
	}

	private void updateRetention()
	{
		boolean enabled = config.retainHistory();
		queue(activeSession, current -> current.history.setRetentionEnabled(enabled));
	}

	private void synchronizeAccount(Session session)
	{
		if (session == null || activeSession != session || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String account = configManager.getRSProfileKey();
		if (!Objects.equals(session.requestedAccount, account))
		{
			session.requestedAccount = account;
			queue(session, current -> switchAccount(current, account));
		}
	}

	private void switchAccount(Session session, String account)
	{
		session.avatars.switchAccount(account);
		session.history.switchAccount(account);
		session.panel.refreshAvatars();
	}

	private void closeSession(Session session)
	{
		session.panel.close();
		session.avatars.close();
		session.history.close();
	}

	private void queue(Session session, Consumer<Session> operation)
	{
		if (session != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (activeSession == session && session.history != null)
				{
					operation.accept(session);
				}
			});
		}
	}

	private static class Session
	{
		private final ConversationService conversations = new ConversationService();
		private volatile String requestedAccount;
		private HistoryCoordinator history;
		private AvatarService avatars;
		private EnhancedMessagingPanel panel;
		private NavigationButton navigationButton;
	}
}
