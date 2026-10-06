package com.enhancedmessaging.application;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

public interface BossIconSource
{
	// Null means this boss has no native icon in the current client.
	String canonicalName(String boss);
	CompletableFuture<BufferedImage> load(String boss);
}
