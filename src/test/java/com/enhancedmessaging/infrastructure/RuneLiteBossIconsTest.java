package com.enhancedmessaging.infrastructure;

import org.junit.Test;

import static org.junit.Assert.*;

public class RuneLiteBossIconsTest
{
	@Test
	public void recognisesNativeBossNamesAndFormattingVariationsWithoutALookup()
	{
		RuneLiteBossIcons icons = new RuneLiteBossIcons(null);
		assertEquals("Vardorvis", icons.canonicalName("vardorvis"));
		assertEquals("Zulrah", icons.canonicalName("Zulrah"));
		assertEquals("Nightmare", icons.canonicalName("The Nightmare"));
		assertEquals("K'ril Tsutsaroth", icons.canonicalName("Kril Tsutsaroth"));
		assertEquals("TzKal-Zuk", icons.canonicalName("tzkal zuk"));
		assertEquals("Chambers of Xeric: Challenge Mode", icons.canonicalName("Chambers of Xeric Challenge Mode"));
		assertNull(icons.canonicalName("Attack"));
		assertNull(icons.canonicalName("Unknown boss"));
		assertNull(icons.load("Unknown boss").join());
	}
}
