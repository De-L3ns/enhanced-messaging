package com.enhancedmessaging.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PrivateMessage
{
	String id;
	String playerName;
	String text;
	Instant timestamp;
	boolean outgoing;

	public PrivateMessage(String playerName, String text, Instant timestamp, boolean outgoing)
	{
		this(UUID.randomUUID().toString(), playerName, text, timestamp, outgoing);
	}
}
