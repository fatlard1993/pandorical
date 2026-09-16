package justfatlard.pandorical.client.structure;

import justfatlard.pandorical.client.structure.StructureManager.ClientStructure;
import justfatlard.pandorical.client.structure.StructureManager.RelPosKey;
import justfatlard.pandorical.client.structure.StructureManager.StructurePoseSnapshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Walkable structures: solid to the local player, and carrying them while they stand on one.
 *
 * <p>Both are measured against the structure as drawn, so the deck you see is the deck you stand
 * on. A block's model is drawn at {@code origin + R(yaw) * (rel + v)}, turning about the pose
 * origin, so a point is taken into the structure's own frame by the inverse of that, where block
 * {@code rel} fills {@code [rel, rel + 1)}.
 *
 * <p>The carry runs at the head of the player's tick, after the position it is drawn from has been
 * kept, and the structure has already ticked: the player is drawn moving by the same step the deck
 * is drawn moving, in the same frames.
 */
public final class StructureDecks {
	private StructureDecks() {}

	/** How far below its feet a player may find deck and be counted aboard. */
	private static final double SUPPORT_REACH = 1.2;

	/**
	 * How far the deck may be below a player already aboard. A jump's height and room to spare,
	 * so jumping on a moving deck comes down where it went up.
	 */
	private static final double AIRBORNE_REACH = 4.0;

	/**
	 * Ticks a walkable structure stays solid after it is hidden: the blocks that replace it are
	 * placed by the server in the same tick but arrive in a later packet, and without this a
	 * player standing on it drops into them.
	 */
	private static final int HIDDEN_SOLID_TICKS = 10;

	private static ClientStructure riding;

	/** At the head of the local player's tick. */
	public static void carry(LocalPlayer player) {
		if (player.isPassenger() || player.isSpectator() || player.getAbilities().flying) {
			riding = null;
			return;
		}

		ClientStructure aboard = null;
		for (ClientStructure structure : StructureManager.getActive()) {
			if (!structure.walkable || !structure.visible) continue;
			double reach = structure == riding ? AIRBORNE_REACH : SUPPORT_REACH;
			if (hasDeckBeneath(structure, structure.lastTick(), player, reach)) {
				aboard = structure;
				break;
			}
		}
		riding = aboard;
		if (aboard != null) move(player, aboard.lastTick(), aboard.thisTick());
	}

	/**
	 * A walkable structure is being hidden, which is a ship docking: it is replaced where it now
	 * is by real blocks, at the newest pose rather than the one still being blended toward.
	 */
	static void hidden(ClientStructure structure) {
		if (!structure.walkable) return;
		LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		StructurePoseSnapshot drawn = structure.thisTick();
		structure.settle();
		structure.solidTicks = HIDDEN_SOLID_TICKS;
		if (player != null && structure == riding) {
			move(player, drawn, structure.thisTick());
		}
		if (structure == riding) riding = null;
	}

	/** Everything solid a walkable structure puts inside {@code area}, for the local player. */
	public static List<VoxelShape> collisions(AABB area) {
		List<VoxelShape> shapes = null;
		for (ClientStructure structure : StructureManager.getActive()) {
			if (!structure.walkable || !(structure.visible || structure.solidTicks > 0)) continue;
			shapes = addCollisions(structure, structure.thisTick(), area, shapes);
		}
		return shapes == null ? List.of() : shapes;
	}

	private static List<VoxelShape> addCollisions(ClientStructure structure, StructurePoseSnapshot pose,
												   AABB area, List<VoxelShape> into) {
		double yaw = Math.toRadians(pose.yaw());
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);

		// The area's footprint in the structure's frame: its four corners turned back.
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int corner = 0; corner < 4; corner++) {
			double dx = ((corner & 1) == 0 ? area.minX : area.maxX) - pose.x();
			double dz = ((corner & 2) == 0 ? area.minZ : area.maxZ) - pose.z();
			double lx = dx * cos + dz * sin;
			double lz = -dx * sin + dz * cos;
			minX = Math.min(minX, lx); maxX = Math.max(maxX, lx);
			minZ = Math.min(minZ, lz); maxZ = Math.max(maxZ, lz);
		}
		// A block reaches at most a cell past its own (a fence's collision is 1.5 tall).
		int x0 = Mth.floor(minX) - 1, x1 = Mth.floor(maxX) + 1;
		int z0 = Mth.floor(minZ) - 1, z1 = Mth.floor(maxZ) + 1;
		int y0 = Mth.floor(area.minY - pose.y()) - 1, y1 = Mth.floor(area.maxY - pose.y());
		if (!structure.mayHold(x0, y0, z0, x1, y1, z1)) return into;

		// Turned to the nearest quarter so a slab stays a slab and a stair keeps its step; exact
		// on the four headings a structure comes to rest on, and close enough between them.
		boolean swapped = (Math.floorMod(Math.round(pose.yaw() / 90.0f), 4) & 1) == 1;

		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					BlockState state = structure.blocks.get(new RelPosKey(x, y, z));
					if (state == null || state.isAir()) continue;
					VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
					if (shape.isEmpty()) continue;

					for (AABB part : shape.toAabbs()) {
						double cx = x + (part.minX + part.maxX) / 2;
						double cz = z + (part.minZ + part.maxZ) / 2;
						double hx = (part.maxX - part.minX) / 2;
						double hz = (part.maxZ - part.minZ) / 2;
						if (swapped) {
							double t = hx;
							hx = hz;
							hz = t;
						}
						double wx = pose.x() + cx * cos - cz * sin;
						double wz = pose.z() + cx * sin + cz * cos;
						AABB box = new AABB(wx - hx, pose.y() + y + part.minY, wz - hz,
							wx + hx, pose.y() + y + part.maxY, wz + hz);
						if (!box.intersects(area)) continue;
						if (into == null) into = new ArrayList<>();
						into.add(Shapes.create(box));
					}
				}
			}
		}
		return into;
	}

	/** Deck within {@code reach} below any corner of the player's feet. */
	private static boolean hasDeckBeneath(ClientStructure structure, StructurePoseSnapshot pose,
										  LocalPlayer player, double reach) {
		double yaw = Math.toRadians(pose.yaw());
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		double half = player.getBbWidth() / 2 - 1.0E-3;
		double ly = player.getY() - pose.y();
		int top = Mth.floor(ly - 0.05);
		int bottom = Mth.floor(ly - reach);

		for (int corner = 0; corner < 4; corner++) {
			double dx = player.getX() + ((corner & 1) == 0 ? -half : half) - pose.x();
			double dz = player.getZ() + ((corner & 2) == 0 ? -half : half) - pose.z();
			int x = Mth.floor(dx * cos + dz * sin);
			int z = Mth.floor(-dx * sin + dz * cos);
			for (int y = top; y >= bottom; y--) {
				BlockState state = structure.blocks.get(new RelPosKey(x, y, z));
				if (state != null && !state.isAir()
						&& !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/** Move the player from where they stand on the deck at one pose to the same spot at another. */
	private static void move(LocalPlayer player, StructurePoseSnapshot from, StructurePoseSnapshot to) {
		if (from.equals(to)) return;

		double fromYaw = Math.toRadians(from.yaw());
		double dx = player.getX() - from.x();
		double dz = player.getZ() - from.z();
		double lx = dx * Math.cos(fromYaw) + dz * Math.sin(fromYaw);
		double lz = -dx * Math.sin(fromYaw) + dz * Math.cos(fromYaw);

		double toYaw = Math.toRadians(to.yaw());
		player.setPos(
			to.x() + lx * Math.cos(toYaw) - lz * Math.sin(toYaw),
			player.getY() + (to.y() - from.y()),
			to.z() + lx * Math.sin(toYaw) + lz * Math.cos(toYaw));

		float turn = Mth.wrapDegrees(to.yaw() - from.yaw());
		if (turn != 0) {
			// The deck turns them and their momentum with it.
			player.setYRot(player.getYRot() + turn);
			double t = Math.toRadians(turn);
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * Math.cos(t) - v.z * Math.sin(t), v.y,
				v.x * Math.sin(t) + v.z * Math.cos(t));
		}
	}
}
