package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.PrivateMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryCoordinatorTest
{
	private final ConversationService conversations = new ConversationService();
	private final FakeStorage storage = new FakeStorage();
	private final ManualScheduler scheduler = new ManualScheduler();
	private final HistoryCoordinator coordinator = new HistoryCoordinator(conversations, storage,
		scheduler, Runnable::run, () -> { }, () -> { });

	@After
	public void tearDown()
	{
		coordinator.close();
		scheduler.shutdownNow();
	}

	@Test
	public void resolvedCommandTextIsSavedAfterTheRawMessageWasAlreadyWritten()
	{
		startRetaining();
		PrivateMessage command = message("!kc zulrah");
		coordinator.record(command);
		scheduler.runTasks();
		assertEquals("!kc zulrah", storage.saves.get(0).get(0).getText());
		PrivateMessage updated = new PrivateMessage(command.getId(), command.getPlayerName(), "Zulrah: 42 killed",
			command.getTimestamp(), command.isOutgoing());
		coordinator.updateMessage(updated);
		scheduler.runTasks();
		assertEquals(2, storage.saves.size());
		assertEquals(List.of(updated), storage.saves.get(1));
		coordinator.updateMessage(updated);
		scheduler.runTasks();
		assertEquals("An unchanged edit must not create another save", 2, storage.saves.size());
		coordinator.deleteHistory();
		coordinator.updateMessage(updated);
		assertTrue(conversations.isEmpty());
	}

	@Test
	public void resolutionDuringHistoryLoadSurvivesAnOlderSavedCopy()
	{
		coordinator.switchAccount("account-a");
		coordinator.setRetentionEnabled(true);
		PrivateMessage command = message("!kc zulrah");
		coordinator.record(command);
		PrivateMessage updated = new PrivateMessage(command.getId(), command.getPlayerName(), "Zulrah: 42 killed",
			command.getTimestamp(), command.isOutgoing());
		coordinator.updateMessage(updated);
		storage.loads.get(0).complete(List.of(command));
		scheduler.runTasks();
		assertEquals(List.of(updated), conversations.snapshot());
		assertEquals(List.of(updated), storage.saves.get(0));
	}

	@Test
	public void disabledByDefaultDoesNotReadOrWriteFiles()
	{
		coordinator.switchAccount("account-a");
		coordinator.record(message("Session only"));
		coordinator.flush();
		assertFalse(coordinator.isRetentionEnabled());
		assertEquals(0, storage.loads.size());
		assertEquals(0, storage.saves.size());
		assertEquals(0, scheduler.tasks.size());
	}

	@Test
	public void enablingMergesSavedHistoryWithMessagesArrivingDuringLoad()
	{
		coordinator.switchAccount("account-a");
		PrivateMessage current = message("Current session");
		PrivateMessage arriving = message("Arrived during load");
		PrivateMessage saved = message("Previous session");
		coordinator.record(current);
		coordinator.setRetentionEnabled(true);
		coordinator.record(arriving);
		storage.loads.get(0).complete(List.of(saved));

		assertEquals(List.of(saved, current, arriving), conversations.snapshot());
		scheduler.runTasks();
		assertEquals(List.of(saved, current, arriving), storage.saves.get(0));
		assertEquals("account-a", storage.savedAccounts.get(0));
		assertFalse(storage.mergedSaves.get(0));
	}

	@Test
	public void sameAccountKeepsWorldHopHistoryWithoutReloading()
	{
		startRetaining();
		coordinator.record(message("Kept across hop"));
		coordinator.switchAccount("account-a");
		assertEquals(1, storage.loads.size());
		assertEquals(1, conversations.snapshot().size());
	}

	@Test
	public void staleLoadCannotMixAccountsAndPendingMessagesAreSavedForTheOldAccount()
	{
		coordinator.switchAccount("account-a");
		coordinator.setRetentionEnabled(true);
		PrivateMessage pending = message("Pending A");
		coordinator.record(pending);
		coordinator.switchAccount("account-b");
		assertEquals("account-a", storage.savedAccounts.get(0));
		assertTrue(storage.mergedSaves.get(0));
		assertEquals(List.of(pending), storage.saves.get(0));

		storage.loads.get(0).complete(List.of(message("Loaded A")));
		assertTrue(conversations.snapshot().isEmpty());
		PrivateMessage fromB = message("Loaded B");
		storage.loads.get(1).complete(List.of(fromB));
		assertEquals(List.of(fromB), conversations.snapshot());
	}

	@Test
	public void turningRetentionOffCancelsThePendingSaveAndKeepsExistingFiles()
	{
		startRetaining();
		coordinator.record(message("Unsaved message"));
		ScheduledFuture<?> pending = scheduler.tasks.get(0);
		coordinator.setRetentionEnabled(false);
		scheduler.runTasks();
		coordinator.flush();

		assertTrue(pending.isCancelled());
		assertTrue(storage.saves.isEmpty());
		assertEquals(0, storage.deleteCount);
		assertEquals(1, conversations.snapshot().size());
	}

	@Test
	public void turningRetentionBackOnDoesNotDuplicatePreviouslyLoadedMessages()
	{
		coordinator.switchAccount("account-a");
		coordinator.setRetentionEnabled(true);
		PrivateMessage previous = message("Previous");
		storage.loads.get(0).complete(List.of(previous));
		coordinator.setRetentionEnabled(false);
		PrivateMessage current = message("Current");
		coordinator.record(current);
		coordinator.setRetentionEnabled(true);
		storage.loads.get(1).complete(List.of(previous));

		assertEquals(List.of(previous, current), conversations.snapshot());
	}

	@Test
	public void corruptHistoryBlocksWritesUntilExplicitDeletion()
	{
		coordinator.switchAccount("account-a");
		coordinator.setRetentionEnabled(true);
		storage.loads.get(0).completeExceptionally(new IOException("Corrupt history"));
		coordinator.record(message("Keep in memory"));
		coordinator.flush();
		assertTrue(storage.saves.isEmpty());
		assertTrue(coordinator.getStatus().contains("File kept"));

		coordinator.deleteHistory();
		assertEquals(1, storage.deleteCount);
		PrivateMessage fresh = message("New history");
		coordinator.record(fresh);
		scheduler.runTasks();
		assertEquals(List.of(fresh), storage.saves.get(0));
	}

	@Test
	public void deletingIgnoresAnOlderLoadAndKeepsMessagesReceivedAfterDeletionStarted()
	{
		coordinator.switchAccount("account-a");
		coordinator.setRetentionEnabled(true);
		coordinator.record(message("Before deletion"));
		storage.deleteResult = new CompletableFuture<>();
		coordinator.deleteHistory();
		PrivateMessage fresh = message("After deletion started");
		coordinator.record(fresh);
		storage.loads.get(0).complete(List.of(message("Old saved history")));
		assertEquals(List.of(fresh), conversations.snapshot());
		assertTrue(storage.saves.isEmpty());

		storage.deleteResult.complete(null);
		scheduler.runTasks();
		assertEquals(List.of(fresh), storage.saves.get(0));
	}

	@Test
	public void closeFlushesPendingMessagesAndCancelsTheScheduledTask()
	{
		startRetaining();
		PrivateMessage message = message("Final message");
		coordinator.record(message);
		ScheduledFuture<?> task = scheduler.tasks.get(0);
		coordinator.close();
		scheduler.runTasks();

		assertTrue(task.isCancelled());
		assertEquals(List.of(message), storage.saves.get(0));
		assertEquals(1, storage.saves.size());
	}

	@Test
	public void messagesArrivingWhileTheSaveWaitsForTheUiAreBatchedTogether()
	{
		ArrayDeque<Runnable> uiTasks = new ArrayDeque<>();
		HistoryCoordinator queued = startRetainingWithQueuedUi(uiTasks);
		try
		{
			PrivateMessage first = message("Before timer fires");
			PrivateMessage second = message("Before UI callback runs");
			queued.record(first);
			scheduler.runTasks();
			queued.record(second);

			assertTrue("The queued UI save must not create another timer", scheduler.tasks.isEmpty());
			uiTasks.removeFirst().run();
			assertEquals(List.of(first, second), storage.saves.get(0));
			assertEquals(1, storage.saves.size());

			PrivateMessage third = message("Next batch");
			queued.record(third);
			ScheduledFuture<?> next = scheduler.tasks.get(0);
			queued.close();
			assertTrue("The next batch timer must remain cancellable", next.isCancelled());
			assertEquals(List.of(first, second, third), storage.saves.get(1));
		}
		finally
		{
			queued.close();
		}
	}

	@Test
	public void closingBeforeTheQueuedSaveRunsFlushesOnceAndIgnoresTheOldCallback()
	{
		ArrayDeque<Runnable> uiTasks = new ArrayDeque<>();
		HistoryCoordinator queued = startRetainingWithQueuedUi(uiTasks);
		try
		{
			PrivateMessage finalMessage = message("Final message");
			queued.record(finalMessage);
			scheduler.runTasks();
			queued.close();
			while (!uiTasks.isEmpty())
			{
				uiTasks.removeFirst().run();
			}

			assertEquals(1, storage.saves.size());
			assertEquals(List.of(finalMessage), storage.saves.get(0));
			assertTrue(scheduler.tasks.isEmpty());
		}
		finally
		{
			queued.close();
		}
	}

	private HistoryCoordinator startRetainingWithQueuedUi(ArrayDeque<Runnable> uiTasks)
	{
		HistoryCoordinator queued = new HistoryCoordinator(conversations, storage, scheduler,
			uiTasks::addLast, () -> { }, () -> { });
		queued.switchAccount("account-a");
		queued.setRetentionEnabled(true);
		storage.loads.get(0).complete(List.of());
		uiTasks.removeFirst().run();
		return queued;
	}

	@Test
	public void failedSavesCanBeRetriedWithAllMessagesStillInMemory()
	{
		startRetaining();
		storage.failSave = true;
		PrivateMessage first = message("First");
		coordinator.record(first);
		scheduler.runTasks();
		assertTrue(coordinator.getStatus().contains("Save failed"));

		storage.failSave = false;
		PrivateMessage second = message("Second");
		coordinator.record(second);
		scheduler.runTasks();
		assertEquals(List.of(first, second), storage.saves.get(1));
	}

	@Test
	public void logoutClearsUnidentifiedSessionMessagesToo()
	{
		coordinator.record(message("No profile key yet"));
		coordinator.logout();
		assertTrue(conversations.snapshot().isEmpty());
	}

	@Test
	public void changingRetentionDuringDeletionDoesNotLeaveTheControllerStuck()
	{
		startRetaining();
		storage.deleteResult = new CompletableFuture<>();
		coordinator.deleteHistory();
		coordinator.setRetentionEnabled(false);
		coordinator.setRetentionEnabled(true);
		storage.deleteResult.complete(null);
		storage.loads.get(1).complete(List.of());
		coordinator.record(message("Still saving"));
		scheduler.runTasks();
		assertEquals(1, storage.saves.size());
	}

	@Test
	public void closingDuringDeletionKeepsMessagesReceivedAfterTheDeleteRequest()
	{
		startRetaining();
		storage.deleteResult = new CompletableFuture<>();
		coordinator.deleteHistory();
		PrivateMessage fresh = message("Received after delete");
		coordinator.record(fresh);
		coordinator.close();

		assertEquals(List.of(fresh), storage.saves.get(0));
		assertTrue(storage.mergedSaves.get(0));
	}

	private void startRetaining()
	{
		coordinator.switchAccount("account-a");
		coordinator.setRetentionEnabled(true);
		storage.loads.get(0).complete(List.of());
	}

	private PrivateMessage message(String text)
	{
		return new PrivateMessage("Alice", text, Instant.ofEpochSecond(100), false);
	}

	private static class FakeStorage implements HistoryStorage
	{
		private final List<CompletableFuture<List<PrivateMessage>>> loads = new ArrayList<>();
		private final List<List<PrivateMessage>> saves = new ArrayList<>();
		private final List<String> savedAccounts = new ArrayList<>();
		private final List<Boolean> mergedSaves = new ArrayList<>();
		private CompletableFuture<Void> deleteResult = CompletableFuture.completedFuture(null);
		private boolean failSave;
		private int deleteCount;

		@Override
		public CompletableFuture<List<PrivateMessage>> load(String account)
		{
			CompletableFuture<List<PrivateMessage>> future = new CompletableFuture<>();
			loads.add(future);
			return future;
		}

		@Override
		public CompletableFuture<Void> save(String account, List<PrivateMessage> messages, boolean merge)
		{
			saves.add(messages);
			savedAccounts.add(account);
			mergedSaves.add(merge);
			return failSave ? CompletableFuture.failedFuture(new IOException("Disk unavailable"))
				: CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> delete(String account)
		{
			deleteCount++;
			return deleteResult;
		}
	}

	private static class ManualScheduler extends ScheduledThreadPoolExecutor
	{
		private final List<Task> tasks = new ArrayList<>();

		ManualScheduler()
		{
			super(1);
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
		{
			Task task = new Task(command);
			tasks.add(task);
			return task;
		}

		void runTasks()
		{
			List<Task> pending = new ArrayList<>(tasks);
			tasks.clear();
			pending.forEach(Task::run);
		}
	}

	private static class Task extends FutureTask<Void> implements ScheduledFuture<Void>
	{
		Task(Runnable command)
		{
			super(command, null);
		}

		@Override
		public long getDelay(TimeUnit unit)
		{
			return 0;
		}

		@Override
		public int compareTo(Delayed other)
		{
			return 0;
		}
	}
}
