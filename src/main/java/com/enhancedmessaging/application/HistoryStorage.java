package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.PrivateMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface HistoryStorage
{
	CompletableFuture<List<PrivateMessage>> load(String accountKey);

	CompletableFuture<Void> save(String accountKey, List<PrivateMessage> messages, boolean mergeExisting);

	CompletableFuture<Void> delete(String accountKey);
}
