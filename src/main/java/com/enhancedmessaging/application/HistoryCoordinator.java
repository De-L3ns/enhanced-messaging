package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.PrivateMessage;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

// All state is confined to the UI thread. Storage operations never run on that thread.
@Slf4j
public class HistoryCoordinator
{
	private final ConversationService conversations;
	private final HistoryStorage storage;
	private final ScheduledExecutorService scheduler;
	private final Executor uiExecutor;
	private final Runnable storageChanged;
	private final Runnable conversationsChanged;
	@Getter
	private boolean retentionEnabled;
	private String accountKey;
	@Getter
	private String status = "Session history only.";
	private boolean loading;
	private boolean blocked;
	private boolean deleting;
	private boolean dirty;
	private boolean closed;
	private long generation;
	private ScheduledFuture<?> pendingSave;

	public HistoryCoordinator(ConversationService conversations, HistoryStorage storage,
		ScheduledExecutorService scheduler, Executor uiExecutor, Runnable storageChanged, Runnable conversationsChanged)
	{
		this.conversations = conversations;
		this.storage = storage;
		this.scheduler = scheduler;
		this.uiExecutor = uiExecutor;
		this.storageChanged = storageChanged;
		this.conversationsChanged = conversationsChanged;
	}

	public void switchAccount(String key)
	{
		if (closed || Objects.equals(accountKey, key))
		{
			return;
		}
		flush();
		if (accountKey != null || key == null)
		{
			conversations.clear();
			conversationsChanged.run();
		}
		accountKey = key;
		resetStorageState();
		if (retentionEnabled && key != null)
		{
			load();
		}
		else
		{
			status = retentionEnabled ? "Log in to restore history." : "Session history only.";
			storageChanged.run();
		}
	}

	public void setRetentionEnabled(boolean enabled)
	{
		if (closed || retentionEnabled == enabled)
		{
			return;
		}
		retentionEnabled = enabled;
		resetStorageState();
		if (enabled && accountKey != null)
		{
			load();
		}
		else
		{
			status = enabled ? "Log in to restore history." : "Saving off. Existing files kept.";
			storageChanged.run();
		}
	}

	public void logout()
	{
		if (closed)
		{
			return;
		}
		if (accountKey != null)
		{
			switchAccount(null);
		}
		else
		{
			conversations.clear();
			conversationsChanged.run();
		}
	}

	private void resetStorageState()
	{
		cancelSave();
		generation++;
		loading = false;
		blocked = false;
		deleting = false;
		dirty = retentionEnabled && !conversations.isEmpty();
	}

	public void record(PrivateMessage message)
	{
		if (closed)
		{
			return;
		}
		conversations.record(message);
		if (retentionEnabled)
		{
			dirty = true;
			scheduleSave();
		}
		conversationsChanged.run();
	}

	private void load()
	{
		loading = true;
		status = "Loading saved history...";
		long token = generation;
		storage.load(accountKey).whenCompleteAsync((messages, error) ->
		{
			if (closed || generation != token)
			{
				return;
			}
			loading = false;
			if (error != null)
			{
				blocked = true;
				status = "History unreadable. File kept.";
				log.debug("Unable to load private message history", error);
			}
			else
			{
				conversations.mergeSavedHistory(messages);
				conversationsChanged.run();
				status = dirty ? "Save pending." : "Local history enabled.";
				scheduleSave();
			}
			storageChanged.run();
		}, uiExecutor);
		storageChanged.run();
	}

	private void scheduleSave()
	{
		if (!dirty || accountKey == null || loading || blocked || deleting || closed
			|| pendingSave != null)
		{
			return;
		}
		long token = generation;
		// A timer remains pending until its UI callback runs, even after the scheduler finishes.
		pendingSave = scheduler.schedule(() -> uiExecutor.execute(() ->
		{
			if (!closed && generation == token)
			{
				pendingSave = null;
				flush();
			}
		}), 2, TimeUnit.SECONDS);
	}

	public void flush()
	{
		if (!retentionEnabled || !dirty || accountKey == null || (blocked && !deleting) || closed)
		{
			return;
		}
		dirty = false;
		long token = generation;
		storage.save(accountKey, conversations.snapshot(), loading || deleting).whenCompleteAsync((ignored, error) ->
		{
			if (closed || generation != token)
			{
				return;
			}
			if (error != null)
			{
				dirty = true;
				status = "Save failed. Messages in memory.";
				log.debug("Unable to save private message history", error);
			}
			else if (!loading)
			{
				status = dirty ? "Save pending." : "History stored locally.";
			}
			storageChanged.run();
		}, uiExecutor);
	}

	public boolean canDelete()
	{
		return accountKey != null && !deleting && !closed;
	}

	public void deleteHistory()
	{
		if (!canDelete())
		{
			return;
		}
		cancelSave();
		generation++;
		long token = generation;
		deleting = true;
		loading = false;
		blocked = true;
		dirty = false;
		conversations.clear();
		conversationsChanged.run();
		status = "Deleting saved history...";
		storage.delete(accountKey).whenCompleteAsync((ignored, error) ->
		{
			if (closed || generation != token)
			{
				return;
			}
			deleting = false;
			blocked = error != null;
			if (error != null)
			{
				status = "Delete failed. Try again.";
				log.debug("Unable to delete private message history", error);
			}
			else
			{
				status = "Saved history deleted.";
				scheduleSave();
			}
			storageChanged.run();
		}, uiExecutor);
		storageChanged.run();
	}

	public void close()
	{
		flush();
		cancelSave();
		closed = true;
		generation++;
	}

	private void cancelSave()
	{
		if (pendingSave != null)
		{
			pendingSave.cancel(false);
			pendingSave = null;
		}
	}
}
