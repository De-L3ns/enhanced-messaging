package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.application.HistoryRepository;
import com.enhancedmessaging.domain.PrivateMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AsyncHistoryStorageTest
{
	private final MemoryRepository repository = new MemoryRepository();
	private final QueuedExecutor executor = new QueuedExecutor();
	private final AsyncHistoryStorage storage = new AsyncHistoryStorage(repository, executor);

	@Test
	public void performsIoOnTheWorkerAndSerializesSavesBeforeDeletion()
	{
		storage.save("a", List.of(message("First")), false);
		storage.save("a", List.of(message("Second")), false);
		storage.delete("a");
		CompletableFuture<List<PrivateMessage>> loaded = storage.load("a");
		CompletableFuture<Void> drained = storage.drain();
		assertTrue(repository.operations.isEmpty());
		assertFalse(drained.isDone());

		executor.runAll();
		assertEquals(List.of("save a", "save a", "delete a", "load a"), repository.operations);
		assertTrue(loaded.isDone());
		assertTrue(loaded.join().isEmpty());
		assertTrue(drained.isDone());
	}

	@Test
	public void mergingAPendingSessionSavePreservesOlderHistoryAndDeduplicatesIds()
	{
		PrivateMessage saved = message("Previous session");
		PrivateMessage fresh = message("New message");
		repository.histories.put("a", List.of(saved));
		storage.save("a", List.of(saved, fresh), true);
		executor.runAll();
		assertEquals(List.of(saved, fresh), repository.histories.get("a"));
		PrivateMessage resolved = new PrivateMessage(saved.getId(), saved.getPlayerName(), "Resolved command text",
			saved.getTimestamp(), saved.isOutgoing());
		storage.save("a", List.of(resolved, fresh), true);
		executor.runAll();
		assertEquals("An older file must not overwrite resolved text during a final merged save",
			List.of(resolved, fresh), repository.histories.get("a"));
	}

	@Test
	public void failureDoesNotPoisonLaterOperationsAndUnreadableHistoryIsNotOverwritten()
	{
		repository.failLoad = true;
		CompletableFuture<Void> save = storage.save("a", List.of(message("New message")), true);
		CompletableFuture<Void> delete = storage.delete("a");
		executor.runAll();

		assertTrue(save.isCompletedExceptionally());
		assertTrue(delete.isDone());
		assertFalse(delete.isCompletedExceptionally());
		assertEquals(List.of("load a", "delete a"), repository.operations);
	}

	@Test
	public void finalSaveDuringDeletionDoesNotResurrectTheDeletedHistory()
	{
		repository.histories.put("a", List.of(message("Before delete")));
		storage.delete("a");
		PrivateMessage fresh = message("After delete");
		storage.save("a", List.of(fresh), true);
		executor.runAll();

		assertEquals(List.of(fresh), repository.histories.get("a"));
		assertEquals(List.of("delete a", "load a", "save a"), repository.operations);
	}

	private PrivateMessage message(String text)
	{
		return new PrivateMessage("Alice", text, Instant.ofEpochSecond(100), false);
	}

	private static class QueuedExecutor implements Executor
	{
		private final Deque<Runnable> tasks = new ArrayDeque<>();

		@Override
		public void execute(Runnable task)
		{
			tasks.addLast(task);
		}

		void runAll()
		{
			while (!tasks.isEmpty())
			{
				tasks.removeFirst().run();
			}
		}
	}

	private static class MemoryRepository implements HistoryRepository
	{
		private final Map<String, List<PrivateMessage>> histories = new HashMap<>();
		private final List<String> operations = new ArrayList<>();
		private boolean failLoad;

		@Override
		public List<PrivateMessage> load(String key) throws IOException
		{
			operations.add("load " + key);
			if (failLoad)
			{
				throw new IOException("Corrupt history");
			}
			return histories.getOrDefault(key, List.of());
		}

		@Override
		public void save(String key, List<PrivateMessage> messages)
		{
			operations.add("save " + key);
			histories.put(key, messages);
		}

		@Override
		public void delete(String key)
		{
			operations.add("delete " + key);
			histories.remove(key);
		}
	}
}
