package com.fiw.graves.config;

import com.fiw.graves.Constants;
import com.fiw.graves.platform.Services;
import com.fiw.graves.util.FileUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gameplay configuration, stored as JSON in the standard mod config directory. Purely tunable
 * behaviour - no messages, aliases, or permission nodes live here.
 */
public class Config {
	private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_NAME);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Config instance = new Config();

	// --- crumbling ---
	// Seconds a grave lingers before it crumbles (its contents drop on the ground). -1 = never.
	public int despawnSeconds = 600;
	// Remove a grave the moment it has been fully emptied.
	public boolean removeWhenEmptied = true;
	// When a grave crumbles from its timer, drop the leftover items instead of deleting them.
	public boolean dropItemsOnExpire = true;

	// --- collecting ---
	// Sneak-interact to sweep the whole grave into your inventory at once.
	public boolean quickCollectEnabled = true;
	// Only the owner may quick-collect.
	public boolean quickCollectOwnerOnly = false;
	// Route stored armor straight into empty armor slots when collecting.
	public boolean equipArmorOnCollect = true;
	// Only the owner (or an operator) may open a grave at all.
	public boolean ownerOnlyAccess = false;

	// --- death capture ---
	public boolean storeItems = true;
	public boolean storeExperience = true;
	// Capture items/xp into a grave even when keepInventory would normally keep them on the player.
	public boolean overrideKeepInventory = false;
	public boolean overrideKeepExperience = false;
	// Fraction of the player's xp that goes into the grave (1.0 = all of it).
	public double experienceStoredFraction = 1.0;

	// --- marker / hologram geometry ---
	// Vertical offset (blocks) of the floating head from the death spot. Raise to lift it out of
	// the ground, lower to sink it in.
	public double markerHeight = -0.6;
	// Vertical offset (blocks) of the floating text.
	public double hologramHeight = 0.5;
	// Slowly spin the head.
	public boolean autoRotateEnabled = false;
	public double autoRotateSpeed = 10.0;
	public HologramStyle hologram = new HologramStyle();

	// --- teleporting ---
	// Let players use /graves tp to warp to their own graves. Operators can always teleport.
	public boolean playerGraveTeleport = false;

	// --- limits ---
	// Max simultaneous graves one player may have; oldest crumbles past the cap. 0 or -1 = unlimited.
	public int graveLimitPerPlayer = -1;

	// --- rollback / anti-grief safety net ---
	// How many recently-lost graves per player an operator can /graves rollback. 0 disables it.
	public int rollbackHistoryPerPlayer = 5;
	// Drop rollback entries older than this many minutes. -1 = keep forever.
	public int rollbackRetentionMinutes = 1440;

	// --- head texture override ---
	// Base64 skin texture used for every grave head instead of the owner's own skin. Empty = off.
	public String headTextureOverride = "";

	// --- restrictions ---
	// Dimension ids (e.g. "minecraft:the_nether") where graves never spawn.
	public List<String> disabledDimensions = new ArrayList<>();
	// Damage type ids (e.g. "minecraft:lava") that produce no grave.
	public List<String> blacklistedDamageTypes = new ArrayList<>();
	// Items destroyed on death rather than stored (they drop as normal instead of entering the grave).
	public List<BlacklistedItem> blacklistedItems = new ArrayList<>();

	public static Config get() {
		return instance;
	}

	public static void load() {
		Path path = configPath();
		if (Files.exists(path)) {
			try {
				String content = Files.readString(path);
				Config loaded = GSON.fromJson(content, Config.class);
				instance = loaded != null ? loaded : new Config();
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Failed to load {}, using defaults", path, e);
				instance = new Config();
			}
		} else {
			instance = new Config();
		}
		instance.normalize();
		save();
	}

	/**
	 * Gson happily writes {@code null} into any field the JSON explicitly nulls out, and numbers
	 * arrive unclamped - repair everything here so no gameplay path ever has to null-check or
	 * range-check config values.
	 */
	private void normalize() {
		if (disabledDimensions == null) disabledDimensions = new ArrayList<>();
		if (blacklistedDamageTypes == null) blacklistedDamageTypes = new ArrayList<>();
		if (blacklistedItems == null) blacklistedItems = new ArrayList<>();
		blacklistedItems.removeIf(java.util.Objects::isNull);
		for (BlacklistedItem entry : blacklistedItems) {
			if (entry.itemId == null) entry.itemId = "";
			if (entry.nameContains == null) entry.nameContains = "";
		}

		if (hologram == null) hologram = new HologramStyle();
		if (hologram.backgroundColor == null) hologram.backgroundColor = "00000000";
		if (hologram.alignment == null) hologram.alignment = "CENTER";
		if (hologram.billboard == null) hologram.billboard = "VERTICAL";

		if (headTextureOverride == null) headTextureOverride = "";

		// 1.0 = store all xp; anything outside [0, 1] would duplicate or corrupt xp on death.
		experienceStoredFraction = Math.max(0.0, Math.min(1.0, experienceStoredFraction));
	}

	public static void save() {
		try {
			FileUtils.writeAtomic(configPath(), GSON.toJson(instance));
		} catch (IOException e) {
			LOGGER.error("Failed to save {}", configPath(), e);
		}
	}

	private static Path configPath() {
		return Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID).resolve("config.json");
	}

	public static class HologramStyle {
		// AARRGGBB hex; default is fully transparent (no text background).
		public String backgroundColor = "00000000";
		public String alignment = "CENTER";
		public String billboard = "VERTICAL";
		public boolean seeThrough = false;
		public boolean shadow = true;
	}

	public static class BlacklistedItem {
		public String itemId = "";
		public String nameContains = "";
	}
}
