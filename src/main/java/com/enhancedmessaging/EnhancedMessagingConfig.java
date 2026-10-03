package com.enhancedmessaging;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(EnhancedMessagingConfig.GROUP)
public interface EnhancedMessagingConfig extends Config
{
	String GROUP = "enhanced-messaging";
	String RETAIN_HISTORY = "retainHistory";
	String STORAGE_NOTICE = "Messages will be saved on this computer as compressed JSON,\n"
		+ "separately for each game character, in RuneLite's\n"
		+ "plugin-data/enhanced-messaging folder.\n\n"
		+ "Files contain player names, message text, direction and timestamps.\n"
		+ "They are not encrypted. Anyone with access to the files can read them.\n"
		+ "No messages are uploaded. Your current session will also be saved.\n\n"
		+ "The latest 500 messages per player are kept for up to 100 players.\n"
		+ "Saves are batched; a crash can lose the newest few seconds.\n"
		+ "Turning this off stops saving but leaves existing files.\n"
		+ "Use Delete saved history in the sidebar to remove them.";

	@ConfigItem(
		keyName = RETAIN_HISTORY,
		name = "Retain message history",
		description = "Save private messages locally between sessions. Files are compressed JSON and are not encrypted.",
		warning = STORAGE_NOTICE
	)
	default boolean retainHistory()
	{
		return false;
	}
}
