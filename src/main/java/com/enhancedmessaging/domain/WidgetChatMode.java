package com.enhancedmessaging.domain;

public enum WidgetChatMode
{
	PINNED_AND_RECENT("Pinned + recent"),
	PINNED_ONLY("Pinned only");

	private final String label;

	WidgetChatMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
