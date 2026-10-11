package justfatlard.pandorical.structure;

import justfatlard.pandorical.api.RelPos;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Set;

/**
 * The cells of a structure the sea is kept out of: the hold of a ship at sea, which stands where
 * the world has water. Asked about points in the world, against a pose of the structure.
 */
public final class DryCells {
	private final Set<RelPos> cells;
	/** The cells with no dry cell above them: where the water's surface would cross. */
	private final java.util.List<RelPos> tops;
	/** How far from the pose's origin, across, the farthest cell reaches. */
	private final double reach;
	private final int minY;
	private final int maxY;

	public DryCells(Collection<RelPos> cells) {
		this.cells = Set.copyOf(cells);
		double far = 0;
		int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
		for (RelPos cell : this.cells) {
			double x = Math.max(Math.abs(cell.x()), Math.abs(cell.x() + 1));
			double z = Math.max(Math.abs(cell.z()), Math.abs(cell.z() + 1));
			far = Math.max(far, x * x + z * z);
			low = Math.min(low, cell.y());
			high = Math.max(high, cell.y());
		}
		this.tops = this.cells.stream().filter(c -> !this.cells.contains(new RelPos(c.x(), c.y() + 1, c.z()))).toList();
		this.reach = Math.sqrt(far);
		this.minY = low;
		this.maxY = high;
	}

	public Set<RelPos> cells() {
		return cells;
	}

	public java.util.List<RelPos> tops() {
		return tops;
	}

	public boolean isEmpty() {
		return cells.isEmpty();
	}

	/**
	 * Where to ask about a block of the world for an entity in it: the point of the block nearest
	 * the entity's middle, the part it is in. A turned structure's cells are not the world's, and a
	 * world block straddling a ship's hull is half sea and half hold; asked at its centre, a player
	 * in the hold beside the hull would be in the sea.
	 */
	public static Vec3 pointFor(Entity entity, BlockPos pos) {
		Vec3 middle = entity.getBoundingBox().getCenter();
		return new Vec3(
			Mth.clamp(middle.x, pos.getX() + 1.0E-3, pos.getX() + 1 - 1.0E-3),
			Mth.clamp(middle.y, pos.getY() + 1.0E-3, pos.getY() + 1 - 1.0E-3),
			Mth.clamp(middle.z, pos.getZ() + 1.0E-3, pos.getZ() + 1 - 1.0E-3));
	}

	/** Whether this point of the world is in a dry cell, with the structure at this pose. */
	public boolean holds(double originX, double originY, double originZ, float yaw, double x, double y, double z) {
		double dx = x - originX;
		double dz = z - originZ;
		double ly = y - originY;
		if (ly < minY || ly >= maxY + 1 || dx * dx + dz * dz > reach * reach) return false;
		double rad = Math.toRadians(yaw);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		return cells.contains(new RelPos(
			Mth.floor(dx * cos + dz * sin), Mth.floor(ly), Mth.floor(-dx * sin + dz * cos)));
	}
}
