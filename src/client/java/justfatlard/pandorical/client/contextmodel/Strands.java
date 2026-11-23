package justfatlard.pandorical.client.contextmodel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A web strung between whatever holds it. A side holds the block when the same block carries on
 * beyond it, or when what stands there is anything solid: a face, a fence post, a wall, bars. A
 * block held on no side keeps its own model.
 *
 * <p>Held on one side only, it is small. Hanging from above it is a frayed edge,
 * {@code <block>_strand_tuft_<x|z>}, in the plane of the web above it when that web runs one way,
 * and in both planes under a ceiling. Held from below or beside it lies flat against that face,
 * {@code <block>_strand_hug_<side>}.
 *
 * <p>Tucked into a corner, two solid sides at right angles with nothing else holding it but more
 * web along that corner, it is one sheet across the corner at 45 degrees,
 * {@code <block>_strand_corner_<a>_<b>}, so a run of webs along the foot of a wall is one sheet.
 *
 * <p>Filling an open rectangle up to {@link #ORB_MOST} blocks a side, with nothing in front of it or
 * behind it and held on at least two of its edges, the rectangle is one orb web, each block its
 * share of it: {@code <block>_strand_orb_<x|y|z>_<width>x<height>_<across>_<up>}, counted from the
 * rectangle's low corner along the sheet's two axes (z then y facing x, x then y facing z, x then z
 * facing y). Every block of one orb takes the same variant, picked by that corner.
 *
 * <p>Otherwise it is one flat sheet through its middle, upright wherever an upright sheet has two
 * sides to hang between and level only where none has, drawn whole for the
 * sides holding it: {@code <block>_strand_sheet_<x|y|z>_<sides>}, the holding sides in the plane
 * named in the order of {@link Direction}, such as {@code cobweb_strand_sheet_z_down_west_east}.
 * Held from above and not from below it hangs, and takes {@code ..._hanging} instead when shipped.
 *
 * <p>Any piece may ship {@code _2}, {@code _3} and on: one is picked per block by its position, so
 * a big web does not repeat block to block. Pieces join neighbours only where their threads cross
 * the block's edge, so every variant of a piece must cross it in the same places.
 */
@Environment(EnvType.CLIENT)
public final class Strands implements ContextModels.Provider {
	private static final int MOST_VARIANTS = 8;

	/** The widest and tallest an orb is: past that a span of web is a sheet, not one spider's work. */
	private static final int ORB_MOST = 4;

	private static final Map<Identifier, List<ExtraModelKey<BlockStateModel>>> KEYS = new HashMap<>();

	/** Every block is asked, so the answer for one that ships nothing is a set lookup. */
	private static final Set<Block> WEBS = new HashSet<>();

	/** Corners tried in this order: wall to wall first, the way webs hang in a room's corner, then floor, then ceiling. */
	private static final Direction[][] CORNERS = corners();

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		WEBS.clear();
		// Listed once: asking for each of several hundred pieces of every block in the game one by one
		// would make every resource reload pay for blocks that ship none.
		Set<Identifier> shipped = new HashSet<>();
		for (Identifier file : resources.listResources("models/block", file -> file.getPath().contains("_strand_")).keySet()) {
			String path = file.getPath();
			shipped.add(Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring("models/".length(), path.length() - ".json".length())));
		}
		if (shipped.isEmpty()) return;
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (!shipped.contains(model(id, "tuft_x")) && !shipped.contains(model(id, "sheet_z_west_east"))) continue;
			List<String> parts = new ArrayList<>();
			for (Direction side : Direction.values()) parts.add("hug_" + side.getSerializedName());
			for (Direction.Axis facing : Direction.Axis.values()) {
				List<Direction> around = new ArrayList<>();
				for (Direction side : Direction.values()) if (side.getAxis() != facing) around.add(side);
				for (int mask = 0; mask < 1 << around.size(); mask++) {
					if (Integer.bitCount(mask) < 2) continue;
					boolean[] held = new boolean[6];
					for (int i = 0; i < around.size(); i++) if ((mask & 1 << i) != 0) held[around.get(i).ordinal()] = true;
					parts.add(sheet(facing, held));
					parts.add(sheet(facing, held) + "_hanging");
				}
				for (int width = 1; width <= ORB_MOST; width++) {
					for (int height = 1; height <= ORB_MOST; height++) {
						for (int across = 0; across < width; across++) {
							for (int up = 0; up < height; up++) parts.add(orb(facing, width, height, across, up));
						}
					}
				}
			}
			for (Direction[] corner : CORNERS) parts.add("corner_" + pair(corner[0], corner[1]));
			for (String upright : new String[] {"x", "z"}) parts.add("tuft_" + upright);
			for (String part : parts) {
				List<ExtraModelKey<BlockStateModel>> variants = new ArrayList<>();
				for (int n = 1; n <= MOST_VARIANTS; n++) {
					Identifier model = model(id, n == 1 ? part : part + "_" + n);
					if (!shipped.contains(model)) break;
					variants.add(add.add(model));
				}
				if (variants.isEmpty()) continue;
				KEYS.put(model(id, part), variants);
				WEBS.add(block);
			}
		}
	}

	private static String orb(Direction.Axis facing, int width, int height, int across, int up) {
		return "orb_" + facing.getSerializedName() + "_" + width + "x" + height + "_" + across + "_" + up;
	}

	private static Identifier model(Identifier block, String part) {
		return Identifier.fromNamespaceAndPath(block.getNamespace(), "block/" + block.getPath() + "_strand_" + part);
	}

	/** Two sides at right angles, named in a fixed order so each corner has one name. */
	private static String pair(Direction a, Direction b) {
		return a.ordinal() < b.ordinal()
			? a.getSerializedName() + "_" + b.getSerializedName()
			: b.getSerializedName() + "_" + a.getSerializedName();
	}

	private static Direction[][] corners() {
		List<Direction[]> found = new ArrayList<>();
		Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		for (Direction a : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
			for (Direction b : new Direction[] {Direction.WEST, Direction.EAST}) found.add(new Direction[] {a, b});
		}
		for (Direction a : new Direction[] {Direction.DOWN, Direction.UP}) {
			for (Direction b : horizontal) found.add(new Direction[] {a, b});
		}
		return found.toArray(new Direction[0][]);
	}

	@Override
	public boolean covers(Block block) {
		return WEBS.contains(block);
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		Block block = state.getBlock();
		if (!WEBS.contains(block)) return null;
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		int variant = (int) (Mth.getSeed(pos) >>> 16);

		boolean[] held = new boolean[6];
		boolean[] same = new boolean[6];
		int holds = 0;
		Direction only = null;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (Direction side : Direction.values()) {
			at.setWithOffset(pos, side);
			BlockState beyond = level.getBlockState(at);
			same[side.ordinal()] = beyond.getBlock() == block;
			held[side.ordinal()] = same[side.ordinal()] || tiesTo(level, at, beyond, side.getOpposite());
			if (held[side.ordinal()]) {
				holds++;
				only = side;
			}
		}
		if (holds == 0) return null;

		Pieces pieces = new Pieces(id, models, variant);
		if (holds == 1) {
			if (only == Direction.UP) {
				// The frayed edge of the web above, in its plane when it runs one way; under a ceiling
				// or a web running both ways, strands in both.
				BlockPos above = pos.above();
				boolean runsX = level.getBlockState(above.east()).getBlock() == block || level.getBlockState(above.west()).getBlock() == block;
				boolean runsZ = level.getBlockState(above.north()).getBlock() == block || level.getBlockState(above.south()).getBlock() == block;
				if (!runsZ || runsX == runsZ) pieces.add("tuft_z");
				if (!runsX || runsX == runsZ) pieces.add("tuft_x");
			} else {
				pieces.add("hug_" + only.getSerializedName());
			}
			return pieces.built();
		}

		Direction[] corner = corner(held, same);
		if (corner != null && pieces.add("corner_" + pair(corner[0], corner[1]))) return pieces.built();

		Direction.Axis facing = facing(held, same);
		if (orb(block, level, pos, facing, pieces)) return pieces.built();
		String sheet = sheet(facing, held);
		boolean hanging = held[Direction.UP.ordinal()] && !held[Direction.DOWN.ordinal()] && facing != Direction.Axis.Y;
		if (!(hanging && pieces.add(sheet + "_hanging"))) pieces.add(sheet);
		return pieces.built();
	}

	/**
	 * Whether this block is part of an open rectangle of web drawn as one orb, adding its share of
	 * the orb if so. The rectangle is found by walking out along the sheet's two axes from here; it
	 * must be web throughout, end where it ends on every side, stand clear in front and behind, and
	 * be held from outside along at least two of its edges.
	 */
	private static boolean orb(Block block, BlockAndTintGetter level, BlockPos pos, Direction.Axis facing, Pieces pieces) {
		Direction.Axis acrossAxis = facing == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		Direction.Axis upAxis = facing == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
		Direction across = Direction.fromAxisAndDirection(acrossAxis, Direction.AxisDirection.POSITIVE);
		Direction up = Direction.fromAxisAndDirection(upAxis, Direction.AxisDirection.POSITIVE);
		Direction front = Direction.fromAxisAndDirection(facing, Direction.AxisDirection.POSITIVE);

		int before = run(block, level, pos, across.getOpposite());
		int after = run(block, level, pos, across);
		int below = run(block, level, pos, up.getOpposite());
		int above = run(block, level, pos, up);
		int width = before + after + 1;
		int height = below + above + 1;
		if (width > ORB_MOST || height > ORB_MOST) return false;
		BlockPos corner = pos.relative(across, -before).relative(up, -below);

		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int a = 0; a < width; a++) {
			for (int u = 0; u < height; u++) {
				at.set(corner).move(across, a).move(up, u);
				if (level.getBlockState(at).getBlock() != block) return false;
				if (!clear(block, level, at, front) || !clear(block, level, at, front.getOpposite())) return false;
			}
		}

		int heldEdges = 0;
		for (Direction edge : new Direction[] {across, across.getOpposite(), up, up.getOpposite()}) {
			boolean alongAcross = edge.getAxis() == upAxis;
			int length = alongAcross ? width : height;
			boolean heldAll = true;
			for (int i = 0; i < length; i++) {
				at.set(corner).move(alongAcross ? across : up, i);
				if (edge == across) at.move(across, width - 1);
				if (edge == up) at.move(up, height - 1);
				at.move(edge);
				BlockState beyond = level.getBlockState(at);
				// More web past an edge means the rectangle is not the whole of it.
				if (beyond.getBlock() == block) return false;
				heldAll &= tiesTo(level, at, beyond, edge.getOpposite());
			}
			if (heldAll) heldEdges++;
		}
		if (heldEdges < 2) return false;
		return pieces.add(orb(facing, width, height, before, below), (int) (Mth.getSeed(corner) >>> 16));
	}

	/** How many more of the same block carry on from here in a direction, stopping once past an orb's size. */
	private static int run(Block block, BlockAndTintGetter level, BlockPos pos, Direction toward) {
		BlockPos.MutableBlockPos at = pos.mutable();
		int count = 0;
		while (count < ORB_MOST && level.getBlockState(at.move(toward)).getBlock() == block) count++;
		return count;
	}

	/** Nothing in front of or behind an orb: no more web, and nothing it would rather be tied to. */
	private static boolean clear(Block block, BlockAndTintGetter level, BlockPos pos, Direction side) {
		BlockPos beside = pos.relative(side);
		BlockState state = level.getBlockState(beside);
		return state.getBlock() != block && !tiesTo(level, beside, state, side.getOpposite());
	}

	/**
	 * Whether a web can be tied to this block on the face toward it: a face to tie to, or anything
	 * solid at all. A fence post is a thin column in the middle of its block, its faces at the block's
	 * edge empty, and silk is strung to it all the same.
	 */
	private static boolean tiesTo(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction face) {
		return state.isFaceSturdy(level, pos, face, SupportType.CENTER) || !state.getCollisionShape(level, pos).isEmpty();
	}

	/** The sheet facing along an axis, named for the sides holding it in its plane. */
	private static String sheet(Direction.Axis facing, boolean[] held) {
		StringBuilder name = new StringBuilder("sheet_").append(facing.getSerializedName());
		for (Direction side : Direction.values()) {
			if (side.getAxis() != facing && held[side.ordinal()]) name.append('_').append(side.getSerializedName());
		}
		return name.toString();
	}

	/**
	 * Two solid sides at right angles where everything else holding the block is more web carried
	 * along the corner between them, or null.
	 */
	private static Direction[] corner(boolean[] held, boolean[] same) {
		for (Direction[] corner : CORNERS) {
			Direction a = corner[0];
			Direction b = corner[1];
			if (!held[a.ordinal()] || same[a.ordinal()] || !held[b.ordinal()] || same[b.ordinal()]) continue;
			Direction.Axis along = third(a.getAxis(), b.getAxis());
			boolean alone = true;
			for (Direction side : Direction.values()) {
				if (side == a || side == b || !held[side.ordinal()]) continue;
				if (side.getAxis() != along || !same[side.ordinal()]) alone = false;
			}
			if (alone) return corner;
		}
		return null;
	}

	private static Direction.Axis third(Direction.Axis a, Direction.Axis b) {
		for (Direction.Axis axis : Direction.Axis.values()) {
			if (axis != a && axis != b) return axis;
		}
		throw new IllegalArgumentException(a + " and " + b + " are one axis");
	}

	/**
	 * Upright wherever an upright sheet has two sides to hang between: of the two, the one with more
	 * holding sides, then more web, then facing x. Level only where neither has. A level sheet sits
	 * at the height of the eyes looking at it, so it is nearly always seen edge on, a streak.
	 */
	private static Direction.Axis facing(boolean[] held, boolean[] same) {
		Direction.Axis best = Direction.Axis.Y;
		int bestScore = -1;
		for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
			int holds = 0;
			int webs = 0;
			for (Direction side : Direction.values()) {
				if (side.getAxis() == axis || !held[side.ordinal()]) continue;
				holds++;
				if (same[side.ordinal()]) webs++;
			}
			if (holds < 2) continue;
			int score = holds * 8 + webs;
			if (score > bestScore) {
				best = axis;
				bestScore = score;
			}
		}
		return best;
	}

	/** The pieces gathered for one block, each in the variant this block's position picks. */
	private static final class Pieces {
		private final Identifier block;
		private final ContextModels.Lookup models;
		private final int variant;
		private final List<BlockStateModel> chosen = new ArrayList<>();

		Pieces(Identifier block, ContextModels.Lookup models, int variant) {
			this.block = block;
			this.models = models;
			this.variant = variant;
		}

		boolean add(String part) {
			return add(part, variant);
		}

		boolean add(String part, int variant) {
			List<ExtraModelKey<BlockStateModel>> variants = KEYS.get(model(block, part));
			if (variants == null) return false;
			BlockStateModel model = models.get(variants.get(Math.floorMod(variant, variants.size())));
			if (model == null) return false;
			chosen.add(model);
			return true;
		}

		BlockStateModel built() {
			return chosen.isEmpty() ? null : new Assembled(chosen);
		}
	}
}
