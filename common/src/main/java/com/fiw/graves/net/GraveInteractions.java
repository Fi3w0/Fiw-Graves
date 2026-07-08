package com.fiw.graves.net;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.UUID;

/**
 * Bridges the interaction-packet mixin (which only knows fake entity ids) to the actual grave
 * business logic (which is wired up once, at mod init, by whichever loader is running).
 */
public final class GraveInteractions {
	private static GraveInteractionHandler handler;

	private GraveInteractions() {
	}

	public static void setHandler(GraveInteractionHandler newHandler) {
		handler = newHandler;
	}

	public static void handle(ServerPlayer player, UUID graveId, InteractionHand hand, boolean sneaking) {
		if (handler != null) handler.onInteract(player, graveId, hand, sneaking);
	}
}
