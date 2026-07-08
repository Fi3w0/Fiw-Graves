package com.fiw.graves.grave;

import com.fiw.graves.config.Config;
import com.fiw.graves.entity.FakeEntityTracker;
import com.fiw.graves.entity.GraveMenu;
import com.fiw.graves.net.GraveInteractionHandler;
import com.fiw.graves.net.GraveInteractions;
import com.fiw.graves.text.Messages;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Central orchestrator for grave lifecycle: spawning on death, interaction (loot menu / instant
 * pickup), and periodic despawn sweeps. Wired as the single {@link GraveInteractionHandler} for
 * the fake entity interaction pipeline.
 */
public final class GraveService implements GraveInteractionHandler {
	public static final GraveService INSTANCE = new GraveService();
	private static final double INTERACT_RANGE_SQ = 49.0; // 7 blocks, matches the vanilla interact packet's own range check

	private GraveService() {
		GraveInteractions.setHandler(this);
	}

	public Grave spawnGrave(ServerPlayer player, List<ItemStack> items, int experience) {
		MinecraftServer server = player.level().getServer();
		GraveManager manager = GraveManager.get(server);

		// Lava, ocean, void: settle the grave at the nearest open, dry, supported spot instead
		// of exactly where the player died.
		Vec3 restingPos = GravePlacement.resolve((ServerLevel) player.level(), player.position());

		Grave grave = new Grave(UUID.randomUUID(), player.getUUID(), player.getGameProfile().name(),
				player.level().dimension(), restingPos, player.getYRot(), items, experience, System.currentTimeMillis());

		manager.addGrave(grave);
		enforceLimit(player, manager);
		return grave;
	}

	private void enforceLimit(ServerPlayer player, GraveManager manager) {
		// <= 0 means unlimited: a limit of 0 would otherwise delete the grave we just spawned.
		int limit = Config.get().graveLimitPerPlayer;
		if (limit <= 0) return;

		List<Grave> owned = new ArrayList<>(manager.getGraves(player.getUUID()));
		owned.sort(Comparator.comparingLong(Grave::spawnedAt));
		while (owned.size() > limit) {
			Grave oldest = owned.remove(0);
			manager.removeGrave(oldest, false);
			FakeEntityTracker.INSTANCE.forceRemove(player.level().getServer(), oldest.id());
		}
	}

	@Override
	public void onInteract(ServerPlayer player, UUID graveId, InteractionHand hand, boolean sneaking) {
		MinecraftServer server = player.level().getServer();
		GraveManager manager = GraveManager.get(server);
		Grave grave = manager.getGrave(graveId);
		if (grave == null) return;
		if (player.distanceToSqr(grave.position().x, grave.position().y, grave.position().z) > INTERACT_RANGE_SQ) return;

		Config config = Config.get();
		boolean isOwner = player.getUUID().equals(grave.owner());
		boolean canBypass = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); // operator (level 2)

		if (config.ownerOnlyAccess && !isOwner && !canBypass) {
			player.sendSystemMessage(Messages.notYourGrave());
			return;
		}

		if (grave.experience() > 0) {
			player.giveExperiencePoints(grave.experience());
			grave.takeExperience();
		}

		if (hand == InteractionHand.MAIN_HAND && sneaking && config.quickCollectEnabled
				&& !player.isSpectator() && (!config.quickCollectOwnerOnly || isOwner)) {
			quickCollect(player, grave, config);
		} else {
			GraveMenu.open(player, grave);
		}

		checkDespawn(server, manager, grave);
	}

	private void quickCollect(ServerPlayer player, Grave grave, Config config) {
		List<ItemStack> items = new ArrayList<>(grave.container().getItems());
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) continue;

			if (config.equipArmorOnCollect) {
				Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
				if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR
						&& player.getItemBySlot(equippable.slot()).isEmpty()) {
					player.setItemSlot(equippable.slot(), stack.copy());
					grave.container().setItem(i, ItemStack.EMPTY);
					continue;
				}
			}

			// Inventory#add returns true as soon as it merges ANY portion of the stack - not only
			// on full consumption - so the leftover count left in `stack` must be checked directly.
			// Trusting the return value here silently discarded the remainder on a partial merge.
			player.getInventory().add(stack);
			if (!stack.isEmpty()) {
				player.drop(stack, false);
			}
			grave.container().setItem(i, ItemStack.EMPTY);
		}
	}

	/**
	 * Called once per tick by the loader's server-tick hook.
	 */
	public void tick(MinecraftServer server) {
		GraveManager manager = GraveManager.get(server);
		int despawnSeconds = Config.get().despawnSeconds;
		long now = System.currentTimeMillis();

		for (Grave grave : List.copyOf(manager.getGraves())) {
			boolean outOfTime = despawnSeconds >= 0 && (now - grave.spawnedAt()) >= despawnSeconds * 1000L;
			if (outOfTime) {
				despawnByTimer(server, manager, grave);
			} else {
				checkDespawn(server, manager, grave);
			}
		}

		manager.pruneRollbackHistory();
		manager.pruneBackups();
	}

	private void checkDespawn(MinecraftServer server, GraveManager manager, Grave grave) {
		if (!Config.get().removeWhenEmptied) return;
		if (!grave.isEmpty()) return;

		manager.removeGrave(grave, true);
		FakeEntityTracker.INSTANCE.forceRemove(server, grave.id());
	}

	private void despawnByTimer(MinecraftServer server, GraveManager manager, Grave grave) {
		boolean recoverable = false;

		if (Config.get().dropItemsOnExpire) {
			ServerLevel level = server.getLevel(grave.dimension());
			if (level != null) {
				List<ItemStack> items = grave.remainingItems();
				int experience = grave.experience();
				grave.container().clearContent();
				grave.takeExperience();

				for (ItemStack stack : items) {
					level.addFreshEntity(new ItemEntity(level, grave.position().x, grave.position().y, grave.position().z, stack));
				}
				if (experience > 0) {
					ExperienceOrb.award(level, grave.position(), experience);
				}
				recoverable = true;
			}
		}

		manager.removeGrave(grave, recoverable);
		FakeEntityTracker.INSTANCE.forceRemove(server, grave.id());
	}
}
