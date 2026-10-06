package com.enhancedmessaging;

import com.enhancedmessaging.domain.ConversationHistory;
import com.enhancedmessaging.domain.Conversation;
import com.enhancedmessaging.presentation.WidgetUnreadStyle;
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
		+ ConversationHistory.MAX_CONVERSATIONS + " players.\n"
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
		description = "Show the movable message widget in the game window.")
	default boolean widgetEnabled() { return false; }

	@ConfigItem(keyName = "widgetLowFootprint", name = "Low footprint", section = widgetSection, position = 1,
		description = "Show only avatars and unread indicators in a resizable grid. Widen for more columns, narrow for more rows. Switching modes resets the dragged size.")
	default boolean widgetLowFootprint() { return false; }

	@Range(min = 1, max = 10)
	@ConfigItem(keyName = "widgetChatCount", name = "Visible chats", section = widgetSection, position = 2,
		description = "Maximum recent chats to show. The latest incoming message sender always comes first.")
	default int widgetChatCount() { return 3; }

	@Range(min = 0, max = 3)
	@ConfigItem(keyName = "widgetPreviewCount", name = "Messages per chat", section = widgetSection, position = 3,
		description = "Show the latest 0 to 3 messages per chat in the regular widget. Previews do not mark messages read.")
	default int widgetPreviewCount() { return 1; }

	@ConfigItem(keyName = "widgetAvatars", name = "Show avatars", section = widgetSection, position = 4,
		description = "Show each chat's local avatar. Low footprint always shows avatars.")
	default boolean widgetAvatars() { return true; }

	@ConfigItem(keyName = "widgetStatus", name = "Show friend status", section = widgetSection, position = 5,
		description = "Show online/offline orbs in the regular widget for players on your friend list.")
	default boolean widgetStatus() { return true; }

	@ConfigItem(keyName = "widgetUnread", name = "Show New indicator", section = widgetSection, position = 6,
		description = "Show the selected unread indicator for new conversations.")
	default boolean widgetUnread() { return true; }

	@ConfigItem(keyName = "widgetUnreadStyle", name = "Unread indicator style", section = widgetSection, position = 7,
		description = "Choose a New pill or a glow around unread avatars in either widget layout. Glow shows avatars while unread indicators are enabled.")
	default WidgetUnreadStyle widgetUnreadStyle() { return WidgetUnreadStyle.PILL; }

	@ConfigItem(keyName = "widgetClickToOpen", name = "Click opens sidebar", section = widgetSection, position = 8,
		description = "Click a widget chat or compact avatar to open its sidebar conversation.")
	default boolean widgetClickToOpen() { return true; }
}
