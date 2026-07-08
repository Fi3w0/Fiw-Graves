package com.fiw.graves.grave;

import com.fiw.graves.Constants;
import com.fiw.graves.config.Config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server-wide grave state, tied to the overworld's data storage so it saves/backs-up/restores
 * with the rest of the world instead of a hand-rolled file next to it. One instance covers every
 * dimension.
 */
public class GraveManager extends SavedData {
	// No vanilla DataFixTypes reference matches our own data shape; this only matters if the
	// server's data version changes between saves of this file, and only picks a narrow/unlikely
	// -to-collide vanilla fixer chain rather than a broad one.
	public static final SavedDataType<GraveManager> TYPE = new SavedDataType<>(
			Constants.MOD_ID, GraveManager::new, codec(), DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private final List<Grave> graves;
	private final Map<UUID, Deque<Grave>> rollbackHistory;

	public GraveManager() {
		this(List.of(), Map.of());
	}

	private GraveManager(List<Grave> graves, Map<UUID, List<Grave>> rollback) {
		this.graves = new ArrayList<>(graves);
		this.rollbackHistory = new HashMap<>();
		rollback.forEach((uuid, list) -> this.rollbackHistory.put(uuid, new ArrayDeque<>(list)));
		this.graves.forEach(this::trackChanges);
		this.rollbackHistory.values().forEach(history -> history.forEach(this::trackChanges));
	}

	private static Codec<GraveManager> codec() {
		return RecordCodecBuilder.create(instance -> instance.group(
				Grave.CODEC.listOf().fieldOf("graves").forGetter(GraveManager::getGraves),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Grave.CODEC.listOf())
						.fieldOf("rollback").forGetter(GraveManager::rollbackSnapshot)
		).apply(instance, GraveManager::new));
	}

	public static GraveManager get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private Map<UUID, List<Grave>> rollbackSnapshot() {
		Map<UUID, List<Grave>> snapshot = new HashMap<>();
		rollbackHistory.forEach((uuid, deque) -> snapshot.put(uuid, new ArrayList<>(deque)));
		return snapshot;
	}

	public List<Grave> getGraves() {
		return Collections.unmodifiableList(graves);
	}

	public List<Grave> getGraves(UUID owner) {
		return graves.stream().filter(grave -> grave.owner().equals(owner)).collect(Collectors.toList());
	}

	public Grave getGrave(UUID id) {
		return graves.stream().filter(grave -> grave.id().equals(id)).findFirst().orElse(null);
	}

	public void addGrave(Grave grave) {
		trackChanges(grave);
		graves.add(grave);
		setDirty();
	}

	/**
	 * @param recoverable true if the grave's contents are still obtainable some other way
	 *                    (e.g. dropped on the ground) - such removals never need a rollback entry.
	 */
	public void removeGrave(Grave grave, boolean recoverable) {
		graves.remove(grave);
		if (!recoverable) pushRollback(grave);
		setDirty();
	}

	private void pushRollback(Grave grave) {
		if (grave.isEmpty()) return;

		trackChanges(grave);
		Deque<Grave> history = rollbackHistory.computeIfAbsent(grave.owner(), k -> new ArrayDeque<>());
		history.addFirst(grave);

		int max = Math.max(0, Config.get().rollbackHistoryPerPlayer);
		while (history.size() > max) {
			history.removeLast();
		}
		setDirty();
	}

	public List<Grave> getRollbackHistory(UUID owner) {
		Deque<Grave> history = rollbackHistory.get(owner);
		return history == null ? List.of() : List.copyOf(history);
	}

	/**
	 * Removes and returns the rollback entry at the given index (0 = most recent), or null if
	 * there is no such entry.
	 */
	public Grave popRollback(UUID owner, int index) {
		Deque<Grave> history = rollbackHistory.get(owner);
		if (history == null || index < 0 || index >= history.size()) return null;

		Grave removed = history.stream().skip(index).findFirst().orElse(null);
		if (removed != null) {
			history.remove(removed);
			setDirty();
		}
		return removed;
	}

	public void pruneRollbackHistory() {
		int retentionMinutes = Config.get().rollbackRetentionMinutes;
		if (retentionMinutes < 0) return;

		long cutoff = System.currentTimeMillis() - retentionMinutes * 60_000L;
		boolean changed = false;
		for (Deque<Grave> history : rollbackHistory.values()) {
			changed |= history.removeIf(grave -> grave.spawnedAt() < cutoff);
		}
		if (changed) setDirty();
	}

	private void trackChanges(Grave grave) {
		grave.setChangeListener(this::setDirty);
	}
}
