package com.fiw.graves.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Accessor("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID")
	static EntityDataAccessor<Byte> fiwGraves$getDataBillboardRenderConstraintsId() {
		throw new AssertionError();
	}
}
