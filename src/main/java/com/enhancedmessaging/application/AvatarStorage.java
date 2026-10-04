package com.enhancedmessaging.application;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.runelite.client.util.Filepath;

public interface AvatarStorage
{
	CompletableFuture<Map<String, BufferedImage>> stock();
	CompletableFuture<BufferedImage> load(String account, String player);
	CompletableFuture<BufferedImage> selectStock(String account, String player, String stockId);
	CompletableFuture<BufferedImage> importImage(String account, String player, Filepath source);
	CompletableFuture<Void> reset(String account, String player);
}
