package com.enhancedmessaging;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EnhancedMessagingLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EnhancedMessagingPlugin.class);
		RuneLite.main(args);
	}
}
