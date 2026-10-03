package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.PrivateMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import net.runelite.client.util.Filepath;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonHistoryRepositoryTest
{
	private Filepath directory;
	private JsonHistoryRepository repository;

	@Before
	public void setUp() throws IOException
	{
		directory = Filepath.Unchecked.getRooted(Paths.get(System.getProperty("java.io.tmpdir")))
			.createTempDir("enhanced-messaging-test-").rooted();
		repository = new JsonHistoryRepository(() -> directory, RuneLiteAPI.GSON);
	}

	@After
	public void tearDown() throws IOException
	{
		directory.deleteRecursively();
	}

	@Test
	public void missingHistoryIsEmpty() throws IOException
	{
		assertTrue(repository.load("account-a").isEmpty());
	}

	@Test
	public void roundTripsCompressedMessagesWithIdsUnicodeAndPreciseTimestamps() throws IOException
	{
		List<PrivateMessage> messages = List.of(new PrivateMessage("Alice", "Hi <friend>! \"Caf\u00e9\"\nSecond line",
			Instant.ofEpochMilli(1700000000123L), false), message("Alice", "My reply", true));
		repository.save("account-a", messages);

		assertEquals(messages, repository.load("account-a"));
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
		repository.save("account-a", first);
		repository.save("account-b", second);
		assertEquals(first, repository.load("account-a"));
		assertEquals(second, repository.load("account-b"));

		repository.delete("account-a");
		assertTrue(repository.load("account-a").isEmpty());
		assertEquals(second, repository.load("account-b"));
	}

	@Test
	public void replacesThePreviousSnapshotInsteadOfAppendingDuplicates() throws IOException
	{
		repository.save("account-a", List.of(message("Alice", "Old", false)));
		List<PrivateMessage> replacement = List.of(message("Alice", "Latest", true));
		repository.save("account-a", replacement);
		assertEquals(replacement, repository.load("account-a"));
		try (Stream<Filepath> files = directory.walk())
		{
			assertEquals(0, files.filter(file -> file.getFileName().endsWith(".tmp")).count());
		}
	}

	@Test
	public void rejectsCorruptedFilesWithoutChangingThem() throws IOException
	{
		repository.save("account-a", List.of(message("Alice", "Original", false)));
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
		repository.save("account-a", List.of(message("Alice", "Original", false)));
		writeJson("{\"version\":2,\"messages\":[]}");
		assertLoadFails();
		writeJson("{\"version\":1,\"messages\":[{\"player\":\"Alice\"}]}");
		assertLoadFails();
	}

	@Test
	public void failedSerializationPreservesThePreviousSaveAndRemovesTemporaryFiles() throws IOException
	{
		List<PrivateMessage> original = List.of(message("Alice", "Original", false));
		repository.save("account-a", original);
		try
		{
			repository.save("account-a", List.of(new PrivateMessage("Alice", "Invalid", null, false)));
			fail("Expected serialization to fail");
		}
		catch (NullPointerException expected)
		{
			assertEquals(original, repository.load("account-a"));
		}
		try (Stream<Filepath> files = directory.walk())
		{
			assertEquals(0, files.filter(file -> file.getFileName().endsWith(".tmp")).count());
		}
	}

	private void assertLoadFails() throws IOException
	{
		try
		{
			repository.load("account-a");
			fail("Expected invalid history to be rejected");
		}
		catch (IOException expected)
		{
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
