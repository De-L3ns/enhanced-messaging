package com.enhancedmessaging.domain;

import java.time.Instant;
import lombok.Value;

@Value
public class PrivateMessage
{
	String playerName;
	String text;
	Instant timestamp;
	boolean outgoing;
}
