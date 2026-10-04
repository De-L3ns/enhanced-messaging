package com.enhancedmessaging.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

// Confined to the UI thread. A generation guards callbacks and widget clicks across accounts.
@Slf4j
public class PinService
{
	public static final int MAX_PINS = 100;
	private final PinStorage storage;
	private final Executor uiExecutor;
	private final Runnable changed;
	private final Consumer<String> failure;
	private final Map<String, String> pins = new LinkedHashMap<>();
	private String account;
	private long generation;
	private long revision;
	private boolean loading;
	private boolean blocked;
	private boolean closed;
	private String status = "";

	public PinService(PinStorage storage, Executor uiExecutor, Runnable changed, Consumer<String> failure)
	{
		this.storage = storage;
		this.uiExecutor = uiExecutor;
		this.changed = changed;
		this.failure = failure;
	}

	public void switchAccount(String key)
	{
		if (closed || Objects.equals(account, key))
		{
			return;
		}
		account = key;
		long token = ++generation;
		pins.clear();
		blocked = false;
		loading = key != null;
		status = loading ? "Loading pins..." : "";
		changed.run();
		if (key == null)
		{
			return;
		}
		storage.load(key).whenCompleteAsync((players, error) ->
		{
			if (closed || generation != token)
			{
				return;
			}
			loading = false;
			blocked = error != null;
			status = blocked ? "Pins unavailable. File kept." : "";
			if (error == null)
			{
				players.forEach(player -> pins.putIfAbsent(player.toLowerCase(Locale.ROOT), player));
			}
			else
			{
				log.debug("Unable to load widget pins", error);
			}
			changed.run();
		}, uiExecutor);
	}

	public boolean canChange()
	{
		return !closed && account != null && !loading && !blocked;
	}

	public long contextToken()
	{
		return generation;
	}

	public boolean isCurrent(long token)
	{
		return !closed && account != null && generation == token;
	}

	public boolean isPinned(String player)
	{
		return pins.containsKey(player.toLowerCase(Locale.ROOT));
	}

	public List<String> getPlayers()
	{
		return Collections.unmodifiableList(new ArrayList<>(pins.values()));
	}

	public String getStatus()
	{
		return status;
	}

	public void toggle(String player)
	{
		if (!canChange() || player == null || player.trim().isEmpty() || player.length() > 32)
		{
			return;
		}
		String key = player.toLowerCase(Locale.ROOT);
		if (pins.containsKey(key))
		{
			pins.remove(key);
		}
		else if (pins.size() < MAX_PINS)
		{
			pins.put(key, player);
		}
		else
		{
			failure.accept("You can pin up to " + MAX_PINS + " chats. Unpin a chat first.");
			return;
		}
		long token = generation;
		long request = ++revision;
		status = "Saving pins...";
		changed.run();
		storage.save(account, getPlayers()).whenCompleteAsync((ignored, error) ->
		{
			if (closed || generation != token || request != revision)
			{
				return;
			}
			status = error == null ? "" : "Pins not saved.";
			if (error != null)
			{
				log.debug("Unable to save widget pins", error);
				failure.accept("Pins changed for this session but could not be saved. Check that RuneLite can write its plugin data folder.");
			}
			changed.run();
		}, uiExecutor);
	}

	public void close()
	{
		closed = true;
		generation++;
		pins.clear();
	}
}
