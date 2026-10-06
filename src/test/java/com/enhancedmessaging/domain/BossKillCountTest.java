package com.enhancedmessaging.domain;

import org.junit.Test;

import static org.junit.Assert.*;

public class BossKillCountTest
{
	@Test
	public void parsesNativeResponsesIncludingModeNamesAndGroupedCounts()
	{
		BossKillCount count = BossKillCount.fromText("Vardorvis kill count: 1,401");
		assertEquals("Vardorvis", count.getBoss());
		assertEquals("1,401", count.getCount());
		count = BossKillCount.fromText(" Chambers of Xeric: Challenge Mode kill count: 42 ");
		assertEquals("Chambers of Xeric: Challenge Mode", count.getBoss());
		assertEquals("42", count.getCount());
		assertEquals("0", BossKillCount.fromText("Zulrah kill count: 0").getCount());
		assertEquals("123", BossKillCount.fromText("Zulrah: 123 killed").getCount());
	}

	@Test
	public void ordinaryCommandsMalformedCountsAndAdditionalTextStayPlain()
	{
		for (String text : new String[]{null, "!kc vardorvis", "Hello", "Zulrah kill count: unknown",
			"Zulrah kill count: 1,40", "Zulrah kill count: -1", "Zulrah kill count: 42 and more", "a".repeat(257)})
		{
			assertNull(text, BossKillCount.fromText(text));
		}
	}
}
