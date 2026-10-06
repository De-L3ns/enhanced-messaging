package com.enhancedmessaging.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

@Value
public class BossKillCount
{
	private static final String COUNT = "([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)";
	private static final Pattern RESPONSE = Pattern.compile("(.+?) kill count: *" + COUNT, Pattern.CASE_INSENSITIVE);
	private static final Pattern LEGACY_RESPONSE = Pattern.compile("(.+?): *" + COUNT + " killed", Pattern.CASE_INSENSITIVE);
	String boss;
	String count;

	public static BossKillCount fromText(String text)
	{
		if (text == null || text.length() > 256) { return null; }
		String trimmed = text.trim();
		Matcher matcher = RESPONSE.matcher(trimmed);
		if (!matcher.matches())
		{
			matcher = LEGACY_RESPONSE.matcher(trimmed);
			if (!matcher.matches()) { return null; }
		}
		String name = matcher.group(1).trim();
		return name.isEmpty() ? null : new BossKillCount(name, matcher.group(2));
	}
}
