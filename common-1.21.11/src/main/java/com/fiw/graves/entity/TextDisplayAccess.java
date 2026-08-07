package com.fiw.graves.entity;

import com.fiw.graves.mixin.DisplayAccessor;
import com.fiw.graves.mixin.TextDisplayAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;

/**
 * Text/style fields on Display.TextDisplay are only settable from private methods on that class
 * (Mojang expects them to be populated by loading NBT through the entity, not built fresh by
 * external code). Since we build these entities purely to generate spawn packets and never add
 * them to a level, we go straight at the underlying synced-data accessors through remapped mixin
 * accessors.
 */
final class TextDisplayAccess {
	static final byte STYLE_SHADOW = 1;
	static final byte STYLE_SEE_THROUGH = 2;
	static final byte STYLE_ALIGN_LEFT = 8;
	static final byte STYLE_ALIGN_RIGHT = 16;

	private static final EntityDataAccessor<Component> TEXT = TextDisplayAccessor.fiwGraves$getDataTextId();
	private static final EntityDataAccessor<Integer> LINE_WIDTH = TextDisplayAccessor.fiwGraves$getDataLineWidthId();
	private static final EntityDataAccessor<Integer> BACKGROUND_COLOR = TextDisplayAccessor.fiwGraves$getDataBackgroundColorId();
	private static final EntityDataAccessor<Byte> STYLE_FLAGS = TextDisplayAccessor.fiwGraves$getDataStyleFlagsId();
	private static final EntityDataAccessor<Byte> BILLBOARD = DisplayAccessor.fiwGraves$getDataBillboardRenderConstraintsId();

	private TextDisplayAccess() {
	}

	static void setText(Display.TextDisplay display, Component text) {
		display.getEntityData().set(TEXT, text);
	}

	static void setLineWidth(Display.TextDisplay display, int lineWidth) {
		display.getEntityData().set(LINE_WIDTH, lineWidth);
	}

	static void setBackgroundColor(Display.TextDisplay display, int argb) {
		display.getEntityData().set(BACKGROUND_COLOR, argb);
	}

	static void setStyleFlags(Display.TextDisplay display, byte flags) {
		display.getEntityData().set(STYLE_FLAGS, flags);
	}

	static void setBillboard(Display display, Display.BillboardConstraints billboard) {
		// BillboardConstraints#getId() is package-private; its declaration order (FIXED, VERTICAL,
		// HORIZONTAL, CENTER) is the wire protocol's byte id, so ordinal() is equivalent and stable.
		display.getEntityData().set(BILLBOARD, (byte) billboard.ordinal());
	}
}
