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
 *
 * <p>Walking legs are worked out by each client from how far an entity moved in a tick, so a deck
 * under sail would have everyone on it walking on the spot. {@link #deckStep} is how far the deck
 * carried an entity this tick, taken back off before the legs are worked out.
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

	/**
	 * How far below its feet another entity may find a moving deck and have its legs measured
	 * against it: past a jump's height (1.25), so a jump on deck is not a stride at the top of it.
	 */
	private static final double ANIMATION_REACH = 1.5;

	private static ClientStructure riding;

	/** How far {@link #carry} moved the local player this tick, or null. */
	private static @org.jspecify.annotations.Nullable Vec3 carried;

	/** At the head of the local player's tick. */
	public static void carry(LocalPlayer player) {
		carried = null;
		if (player.isPassenger() || player.isSpectator() || player.getAbilities().flying) {
			riding = null;
			return;
		}

		ClientStructure aboard = null;
		for (ClientStructure structure : StructureManager.getActive()) {
			if (!structure.walkable || !structure.visible) continue;
			double reach = structure == riding ? AIRBORNE_REACH : SUPPORT_REACH;
			if (hasDeckBeneath(structure, structure.lastTick(), player.getX(), player.getY(), player.getZ(),
					player.getBbWidth() / 2, reach)) {
				aboard = structure;
				break;
			}
		}
		riding = aboard;
		if (aboard != null) {
			double x = player.getX(), y = player.getY(), z = player.getZ();
			move(player, aboard.lastTick(), aboard.thisTick());
			if (player.getX() != x || player.getY() != y || player.getZ() != z) {
				carried = new Vec3(player.getX() - x, player.getY() - y, player.getZ() - z);
			}
		}
	}

	/**
	 * How far a moving deck carried this entity this tick, for its walk animation, or null if none
	 * did. The local player's is the step {@link #carry} took. Anyone else, another player or a mob,
	 * is drawn where the server last had them, moving at the deck's speed, so theirs is how far the
	 * deck under where they stood at the start of the tick moved over it.
	 */
	public static @org.jspecify.annotations.Nullable Vec3 deckStep(net.minecraft.world.entity.LivingEntity entity) {
		if (entity instanceof LocalPlayer) return carried;
		if (!StructureManager.anyDeckMoving()) return null;
		double x = entity.xo, y = entity.yo, z = entity.zo;
		for (ClientStructure structure : StructureManager.getActive()) {
			if (!structure.walkable || !structure.visible) continue;
			StructurePoseSnapshot from = structure.lastTick();
			StructurePoseSnapshot to = structure.thisTick();
			if (from.equals(to)) continue;
			if (hasDeckBeneath(structure, from, x, y, z, entity.getBbWidth() / 2, ANIMATION_REACH)) {
				Vec3 there = carriedTo(x, y, z, from, to);
				return new Vec3(there.x - x, there.y - y, there.z - z);
			}
		}
		return null;
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

	/**
	 * Whether a walkable structure has a ladder, vine or anything else climbable at this entity's
	 * feet: on a ship at sea there is no block in the world to climb, only the one drawn.
	 */
	public static boolean climbable(net.minecraft.world.entity.LivingEntity entity) {
		for (ClientStructure structure : StructureManager.getActive()) {
			if (!structure.walkable || !structure.visible) continue;
			StructurePoseSnapshot pose = structure.thisTick();
			double yaw = Math.toRadians(pose.yaw());
			double dx = entity.getX() - pose.x();
			double dz = entity.getZ() - pose.z();
			int x = Mth.floor(dx * Math.cos(yaw) + dz * Math.sin(yaw));
			int z = Mth.floor(-dx * Math.sin(yaw) + dz * Math.cos(yaw));
			int y = Mth.floor(entity.getY() - pose.y());
			BlockState state = structure.blocks.get(new RelPosKey(x, y, z));
			if (state != null && (state.is(net.minecraft.tags.BlockTags.CLIMBABLE)
					|| justfatlard.pandorical.client.content.ContentManager.isClimbable(state))) {
				return true;
			}
		}
		return false;
	}

	/** Blocks a mod answers a use of on a walkable structure; see {@code StructureApi.onBlockUse}. */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> USABLE = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath("pandorical", "usable_on_structures"));

	/** A block of a structure the player is pointing at: which structure, and where in it. */
	public record Pick(String structureId, int x, int y, int z) {}

	/**
	 * What pressing use now means on a walkable structure, if anything: the nearest block along the
	 * player's look on any of them, if it is one tagged usable and nothing in the world stands nearer.
	 */
	public static @org.jspecify.annotations.Nullable Pick usePick(LocalPlayer player, net.minecraft.world.phys.HitResult worldHit) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1);
		double reach = player.blockInteractionRange();
		if (worldHit != null && worldHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			reach = Math.min(reach, eye.distanceTo(worldHit.getLocation()) + 1.0E-3);
		}
		Pick nearest = null;
		BlockState nearestState = null;
		double best = reach;
		for (java.util.Map.Entry<String, ClientStructure> entry : StructureManager.byId().entrySet()) {
			ClientStructure structure = entry.getValue();
			if (!structure.walkable || !structure.visible) continue;
			StructurePoseSnapshot pose = structure.thisTick();
			double[] hit = justfatlard.pandorical.structure.StructureRay.nearest(eye.x - pose.x(), eye.y - pose.y(), eye.z - pose.z(),
				look.x, look.y, look.z, pose.yaw(), best, (x, y, z) -> structure.blocks.get(new RelPosKey(x, y, z)));
			if (hit != null && hit[0] < best) {
				best = hit[0];
				nearest = new Pick(entry.getKey(), (int) hit[1], (int) hit[2], (int) hit[3]);
				nearestState = structure.blocks.get(new RelPosKey(nearest.x(), nearest.y(), nearest.z()));
			}
		}
		// The tag alone, the same test the server's handlers make: a block the client took over the
		// click for and the server then passed on would be a click that did nothing.
		return nearestState != null && nearestState.is(USABLE) ? nearest : null;
	}

	/** Deck within {@code reach} below any corner of feet at this point, {@code halfWidth} across. */
	private static boolean hasDeckBeneath(ClientStructure structure, StructurePoseSnapshot pose,
										  double px, double py, double pz, double halfWidth, double reach) {
		double yaw = Math.toRadians(pose.yaw());
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		double half = halfWidth - 1.0E-3;
		double ly = py - pose.y();
		int top = Mth.floor(ly - 0.05);
		int bottom = Mth.floor(ly - reach);

		// Nowhere near it: one bounds test rather than a lookup per corner and layer.
		double cx = px - pose.x();
		double cz = pz - pose.z();
		int lx = Mth.floor(cx * cos + cz * sin);
		int lz = Mth.floor(-cx * sin + cz * cos);
		int spread = Mth.ceil(half) + 1;
		if (!structure.mayHold(lx - spread, bottom, lz - spread, lx + spread, top, lz + spread)) return false;

		for (int corner = 0; corner < 4; corner++) {
			double dx = px + ((corner & 1) == 0 ? -half : half) - pose.x();
			double dz = pz + ((corner & 2) == 0 ? -half : half) - pose.z();
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

	/** Where a point on the deck at one pose is at another. */
	private static Vec3 carriedTo(double x, double y, double z, StructurePoseSnapshot from, StructurePoseSnapshot to) {
		double fromYaw = Math.toRadians(from.yaw());
		double dx = x - from.x();
		double dz = z - from.z();
		double lx = dx * Math.cos(fromYaw) + dz * Math.sin(fromYaw);
		double lz = -dx * Math.sin(fromYaw) + dz * Math.cos(fromYaw);

		double toYaw = Math.toRadians(to.yaw());
		return new Vec3(
			to.x() + lx * Math.cos(toYaw) - lz * Math.sin(toYaw),
			y + (to.y() - from.y()),
			to.z() + lx * Math.sin(toYaw) + lz * Math.cos(toYaw));
	}

	/** Move the player from where they stand on the deck at one pose to the same spot at another. */
	private static void move(LocalPlayer player, StructurePoseSnapshot from, StructurePoseSnapshot to) {
		if (from.equals(to)) return;

		player.setPos(carriedTo(player.getX(), player.getY(), player.getZ(), from, to));

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
