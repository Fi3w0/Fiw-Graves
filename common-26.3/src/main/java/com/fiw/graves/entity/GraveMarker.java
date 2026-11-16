package com.fiw.graves.entity;

import com.fiw.graves.config.Config;
import com.fiw.graves.grave.Grave;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The packet-only (never added to a ServerLevel) head + hologram pair for one grave. Every
 * packet this generates is purely a protocol message to specific players - nothing here is ever
 * written into world/chunk data, so there is nothing that can survive a crash or be left behind.
 */
public class GraveMarker {
	private final Grave grave;
	private final ArmorStand head;
	private final Display.TextDisplay hologram;
	private final ItemStack headItem;
	private final Map<UUID, Integer> viewers = new HashMap<>();

	public GraveMarker(ServerLevel level, Grave grave, ItemStack headItem, List<Component> hologramLines) {
		this.grave = grave;
		this.headItem = headItem;

		Config config = Config.get();
		Vec3 pos = grave.position();

		this.head = new ArmorStand(EntityTypes.ARMOR_STAND, level);
		head.setPos(pos.x, pos.y + config.markerHeight, pos.z);
		head.setYRot(grave.yaw());
		head.setYHeadRot(grave.yaw());
		head.setInvisible(true);
		ArmorStandAccess.setSmall(head, true);
		head.setShowArms(false);
		head.setNoBasePlate(true);

		this.hologram = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		hologram.setPos(pos.x, pos.y + config.hologramHeight + 1.0, pos.z);
		TextDisplayAccess.setBillboard(hologram, parseBillboard(config.hologram.billboard));
		TextDisplayAccess.setBackgroundColor(hologram, parseArgb(config.hologram.backgroundColor));
		TextDisplayAccess.setLineWidth(hologram, 1000);

		byte styleFlags = 0;
		if (config.hologram.shadow) styleFlags |= TextDisplayAccess.STYLE_SHADOW;
		if (config.hologram.seeThrough) styleFlags |= TextDisplayAccess.STYLE_SEE_THROUGH;
		styleFlags |= switch (config.hologram.alignment.toUpperCase()) {
			case "LEFT" -> TextDisplayAccess.STYLE_ALIGN_LEFT;
			case "RIGHT" -> TextDisplayAccess.STYLE_ALIGN_RIGHT;
			default -> 0;
		};
		TextDisplayAccess.setStyleFlags(hologram, styleFlags);

		updateHologramText(hologramLines);
	}

	private static Display.BillboardConstraints parseBillboard(String value) {
		try {
			return Display.BillboardConstraints.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			return Display.BillboardConstraints.VERTICAL;
		}
	}

	private static int parseArgb(String hex) {
		try {
			return (int) Long.parseLong(hex, 16);
		} catch (NumberFormatException e) {
			return 0; // fully transparent
		}
	}

	public Grave grave() {
		return grave;
	}

	public int headEntityId() {
		return head.getId();
	}

	public int hologramEntityId() {
		return hologram.getId();
	}

	public void updateHologramText(List<Component> lines) {
		MutableComponent joined = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) joined.append("\n");
			joined.append(lines.get(i));
		}
		TextDisplayAccess.setText(hologram, joined);
	}

	public List<Packet<ClientGamePacketListener>> spawnPackets() {
		List<Packet<ClientGamePacketListener>> packets = new ArrayList<>();

		packets.add(addEntityPacket(head));
		List<SynchedEntityData.DataValue<?>> headData = head.getEntityData().getNonDefaultValues();
		if (headData != null) packets.add(new ClientboundSetEntityDataPacket(head.getId(), headData));
		packets.add(new ClientboundSetEquipmentPacket(head.getId(), List.of(Pair.of(EquipmentSlot.HEAD, headItem))));

		packets.add(addEntityPacket(hologram));
		List<SynchedEntityData.DataValue<?>> hologramData = hologram.getEntityData().getNonDefaultValues();
		if (hologramData != null) packets.add(new ClientboundSetEntityDataPacket(hologram.getId(), hologramData));

		return packets;
	}

	/**
	 * The (Entity, int, BlockPos) ClientboundAddEntityPacket constructor is for block-snapped
	 * entities (falling blocks etc.) and reads back only whole-number BlockPos coordinates -
	 * using it here silently floors away the fractional Y and visibly sinks the marker. Build the
	 * packet from the entity's exact double position instead.
	 */
	private static ClientboundAddEntityPacket addEntityPacket(Entity entity) {
		return new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
				entity.getXRot(), entity.getYRot(), entity.getType(), 0, entity.getDeltaMovement(), entity.getYHeadRot());
	}

	public Packet<ClientGamePacketListener> removePacket() {
		return new ClientboundRemoveEntitiesPacket(head.getId(), hologram.getId());
	}

	public Packet<ClientGamePacketListener> hologramDataPacket() {
		List<SynchedEntityData.DataValue<?>> values = hologram.getEntityData().getNonDefaultValues();
		return new ClientboundSetEntityDataPacket(hologram.getId(), values != null ? values : List.of());
	}

	public Packet<ClientGamePacketListener> rotate(float yawDelta) {
		head.setYRot(head.getYRot() + yawDelta);
		head.setYHeadRot(head.getYRot());
		return ClientboundTeleportEntityPacket.teleport(head.getId(), PositionMoveRotation.of(head), Set.of(), true);
	}

	public boolean isViewing(UUID playerId) {
		return viewers.containsKey(playerId);
	}

	public boolean isViewing(ServerPlayer player) {
		Integer entityId = viewers.get(player.getUUID());
		return entityId != null && entityId == player.getId();
	}

	public void markViewing(ServerPlayer player) {
		viewers.put(player.getUUID(), player.getId());
	}

	public void unmarkViewing(UUID playerId) {
		viewers.remove(playerId);
	}

	public Set<UUID> viewers() {
		return viewers.keySet();
	}
}
