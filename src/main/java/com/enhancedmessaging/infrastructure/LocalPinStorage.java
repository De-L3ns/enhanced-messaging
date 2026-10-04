package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.application.PinService;
import com.enhancedmessaging.application.PinStorage;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.runelite.client.util.Filepath;

public class LocalPinStorage implements PinStorage
{
	private final RootDirectory rootDirectory;
	private final Gson gson;
	private final Executor executor;
	private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

	public LocalPinStorage(RootDirectory rootDirectory, Gson gson, Executor executor)
	{
		this.rootDirectory = rootDirectory;
		this.gson = gson;
		this.executor = executor;
	}

	@Override
	public CompletableFuture<List<String>> load(String account)
	{
		return submit(() ->
		{
			Filepath file = pinFile(account);
			if (!file.exists())
			{
				return List.of();
			}
			if (file.size() > 16384)
			{
				throw new IOException("Pin file is too large");
			}
			try (InputStream input = file.openInputStream();
				JsonReader reader = gson.newJsonReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
			{
				reader.setLenient(false);
				int version = -1;
				List<String> players = null;
				reader.beginObject();
				while (reader.hasNext())
				{
					switch (reader.nextName())
					{
						case "version": version = reader.nextInt(); break;
						case "players":
							players = new ArrayList<>();
							reader.beginArray();
							while (reader.hasNext())
							{
								if (players.size() == PinService.MAX_PINS)
								{
									throw new IOException("Too many pinned chats");
								}
								players.add(reader.nextString());
							}
							reader.endArray();
							break;
						default: reader.skipValue();
					}
				}
				reader.endObject();
				if (version != 1 || players == null || reader.peek() != JsonToken.END_DOCUMENT)
				{
					throw new IOException("Unsupported or incomplete pin file");
				}
				validate(players);
				return List.copyOf(players);
			}
		});
	}

	@Override
	public CompletableFuture<Void> save(String account, List<String> players)
	{
		List<String> snapshot = List.copyOf(players);
		return submit(() ->
		{
			validate(snapshot);
			Filepath file = pinFile(account);
			file.getParent().createDirectories();
			Filepath temporary = file.getParent().createTempFile("pins-", ".tmp");
			try
			{
				try (OutputStream output = temporary.openOutputStream();
					JsonWriter writer = gson.newJsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)))
				{
					writer.beginObject().name("version").value(1).name("players").beginArray();
					for (String player : snapshot)
					{
						writer.value(player);
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
			return null;
		});
	}

	private static void validate(List<String> players) throws IOException
	{
		if (players.size() > PinService.MAX_PINS)
		{
			throw new IOException("Too many pinned chats");
		}
		for (String player : players)
		{
			if (player == null || player.trim().isEmpty() || !player.matches("[A-Za-z0-9 _-]{1,32}"))
			{
				throw new IOException("Invalid pinned player name");
			}
		}
	}

	private Filepath pinFile(String account) throws IOException
	{
		if (account == null)
		{
			throw new IOException("Log in before saving pins");
		}
		try
		{
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
			StringBuilder name = new StringBuilder();
			for (byte value : hash)
			{
				name.append(Character.forDigit((value & 0xff) >> 4, 16));
				name.append(Character.forDigit(value & 0xf, 16));
			}
			return rootDirectory.get().joinSegment("pins").joinSegment(name + ".json");
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IOException("Unable to identify character pin storage", ex);
		}
	}

	public synchronized CompletableFuture<Void> drain()
	{
		return tail.handle((ignored, error) -> null);
	}

	private synchronized <T> CompletableFuture<T> submit(IoOperation<T> operation)
	{
		CompletableFuture<T> result = tail.handle((ignored, error) -> null).thenApplyAsync(ignored ->
		{
			try
			{
				return operation.run();
			}
			catch (IOException | IllegalArgumentException | IllegalStateException ex)
			{
				throw new CompletionException(ex);
			}
		}, executor);
		tail = result;
		return result;
	}

	@FunctionalInterface
	public interface RootDirectory
	{
		Filepath get() throws IOException;
	}

	@FunctionalInterface
	private interface IoOperation<T>
	{
		T run() throws IOException;
	}
}
