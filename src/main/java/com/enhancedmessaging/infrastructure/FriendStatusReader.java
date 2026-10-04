package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.domain.FriendStatus;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Friend;
import net.runelite.client.util.Text;

public final class FriendStatusReader
{
	private FriendStatusReader()
	{
	}

	// Read only on the client thread; the returned snapshot contains no live client objects.
	public static Map<String, FriendStatus> snapshot(Friend[] friends)
	{
		if (friends == null || friends.length == 0)
		{
			return Collections.emptyMap();
		}
		Map<String, FriendStatus> statuses = new HashMap<>();
		Map<String, FriendStatus> previousNames = new HashMap<>();
		for (Friend friend : friends)
		{
			if (friend == null)
			{
				continue;
			}
			int world = friend.getWorld();
			FriendStatus status = world > 0 ? FriendStatus.ONLINE : world == 0 ? FriendStatus.OFFLINE : FriendStatus.UNKNOWN;
			if (friend.getName() != null)
			{
				statuses.put(key(friend.getName()), status);
			}
			if (friend.getPrevName() != null)
			{
				previousNames.put(key(friend.getPrevName()), status);
			}
		}
		// A current name takes precedence over another friend's old name.
		previousNames.forEach(statuses::putIfAbsent);
		return Collections.unmodifiableMap(statuses);
	}

	private static String key(String name)
	{
		return Text.toJagexName(name).toLowerCase(Locale.ROOT);
	}
}
