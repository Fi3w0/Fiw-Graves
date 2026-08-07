package com.fiw.graves.grave;

import com.fiw.graves.Constants;
import com.fiw.graves.config.Config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_NAME);

	// No vanilla DataFixTypes reference matches our own data shape; this only matters if the
	// server's data version changes between saves of this file, and only picks a narrow/unlikely
	// -to-collide vanilla fixer chain rather than a broad one.
	public static final SavedData.Factory<GraveManager> FACTORY = new SavedData.Factory<>(
			GraveManager::new, GraveManager::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private final List<Grave> graves;
	private final Map<UUID, Deque<Grave>> rollbackHistory;
	private final Map<UUID, Deque<Grave>> lootBackups;

	public GraveManager() {
		this(List.of(), Map.of(), Map.of());
	}

	private GraveManager(List<Grave> graves, Map<UUID, List<Grave>> rollback, Map<UUID, List<Grave>> backups) {
		this.graves = new ArrayList<>(graves);
		this.rollbackHistory = new HashMap<>();
		rollback.forEach((uuid, list) -> this.rollbackHistory.put(uuid, new ArrayDeque<>(list)));
		this.lootBackups = new HashMap<>();
		backups.forEach((uuid, list) -> this.lootBackups.put(uuid, new ArrayDeque<>(list)));
		this.graves.forEach(this::trackChanges);
		this.rollbackHistory.values().forEach(history -> history.forEach(this::trackChanges));
		this.lootBackups.values().forEach(history -> history.forEach(this::trackChanges));
	}

	private static Codec<GraveManager> codec() {
		return RecordCodecBuilder.create(instance -> instance.group(
				Grave.CODEC.listOf().fieldOf("graves").forGetter(GraveManager::getGraves),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Grave.CODEC.listOf())
						.fieldOf("rollback").forGetter(GraveManager::rollbackSnapshot),
				// optionalFieldOf so save files written before this feature existed still load.
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Grave.CODEC.listOf())
						.optionalFieldOf("loot_backups", Map.of()).forGetter(GraveManager::backupSnapshot)
		).apply(instance, GraveManager::new));
	}

	public static GraveManager get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, Constants.MOD_ID);
	}

	private static GraveManager load(CompoundTag tag, HolderLookup.Provider registries) {
		return codec().parse(NbtOps.INSTANCE, tag)
				.resultOrPartial(error -> LOGGER.error("Failed to load grave data, some graves may be lost: {}", error))
				.orElseGet(GraveManager::new);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		codec().encodeStart(NbtOps.INSTANCE, this)
				.resultOrPartial(error -> LOGGER.error("Failed to save grave data: {}", error))
				.ifPresent(encoded -> {
					if (encoded instanceof CompoundTag compound) tag.merge(compound);
				});
		return tag;
	}

	private Map<UUID, List<Grave>> rollbackSnapshot() {
		return snapshotOf(rollbackHistory);
	}

	private Map<UUID, List<Grave>> backupSnapshot() {
		return snapshotOf(lootBackups);
	}

	private static Map<UUID, List<Grave>> snapshotOf(Map<UUID, Deque<Grave>> history) {
		Map<UUID, List<Grave>> snapshot = new HashMap<>();
		history.forEach((uuid, deque) -> snapshot.put(uuid, new ArrayList<>(deque)));
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
		pushBackup(grave);
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

	/**
	 * Snapshots a grave's original contents into the loot-backup safety net, independent of the
	 * rollback history above: this always runs (not just on anti-grief loss), so a bug anywhere in
	 * the live collection/expiry path can never erase the only copy of what a grave held.
	 */
	private void pushBackup(Grave grave) {
		if (Config.get().lootBackupRetentionDays == 0) return;
		if (grave.isEmpty()) return;

		Grave snapshot = grave.copySnapshot();
		trackChanges(snapshot);
		lootBackups.computeIfAbsent(grave.owner(), k -> new ArrayDeque<>()).addFirst(snapshot);
	}

	public List<Grave> getBackups(UUID owner) {
		Deque<Grave> history = lootBackups.get(owner);
		return history == null ? List.of() : List.copyOf(history);
	}

	/**
	 * Reads (without removing) the backup entry at the given index (0 = most recent), or null if
	 * there is no such entry. Backups are never consumed by restoring them - they're a durable
	 * safety net, not a one-shot rollback, so the same entry can be restored again if needed.
	 */
	public Grave getBackup(UUID owner, int index) {
		Deque<Grave> history = lootBackups.get(owner);
		if (history == null || index < 0 || index >= history.size()) return null;
		return history.stream().skip(index).findFirst().orElse(null);
	}

	public void pruneBackups() {
		int retentionDays = Config.get().lootBackupRetentionDays;
		if (retentionDays < 0) return;

		long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
		boolean changed = false;
		for (Deque<Grave> history : lootBackups.values()) {
			changed |= history.removeIf(grave -> grave.spawnedAt() < cutoff);
		}
		if (changed) setDirty();
	}

	private void trackChanges(Grave grave) {
		grave.setChangeListener(this::setDirty);
	}
}
