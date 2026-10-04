package com.enhancedmessaging.application;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.runelite.client.util.Filepath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class AvatarServiceTest
{
	private final FakeStorage storage = new FakeStorage();
	private final List<String> failures = new ArrayList<>();
	private final AvatarService service = new AvatarService(storage, Runnable::run, () -> { }, failures::add);

	@Test
	public void cachedMissingOrFailedImagesAreNotReadOnEveryRepaint()
	{
		service.switchAccount("account-a");
		assertSame(storage.fallback, service.imageFor("Alice"));
		storage.loads.get(0).complete(null);
		service.imageFor("ALICE");
		assertEquals(1, storage.loads.size());
		service.imageFor("Bob");
		storage.loads.get(1).completeExceptionally(new IOException("Unreadable image"));
		service.imageFor("Bob");
		assertEquals(2, storage.loads.size());
		assertEquals(0, failures.size());
	}

	@Test
	public void aLateLoadCannotOverwriteANewerAvatarChoice()
	{
		service.switchAccount("account-a");
		service.imageFor("Alice");
		CompletableFuture<BufferedImage> load = storage.loads.get(0);
		service.selectStock("Alice", "mage");
		BufferedImage chosen = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
		storage.saves.get(0).complete(chosen);
		load.complete(new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB));
		assertSame(chosen, service.imageFor("Alice"));
	}

	@Test
	public void accountChangesAndCloseIgnoreStaleCallbacksAndEditorContexts()
	{
		service.switchAccount("account-a");
		long token = service.contextToken();
		service.imageFor("Alice");
		service.switchAccount("account-b");
		assertFalse(service.isCurrent(token));
		storage.loads.get(0).complete(new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB));
		assertSame(storage.fallback, service.imageFor("Alice"));
		service.close();
		storage.loads.get(1).complete(new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB));
		assertFalse(service.canChange());
	}

	@Test
	public void failedChoicesKeepThePreviousImageAndNotifyTheUser()
	{
		service.switchAccount("account-a");
		service.imageFor("Alice");
		BufferedImage previous = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
		storage.loads.get(0).complete(previous);
		service.selectStock("Alice", "mage");
		storage.saves.get(0).completeExceptionally(new IOException("Disk full"));
		assertSame(previous, service.imageFor("Alice"));
		assertEquals(1, failures.size());
	}

	private static class FakeStorage implements AvatarStorage
	{
		private final BufferedImage fallback = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
		private final List<CompletableFuture<BufferedImage>> loads = new ArrayList<>();
		private final List<CompletableFuture<BufferedImage>> saves = new ArrayList<>();

		@Override
		public CompletableFuture<Map<String, BufferedImage>> stock()
		{
			return CompletableFuture.completedFuture(Map.of("default", fallback));
		}

		@Override
		public CompletableFuture<BufferedImage> load(String account, String player)
		{
			CompletableFuture<BufferedImage> result = new CompletableFuture<>();
			loads.add(result);
			return result;
		}

		@Override
		public CompletableFuture<BufferedImage> selectStock(String account, String player, String id)
		{
			CompletableFuture<BufferedImage> result = new CompletableFuture<>();
			saves.add(result);
			return result;
		}

		@Override
		public CompletableFuture<BufferedImage> importImage(String account, String player, Filepath file)
		{
			return selectStock(account, player, "import");
		}

		@Override
		public CompletableFuture<Void> reset(String account, String player)
		{
			return CompletableFuture.completedFuture(null);
		}
	}
}
