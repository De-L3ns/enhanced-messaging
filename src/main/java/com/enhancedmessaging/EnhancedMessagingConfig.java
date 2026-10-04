package com.enhancedmessaging;

import com.enhancedmessaging.application.ConversationService;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.domain.WidgetChatMode;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

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
		+ "The latest " + Conversation.MAX_MESSAGES + " messages per player are kept for up to "
		+ ConversationService.MAX_CONVERSATIONS + " players.\n"
		+ "Saves are batched; a crash can lose the newest few seconds.\n"
		+ "Turning this off stops saving but leaves existing files.\n"
		+ "Use Delete saved history in the sidebar to remove them.";

	@ConfigSection(name = "General", description = "Message history and general settings", position = 0)
	String generalSection = "general";

	@ConfigItem(
		keyName = RETAIN_HISTORY,
		name = "Retain message history",
		section = generalSection,
		position = 0,
		description = "Save private messages locally between sessions. Files are compressed JSON and are not encrypted.",
		warning = STORAGE_NOTICE
	)
	default boolean retainHistory()
	{
		return false;
	}

	@ConfigSection(name = "Message widget", description = "Private message overlay in the game window", position = 1)
	String widgetSection = "widget";

	@ConfigItem(keyName = "widgetEnabled", name = "Enable widget", section = widgetSection, position = 0,
		description = "Show the movable message widget. Pins are saved locally per character, independently of message retention.")
	default boolean widgetEnabled() { return false; }

	@Range(min = 1, max = 10)
	@ConfigItem(keyName = "widgetChatCount", name = "Visible chats", section = widgetSection, position = 1,
		description = "Maximum chats to show, including pins. Extra pins stay saved and can be unpinned from the sidebar.")
	default int widgetChatCount() { return 3; }

	@ConfigItem(keyName = "widgetChatMode", name = "Chat selection", section = widgetSection, position = 2,
		description = "The latest live message sender comes first. Fill remaining slots with pins and recent chats, or pins only.")
	default WidgetChatMode widgetChatMode() { return WidgetChatMode.PINNED_AND_RECENT; }

	@Range(min = 0, max = 3)
	@ConfigItem(keyName = "widgetPreviewCount", name = "Messages per chat", section = widgetSection, position = 3,
		description = "Show the latest 0 to 3 messages per chat. Previews do not mark messages read.")
	default int widgetPreviewCount() { return 1; }

	@ConfigItem(keyName = "widgetAvatars", name = "Show avatars", section = widgetSection, position = 4,
		description = "Show each chat's local avatar.")
	default boolean widgetAvatars() { return true; }

	@ConfigItem(keyName = "widgetStatus", name = "Show friend status", section = widgetSection, position = 5,
		description = "Show online/offline orbs for players on your friend list.")
	default boolean widgetStatus() { return true; }

	@ConfigItem(keyName = "widgetUnread", name = "Show New indicator", section = widgetSection, position = 6,
		description = "Show the New pill for unread conversations.")
	default boolean widgetUnread() { return true; }

	@Range(min = 180, max = 360)
	@ConfigItem(keyName = "widgetWidth", name = "Widget width", section = widgetSection, position = 7,
		description = "Default width in pixels. Changing this resets the dragged size. Hold RuneLite's overlay drag hotkey and drag an edge to resize.")
	default int widgetWidth() { return 240; }

	@ConfigItem(keyName = "widgetClickToOpen", name = "Click opens sidebar", section = widgetSection, position = 8,
		description = "Click a widget chat to open its sidebar conversation. Pin controls work independently.")
	default boolean widgetClickToOpen() { return true; }
}
