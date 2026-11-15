package com.fiw.graves.fabric.mixin;

import com.fiw.graves.config.Config;
import com.fiw.graves.fabric.FabricGravesMod;
import com.fiw.graves.grave.DeathCapture;
import com.fiw.graves.grave.GraveService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric API has no cancellable "living drops" event, so this is the one mixin in the whole
 * project: it fully takes over a dying player's item/xp drops (mirroring what NeoForge gets for
 * free via LivingDropsEvent/LivingExperienceDropEvent) before vanilla ever scatters anything.
 * dropAllDeathLoot is declared on LivingEntity (Player only overrides dropEquipment/dropExperience),
 * so this mixes into LivingEntity and filters to players itself.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerDeathMixin {
	@Unique
	private boolean fiwGraves$handledDeath = false;

	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void fiwGraves$onDropAllDeathLoot(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
		if (!(((Object) this) instanceof ServerPlayer player)) return;
		if (fiwGraves$handledDeath) {
			ci.cancel();
			return;
		}

		String damageTypeId = damageSource.typeHolder().unwrapKey()
				.map(key -> key.location().toString())
				.orElse("");
		if (!DeathCapture.isAllowed(player, damageTypeId)) return;

		Config config = Config.get();
		boolean keepInventory = DeathCapture.keepInventory(level);
		boolean captureItems = config.storeItems && (!keepInventory || config.overrideKeepInventory);
		boolean captureExperience = config.storeExperience && (!keepInventory || config.overrideKeepExperience);
		if (!captureItems && !captureExperience) return;

		ci.cancel();
		fiwGraves$handledDeath = true;

		List<ItemStack> drops = List.of();
		if (config.storeItems) {
			if (!keepInventory || config.overrideKeepInventory) {
				DeathCapture.ItemSplit split = DeathCapture.splitBlacklisted(collectAndClearInventory(player));
				drops = split.stored();
				dropOnGround(level, player, split.dropped());
			}
		} else if (!keepInventory) {
			dropOnGround(level, player, collectAndClearInventory(player));
		}

		int xp = 0;
		if (config.storeExperience) {
			if (!keepInventory || config.overrideKeepExperience) {
				xp = Math.round(DeathCapture.getTotalExperience(player) * (float) config.experienceStoredFraction);
				DeathCapture.resetExperience(player);
			}
		} else if (!keepInventory) {
			int reward = DeathCapture.vanillaExperienceReward(player);
			if (reward > 0) ExperienceOrb.award(level, player.position(), reward);
			DeathCapture.resetExperience(player);
		}

		if (!drops.isEmpty() || xp > 0) {
			// The inventory is already cleared by this point - if grave creation fails for any
			// reason, fall back to vanilla-style ground drops rather than losing everything.
			try {
				GraveService.INSTANCE.spawnGrave(player, drops, xp);
			} catch (Exception e) {
				FabricGravesMod.LOGGER.error("Grave creation failed for {}, dropping items on the ground instead", player.getGameProfile().getName(), e);
				dropOnGround(level, player, drops);
				if (xp > 0) ExperienceOrb.award(level, player.position(), xp);
			}
		}
	}

	@Unique
	private static List<ItemStack> collectAndClearInventory(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) items.add(stack.copy());
		}
		inventory.clearContent();
		return items;
	}

	@Unique
	private static void dropOnGround(ServerLevel level, ServerPlayer player, List<ItemStack> items) {
		for (ItemStack stack : items) {
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
		}
	}
}
