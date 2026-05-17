package com.villagerreroll.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;

public final class VillagerRerollConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final List<TargetBook> targets = new ArrayList<>();
	private int range = 6;
	private BlockPos jobSite;
	private boolean lockInTrade;

	public static VillagerRerollConfig load(Path path) {
		VillagerRerollConfig config = new VillagerRerollConfig();

		if(!Files.exists(path))
			return config;

		try(Reader reader = Files.newBufferedReader(path)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

			if(json.has("range"))
				config.range = Math.max(1, json.get("range").getAsInt());
			if(json.has("jobSite"))
				config.jobSite = readBlockPos(json.getAsJsonObject("jobSite"));
			if(json.has("lockInTrade"))
				config.lockInTrade = json.get("lockInTrade").getAsBoolean();

			config.targets.clear();
			if(json.has("targets")) {
				for(JsonElement element : json.getAsJsonArray("targets")) {
					if(!element.isJsonObject())
						continue;
					TargetBook target = readTarget(element.getAsJsonObject());
					if(target != null)
						config.targets.add(target);
				}
			}
			return config;
		}catch(Exception e) {
			System.out.println("Failed to load Villager Reroll config: " + path);
			e.printStackTrace();
			return config;
		}
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try(Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(toJson(), writer);
			}
		}catch(IOException e) {
			throw new IllegalStateException("Failed to save config: " + path, e);
		}
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("range", range);

		if(jobSite != null) {
			JsonObject pos = new JsonObject();
			pos.addProperty("x", jobSite.getX());
			pos.addProperty("y", jobSite.getY());
			pos.addProperty("z", jobSite.getZ());
			json.add("jobSite", pos);
		}

		JsonArray array = new JsonArray();
		for(TargetBook target : targets) {
			JsonObject entry = new JsonObject();
			entry.addProperty("enchantmentId", target.enchantmentId());
			entry.addProperty("level", target.level());
			entry.addProperty("maxPrice", target.maxPrice());
			array.add(entry);
		}
		json.add("targets", array);
		json.addProperty("lockInTrade", lockInTrade);
		return json;
	}

	public List<TargetBook> getTargets() {
		return targets;
	}

	public int getRange() {
		return range;
	}

	public void setRange(int range) {
		this.range = Math.max(1, range);
	}

	public BlockPos getJobSite() {
		return jobSite;
	}

	public void setJobSite(BlockPos jobSite) {
		this.jobSite = jobSite;
	}

	public boolean isLockInTrade() {
		return lockInTrade;
	}

	public void setLockInTrade(boolean lockInTrade) {
		this.lockInTrade = lockInTrade;
	}

	public void addTarget(TargetBook target) {
		targets.add(target);
	}

	public boolean replaceTarget(int index, TargetBook target) {
		if(index < 0 || index >= targets.size())
			return false;
		targets.set(index, target);
		return true;
	}

	public boolean removeTarget(int index) {
		if(index < 0 || index >= targets.size())
			return false;
		targets.remove(index);
		return true;
	}

	public boolean removeTarget(TargetBook target) {
		return targets.remove(target);
	}

	public TargetBook getTarget(int index) {
		return targets.get(index);
	}

	private static TargetBook readTarget(JsonObject json) {
		if(!json.has("enchantmentId") || !json.has("level"))
			return null;
		String enchantmentId = json.get("enchantmentId").getAsString();
		int level = json.get("level").getAsInt();
		int maxPrice = json.has("maxPrice") ? json.get("maxPrice").getAsInt() : 64;
		return new TargetBook(enchantmentId, level, Math.max(1, maxPrice));
	}

	private static BlockPos readBlockPos(JsonObject json) {
		if(!json.has("x") || !json.has("y") || !json.has("z"))
			return null;
		return new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(),
			json.get("z").getAsInt());
	}
}
