package com.fiw.graves.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Picks where a grave actually settles when the death spot itself is a bad home - underwater,
 * submerged in lava, buried in blocks, or falling through the void. Preference order:
 *
 * <ol>
 *   <li>the death spot itself (clamped into world bounds), if it's open, dry, and has
 *       something underneath;</li>
 *   <li>straight up the same column - the first open, dry pocket resting on ground or on a
 *       liquid surface (ocean and lava-lake deaths surface here);</li>
 *   <li>the nearest neighbouring column that has any terrain, via the heightmap (void deaths
 *       near an island edge walk back onto land);</li>
 *   <li>as a last resort, the clamped death spot as-is - a floating grave is still visible,
 *       teleportable, and lootable, which beats no grave.</li>
 * </ol>
 */
public final class GravePlacement {
	private static final int RING_SEARCH_RADIUS = 16;
	private static final int SUPPORT_SEARCH_DEPTH = 4;

	private GravePlacement() {
	}

	public static Vec3 resolve(ServerLevel level, Vec3 deathPos) {
		int minY = level.getMinY() + 1;
		int maxY = level.getMaxY() - 2;

		double clampedY = Mth.clamp(deathPos.y, minY, maxY);
		BlockPos start = BlockPos.containing(deathPos.x, clampedY, deathPos.z);

		if (isOpenAndDry(level, start) && hasSupport(level, start)) {
			return new Vec3(deathPos.x, clampedY, deathPos.z);
		}

		// Climb the column: first breathable pocket sitting on ground or on a liquid surface.
		BlockPos.MutableBlockPos cursor = start.mutable();
		for (int y = start.getY() + 1; y <= maxY; y++) {
			cursor.setY(y);
			if (isOpenAndDry(level, cursor) && hasSupport(level, cursor)) {
				return new Vec3(deathPos.x, y, deathPos.z);
			}
		}

		// Nothing in this column at all (e.g. off the edge in the End) - walk outward and take
		// the closest column that has real terrain under it.
		for (int r = 1; r <= RING_SEARCH_RADIUS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;

					int x = start.getX() + dx;
					int z = start.getZ() + dz;
					int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					if (surface <= level.getMinY()) continue; // empty column

					BlockPos candidate = new BlockPos(x, Mth.clamp(surface, minY, maxY), z);
					if (isOpenAndDry(level, candidate)) {
						return Vec3.atBottomCenterOf(candidate);
					}
				}
			}
		}

		return new Vec3(deathPos.x, clampedY, deathPos.z);
	}

	/** Open enough to see and click the marker: no collision box, no fluid, in both its blocks. */
	private static boolean isOpenAndDry(ServerLevel level, BlockPos pos) {
		return isClear(level, pos) && isClear(level, pos.above());
	}

	private static boolean isClear(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !blocksMotion(state) && level.getFluidState(pos).isEmpty();
	}

	/** Ground - or a liquid surface - within a few blocks below, so graves don't float mid-air. */
	private static boolean hasSupport(ServerLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 1; i <= SUPPORT_SEARCH_DEPTH; i++) {
			cursor.setY(pos.getY() - i);
			if (cursor.getY() < level.getMinY()) return false;
			if (blocksMotion(level.getBlockState(cursor)) || !level.getFluidState(cursor).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** 26.3 removed BlockState#blocksMotion; this is the same rule older versions apply. */
	private static boolean blocksMotion(BlockState state) {
		return state.getBlock() != Blocks.COBWEB && state.getBlock() != Blocks.BAMBOO_SAPLING && state.isSolid();
	}
}
