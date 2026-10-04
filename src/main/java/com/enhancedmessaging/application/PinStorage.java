package com.enhancedmessaging.application;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PinStorage
{
	CompletableFuture<List<String>> load(String account);
	CompletableFuture<Void> save(String account, List<String> players);
}
