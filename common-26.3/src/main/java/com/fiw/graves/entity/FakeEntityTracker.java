package com.fiw.graves.entity;

import com.fiw.graves.config.Config;
import com.fiw.graves.grave.Grave;
import com.fiw.graves.grave.GraveManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide registry of live grave markers. Owns the only bookkeeping needed to know which
 * fake entity ids belong to which grave, and which players currently have them spawned client-side.
 */
public class FakeEntityTracker {
	public static final FakeEntityTracker INSTANCE = new FakeEntityTracker();

	private static final double VIEW_DISTANCE_SQ = 48.0 * 48.0;
	private static final int HOLOGRAM_REFRESH_INTERVAL_TICKS = 20;

	private final Map<UUID, GraveMarker> markers = new HashMap<>();
	private final Map<Integer, UUID> entityIdToGrave = new HashMap<>();

	private FakeEntityTracker() {
	}

	public void tick(MinecraftServer server) {
		GraveManager manager = GraveManager.get(server);
		Set<UUID> currentGraveIds = new HashSet<>();
		boolean refreshHolograms = server.getTickCount() % HOLOGRAM_REFRESH_INTERVAL_TICKS == 0;

		for (Grave grave : manager.getGraves()) {
			currentGraveIds.add(grave.id());
			ServerLevel level = server.getLevel(grave.dimension());
			if (level == null) continue;

			GraveMarker marker = markers.get(grave.id());
			if (marker == null) {
				marker = createMarker(level, grave);
				markers.put(grave.id(), marker);
			}

			updateViewers(server, level, grave, marker);

			if (refreshHolograms) {
				marker.updateHologramText(HologramText.build(grave));
				broadcast(server, marker, marker.hologramDataPacket());
			}
			if (Config.get().autoRotateEnabled) {
				broadcast(server, marker, marker.rotate((float) Config.get().autoRotateSpeed));
			}
		}

		removeStaleMarkers(server, currentGraveIds);
	}

	private GraveMarker createMarker(ServerLevel level, Grave grave) {
		GraveMarker marker = new GraveMarker(level, grave, GraveHeads.resolve(grave), HologramText.build(grave));
		entityIdToGrave.put(marker.headEntityId(), grave.id());
		entityIdToGrave.put(marker.hologramEntityId(), grave.id());
		return marker;
	}

	private void updateViewers(MinecraftServer server, ServerLevel level, Grave grave, GraveMarker marker) {
		Set<UUID> present = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			present.add(player.getUUID());
			boolean inRange = player.position().distanceToSqr(grave.position()) <= VIEW_DISTANCE_SQ;
			boolean viewing = marker.isViewing(player);
			boolean tracked = marker.isViewing(player.getUUID());

			if (inRange && !viewing) {
				if (tracked) player.connection.send(marker.removePacket());
				for (Packet<ClientGamePacketListener> packet : marker.spawnPackets()) {
					player.connection.send(packet);
				}
				marker.markViewing(player);
			} else if (!inRange && tracked) {
				player.connection.send(marker.removePacket());
				marker.unmarkViewing(player.getUUID());
			}
		}

		// Dimension changes discard client-side entities; mark absent players unspawned so they
		// receive fresh spawn packets when they return.
		marker.viewers().removeIf(uuid -> !present.contains(uuid));
	}

	private void removeStaleMarkers(MinecraftServer server, Set<UUID> currentGraveIds) {
		Iterator<Map.Entry<UUID, GraveMarker>> it = markers.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, GraveMarker> entry = it.next();
			if (currentGraveIds.contains(entry.getKey())) continue;

			GraveMarker marker = entry.getValue();
			despawnForAll(server, marker);
			entityIdToGrave.remove(marker.headEntityId());
			entityIdToGrave.remove(marker.hologramEntityId());
			it.remove();
		}
	}

	/**
	 * Immediately tears down a grave's marker (e.g. right after it was looted/removed) instead of
	 * waiting for the next tick to notice it's gone.
	 */
	public void forceRemove(MinecraftServer server, UUID graveId) {
		GraveMarker marker = markers.remove(graveId);
		if (marker == null) return;

		entityIdToGrave.remove(marker.headEntityId());
		entityIdToGrave.remove(marker.hologramEntityId());
		despawnForAll(server, marker);
	}

	private void despawnForAll(MinecraftServer server, GraveMarker marker) {
		broadcast(server, marker, marker.removePacket());
	}

	private void broadcast(MinecraftServer server, GraveMarker marker, Packet<ClientGamePacketListener> packet) {
		for (UUID uuid : marker.viewers()) {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) player.connection.send(packet);
		}
	}

	public UUID getGraveIdForEntity(int entityId) {
		return entityIdToGrave.get(entityId);
	}
}
