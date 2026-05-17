package com.villagerreroll.config;

import java.util.Locale;

public record TargetBook(String enchantmentId, int level, int maxPrice) {
	public TargetBook {
		enchantmentId = normalize(enchantmentId);
	}

	public boolean matches(String actualId, int actualLevel, int actualPrice) {
		return enchantmentId.equals(normalize(actualId)) && level == actualLevel
			&& actualPrice <= maxPrice;
	}

	public static String normalize(String id) {
		if(id == null)
			return "";
		String trimmed = id.trim().toLowerCase(Locale.ROOT);
		if(!trimmed.contains(":"))
			return "minecraft:" + trimmed;
		return trimmed;
	}
}
