package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.WidgetChatMode;
import lombok.Value;

@Value
public class WidgetOptions
{
	boolean enabled;
	int chatCount;
	int previewCount;
	WidgetChatMode mode;
	boolean avatars;
	boolean status;
	boolean unread;
	int width;
	boolean clickToOpen;
}
