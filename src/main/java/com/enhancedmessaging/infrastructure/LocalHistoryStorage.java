package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.ConversationHistory;
import com.enhancedmessaging.application.HistoryStorage;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.PrivateMessage;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.runelite.client.util.Filepath;

public class LocalHistoryStorage implements HistoryStorage
{
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_RECORDS = Conversation.MAX_MESSAGES * ConversationHistory.MAX_CONVERSATIONS;
	private static final int MAX_BYTES = 32 * 1024 * 1024;
	private final RootDirectory rootDirectory;
	private final Gson gson;
	private final Executor executor;
	private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

	public LocalHistoryStorage(RootDirectory rootDirectory, Gson gson, Executor executor)
	{
		this.rootDirectory = rootDirectory;
		this.gson = gson;
		this.executor = executor;
	}

	@Override
	public CompletableFuture<List<PrivateMessage>> load(String accountKey)
	{
		return submit(() -> read(accountKey));
	}

	@Override
	public CompletableFuture<Void> save(String accountKey, List<PrivateMessage> messages, boolean mergeExisting)
	{
		return submit(() ->
		{
			List<PrivateMessage> snapshot = messages;
			if (mergeExisting)
			{
				ConversationHistory merged = new ConversationHistory();
				messages.forEach(merged::record);
				merged.mergeSavedHistory(read(accountKey));
				snapshot = merged.snapshot();
			}
			write(accountKey, snapshot);
			return null;
		});
	}

	@Override
	public CompletableFuture<Void> delete(String accountKey)
	{
		return submit(() ->
		{
			historyFile(accountKey).deleteIfExists();
			return null;
		});
	}

	public synchronized CompletableFuture<Void> drain()
	{
		return tail.thenApply(ignored -> null);
	}

	private synchronized <T> CompletableFuture<T> submit(IoOperation<T> operation)
	{
		CompletableFuture<T> result = tail.handle((ignored, error) -> null).thenApplyAsync(ignored ->
		{
			try
			{
				return operation.run();
			}
			catch (IOException ex)
			{
				throw new CompletionException(ex);
			}
		}, executor);
		tail = result;
		return result;
	}

	@FunctionalInterface
	private interface IoOperation<T>
	{
		T run() throws IOException;
	}

	private List<PrivateMessage> read(String accountKey) throws IOException
	{
		Filepath file = historyFile(accountKey);
		if (!file.exists())
		{
			return Collections.emptyList();
		}
		if (file.size() > MAX_BYTES)
		{
			throw new IOException("Saved history is too large");
		}
		try (InputStream raw = file.openInputStream();
			InputStream stream = new LimitedInputStream(new GZIPInputStream(raw));
			JsonReader reader = gson.newJsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			reader.setLenient(false);
			List<PrivateMessage> messages = null;
			int version = -1;
			reader.beginObject();
			while (reader.hasNext())
			{
				switch (reader.nextName())
				{
					case "version":
						version = reader.nextInt();
						break;
					case "messages":
						messages = new ArrayList<>();
						reader.beginArray();
						while (reader.hasNext())
						{
							if (messages.size() >= MAX_RECORDS)
							{
								throw new IOException("Saved history contains too many messages");
							}
							messages.add(readMessage(reader));
						}
						reader.endArray();
						break;
					default:
						reader.skipValue();
				}
			}
			reader.endObject();
			if (version != FORMAT_VERSION || messages == null || reader.peek() != JsonToken.END_DOCUMENT)
			{
				throw new IOException("Unsupported or incomplete saved history");
			}
			return messages;
		}
		catch (IllegalStateException | IllegalArgumentException ex)
		{
			throw new IOException("Invalid saved history", ex);
		}
	}

	private void write(String accountKey, List<PrivateMessage> messages) throws IOException
	{
		Filepath file = historyFile(accountKey);
		file.getParent().createDirectories();
		Filepath temporary = file.getParent().createTempFile("history-", ".tmp");
		try
		{
			try (OutputStream raw = temporary.openOutputStream();
				JsonWriter writer = gson.newJsonWriter(new OutputStreamWriter(
				new GZIPOutputStream(raw), StandardCharsets.UTF_8)))
			{
				writer.beginObject();
				writer.name("version").value(FORMAT_VERSION);
				writer.name("messages").beginArray();
				for (PrivateMessage message : messages)
				{
					writer.beginObject();
					writer.name("id").value(message.getId());
					writer.name("player").value(message.getPlayerName());
					writer.name("text").value(message.getText());
					writer.name("time").value(message.getTimestamp().toEpochMilli());
					writer.name("outgoing").value(message.isOutgoing());
					writer.endObject();
				}
				writer.endArray().endObject();
			}
			try
			{
				temporary.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException ex)
			{
				temporary.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			temporary.deleteIfExists();
		}
	}

	private PrivateMessage readMessage(JsonReader reader) throws IOException
	{
		String id = null;
		String player = null;
		String text = null;
		Long timestamp = null;
		Boolean outgoing = null;
		reader.beginObject();
		while (reader.hasNext())
		{
			switch (reader.nextName())
			{
				case "id": id = reader.nextString(); break;
				case "player": player = reader.nextString(); break;
				case "text": text = reader.nextString(); break;
				case "time": timestamp = reader.nextLong(); break;
				case "outgoing": outgoing = reader.nextBoolean(); break;
				default: reader.skipValue();
			}
		}
		reader.endObject();
		if (id == null || player == null || player.trim().isEmpty() || player.length() > 32
			|| text == null || text.length() > 8192 || timestamp == null || outgoing == null)
		{
			throw new IOException("Incomplete or invalid saved message");
		}
		UUID.fromString(id);
		return new PrivateMessage(id, player, text, Instant.ofEpochMilli(timestamp), outgoing);
	}

	private Filepath historyFile(String accountKey) throws IOException
	{
		if (accountKey == null || accountKey.isEmpty())
		{
			throw new IOException("No character selected");
		}
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(accountKey.getBytes(StandardCharsets.UTF_8));
			StringBuilder directory = new StringBuilder();
			for (byte value : digest)
			{
				directory.append(Character.forDigit((value & 0xff) >> 4, 16));
				directory.append(Character.forDigit(value & 0xf, 16));
			}
			return rootDirectory.get().joinSegment(directory.toString()).joinSegment("conversations.json.gz");
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IOException("Unable to identify character storage", ex);
		}
	}

	@FunctionalInterface
	public interface RootDirectory
	{
		Filepath get() throws IOException;
	}

	private static class LimitedInputStream extends FilterInputStream
	{
		private int remaining = MAX_BYTES;

		LimitedInputStream(InputStream stream)
		{
			super(stream);
		}

		@Override
		public int read() throws IOException
		{
			int value = in.read();
			if (value != -1 && --remaining < 0)
			{
				throw new IOException("Decompressed history is too large");
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException
		{
			int count = in.read(buffer, offset, Math.min(length, remaining + 1));
			if (count > 0 && (remaining -= count) < 0)
			{
				throw new IOException("Decompressed history is too large");
			}
			return count;
		}
	}
}
