package com.enhancedmessaging.infrastructure;

import com.enhancedmessaging.application.BossIconSource;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

public class RuneLiteBossIcons implements BossIconSource
{
	private static final Map<String, HiscoreSkill> BOSSES = new HashMap<>();
	static
	{
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() == HiscoreSkillType.BOSS && skill.getSpriteId() >= 0)
			{
				BOSSES.put(key(skill.getName()), skill);
			}
		}
	}
	private final SpriteManager sprites;

	public RuneLiteBossIcons(SpriteManager sprites) { this.sprites = sprites; }

	@Override
	public String canonicalName(String boss)
	{
		HiscoreSkill skill = BOSSES.get(key(boss));
		return skill == null ? null : skill.getName();
	}

	@Override
	public CompletableFuture<BufferedImage> load(String boss)
	{
		HiscoreSkill skill = BOSSES.get(key(boss));
		if (skill == null) { return CompletableFuture.completedFuture(null); }
		CompletableFuture<BufferedImage> result = new CompletableFuture<>();
		// HiscoreSkill's sprite IDs come from RuneLite's gameval constants.
		sprites.getSpriteAsync(skill.getSpriteId(), 0, result::complete);
		return result;
	}

	private static String key(String name)
	{
		return name.toLowerCase(Locale.ROOT).trim().replaceFirst("^the\\s+", "").replaceAll("[^a-z0-9]", "");
	}
}
