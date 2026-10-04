package com.enhancedmessaging.application;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

// Like conversation state, this cache is confined to the Swing UI thread.
@Slf4j
public class AvatarService
{
	private final AvatarStorage storage;
	private final Executor uiExecutor;
	private final Runnable changed;
	private final Consumer<String> failure;
	private final Map<String, BufferedImage> images = new LinkedHashMap<>();
	private final Map<String, Long> requests = new LinkedHashMap<>();
	private Map<String, BufferedImage> stock = Collections.emptyMap();
	private String account;
	private long generation;
	private long nextRequest;
	private boolean closed;

	public AvatarService(AvatarStorage storage, Executor uiExecutor, Runnable changed, Consumer<String> failure)
	{
		this.storage = storage;
		this.uiExecutor = uiExecutor;
		this.changed = changed;
		this.failure = failure;
		storage.stock().whenCompleteAsync((loaded, error) ->
		{
			if (closed)
			{
				return;
			}
			if (error == null)
			{
				stock = loaded;
				changed.run();
			}
			else
			{
				log.debug("Unable to load stock avatars", error);
			}
		}, uiExecutor);
	}

	public void switchAccount(String key)
	{
		if (!Objects.equals(account, key))
		{
			account = key;
			generation++;
			images.clear();
			requests.clear();
		}
	}

	public Map<String, BufferedImage> getStock()
	{
		return stock;
	}

	public boolean canChange()
	{
		return !closed && account != null;
	}

	public long contextToken()
	{
		return generation;
	}

	public boolean isCurrent(long token)
	{
		return canChange() && generation == token;
	}

	public BufferedImage imageFor(String player)
	{
		String key = player.toLowerCase(Locale.ROOT);
		if (canChange() && !images.containsKey(key) && !requests.containsKey(key))
		{
			accept(key, storage.load(account, player), false);
		}
		BufferedImage image = images.get(key);
		return image == null ? stock.get("default") : image;
	}

	public void selectStock(String player, String id)
	{
		if (canChange())
		{
			accept(player.toLowerCase(Locale.ROOT), storage.selectStock(account, player, id), true);
		}
	}

	public void importImage(String player, Filepath source)
	{
		if (canChange())
		{
			accept(player.toLowerCase(Locale.ROOT), storage.importImage(account, player, source), true);
		}
	}

	public void reset(String player)
	{
		if (canChange())
		{
			accept(player.toLowerCase(Locale.ROOT), storage.reset(account, player).thenApply(ignored -> null), true);
		}
	}

	private void accept(String key, CompletableFuture<BufferedImage> result, boolean reportFailure)
	{
		long token = generation;
		long request = ++nextRequest;
		requests.put(key, request);
		result.whenCompleteAsync((image, error) ->
		{
			if (closed || generation != token || !Objects.equals(requests.get(key), request))
			{
				return;
			}
			requests.remove(key);
			if (error == null)
			{
				images.put(key, image);
			}
			else
			{
				// Cache a failed read as a fallback; repainting must not repeatedly retry disk reads.
				if (!images.containsKey(key))
				{
					images.put(key, null);
				}
				log.debug("Unable to update chat avatar", error);
				if (reportFailure)
				{
					failure.accept("Unable to update avatar. The previous avatar was kept.\n"
						+ "Imports must be PNG/JPEG, up to 2 MiB and 2048 × 2048 pixels. Check that RuneLite can write its plugin data folder.");
				}
			}
			while (images.size() > ConversationService.MAX_CONVERSATIONS)
			{
				images.remove(images.keySet().iterator().next());
			}
			changed.run();
		}, uiExecutor);
	}

	public void close()
	{
		closed = true;
		generation++;
		images.clear();
		requests.clear();
	}
}
