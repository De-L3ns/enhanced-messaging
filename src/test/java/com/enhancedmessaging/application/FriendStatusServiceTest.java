package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.FriendStatus;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FriendStatusServiceTest
{
	@Test
	public void updatesOnlyWhenStatusChangesAndRemovesDeletedFriends()
	{
		FriendStatusService service = new FriendStatusService();
		service.switchAccount("account-a");
		assertTrue(service.update("account-a", Map.of("alice", FriendStatus.ONLINE)));
		assertEquals(FriendStatus.ONLINE, service.statusFor("ALICE"));
		assertEquals(FriendStatus.UNKNOWN, service.statusFor("Bob"));
		assertFalse(service.update("account-a", Map.of("alice", FriendStatus.ONLINE)));
		assertTrue(service.update("account-a", Map.of("alice", FriendStatus.OFFLINE)));
		assertEquals(FriendStatus.OFFLINE, service.statusFor("Alice"));
		assertTrue(service.update("account-a", Collections.emptyMap()));
		assertEquals(FriendStatus.UNKNOWN, service.statusFor("Alice"));
	}

	@Test
	public void accountChangesLogoutAndCloseDiscardStatusAndRejectLateUpdates()
	{
		FriendStatusService service = new FriendStatusService();
		Map<String, FriendStatus> online = Map.of("alice", FriendStatus.ONLINE);
		assertFalse(service.update(null, online));
		service.switchAccount("account-a");
		service.update("account-a", online);
		service.switchAccount("account-a");
		assertEquals(FriendStatus.ONLINE, service.statusFor("Alice"));
		service.switchAccount("account-b");
		assertEquals(FriendStatus.UNKNOWN, service.statusFor("Alice"));
		assertFalse(service.update("account-a", online));
		assertTrue(service.update("account-b", online));
		service.switchAccount(null);
		assertEquals(FriendStatus.UNKNOWN, service.statusFor("Alice"));
		assertFalse(service.update("account-b", online));
		service.switchAccount("account-b");
		service.update("account-b", online);
		service.close();
		service.switchAccount("account-b");
		assertFalse(service.update("account-b", online));
		assertEquals(FriendStatus.UNKNOWN, service.statusFor("Alice"));
	}

	@Test
	public void keepsACopyOfTheSnapshot()
	{
		FriendStatusService service = new FriendStatusService();
		service.switchAccount("account-a");
		Map<String, FriendStatus> snapshot = new HashMap<>();
		snapshot.put("alice", FriendStatus.ONLINE);
		service.update("account-a", snapshot);
		snapshot.clear();
		assertEquals(FriendStatus.ONLINE, service.statusFor("Alice"));
	}
}
