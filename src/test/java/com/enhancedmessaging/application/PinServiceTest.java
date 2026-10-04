package com.enhancedmessaging.application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

import static org.junit.Assert.*;

public class PinServiceTest
{
	private final FakeStorage storage = new FakeStorage();
	private final List<String> failures = new ArrayList<>();
	private final PinService pins = new PinService(storage, Runnable::run, () -> { }, failures::add);

	@Test
	public void loadingBlocksEditsAndPinsKeepTheirOrderAcrossUnpinAndRepin()
	{
		pins.switchAccount("a");
		pins.toggle("Bob");
		assertFalse(pins.canChange());
		assertTrue(storage.saves.isEmpty());
		storage.loads.get(0).complete(List.of("Alice", "ALICE", "Bob"));
		assertEquals(List.of("Alice", "Bob"), pins.getPlayers());
		pins.toggle("alice");
		assertEquals(List.of("Bob"), pins.getPlayers());
		pins.toggle("Alice");
		assertEquals(List.of("Bob", "Alice"), pins.getPlayers());
		assertEquals(List.of("Bob"), storage.snapshots.get(0));
	}

	@Test
	public void accountChangesAndCloseIgnoreLateLoadsAndOldClickTokens()
	{
		pins.switchAccount("a");
		long old = pins.contextToken();
		pins.switchAccount("b");
		storage.loads.get(0).complete(List.of("Alice"));
		assertTrue(pins.getPlayers().isEmpty());
		assertFalse(pins.isCurrent(old));
		storage.loads.get(1).complete(List.of("Bob"));
		assertEquals(List.of("Bob"), pins.getPlayers());
		pins.switchAccount(null);
		assertTrue(pins.getPlayers().isEmpty());
		pins.switchAccount("b");
		pins.close();
		storage.loads.get(2).complete(List.of("Bob"));
		assertTrue(pins.getPlayers().isEmpty());
		assertFalse(pins.canChange());
	}

	@Test
	public void unreadableFilesArePreservedAndCannotBeOverwrittenByEdits()
	{
		pins.switchAccount("a");
		storage.loads.get(0).completeExceptionally(new IOException("Corrupt file"));
		pins.toggle("Alice");
		assertFalse(pins.canChange());
		assertTrue(storage.saves.isEmpty());
		assertEquals("Pins unavailable. File kept.", pins.getStatus());
	}

	@Test
	public void failedSaveKeepsSessionPinsAndOlderCallbacksCannotChangeNewerStatus()
	{
		pins.switchAccount("a");
		storage.loads.get(0).complete(List.of());
		pins.toggle("Alice");
		storage.saves.get(0).completeExceptionally(new IOException("Disk full"));
		assertTrue(pins.isPinned("Alice"));
		assertEquals("Pins not saved.", pins.getStatus());
		assertEquals(1, failures.size());
		pins.toggle("Bob");
		pins.toggle("Carol");
		storage.saves.get(2).complete(null);
		storage.saves.get(1).completeExceptionally(new IOException("Old failure"));
		assertEquals("", pins.getStatus());
		assertEquals(1, failures.size());
	}

	private static class FakeStorage implements PinStorage
	{
		private final List<CompletableFuture<List<String>>> loads = new ArrayList<>();
		private final List<CompletableFuture<Void>> saves = new ArrayList<>();
		private final List<List<String>> snapshots = new ArrayList<>();
		public CompletableFuture<List<String>> load(String account)
		{
			CompletableFuture<List<String>> future = new CompletableFuture<>();
			loads.add(future);
			return future;
		}
		public CompletableFuture<Void> save(String account, List<String> players)
		{
			snapshots.add(players);
			CompletableFuture<Void> future = new CompletableFuture<>();
			saves.add(future);
			return future;
		}
	}
}
