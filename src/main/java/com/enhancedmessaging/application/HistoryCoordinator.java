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
	private final Runnable changed;
	@Getter
	private boolean retentionEnabled;
	@Getter
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
		ScheduledExecutorService scheduler, Executor uiExecutor, Runnable changed)
	{
		this.conversations = conversations;
		this.storage = storage;
		this.scheduler = scheduler;
		this.uiExecutor = uiExecutor;
		this.changed = changed;
	}

	public void switchAccount(String key)
	{
		if (closed || Objects.equals(accountKey, key))
		{
			return;
		}
		flush();
		cancelSave();
		generation++;
		if (accountKey != null || key == null)
		{
			conversations.clear();
		}
		accountKey = key;
		loading = false;
		blocked = false;
		deleting = false;
		dirty = retentionEnabled && !conversations.snapshot().isEmpty();
		if (retentionEnabled && key != null)
		{
			load();
		}
		else
		{
			status = retentionEnabled ? "Log in to restore history." : "Session history only.";
			changed.run();
		}
	}

	public void setRetentionEnabled(boolean enabled)
	{
		if (closed || retentionEnabled == enabled)
		{
			return;
		}
		cancelSave();
		generation++;
		retentionEnabled = enabled;
		loading = false;
		blocked = false;
		deleting = false;
		dirty = enabled && !conversations.snapshot().isEmpty();
		if (enabled && accountKey != null)
		{
			load();
		}
		else
		{
			status = enabled ? "Log in to restore history." : "Saving off. Existing files kept.";
			changed.run();
		}
	}

	public void logout()
	{
		switchAccount(null);
		conversations.clear();
		changed.run();
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
		changed.run();
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
				status = dirty ? "Save pending." : "Local history enabled.";
				scheduleSave();
			}
			changed.run();
		}, uiExecutor);
		changed.run();
	}

	private void scheduleSave()
	{
		if (!dirty || accountKey == null || loading || blocked || deleting || closed
			|| (pendingSave != null && !pendingSave.isDone()))
		{
			return;
		}
		long token = generation;
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
			changed.run();
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
			changed.run();
		}, uiExecutor);
		changed.run();
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
