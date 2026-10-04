package com.enhancedmessaging.infrastructure;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalAvatarStorageTest
{
	private Filepath directory;
	private LocalAvatarStorage storage;

	@Before
	public void setUp() throws IOException
	{
		directory = Filepath.Unchecked.getRooted(Paths.get(System.getProperty("java.io.tmpdir")))
			.createTempDir("enhanced-messaging-avatars-").rooted();
		storage = new LocalAvatarStorage(() -> directory, Runnable::run);
	}

	@After
	public void tearDown() throws IOException
	{
		directory.deleteRecursively();
	}

	@Test
	public void stockAssetsAreBundledAndLoadedOnce()
	{
		CompletableFuture<Map<String, BufferedImage>> stock = storage.stock();
		assertTrue(stock == storage.stock());
		assertEquals(6, stock.join().size());
		stock.join().values().forEach(image ->
		{
			assertEquals(48, image.getWidth());
			assertEquals(48, image.getHeight());
			assertTrue(image.getColorModel().hasAlpha());
		});
	}

	@Test
	public void importCenterCropsAndKeepsItsOwnSmallCopy() throws IOException
	{
		BufferedImage source = new BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 80; y++)
		{
			for (int x = 0; x < 160; x++)
			{
				source.setRGB(x, y, (x < 40 ? Color.RED : x >= 120 ? Color.BLUE : Color.GREEN).getRGB());
			}
		}
		Filepath file = writeSource("original.png", source, "png");
		BufferedImage imported = storage.importImage("account-a", "Alice", file).join();
		assertEquals(48, imported.getWidth());
		assertEquals(Color.GREEN.getRGB(), imported.getRGB(0, 0));
		assertEquals(Color.GREEN.getRGB(), imported.getRGB(47, 47));
		file.delete();
		assertEquals(Color.GREEN.getRGB(), storage.load("account-a", "ALICE").join().getRGB(24, 24));
		try (java.util.stream.Stream<Filepath> files = directory.joinSegment("avatars").walk(1))
		{
			Filepath saved = files.filter(Filepath::isFile).findFirst().get();
			assertTrue(saved.getFileName().matches("[0-9a-f]{64}\\.png"));
			assertTrue(saved.size() < 2048);
		}
	}

	@Test
	public void jpegImportsAreConvertedToPng() throws IOException
	{
		BufferedImage source = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
		BufferedImage imported = storage.importImage("account-a", "Alice", writeSource("photo.jpg", source, "jpeg")).join();
		assertEquals(48, imported.getWidth());
		assertEquals(48, imported.getHeight());
		assertTrue(imported.getColorModel().hasAlpha());
		assertEquals(48, storage.load("account-a", "Alice").join().getWidth());
	}

	@Test
	public void invalidAndOversizedImportsKeepThePreviousAvatar() throws IOException
	{
		BufferedImage previous = storage.selectStock("account-a", "Alice", "mage").join();
		Filepath broken = directory.joinSegment("broken.png");
		broken.write("This is not an image");
		assertFails(storage.importImage("account-a", "Alice", broken));
		Filepath large = directory.joinSegment("large.png");
		large.write(new byte[2 * 1024 * 1024 + 1]);
		assertFails(storage.importImage("account-a", "Alice", large));
		Filepath wide = writeSource("wide.png", new BufferedImage(2049, 1, BufferedImage.TYPE_INT_ARGB), "png");
		assertFails(storage.importImage("account-a", "Alice", wide));
		assertEquals(previous.getRGB(24, 24), storage.load("account-a", "Alice").join().getRGB(24, 24));
	}

	@Test
	public void assignmentsAreScopedToCharacterAndPlayerAndResetIsSelective()
	{
		storage.selectStock("account-a", "Alice", "mage").join();
		storage.selectStock("account-a", "Bob", "knight").join();
		storage.selectStock("account-b", "Alice", "ranger").join();
		storage.reset("account-a", "ALICE").join();
		assertNull(storage.load("account-a", "Alice").join());
		assertEquals(storage.stock().join().get("knight").getRGB(24, 12), storage.load("account-a", "Bob").join().getRGB(24, 12));
		assertEquals(storage.stock().join().get("ranger").getRGB(24, 12), storage.load("account-b", "Alice").join().getRGB(24, 12));
	}

	@Test
	public void resetCannotBeUndoneByAnEarlierPendingImport() throws IOException
	{
		ArrayDeque<Runnable> tasks = new ArrayDeque<>();
		LocalAvatarStorage queued = new LocalAvatarStorage(() -> directory, tasks::addLast);
		Filepath source = writeSource("photo.png", new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB), "png");
		CompletableFuture<BufferedImage> imported = queued.importImage("account-a", "Alice", source);
		CompletableFuture<Void> reset = queued.reset("account-a", "Alice");
		CompletableFuture<Void> drained = queued.drain();
		assertFalse(imported.isDone());
		assertFalse(reset.isDone());
		assertFalse(drained.isDone());
		while (!tasks.isEmpty())
		{
			tasks.removeFirst().run();
		}
		assertTrue(drained.isDone());
		assertNull(storage.load("account-a", "Alice").join());
	}

	private Filepath writeSource(String name, BufferedImage image, String format) throws IOException
	{
		Filepath file = directory.joinSegment(name);
		try (OutputStream output = file.openOutputStream(); MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output))
		{
			assertTrue(ImageIO.write(image, format, stream));
		}
		return file;
	}

	private void assertFails(CompletableFuture<?> result)
	{
		try
		{
			result.join();
			fail("Expected import to be rejected");
		}
		catch (CompletionException expected)
		{
			assertTrue(expected.getCause() instanceof IOException);
		}
	}
}
