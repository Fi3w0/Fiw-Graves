package com.fiw.graves.neoforge;

import com.fiw.graves.command.GravesCommand;
import com.fiw.graves.config.Config;
import com.fiw.graves.entity.FakeEntityTracker;
import com.fiw.graves.grave.DeathCapture;
import com.fiw.graves.grave.GraveService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge gets native cancellable death-drop events, so no mixin is needed here (unlike Fabric).
 * NeoForge posts LivingExperienceDropEvent before LivingDropsEvent for the same death, so we record
 * the death policy first, capture XP when that event fires, then spawn the grave from the drops event.
 */
public final class NeoForgeEventHooks {
	private record PendingDeath(boolean allowed, int experience) {
	}

	private static final Map<UUID, PendingDeath> PENDING = new ConcurrentHashMap<>();

	private NeoForgeEventHooks() {
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;

		String damageTypeId = damageTypeId(event.getSource());
		PENDING.put(player.getUUID(), new PendingDeath(DeathCapture.isAllowed(player, damageTypeId), 0));
	}

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;

		PendingDeath state = PENDING.remove(player.getUUID());
		boolean allowed = state != null ? state.allowed() : DeathCapture.isAllowed(player, damageTypeId(event.getSource()));
		if (!allowed) return;

		ServerLevel level = (ServerLevel) player.level();
		Config config = Config.get();
		boolean keepInventory = DeathCapture.keepInventory(level);
		int xp = state != null ? state.experience() : 0;

		List<ItemStack> storedItems = List.of();
		if (!keepInventory) {
			if (config.storeItems) {
				List<ItemStack> collected = new ArrayList<>();
				for (ItemEntity itemEntity : event.getDrops()) collected.add(itemEntity.getItem());
				DeathCapture.ItemSplit split = DeathCapture.splitBlacklisted(collected);
				storedItems = split.stored();
				event.getDrops().clear();
				dropOnGround(level, player, split.dropped());
			}
		} else if (config.storeItems && config.overrideKeepInventory) {
			DeathCapture.ItemSplit split = DeathCapture.splitBlacklisted(collectAndClearInventory(player));
			storedItems = split.stored();
			dropOnGround(level, player, split.dropped());
		}

		if (!storedItems.isEmpty() || xp > 0) {
			// The event's drops are already cleared by this point - if grave creation fails for
			// any reason, fall back to vanilla-style ground drops rather than losing everything.
			try {
				GraveService.INSTANCE.spawnGrave(player, storedItems, xp);
			} catch (Exception e) {
				NeoForgeGravesMod.LOGGER.error("Grave creation failed for {}, dropping items on the ground instead", player.getGameProfile().getName(), e);
				dropOnGround(level, player, storedItems);
				if (xp > 0) ExperienceOrb.award(level, player.position(), xp);
			}
		}
	}

	@SubscribeEvent
	public static void onExperienceDrop(LivingExperienceDropEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;

		PendingDeath state = PENDING.get(player.getUUID());
		if (state == null || !state.allowed()) return;

		ServerLevel level = (ServerLevel) player.level();
		Config config = Config.get();
		boolean keepInventory = DeathCapture.keepInventory(level);

		int xp = 0;
		if (config.storeExperience) {
			if (!keepInventory || config.overrideKeepExperience) {
				xp = Math.round(DeathCapture.getTotalExperience(player) * (float) config.experienceStoredFraction);
				DeathCapture.resetExperience(player);
				event.setDroppedExperience(0);
			}
		}
		// else: storeExperience false -> leave the event untouched, vanilla's own reward proceeds normally

		PENDING.put(player.getUUID(), new PendingDeath(true, xp));
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		GravesCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		GraveService.INSTANCE.tick(event.getServer());
		FakeEntityTracker.INSTANCE.tick(event.getServer());
	}

	private static String damageTypeId(DamageSource source) {
		return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
	}

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

	private static void dropOnGround(ServerLevel level, ServerPlayer player, List<ItemStack> items) {
		for (ItemStack stack : items) {
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
		}
	}
}
