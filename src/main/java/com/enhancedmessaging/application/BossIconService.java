package com.enhancedmessaging.application;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

// UI-thread only. Cache is limited to the native boss catalogue, not player data.
@Slf4j
public class BossIconService
{
	private final BossIconSource source;
	private final Executor uiExecutor;
	private final Runnable changed;
	private final Map<String, BufferedImage> images = new HashMap<>();
	private final Map<String, CompletableFuture<BufferedImage>> pending = new HashMap<>();
	private boolean closed;

	public BossIconService(BossIconSource source, Executor uiExecutor, Runnable changed)
	{
		this.source = source;
		this.uiExecutor = uiExecutor;
		this.changed = changed;
	}

	public boolean supports(String boss) { return !closed && source.canonicalName(boss) != null; }

	public BufferedImage imageFor(String boss)
	{
		String name = closed ? null : source.canonicalName(boss);
		if (name == null) { return null; }
		if (!images.containsKey(name) && !pending.containsKey(name))
		{
			CompletableFuture<BufferedImage> future = source.load(name);
			pending.put(name, future);
			future.whenCompleteAsync((image, error) ->
			{
				if (closed) { return; }
				pending.remove(name);
				images.put(name, error == null ? image : null);
				if (error != null) { log.debug("Unable to load native boss icon for {}", name, error); }
				changed.run();
			}, uiExecutor);
		}
		return images.get(name);
	}

	public void close()
	{
		closed = true;
		pending.values().forEach(future -> future.cancel(false));
		pending.clear();
		images.clear();
	}
}
