package com.fiw.graves.grave;

import com.fiw.graves.config.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared "what should happen to a dying player's items/xp" decision logic, reused by both the
 * Fabric death-drop mixin (which must fully take over vanilla's drop logic - Fabric API has no
 * cancellable drops event) and NeoForge's native LivingDropsEvent/LivingExperienceDropEvent
 * handlers, so the rules stay identical on both loaders.
 */
public final class DeathCapture {
	public record ItemSplit(List<ItemStack> stored, List<ItemStack> dropped) {
	}

	private DeathCapture() {
	}

	public static boolean isAllowed(ServerPlayer player, String damageTypeId) {
		Config config = Config.get();
		if (config.disabledDimensions.contains(player.level().dimension().identifier().toString())) return false;
		if (config.blacklistedDamageTypes.contains(damageTypeId)) return false;
		return true;
	}

	public static boolean keepInventory(ServerLevel level) {
		return level.getGameRules().get(GameRules.KEEP_INVENTORY);
	}

	public static void resetExperience(ServerPlayer player) {
		player.totalExperience = 0;
		player.experienceLevel = 0;
		player.experienceProgress = 0;
	}

	/**
	 * Player#totalExperience is notoriously unreliable as "how much xp does this player currently
	 * have" - it only tracks points ever gained via giveExperiencePoints and easily desyncs from
	 * experienceLevel/experienceProgress (e.g. after "/xp set" or any other mod touching levels
	 * directly). Derive the real total from level + progress instead, using vanilla's own
	 * level-to-xp curve.
	 */
	public static int getTotalExperience(ServerPlayer player) {
		int level = player.experienceLevel;
		float progress = player.experienceProgress;
		return experienceAtLevelStart(level) + Math.round(progress * player.getXpNeededForNextLevel());
	}

	private static int experienceAtLevelStart(int level) {
		if (level <= 15) return level * level + 6 * level;
		if (level <= 30) return (int) (2.5 * level * level - 40.5 * level + 360);
		return (int) (4.5 * level * level - 162.5 * level + 2220);
	}

	/** Mirrors vanilla's own (small) on-death xp orb reward, for when we're not storing xp. */
	public static int vanillaExperienceReward(ServerPlayer player) {
		return player.isSpectator() ? 0 : Math.min(player.experienceLevel * 7, 100);
	}

	public static List<ItemStack> filterBlacklisted(List<ItemStack> items) {
		return splitBlacklisted(items).stored();
	}

	public static ItemSplit splitBlacklisted(List<ItemStack> items) {
		List<ItemStack> filtered = new ArrayList<>();
		List<ItemStack> dropped = new ArrayList<>();
		for (ItemStack stack : items) {
			if (stack.isEmpty()) continue;
			if (isBlacklisted(stack)) {
				dropped.add(stack.copy());
			} else {
				filtered.add(stack.copy());
			}
		}
		return new ItemSplit(filtered, dropped);
	}

	private static boolean isBlacklisted(ItemStack stack) {
		for (Config.BlacklistedItem entry : Config.get().blacklistedItems) {
			String itemId = entry.itemId == null ? "" : entry.itemId.trim();
			String nameContains = entry.nameContains == null ? "" : entry.nameContains.trim();
			if (itemId.isBlank() && nameContains.isBlank()) continue;

			boolean matches = true;
			if (!itemId.isBlank()) {
				matches = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId);
			}
			if (matches && !nameContains.isBlank()) {
				matches = stack.getHoverName().getString().contains(nameContains);
			}
			if (matches) return true;
		}
		return false;
	}
}
