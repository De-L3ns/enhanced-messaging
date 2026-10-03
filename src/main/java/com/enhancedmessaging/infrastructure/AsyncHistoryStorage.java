package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.application.HistoryRepository;
import com.enhancedmessaging.application.HistoryStorage;
import com.enhancedmessaging.domain.PrivateMessage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public class AsyncHistoryStorage implements HistoryStorage
{
	private final HistoryRepository repository;
	private final Executor executor;
	private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

	public AsyncHistoryStorage(HistoryRepository repository, Executor executor)
	{
		this.repository = repository;
		this.executor = executor;
	}

	@Override
	public CompletableFuture<List<PrivateMessage>> load(String accountKey)
	{
		return submit(() -> repository.load(accountKey));
	}

	@Override
	public CompletableFuture<Void> save(String accountKey, List<PrivateMessage> messages, boolean mergeExisting)
	{
		return submit(() ->
		{
			List<PrivateMessage> snapshot = messages;
			if (mergeExisting)
			{
				ConversationService merged = new ConversationService();
				messages.forEach(merged::record);
				merged.mergeSavedHistory(repository.load(accountKey));
				snapshot = merged.snapshot();
			}
			repository.save(accountKey, snapshot);
			return null;
		});
	}

	@Override
	public CompletableFuture<Void> delete(String accountKey)
	{
		return submit(() ->
		{
			repository.delete(accountKey);
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
}
