package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.FriendStatus;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

// Session-only friend state, confined to the Swing UI thread.
public class FriendStatusService
{
	private Map<String, FriendStatus> statuses = Collections.emptyMap();
	private String account;
	private boolean closed;

	public void switchAccount(String key)
	{
		if (!closed && !Objects.equals(account, key))
		{
			account = key;
			statuses = Collections.emptyMap();
		}
	}

	public boolean update(String key, Map<String, FriendStatus> snapshot)
	{
		if (closed || account == null || !Objects.equals(account, key) || statuses.equals(snapshot))
		{
			return false;
		}
		statuses = Collections.unmodifiableMap(new HashMap<>(snapshot));
		return true;
	}

	public FriendStatus statusFor(String player)
	{
		return statuses.getOrDefault(player.toLowerCase(Locale.ROOT), FriendStatus.UNKNOWN);
	}

	public void close()
	{
		closed = true;
		account = null;
		statuses = Collections.emptyMap();
	}
}
