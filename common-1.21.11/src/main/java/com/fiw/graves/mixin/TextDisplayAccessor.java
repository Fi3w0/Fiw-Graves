package com.fiw.graves.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Accessor("DATA_TEXT_ID")
	static EntityDataAccessor<Component> fiwGraves$getDataTextId() {
		throw new AssertionError();
	}

	@Accessor("DATA_LINE_WIDTH_ID")
	static EntityDataAccessor<Integer> fiwGraves$getDataLineWidthId() {
		throw new AssertionError();
	}

	@Accessor("DATA_BACKGROUND_COLOR_ID")
	static EntityDataAccessor<Integer> fiwGraves$getDataBackgroundColorId() {
		throw new AssertionError();
	}

	@Accessor("DATA_STYLE_FLAGS_ID")
	static EntityDataAccessor<Byte> fiwGraves$getDataStyleFlagsId() {
		throw new AssertionError();
	}
}
