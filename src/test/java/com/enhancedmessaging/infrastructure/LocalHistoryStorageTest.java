package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.PrivateMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import net.runelite.client.util.Filepath;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalHistoryStorageTest
{
	private Filepath directory;
	private LocalHistoryStorage storage;

	@Before
	public void setUp() throws IOException
	{
		directory = Filepath.Unchecked.getRooted(Paths.get(System.getProperty("java.io.tmpdir")))
			.createTempDir("enhanced-messaging-test-").rooted();
		storage = new LocalHistoryStorage(() -> directory, RuneLiteAPI.GSON, Runnable::run);
	}

	@After
	public void tearDown() throws IOException
	{
		directory.deleteRecursively();
	}

	@Test
	public void missingHistoryIsEmpty() throws IOException
	{
		assertTrue(storage.load("account-a").join().isEmpty());
	}

	@Test
	public void roundTripsCompressedMessagesWithIdsUnicodeAndPreciseTimestamps() throws IOException
	{
		List<PrivateMessage> messages = List.of(new PrivateMessage("Alice", "Hi <friend>! \"Caf\u00e9\"\nSecond line",
			Instant.ofEpochMilli(1700000000123L), false), message("Alice", "My reply", true));
		storage.save("account-a", messages, false).join();

		assertEquals(messages, storage.load("account-a").join());
		try (InputStream stream = historyFile().openInputStream())
		{
			assertEquals(0x1f, stream.read());
			assertEquals(0x8b, stream.read());
		}
	}

	@Test
	public void separatesAccountsAndDeletesOnlyTheSelectedAccount() throws IOException
	{
		List<PrivateMessage> first = List.of(message("Alice", "Account A", false));
		List<PrivateMessage> second = List.of(message("Bob", "Account B", true));
		storage.save("account-a", first, false).join();
		storage.save("account-b", second, false).join();
		assertEquals(first, storage.load("account-a").join());
		assertEquals(second, storage.load("account-b").join());

		storage.delete("account-a").join();
		assertTrue(storage.load("account-a").join().isEmpty());
		assertEquals(second, storage.load("account-b").join());
	}

	@Test
	public void replacesThePreviousSnapshotInsteadOfAppendingDuplicates() throws IOException
	{
		storage.save("account-a", List.of(message("Alice", "Old", false)), false).join();
		List<PrivateMessage> replacement = List.of(message("Alice", "Latest", true));
		storage.save("account-a", replacement, false).join();
		assertEquals(replacement, storage.load("account-a").join());
		try (Stream<Filepath> files = directory.walk())
		{
			assertEquals(0, files.filter(file -> file.getFileName().endsWith(".tmp")).count());
		}
	}

	@Test
	public void rejectsCorruptedFilesWithoutChangingThem() throws IOException
	{
		storage.save("account-a", List.of(message("Alice", "Original", false)), false).join();
		Filepath file = historyFile();
		file.write("not gzip");
		assertLoadFails();
		try (java.io.BufferedReader reader = file.openBufferedReader())
		{
			assertEquals("not gzip", reader.readLine());
		}
	}

	@Test
	public void rejectsUnsupportedVersionsAndIncompleteRecords() throws IOException
	{
		storage.save("account-a", List.of(message("Alice", "Original", false)), false).join();
		writeJson("{\"version\":2,\"messages\":[]}");
		assertLoadFails();
		writeJson("{\"version\":1,\"messages\":[{\"player\":\"Alice\"}]}");
		assertLoadFails();
	}

	@Test
	public void failedSerializationPreservesThePreviousSaveAndRemovesTemporaryFiles() throws IOException
	{
		List<PrivateMessage> original = List.of(message("Alice", "Original", false));
		storage.save("account-a", original, false).join();
		try
		{
			storage.save("account-a", List.of(new PrivateMessage("Alice", "Invalid", null, false)), false).join();
			fail("Expected serialization to fail");
		}
		catch (CompletionException expected)
		{
			assertTrue(expected.getCause() instanceof NullPointerException);
			assertEquals(original, storage.load("account-a").join());
		}
		try (Stream<Filepath> files = directory.walk())
		{
			assertEquals(0, files.filter(file -> file.getFileName().endsWith(".tmp")).count());
		}
	}

	@Test
	public void performsIoOnTheWorkerAndSerializesSavesBeforeDeletion()
	{
		QueuedExecutor executor = new QueuedExecutor();
		List<String> directoryReads = new ArrayList<>();
		LocalHistoryStorage queued = new LocalHistoryStorage(() ->
		{
			directoryReads.add("read");
			return directory;
		}, RuneLiteAPI.GSON, executor);
		PrivateMessage first = message("Alice", "First", false);
		PrivateMessage second = message("Alice", "Second", false);
		queued.save("a", List.of(first), false);
		CompletableFuture<List<PrivateMessage>> firstLoad = queued.load("a");
		queued.save("a", List.of(second), false);
		CompletableFuture<List<PrivateMessage>> secondLoad = queued.load("a");
		queued.delete("a");
		CompletableFuture<List<PrivateMessage>> deletedLoad = queued.load("a");
		CompletableFuture<Void> drained = queued.drain();
		assertTrue("Submitting operations must not access the filesystem", directoryReads.isEmpty());
		assertFalse(firstLoad.isDone());
		assertFalse(drained.isDone());

		executor.runAll();
		assertEquals(List.of(first), firstLoad.join());
		assertEquals(List.of(second), secondLoad.join());
		assertTrue(deletedLoad.join().isEmpty());
		assertTrue(drained.isDone());
	}

	@Test
	public void mergingAPendingSessionSavePreservesOlderHistoryAndDeduplicatesIds()
	{
		PrivateMessage saved = message("Alice", "Previous session", false);
		PrivateMessage fresh = message("Alice", "New message", false);
		storage.save("a", List.of(saved), false).join();
		QueuedExecutor executor = new QueuedExecutor();
		LocalHistoryStorage queued = new LocalHistoryStorage(() -> directory, RuneLiteAPI.GSON, executor);
		queued.save("a", List.of(saved, fresh), true);
		executor.runAll();
		assertEquals(List.of(saved, fresh), storage.load("a").join());
		PrivateMessage resolved = new PrivateMessage(saved.getId(), saved.getPlayerName(), "Resolved command text",
			saved.getTimestamp(), saved.isOutgoing());
		queued.save("a", List.of(resolved, fresh), true);
		executor.runAll();
		assertEquals("An older file must not overwrite resolved text during a final merged save",
			List.of(resolved, fresh), storage.load("a").join());
	}

	@Test
	public void failureDoesNotPoisonLaterOperationsAndUnreadableHistoryIsNotOverwritten() throws IOException
	{
		storage.save("a", List.of(message("Alice", "Original", false)), false).join();
		historyFile().write("not gzip");
		QueuedExecutor executor = new QueuedExecutor();
		LocalHistoryStorage queued = new LocalHistoryStorage(() -> directory, RuneLiteAPI.GSON, executor);
		CompletableFuture<Void> save = queued.save("a", List.of(message("Alice", "New message", false)), true);
		CompletableFuture<List<PrivateMessage>> load = queued.load("a");
		executor.runAll();
		assertTrue(save.isCompletedExceptionally());
		assertTrue(load.isCompletedExceptionally());
		try (java.io.BufferedReader reader = historyFile().openBufferedReader())
		{
			assertEquals("not gzip", reader.readLine());
		}
		CompletableFuture<Void> delete = queued.delete("a");
		CompletableFuture<List<PrivateMessage>> afterDelete = queued.load("a");
		executor.runAll();
		assertFalse(delete.isCompletedExceptionally());
		assertTrue(afterDelete.join().isEmpty());
	}

	@Test
	public void finalSaveDuringDeletionDoesNotResurrectTheDeletedHistory()
	{
		storage.save("a", List.of(message("Alice", "Before delete", false)), false).join();
		QueuedExecutor executor = new QueuedExecutor();
		LocalHistoryStorage queued = new LocalHistoryStorage(() -> directory, RuneLiteAPI.GSON, executor);
		queued.delete("a");
		PrivateMessage fresh = message("Alice", "After delete", false);
		queued.save("a", List.of(fresh), true);
		executor.runAll();
		assertEquals(List.of(fresh), storage.load("a").join());
	}

	private static class QueuedExecutor implements Executor
	{
		private final Deque<Runnable> tasks = new ArrayDeque<>();

		@Override
		public void execute(Runnable task) { tasks.addLast(task); }

		void runAll()
		{
			while (!tasks.isEmpty()) { tasks.removeFirst().run(); }
		}
	}

	private void assertLoadFails() throws IOException
	{
		try
		{
			storage.load("account-a").join();
			fail("Expected invalid history to be rejected");
		}
		catch (CompletionException expected)
		{
			assertTrue(expected.getCause() instanceof IOException);
			assertTrue(historyFile().exists());
		}
	}

	private void writeJson(String json) throws IOException
	{
		try (OutputStream raw = historyFile().openOutputStream(); GZIPOutputStream gzip = new GZIPOutputStream(raw))
		{
			gzip.write(json.getBytes(StandardCharsets.UTF_8));
		}
	}

	private Filepath historyFile() throws IOException
	{
		try (Stream<Filepath> files = directory.walk())
		{
			return files.filter(file -> file.getFileName().equals("conversations.json.gz")).findFirst().orElseThrow();
		}
	}

	private PrivateMessage message(String player, String text, boolean outgoing)
	{
		return new PrivateMessage(player, text, Instant.ofEpochSecond(100), outgoing);
	}
}
