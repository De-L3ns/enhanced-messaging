package com.enhancedmessaging.domain;

public enum WidgetUnreadStyle
{
	PILL("New pill"),
	GLOW("Glowing avatar");

	private final String label;

	WidgetUnreadStyle(String label) { this.label = label; }

	@Override
	public String toString() { return label; }
}
