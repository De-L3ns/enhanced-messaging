package com.enhancedmessaging.application;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class BossIconServiceTest
{
	private final CompletableFuture<BufferedImage> response = new CompletableFuture<>();
	private final AtomicInteger loads = new AtomicInteger();
	private final AtomicInteger changes = new AtomicInteger();
	private final BossIconService.Source source = new BossIconService.Source()
	{
		public String canonicalName(String name) { return name.equalsIgnoreCase("Vardorvis") ? "Vardorvis" : null; }
		public CompletableFuture<BufferedImage> load(String name) { loads.incrementAndGet(); return response; }
	};

	@Test
	public void cachesOneNativeIconAcrossMessagesAndSkipsUnknownBosses()
	{
		BossIconService icons = new BossIconService(source, Runnable::run, changes::incrementAndGet);
		assertTrue(icons.supports("Vardorvis"));
		assertFalse(icons.supports("Unknown"));
		assertNull(icons.imageFor("Unknown"));
		assertNull(icons.imageFor("Vardorvis"));
		assertNull(icons.imageFor("vardorvis"));
		assertEquals(1, loads.get());
		BufferedImage image = new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB);
		response.complete(image);
		assertSame(image, icons.imageFor("Vardorvis"));
		assertSame(image, icons.imageFor("vardorvis"));
		assertEquals(1, loads.get());
		assertEquals(1, changes.get());
	}

	@Test
	public void failedSpritesStayOptionalWithoutRepeatedRequests()
	{
		BossIconService icons = new BossIconService(source, Runnable::run, changes::incrementAndGet);
		icons.imageFor("Vardorvis");
		response.completeExceptionally(new IOException("Unavailable sprite"));
		assertNull(icons.imageFor("Vardorvis"));
		assertEquals(1, loads.get());
		assertEquals(1, changes.get());
	}

	@Test
	public void closingCancelsPendingSpritesAndSuppressesQueuedCallbacks()
	{
		ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
		BossIconService icons = new BossIconService(source, callbacks::addLast, changes::incrementAndGet);
		icons.imageFor("Vardorvis");
		icons.close();
		assertTrue(response.isCancelled());
		while (!callbacks.isEmpty()) { callbacks.removeFirst().run(); }
		assertNull(icons.imageFor("Vardorvis"));
		assertEquals(0, changes.get());
		assertEquals(1, loads.get());
	}
}
