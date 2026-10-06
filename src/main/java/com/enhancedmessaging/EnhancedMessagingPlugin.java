package com.enhancedmessaging;

import com.enhancedmessaging.application.AvatarService;
import com.enhancedmessaging.application.BossIconService;
import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.application.FriendStatusService;
import com.enhancedmessaging.application.HistoryCoordinator;
import com.enhancedmessaging.application.WidgetOptions;
import com.enhancedmessaging.application.WidgetService;
import com.enhancedmessaging.domain.PrivateMessage;
import com.enhancedmessaging.domain.FriendStatus;
import com.enhancedmessaging.infrastructure.AsyncHistoryStorage;
import com.enhancedmessaging.infrastructure.ChatCommandMessages;
import com.enhancedmessaging.infrastructure.FriendStatusReader;
import com.enhancedmessaging.infrastructure.JsonHistoryRepository;
import com.enhancedmessaging.infrastructure.LocalAvatarStorage;
import com.enhancedmessaging.infrastructure.PrivateMessageMapper;
import com.enhancedmessaging.infrastructure.RuneLiteBossIcons;
import com.enhancedmessaging.presentation.EnhancedMessagingPanel;
import com.enhancedmessaging.presentation.MessageWidgetOverlay;
import com.enhancedmessaging.presentation.MessageWidgetMouseListener;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.Objects;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.FriendContainer;
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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.game.SpriteManager;
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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private SpriteManager spriteManager;

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
			session.widget = new MessageWidgetOverlay(this,
				() -> activeSession == session && !session.closed && client.getGameState() == GameState.LOGGED_IN,
				() -> !client.isMenuOpen() && !client.isWidgetSelected(), client::getCanvasHeight);
			session.avatars = new AvatarService(avatarStorage, SwingUtilities::invokeLater, () ->
			{
				if (session.panel != null)
				{
					session.panel.refreshAvatars();
					refreshWidget(session);
				}
			}, message ->
			{
				if (activeSession == session)
				{
					JOptionPane.showMessageDialog(session.panel, message, "Avatar", JOptionPane.ERROR_MESSAGE);
				}
			});
			session.panel = new EnhancedMessagingPanel(session.conversations, session.avatars, session.friends,
				() -> session.history.deleteHistory());
			session.bossIcons = new BossIconService(new RuneLiteBossIcons(spriteManager), SwingUtilities::invokeLater, () ->
			{
				if (activeSession == session && !session.closed) { session.panel.refreshBossIcons(); }
			});
			session.panel.setBossIcons(session.bossIcons);
			session.widgetService = new WidgetService(session.conversations, session.avatars, session.friends);
			session.panel.setReadChanged(() -> refreshWidget(session));
			session.history = new HistoryCoordinator(session.conversations, storage, scheduler,
				SwingUtilities::invokeLater, () ->
				{
					session.panel.setStorageState(session.history.canDelete(),
						session.history.getStatus());
				}, () ->
				{
					refreshConversations(session);
				});
			session.history.setRetentionEnabled(config.retainHistory());
			session.navigationButton = NavigationButton.builder()
				.tooltip("Enhanced Messaging")
				.icon(EnhancedMessagingPanel.createIcon())
				.priority(7)
				.panel(session.panel)
				.build();
			clientToolbar.addNavigation(session.navigationButton);
			session.widgetMouse = new MessageWidgetMouseListener(session.widget, action ->
				queue(session, current ->
				{
					if (current.widgetService.isCurrent(action.getContextToken()) && config.widgetClickToOpen() && config.widgetEnabled())
					{
						current.panel.selectConversation(action.getPlayer());
						clientToolbar.openPanel(current.navigationButton);
					}
				}));
			overlayManager.add(session.widget);
			mouseManager.registerMouseListener(session.widgetMouse);
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
		if (!Objects.equals(session.requestedAccount, account))
		{
			session.lastFriendStatuses = null;
		}
		session.requestedAccount = account;
		session.commandMessages.switchAccount(account);
		session.commandMessages.track(event, message);
		queue(session, current ->
		{
			switchAccount(current, account);
			current.history.record(message);
		});
		refreshFriendStatuses(session, account);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		Session active = activeSession;
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN && active != null && active.widget != null)
		{
			active.widget.clearInteraction();
		}
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			Session session = activeSession;
			if (session != null)
			{
				session.requestedAccount = null;
				session.commandMessages.switchAccount(null);
				session.lastFriendStatuses = null;
					queue(session, current ->
					{
						switchAccount(current, null);
						current.history.logout();
						current.panel.refreshAvatars();
						refreshWidget(current);
					});
			}
		}
		else if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			synchronizeAccount(activeSession);
		}
		else if (gameStateChanged.getGameState() == GameState.HOPPING
			|| gameStateChanged.getGameState() == GameState.CONNECTION_LOST
			|| gameStateChanged.getGameState() == GameState.LOGGING_IN)
		{
			Session session = activeSession;
			if (session != null)
			{
				session.lastFriendStatuses = null;
				String account = session.requestedAccount;
				queue(session, current ->
				{
					if (current.friends.update(account, Collections.emptyMap()))
					{
						current.panel.refreshAvatars();
						refreshWidget(current);
					}
				});
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Session session = activeSession;
		synchronizeAccount(session);
		if (session == null || session.closed || activeSession != session || client.getGameState() != GameState.LOGGED_IN) { return; }
		String account = session.requestedAccount;
		List<PrivateMessage> updates = session.commandMessages.poll();
		if (!updates.isEmpty())
		{
			queue(session, current ->
			{
				if (Objects.equals(current.account, account)) { updates.forEach(current.history::updateMessage); }
			});
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		clientThread.invoke(() -> synchronizeAccount(activeSession));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (EnhancedMessagingConfig.GROUP.equals(event.getGroup()) && event.getKey().startsWith("widget"))
		{
			queue(activeSession, current ->
			{
				if ("widgetLowFootprint".equals(event.getKey()))
				{
					current.widget.setPreferredSize(null);
					overlayManager.saveOverlay(current.widget);
				}
				refreshWidget(current);
			});
		}
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
		queue(activeSession, this::refreshWidget);
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
		if (session == null || session.closed || activeSession != session || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String account = configManager.getRSProfileKey();
		if (!Objects.equals(session.requestedAccount, account))
		{
			session.requestedAccount = account;
			session.commandMessages.switchAccount(account);
			session.lastFriendStatuses = null;
			queue(session, current -> switchAccount(current, account));
		}
		refreshFriendStatuses(session, account);
	}

	private void refreshFriendStatuses(Session session, String account)
	{
		if (client.getGameState() != GameState.LOGGED_IN || account == null)
		{
			return;
		}
		FriendContainer container = client.getFriendContainer();
		Map<String, FriendStatus> snapshot = FriendStatusReader.snapshot(container == null ? null : container.getMembers());
		if (!snapshot.equals(session.lastFriendStatuses))
		{
			session.lastFriendStatuses = snapshot;
			queue(session, current ->
			{
				if (current.friends.update(account, snapshot))
				{
					current.panel.refreshAvatars();
					refreshWidget(current);
				}
			});
		}
	}

	private void switchAccount(Session session, String account)
	{
		boolean changed = !Objects.equals(session.account, account);
		session.updatingAccount = changed;
		if (changed)
		{
			session.widget.publish(null);
			session.account = account;
		}
		session.avatars.switchAccount(account);
		session.friends.switchAccount(account);
		session.history.switchAccount(account);
		session.widgetService.switchAccount(account);
		session.updatingAccount = false;
		if (changed) { session.panel.refresh(); }
		session.panel.refreshAvatars();
		refreshWidget(session);
	}

	private void refreshConversations(Session session)
	{
		if (activeSession == session && !session.closed && session.panel != null && !session.updatingAccount)
		{
			session.panel.refresh();
			refreshWidget(session);
		}
	}

	private void refreshWidget(Session session)
	{
		if (activeSession != session || session.closed || session.widgetService == null || session.updatingAccount) { return; }
		if (!config.widgetEnabled() || session.account == null)
		{
			session.widget.publish(null);
			return;
		}
		WidgetOptions options = new WidgetOptions(config.widgetEnabled(), config.widgetChatCount(), config.widgetPreviewCount(),
			config.widgetLowFootprint(), config.widgetAvatars(), config.widgetStatus(), config.widgetUnread(), config.widgetUnreadStyle(),
			config.widgetClickToOpen());
		session.widget.publish(session.widgetService.snapshot(options));
	}

	private void closeSession(Session session)
	{
		if (session.closed) { return; }
		session.closed = true;
		clientThread.invoke(session.commandMessages::clear);
		session.widget.publish(null);
		mouseManager.unregisterMouseListener(session.widgetMouse);
		overlayManager.remove(session.widget);
		session.panel.close();
		session.bossIcons.close();
		session.avatars.close();
		session.friends.close();
		session.history.close();
	}

	private void queue(Session session, Consumer<Session> operation)
	{
		if (session != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (activeSession == session && !session.closed && session.history != null)
				{
					operation.accept(session);
				}
			});
		}
	}

	private static class Session
	{
		private final ConversationService conversations = new ConversationService();
		private final ChatCommandMessages commandMessages = new ChatCommandMessages();
		private final FriendStatusService friends = new FriendStatusService();
		private String account;
		private boolean updatingAccount;
		private volatile boolean closed;
		private WidgetService widgetService;
		private volatile MessageWidgetOverlay widget;
		private MessageWidgetMouseListener widgetMouse;
		// Only the client thread reads and writes this snapshot.
		private Map<String, FriendStatus> lastFriendStatuses;
		private volatile String requestedAccount;
		private HistoryCoordinator history;
		private AvatarService avatars;
		private BossIconService bossIcons;
		private EnhancedMessagingPanel panel;
		private NavigationButton navigationButton;
	}
}
