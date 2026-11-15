package com.fiw.graves.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {
	@Accessor("DATA_CLIENT_FLAGS")
	static EntityDataAccessor<Byte> fiwGraves$getDataClientFlags() {
		throw new AssertionError();
	}
}
