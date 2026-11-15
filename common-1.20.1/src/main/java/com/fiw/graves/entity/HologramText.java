package com.fiw.graves.entity;

import com.fiw.graves.config.Config;
import com.fiw.graves.grave.Grave;
import com.fiw.graves.text.Messages;
import com.fiw.graves.util.TimeUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * The floating text above a grave, composed as styled {@link Component}s in code (no message
 * file, no colour codes). Each grave gets one deterministic epitaph line so different graves read
 * a little differently without any per-grave state.
 */
final class HologramText {
	private static final int MUTED = 0x8A8A8A;

	private static final String[] EPITAPHS = {
			"Here lie your belongings.",
			"Gone, but not looted yet.",
			"Everything you dropped, in one spot.",
			"Grab your stuff before it fades.",
			"A quiet resting place for your loot.",
			"Dust to dust, items to inventory.",
			"You were here. So was your gear."
	};

	private HologramText() {
	}

	static List<Component> build(Grave grave) {
		MutableComponent title = Component.literal("⚰ " + grave.ownerName() + "'s Grave")
				.withStyle(style -> style.withColor(Messages.ACCENT)).withStyle(ChatFormatting.BOLD);

		MutableComponent epitaph = Component.literal(epitaphFor(grave)).withStyle(style -> style.withColor(MUTED)).withStyle(ChatFormatting.ITALIC);

		int items = grave.remainingItems().size();
		int xp = grave.experience();
		MutableComponent stats = Component.literal(items + (items == 1 ? " item" : " items")).withStyle(ChatFormatting.WHITE)
				.append(Component.literal("  •  ").withStyle(style -> style.withColor(MUTED)))
				.append(Component.literal(xp + " xp").withStyle(style -> style.withColor(Messages.ACCENT)));

		int despawnSeconds = Config.get().despawnSeconds;
		if (despawnSeconds < 0) {
			return List.of(title, epitaph, stats);
		}

		long remaining = despawnSeconds * 1000L - (System.currentTimeMillis() - grave.spawnedAt());
		MutableComponent countdown = Component.literal("crumbles in " + TimeUtils.formatDuration(Math.max(0, remaining)))
				.withStyle(style -> style.withColor(MUTED));
		return List.of(title, epitaph, stats, countdown);
	}

	private static String epitaphFor(Grave grave) {
		int index = Math.floorMod(grave.id().hashCode(), EPITAPHS.length);
		return EPITAPHS[index];
	}
}
