package justfatlard.pandorical.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * A look along a structure: which of its blocks it meets first. Shared by the client, which asks
 * whether to send a use, and the server, which works out what was used.
 *
 * <p>Worked in the structure's own frame, where its blocks sit on whole cells as they were stored,
 * so a door is clipped against the door's own outline and needs no turning. The look is turned
 * into that frame the same way the deck code turns a player's feet.
 */
public final class StructureRay {
	private StructureRay() {}

	@FunctionalInterface
	public interface Blocks {
		BlockState at(int x, int y, int z);
	}

	/**
	 * @param dx the eye relative to the structure's origin, in the world's axes
	 * @param yawDegrees the structure's heading
	 * @return {distance, x, y, z} of the nearest block met within {@code reach}, or null
	 */
	public static double[] nearest(double dx, double dy, double dz, double lookX, double lookY, double lookZ,
			float yawDegrees, double reach, Blocks blocks) {
		double yaw = Math.toRadians(yawDegrees);
		double cos = Math.cos(yaw), sin = Math.sin(yaw);
		Vec3 from = new Vec3(dx * cos + dz * sin, dy, -dx * sin + dz * cos);
		Vec3 dir = new Vec3(lookX * cos + lookZ * sin, lookY, -lookX * sin + lookZ * cos);
		Vec3 to = from.add(dir.scale(reach));

		double[] best = null;
		double nearest = reach;
		int x0 = Mth.floor(Math.min(from.x, to.x)) - 1, x1 = Mth.floor(Math.max(from.x, to.x)) + 1;
		int y0 = Mth.floor(Math.min(from.y, to.y)) - 1, y1 = Mth.floor(Math.max(from.y, to.y)) + 1;
		int z0 = Mth.floor(Math.min(from.z, to.z)) - 1, z1 = Mth.floor(Math.max(from.z, to.z)) + 1;
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					BlockState state = blocks.at(x, y, z);
					if (state == null || state.isAir()) continue;
					VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
					for (AABB part : shape.toAabbs()) {
						Optional<Vec3> hit = part.move(x, y, z).clip(from, to);
						if (hit.isEmpty()) continue;
						double distance = hit.get().distanceTo(from);
						if (distance < nearest) {
							nearest = distance;
							best = new double[] {distance, x, y, z};
						}
					}
				}
			}
		}
		return best;
	}
}
