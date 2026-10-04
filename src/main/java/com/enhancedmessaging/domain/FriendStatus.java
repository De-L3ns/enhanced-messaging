package com.enhancedmessaging.domain;

public enum FriendStatus
{
	ONLINE("Online"),
	OFFLINE("Offline"),
	UNKNOWN("Status unavailable");

	private final String description;

	FriendStatus(String description)
	{
		this.description = description;
	}

	public String getDescription()
	{
		return description;
	}
}
