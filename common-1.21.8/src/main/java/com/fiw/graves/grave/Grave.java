package com.fiw.graves.grave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * A single grave's persistent state. The live head/hologram (packet-only fake entities) and
 * the open-able container are runtime-only and rebuilt from this data - none of that is ever
 * written into the actual world/chunk data, so a crash can never leave anything floating.
 */
public class Grave {
	public static final Codec<Grave> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Grave::id),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Grave::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(Grave::ownerName),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Grave::dimension),
			Vec3.CODEC.fieldOf("position").forGetter(Grave::position),
			Codec.FLOAT.fieldOf("yaw").forGetter(Grave::yaw),
			ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(g -> g.container.getItems()),
			Codec.INT.fieldOf("experience").forGetter(Grave::experience),
			Codec.LONG.fieldOf("spawned_at").forGetter(Grave::spawnedAt)
	).apply(instance, Grave::new));

	private final UUID id;
	private final UUID owner;
	private final String ownerName;
	private final ResourceKey<Level> dimension;
	private final Vec3 position;
	private final float yaw;
	private final SimpleContainer container;
	private int experience;
	private final long spawnedAt;
	private Runnable onChanged = () -> {
	};

	public Grave(UUID id, UUID owner, String ownerName, ResourceKey<Level> dimension, Vec3 position, float yaw,
				 List<ItemStack> items, int experience, long spawnedAt) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
		this.dimension = dimension;
		this.position = position;
		this.yaw = yaw;
		this.container = new SimpleContainer(slotsFor(items.size()));
		for (int i = 0; i < items.size(); i++) {
			this.container.setItem(i, items.get(i));
		}
		this.experience = experience;
		this.spawnedAt = spawnedAt;
		this.container.addListener(container -> this.onChanged.run());
	}

	public UUID id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public Vec3 position() {
		return position;
	}

	public float yaw() {
		return yaw;
	}

	public SimpleContainer container() {
		return container;
	}

	public int experience() {
		return experience;
	}

	public void takeExperience() {
		if (this.experience == 0) return;
		this.experience = 0;
		onChanged.run();
	}

	public long spawnedAt() {
		return spawnedAt;
	}

	public boolean isEmpty() {
		return experience <= 0 && container.isEmpty();
	}

	public List<ItemStack> remainingItems() {
		return container.getItems().stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
	}

	/**
	 * An independent copy of this grave's current contents, immune to whatever happens to this
	 * grave afterwards (collection, expiry, a bug in either). Used to seed the loot-backup safety
	 * net at the moment a grave is created.
	 */
	public Grave copySnapshot() {
		return new Grave(id, owner, ownerName, dimension, position, yaw, remainingItems(), experience, spawnedAt);
	}

	/** Rows the chest UI shows - capped at a double chest even if the container holds more. */
	public int rows() {
		return Math.min(6, container.getContainerSize() / 9);
	}

	/**
	 * The container is sized to hold every captured stack, even past the 54 slots a chest UI can
	 * show (heavily modded inventories can exceed it). The UI only maps the first 54 slots, so
	 * this shifts every stack down into the lowest slots - overflow items become visible/takeable
	 * as earlier slots are emptied. Called before opening the menu.
	 */
	public void compactIfOverflowing() {
		if (container.getContainerSize() <= 54) return;

		List<ItemStack> stacks = remainingItems();
		for (int i = 0; i < container.getContainerSize(); i++) {
			container.setItem(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
		}
	}

	private static int slotsFor(int itemCount) {
		// Never fewer than one chest row; grows unbounded so no captured stack is ever dropped.
		int rows = Math.max(1, (itemCount + 8) / 9);
		return rows * 9;
	}

	void setChangeListener(Runnable onChanged) {
		this.onChanged = onChanged;
	}
}
