package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.FriendStatus;
import java.util.Map;
import net.runelite.api.Friend;
import net.runelite.api.Nameable;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FriendStatusReaderTest
{
	@Test
	public void mapsOnlineOfflineAndUninitializedWorlds()
	{
		Map<String, FriendStatus> statuses = FriendStatusReader.snapshot(new Friend[]{
			friend("Alice", null, 301), friend("Bob", null, 0), friend("Carol", null, -1), null});
		assertEquals(FriendStatus.ONLINE, statuses.get("alice"));
		assertEquals(FriendStatus.OFFLINE, statuses.get("bob"));
		assertEquals(FriendStatus.UNKNOWN, statuses.get("carol"));
		assertTrue(FriendStatusReader.snapshot(null).isEmpty());
		assertTrue(FriendStatusReader.snapshot(new Friend[0]).isEmpty());
	}

	@Test
	public void normalizesNamesAndUsesPreviousNamesWithoutOverridingCurrentNames()
	{
		Map<String, FriendStatus> statuses = FriendStatusReader.snapshot(new Friend[]{
			friend("Alice\u00a0Smith", "Old_Alice", 301), friend("Old Alice", null, 0)});
		assertEquals(FriendStatus.ONLINE, statuses.get("alice smith"));
		assertEquals(FriendStatus.OFFLINE, statuses.get("old alice"));
		Map<String, FriendStatus> renamed = FriendStatusReader.snapshot(new Friend[]{friend("New Alice", "Old Alice", 301)});
		assertEquals(FriendStatus.ONLINE, renamed.get("old alice"));
	}

	private Friend friend(String name, String previous, int world)
	{
		return new Friend()
		{
			@Override
			public String getName()
			{
				return name;
			}

			@Override
			public String getPrevName()
			{
				return previous;
			}

			@Override
			public int getWorld()
			{
				return world;
			}

			@Override
			public int compareTo(Nameable other)
			{
				return name.compareTo(other.getName());
			}
		};
	}
}
