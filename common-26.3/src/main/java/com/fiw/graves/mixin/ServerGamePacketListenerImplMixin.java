package com.fiw.graves.mixin;

import com.fiw.graves.entity.FakeEntityTracker;
import com.fiw.graves.net.GraveInteractions;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Our grave head/hologram are packet-only "entities" that never actually exist in the
 * ServerLevel, so vanilla's own entity-interact handling (which looks the target id up in the
 * real level) silently finds nothing and no-ops. This is the one place we intercept the raw
 * interact packet ourselves to notice when it targets one of our fake entity ids.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleInteract", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
			shift = At.Shift.AFTER), cancellable = true)
	private void fiwGraves$onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
		int entityId = packet.entityId();
		UUID graveId = FakeEntityTracker.INSTANCE.getGraveIdForEntity(entityId);
		if (graveId == null) return;

		GraveInteractions.handle(player, graveId, packet.hand(), packet.usingSecondaryAction());
		ci.cancel();
	}
}
