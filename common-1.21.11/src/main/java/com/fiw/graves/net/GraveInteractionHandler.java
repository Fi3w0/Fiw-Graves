package com.fiw.graves.net;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.UUID;

public interface GraveInteractionHandler {
	void onInteract(ServerPlayer player, UUID graveId, InteractionHand hand, boolean sneaking);
}
