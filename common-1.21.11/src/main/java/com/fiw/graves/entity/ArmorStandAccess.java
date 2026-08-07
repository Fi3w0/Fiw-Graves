package com.fiw.graves.entity;

import com.fiw.graves.mixin.ArmorStandAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * ArmorStand#setSmall is private (unlike its sibling setShowArms/setNoBasePlate, which are
 * public) - go straight at the backing synced-data flag byte instead, same rationale as
 * {@link TextDisplayAccess}.
 */
final class ArmorStandAccess {
	private static final byte SMALL_BIT = 1;
	private static final EntityDataAccessor<Byte> CLIENT_FLAGS = ArmorStandAccessor.fiwGraves$getDataClientFlags();

	private ArmorStandAccess() {
	}

	static void setSmall(ArmorStand armorStand, boolean small) {
		byte flags = armorStand.getEntityData().get(CLIENT_FLAGS);
		flags = small ? (byte) (flags | SMALL_BIT) : (byte) (flags & ~SMALL_BIT);
		armorStand.getEntityData().set(CLIENT_FLAGS, flags);
	}
}
