package justfatlard.pandorical.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a walkable structure puts in the way of something moving: each block's own collision shape,
 * turned with the structure. Shared by the server, which holds up the entities it moves, and the
 * client, which holds up its own player, so both stand on the same deck.
 *
 * <p>A block is drawn at {@code origin + R(yaw) * (rel + v)}, turning about the pose origin, so
 * block {@code rel} fills {@code [rel, rel + 1)} in the structure's own frame.
 */
public final class StructureCollisions {
	private StructureCollisions() {}

	/** A structure's blocks, by cell in its own frame. */
	public interface Cells {
		@Nullable BlockState at(int x, int y, int z);

		/** Whether any block could lie in this box of cells: a cheap no before walking them. */
		boolean mayHold(int x0, int y0, int z0, int x1, int y1, int z1);
	}

	/**
	 * Adds the collision boxes this structure, at this pose, has inside {@code area} to
	 * {@code into}, which is made on the first box found.
	 */
	public static @Nullable List<VoxelShape> add(Cells cells, double poseX, double poseY, double poseZ, float yawDegrees,
												  AABB area, @Nullable List<VoxelShape> into) {
		double yaw = Math.toRadians(yawDegrees);
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);

		// The area's footprint in the structure's frame: its four corners turned back.
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int corner = 0; corner < 4; corner++) {
			double dx = ((corner & 1) == 0 ? area.minX : area.maxX) - poseX;
			double dz = ((corner & 2) == 0 ? area.minZ : area.maxZ) - poseZ;
			double lx = dx * cos + dz * sin;
			double lz = -dx * sin + dz * cos;
			minX = Math.min(minX, lx); maxX = Math.max(maxX, lx);
			minZ = Math.min(minZ, lz); maxZ = Math.max(maxZ, lz);
		}
		// A block reaches at most a cell past its own (a fence's collision is 1.5 tall).
		int x0 = Mth.floor(minX) - 1, x1 = Mth.floor(maxX) + 1;
		int z0 = Mth.floor(minZ) - 1, z1 = Mth.floor(maxZ) + 1;
		int y0 = Mth.floor(area.minY - poseY) - 1, y1 = Mth.floor(area.maxY - poseY);
		if (!cells.mayHold(x0, y0, z0, x1, y1, z1)) return into;

		// Turned to the nearest quarter so a slab stays a slab and a stair keeps its step; exact
		// on the four headings a structure comes to rest on, and close enough between them.
		boolean swapped = (Math.floorMod(Math.round(yawDegrees / 90.0f), 4) & 1) == 1;

		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					BlockState state = cells.at(x, y, z);
					if (state == null || state.isAir()) continue;
					VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
					if (shape.isEmpty()) continue;

					for (AABB part : boxes(shape)) {
						double cx = x + (part.minX + part.maxX) / 2;
						double cz = z + (part.minZ + part.maxZ) / 2;
						double hx = (part.maxX - part.minX) / 2;
						double hz = (part.maxZ - part.minZ) / 2;
						if (swapped) {
							double t = hx;
							hx = hz;
							hz = t;
						}
						double wx = poseX + cx * cos - cz * sin;
						double wz = poseZ + cx * sin + cz * cos;
						AABB box = new AABB(wx - hx, poseY + y + part.minY, wz - hz,
							wx + hx, poseY + y + part.maxY, wz + hz);
						if (!box.intersects(area)) continue;
						if (into == null) into = new ArrayList<>();
						into.add(Shapes.create(box));
					}
				}
			}
		}
		return into;
	}

	/** Asked from the server and client threads at once in single player. */
	private static final Map<VoxelShape, List<AABB>> BOXES = Collections.synchronizedMap(new WeakHashMap<>());

	/** A shape's boxes, kept per shape: shapes are shared and never change, and are asked for every tick. */
	public static List<AABB> boxes(VoxelShape shape) {
		return BOXES.computeIfAbsent(shape, s -> List.copyOf(s.toAabbs()));
	}
}
