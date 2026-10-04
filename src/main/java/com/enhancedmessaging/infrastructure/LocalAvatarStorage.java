package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.application.AvatarStorage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.runelite.client.util.Filepath;

public class LocalAvatarStorage implements AvatarStorage
{
	public static final int IMAGE_SIZE = 48;
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final int MAX_DIMENSION = 2048;
	private final RootDirectory rootDirectory;
	private final Executor executor;
	private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);
	private CompletableFuture<Map<String, BufferedImage>> stock;

	public LocalAvatarStorage(RootDirectory rootDirectory, Executor executor)
	{
		this.rootDirectory = rootDirectory;
		this.executor = executor;
	}

	@Override
	public synchronized CompletableFuture<Map<String, BufferedImage>> stock()
	{
		if (stock == null)
		{
			stock = submit(() ->
			{
				Map<String, BufferedImage> images = new LinkedHashMap<>();
				for (String id : new String[]{"default", "knight", "mage", "ranger", "zuk", "jad"})
				{
					try (InputStream stream = LocalAvatarStorage.class.getResourceAsStream(
						"/com/enhancedmessaging/avatars/" + id + ".png"))
					{
						if (stream == null)
						{
							throw new IOException("Missing stock avatar: " + id);
						}
						images.put(id, readImage(stream));
					}
				}
				return Collections.unmodifiableMap(images);
			});
		}
		return stock;
	}

	@Override
	public CompletableFuture<BufferedImage> load(String account, String player)
	{
		return submit(() ->
		{
			Filepath file = avatarFile(account, player);
			return file.exists() ? readFile(file) : null;
		});
	}

	@Override
	public CompletableFuture<BufferedImage> selectStock(String account, String player, String stockId)
	{
		CompletableFuture<Map<String, BufferedImage>> loaded = stock();
		return submit(() ->
		{
			// Stock loading is ahead of this operation in the same serial queue.
			BufferedImage image = loaded.getNow(Collections.emptyMap()).get(stockId);
			if (image == null)
			{
				throw new IOException("Unknown stock avatar");
			}
			write(avatarFile(account, player), image);
			return image;
		});
	}

	@Override
	public CompletableFuture<BufferedImage> importImage(String account, String player, Filepath source)
	{
		return submit(() ->
		{
			BufferedImage image = readFile(source);
			write(avatarFile(account, player), image);
			return image;
		});
	}

	@Override
	public CompletableFuture<Void> reset(String account, String player)
	{
		return submit(() ->
		{
			avatarFile(account, player).deleteIfExists();
			return null;
		});
	}

	private BufferedImage readFile(Filepath file) throws IOException
	{
		if (!file.isFile() || file.size() > MAX_BYTES)
		{
			throw new IOException("Choose an image up to 2 MiB");
		}
		try (InputStream input = file.openInputStream())
		{
			return readImage(input);
		}
	}

	private BufferedImage readImage(InputStream input) throws IOException
	{
		// Avoid ImageIO's optional disk cache; all file access stays behind Filepath.
		try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(input))
		{
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext())
			{
				throw new IOException("Choose a PNG or JPEG image");
			}
			ImageReader reader = readers.next();
			try
			{
				String format = reader.getFormatName();
				if (!"png".equalsIgnoreCase(format) && !"jpeg".equalsIgnoreCase(format))
				{
					throw new IOException("Choose a PNG or JPEG image");
				}
				reader.setInput(stream, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION)
				{
					throw new IOException("Choose an image up to 2048 × 2048 pixels");
				}
				BufferedImage source = reader.read(0);
				BufferedImage resized = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = resized.createGraphics();
				try
				{
					graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					int side = Math.min(width, height);
					int x = (width - side) / 2;
					int y = (height - side) / 2;
					graphics.drawImage(source, 0, 0, IMAGE_SIZE, IMAGE_SIZE, x, y, x + side, y + side, null);
				}
				finally
				{
					graphics.dispose();
				}
				return resized;
			}
			finally
			{
				reader.dispose();
			}
		}
	}

	private void write(Filepath file, BufferedImage image) throws IOException
	{
		file.getParent().createDirectories();
		Filepath temporary = file.getParent().createTempFile("avatar-", ".tmp");
		try
		{
			try (OutputStream output = temporary.openOutputStream();
				MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output))
			{
				if (!ImageIO.write(image, "png", stream))
				{
					throw new IOException("Unable to encode avatar");
				}
			}
			try
			{
				temporary.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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

	private Filepath avatarFile(String account, String player) throws IOException
	{
		if (account == null || player == null || player.isEmpty())
		{
			throw new IOException("Log in before assigning an avatar");
		}
		try
		{
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(
				(account + '\0' + player.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
			StringBuilder name = new StringBuilder();
			for (byte value : hash)
			{
				name.append(Character.forDigit((value & 0xff) >> 4, 16));
				name.append(Character.forDigit(value & 0xf, 16));
			}
			return rootDirectory.get().joinSegment("avatars").joinSegment(name + ".png");
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IOException("Unable to identify avatar storage", ex);
		}
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
