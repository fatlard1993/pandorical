package justfatlard.pandorical.client.contextmodel;

import com.mojang.math.Quadrant;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blocks of one kind side by side drawn as one sheet: the frame is left off wherever the same
 * block stands beyond it. The model is put together from pieces, so a block ships a handful of
 * them rather than one per neighbour pattern.
 *
 * <p>A full block ships {@code <block>_connected_<face>}, each face without a frame, and
 * {@code <block>_connected_<face>_<toward>}, the frame along one edge of one face, laid over that
 * face. No edge is framed where the same block carries on beyond it, or where the surface folds
 * inward or outward round it into more of the same. Of the edges left, one where the same block
 * still touches beyond either face, by an edge or a corner, is an odd corner of a larger shape, and
 * takes {@code <block>_connected_<face>_<toward>_faint} instead when the block ships it. A box keeps
 * its outline at full strength; a sphere's steps are faint.
 *
 * <p>A pane ships vanilla's four pane parts without their caps as
 * {@code <pane>_connected_<side|side_alt|noside|noside_alt>}, each with {@code _top} and
 * {@code _bottom} frame strips, the arms also with {@code _end} and {@code _cap_<up|down>}, and
 * the post's caps as {@code <pane>_connected_cap_<up|down>}; they are turned as vanilla's
 * multipart turns its own.
 *
 * <p>A face, or one of the four pane parts, may also ship {@code _bare}: the same without its
 * streaks. A joined sheet draws it on all but one face in {@link #SPARSE}; a block standing alone
 * keeps its streaks everywhere.
 */
@Environment(EnvType.CLIENT)
public final class ConnectedFaces implements ContextModels.Provider {
	private record Piece(Block block, String part, Quadrant turn) {}

	private record Arm(String model, Quadrant turn) {}

	private static final Map<Piece, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();

	/** Every block is asked, so the answer for one that ships nothing is a set lookup. */
	private static final Set<Block> SHEETS = new HashSet<>();
	private static final Set<Block> PANES = new HashSet<>();

	/** One face of a joined sheet in this many keeps its streaks; a block on its own keeps them all. */
	private static final int SPARSE = 5;

	private static final Direction[] AROUND = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** Vanilla's pane multipart, side by side: which model, turned how far, for an arm and for its absence. */
	private static final Map<Direction, Arm> ARM = new EnumMap<>(Map.of(
		Direction.NORTH, new Arm("side", Quadrant.R0),
		Direction.EAST, new Arm("side", Quadrant.R90),
		Direction.SOUTH, new Arm("side_alt", Quadrant.R0),
		Direction.WEST, new Arm("side_alt", Quadrant.R90)));
	private static final Map<Direction, Arm> NO_ARM = new EnumMap<>(Map.of(
		Direction.NORTH, new Arm("noside", Quadrant.R0),
		Direction.EAST, new Arm("noside_alt", Quadrant.R0),
		Direction.SOUTH, new Arm("noside_alt", Quadrant.R90),
		Direction.WEST, new Arm("noside", Quadrant.R270)));

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		SHEETS.clear();
		PANES.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			String base = id.getPath() + "_connected";
			if (block instanceof IronBarsBlock) {
				if (!exists(resources, id, base + "_side")) continue;
				PANES.add(block);
				want(resources, add, block, id, "cap_up", Quadrant.R0);
				want(resources, add, block, id, "cap_down", Quadrant.R0);
				for (Direction side : AROUND) {
					Arm arm = ARM.get(side);
					for (String suffix : new String[] {"", "_bare", "_top", "_bottom", "_end", "_cap_up", "_cap_down"}) {
						want(resources, add, block, id, arm.model() + suffix, arm.turn());
					}
					Arm none = NO_ARM.get(side);
					for (String suffix : new String[] {"", "_bare", "_top", "_bottom"}) {
						want(resources, add, block, id, none.model() + suffix, none.turn());
					}
				}
			} else {
				if (!exists(resources, id, base + "_up")) continue;
				SHEETS.add(block);
				for (Direction face : Direction.values()) {
					want(resources, add, block, id, face.getSerializedName(), Quadrant.R0);
					want(resources, add, block, id, face.getSerializedName() + "_bare", Quadrant.R0);
					for (Direction toward : Direction.values()) {
						if (toward.getAxis() == face.getAxis()) continue;
						want(resources, add, block, id, edge(face, toward), Quadrant.R0);
						want(resources, add, block, id, edge(face, toward) + "_faint", Quadrant.R0);
					}
				}
			}
		}
	}

	private static boolean exists(ResourceManager resources, Identifier id, String path) {
		return resources.getResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json")).isPresent();
	}

	private static void want(ResourceManager resources, ContextModels.Registrar add, Block block, Identifier id, String part, Quadrant turn) {
		String path = id.getPath() + "_connected" + (part.isEmpty() ? "" : "_" + part);
		if (!exists(resources, id, path)) return;
		ModelState state = BlockModelRotation.get(Quadrant.fromXYZAngles(Quadrant.R0, turn, Quadrant.R0));
		KEYS.put(new Piece(block, part, turn), add.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path), state));
	}

	@Override
	public boolean covers(Block block) {
		return SHEETS.contains(block) || PANES.contains(block);
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		Block block = state.getBlock();
		if (!PANES.contains(block) && !SHEETS.contains(block)) return null;
		List<BlockStateModel> pieces = new ArrayList<>();
		if (PANES.contains(block)) {
			pane(state, level, pos, models, pieces);
		} else {
			sheet(block, level, pos, models, pieces);
		}
		return pieces.isEmpty() ? null : new Assembled(pieces);
	}

	private static void sheet(Block block, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models, List<BlockStateModel> pieces) {
		// For each side, whether the same block lies anywhere in the layer of nine on that side.
		boolean[] beyond = new boolean[6];
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx | dy | dz) == 0) continue;
					if (level.getBlockState(at.setWithOffset(pos, dx, dy, dz)).getBlock() != block) continue;
					if (dx != 0) beyond[(dx > 0 ? Direction.EAST : Direction.WEST).ordinal()] = true;
					if (dy != 0) beyond[(dy > 0 ? Direction.UP : Direction.DOWN).ordinal()] = true;
					if (dz != 0) beyond[(dz > 0 ? Direction.SOUTH : Direction.NORTH).ordinal()] = true;
				}
			}
		}
		boolean lone = true;
		for (boolean any : beyond) lone &= !any;
		boolean[] open = new boolean[6];
		for (Direction face : Direction.values()) {
			if (level.getBlockState(pos.relative(face)).getBlock() == block) continue;
			open[face.ordinal()] = true;
			sheetFace(models, pieces, block, face.getSerializedName(), Quadrant.R0, lone || glints(pos, face));
		}
		for (Direction face : Direction.values()) {
			if (!open[face.ordinal()]) continue;
			for (Direction toward : Direction.values()) {
				if (toward.getAxis() == face.getAxis()) continue;
				// Carried on flat, or folding inward over the edge or outward round it: still one sheet.
				BlockPos past = pos.relative(toward);
				if (level.getBlockState(past).getBlock() == block) continue;
				if (level.getBlockState(past.relative(face)).getBlock() == block) continue;
				if (level.getBlockState(past.relative(face.getOpposite())).getBlock() == block) continue;
				String piece = edge(face, toward);
				if (beyond[toward.ordinal()] || beyond[face.ordinal()]) {
					if (add(models, pieces, block, piece + "_faint", Quadrant.R0)) continue;
				}
				add(models, pieces, block, piece, Quadrant.R0);
			}
		}
	}

	/**
	 * Whether this face of a sheet keeps its streaks: one face in {@link #SPARSE}, a knight's move
	 * apart, so a big window has a few spread evenly across it rather than one on every block.
	 */
	private static boolean glints(BlockPos pos, Direction face) {
		return switch (face.getAxis()) {
			case X -> Math.floorMod(pos.getZ() + 2 * pos.getY(), SPARSE) == 0;
			case Y -> Math.floorMod(pos.getX() + 2 * pos.getZ(), SPARSE) == 0;
			case Z -> Math.floorMod(pos.getX() + 2 * pos.getY(), SPARSE) == 0;
		};
	}

	/** A face with its streaks, or the bare one where it has none to spare and the block ships it. */
	private static void sheetFace(ContextModels.Lookup models, List<BlockStateModel> pieces, Block block, String part, Quadrant turn, boolean streaked) {
		if (streaked || !add(models, pieces, block, part + "_bare", turn)) add(models, pieces, block, part, turn);
	}

	private static String edge(Direction face, Direction toward) {
		return face.getSerializedName() + "_" + toward.getSerializedName();
	}

	/**
	 * Each piece of frame is left off only where the pane beyond carries on the same piece: an arm's
	 * top under the same arm, a post's cap under the same pane, an arm's end against the same pane.
	 */
	private static void pane(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models, List<BlockStateModel> pieces) {
		Block block = state.getBlock();
		BlockState above = level.getBlockState(pos.above());
		BlockState below = level.getBlockState(pos.below());
		boolean sameAbove = above.getBlock() == block;
		boolean sameBelow = below.getBlock() == block;

		if (!sameAbove) add(models, pieces, block, "cap_up", Quadrant.R0);
		if (!sameBelow) add(models, pieces, block, "cap_down", Quadrant.R0);
		boolean lone = !sameAbove && !sameBelow;
		for (Direction side : AROUND) lone &= level.getBlockState(pos.relative(side)).getBlock() != block;
		boolean streaked = lone || Math.floorMod(pos.getX() + pos.getZ() + 2 * pos.getY(), SPARSE) == 0;

		for (Direction side : AROUND) {
			BooleanProperty toward = PipeBlock.PROPERTY_BY_DIRECTION.get(side);
			boolean arm = state.getValue(toward);
			boolean armAbove = sameAbove && above.getValue(toward);
			boolean armBelow = sameBelow && below.getValue(toward);
			if (arm) {
				Arm a = ARM.get(side);
				sheetFace(models, pieces, block, a.model(), a.turn(), streaked);
				if (!armAbove) {
					add(models, pieces, block, a.model() + "_top", a.turn());
					add(models, pieces, block, a.model() + "_cap_up", a.turn());
				}
				if (!armBelow) {
					add(models, pieces, block, a.model() + "_bottom", a.turn());
					add(models, pieces, block, a.model() + "_cap_down", a.turn());
				}
				if (level.getBlockState(pos.relative(side)).getBlock() != block) {
					add(models, pieces, block, a.model() + "_end", a.turn());
				}
			} else {
				Arm none = NO_ARM.get(side);
				sheetFace(models, pieces, block, none.model(), none.turn(), streaked);
				if (!(sameAbove && !armAbove)) add(models, pieces, block, none.model() + "_top", none.turn());
				if (!(sameBelow && !armBelow)) add(models, pieces, block, none.model() + "_bottom", none.turn());
			}
		}
	}

	private static boolean add(ContextModels.Lookup models, List<BlockStateModel> pieces, Block block, String part, Quadrant turn) {
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Piece(block, part, turn));
		if (key == null) return false;
		BlockStateModel model = models.get(key);
		if (model == null) return false;
		pieces.add(model);
		return true;
	}

	/** Pieces drawn in order, so a frame laid over the same face as the sheet lands on top of it. */
	private record Assembled(List<BlockStateModel> pieces) implements BlockStateModel {
		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			for (BlockStateModel piece : pieces) piece.collectParts(random, parts);
		}

		@Override
		public Material.Baked particleMaterial() {
			return pieces.getFirst().particleMaterial();
		}

		@Override
		public int materialFlags() {
			int flags = 0;
			for (BlockStateModel piece : pieces) flags |= piece.materialFlags();
			return flags;
		}
	}
}
