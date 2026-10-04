package com.enhancedmessaging.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalPinStorageTest
{
	private Filepath directory;
	private LocalPinStorage storage;
	@Before
	public void setUp() throws IOException
	{
		directory = Filepath.Unchecked.getRooted(Paths.get(System.getProperty("java.io.tmpdir")))
			.createTempDir("enhanced-pins-test-").rooted();
		storage = new LocalPinStorage(() -> directory, RuneLiteAPI.GSON, Runnable::run);
	}
	@After
	public void tearDown() throws IOException { directory.deleteRecursively(); }

	@Test
	public void roundTripsOrderAndKeepsCharactersSeparateInSmallFiles() throws IOException
	{
		assertTrue(storage.load("a").join().isEmpty());
		storage.save("a", List.of("Bob", "Alice")).join();
		storage.save("b", List.of("Carol")).join();
		assertEquals(List.of("Bob", "Alice"), storage.load("a").join());
		assertEquals(List.of("Carol"), storage.load("b").join());
		storage.save("a", List.of()).join();
		assertTrue(storage.load("a").join().isEmpty());
		assertEquals(List.of("Carol"), storage.load("b").join());
		try (Stream<Filepath> files = directory.joinSegment("pins").walk(1))
		{
			for (Filepath file : (Iterable<Filepath>) files.filter(Filepath::isFile)::iterator)
			{
				assertTrue(file.getFileName().matches("[0-9a-f]{64}\\.json"));
				assertTrue(file.size() < 1024);
			}
		}
	}

	@Test
	public void corruptUnsupportedOversizedAndExcessivePinFilesAreRejected() throws IOException
	{
		storage.save("a", List.of("Alice")).join();
		Filepath file;
		try (Stream<Filepath> files = directory.joinSegment("pins").walk(1))
		{
			file = files.filter(Filepath::isFile).findFirst().get();
		}
		for (String invalid : List.of("broken", "{\"version\":2,\"players\":[]}",
			"{\"version\":1,\"players\":[\"<html>\"]}", "{\"version\":1,\"players\":[]}" + " ".repeat(16384),
			"{\"version\":1,\"players\":[" + "\"Alice\",".repeat(100) + "\"Bob\"]}"))
		{
			file.write(invalid);
			try { storage.load("a").join(); fail("Accepted invalid file"); } catch (CompletionException expected) { }
			try (InputStream input = file.openInputStream())
			{
				assertEquals(invalid, new String(input.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
	}

	@Test
	public void backgroundOperationsAreSerialAndAnUnpinCannotBeUndoneByEarlierSave()
	{
		ArrayDeque<Runnable> tasks = new ArrayDeque<>();
		LocalPinStorage queued = new LocalPinStorage(() -> directory, RuneLiteAPI.GSON, tasks::addLast);
		CompletableFuture<Void> first = queued.save("a", List.of("Alice"));
		CompletableFuture<Void> last = queued.save("a", List.of());
		CompletableFuture<List<String>> loaded = queued.load("a");
		CompletableFuture<Void> drained = queued.drain();
		assertFalse(first.isDone());
		assertFalse(last.isDone());
		assertFalse(drained.isDone());
		while (!tasks.isEmpty()) { tasks.removeFirst().run(); }
		assertTrue(drained.isDone());
		assertTrue(loaded.join().isEmpty());
	}
}
