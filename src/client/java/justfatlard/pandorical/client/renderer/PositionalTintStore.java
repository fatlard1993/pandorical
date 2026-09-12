package justfatlard.pandorical.client.renderer;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import justfatlard.pandorical.client.mixin.SingleQuadParticleAccessor;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.level.block.Block;

/**
 * Colours the server has asked for at particular places.
 *
 * <p>The block's own colour is a property of the block everywhere else in the game; this is the
 * one place it is a property of a spot. Kept as a flat map of packed positions because that is
 * what a block colour lookup has to hand and because the map is read once per face per rebuild -
 * often enough that anything cleverer would need to be measured first.
 *
 * <p>Nothing here persists. A reconnect starts empty and the server states it all again, which is
 * the same contract entity and chest overlays keep.
 */
public final class PositionalTintStore {
	private PositionalTintStore() {}

	/** What an unpainted position renders as: white, which multiplies to no change at all. */
	private static final int UNTINTED = -1;

	private static final Map<Long, Integer> painted = new HashMap<>();

	/**
	 * A source for one block type, answering the fallback wherever nothing has been painted.
	 * Zero means no fallback, which is vanilla's untinted white.
	 */
	public static BlockTintSource source(int fallback) {
		int unpainted = fallback == 0 ? UNTINTED : fallback;
		return new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				// Asked without a position: an item in a hand, a block in an inventory. There
				// is no spot to look up, so the fallback is the honest answer.
				return unpainted;
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				Integer argb = painted.get(pos.asLong());
				return argb == null ? unpainted : argb;
			}
		};
	}

	/** Blocks registered with a positional tint, whose particles take the tint too. */
	private static final Set<Block> positional =
		Collections.newSetFromMap(new IdentityHashMap<>());

	public static void track(Block... blocks) {
		positional.addAll(List.of(blocks));
	}

	/**
	 * The tint of the block currently spawning particles, or zero when it is not one of ours or
	 * is unpainted. Set around a block's animate tick, read as each particle is made.
	 */
	private static int emitting;

	public static void emitFrom(BlockState state, BlockPos pos) {
		emitting = positional.contains(state.getBlock()) ? painted.getOrDefault(pos.asLong(), 0) : 0;
	}

	public static void doneEmitting() {
		emitting = 0;
	}

	/**
	 * A particle from a painted block wears the paint.
	 *
	 * <p>The tint replaces the particle's colour rather than multiplying it, keeping only the
	 * particle's brightness: a portal's particles are purple, and purple times white is purple.
	 * Brightness is the strongest channel, so a white portal throws white sparks and not grey.
	 */
	public static void tintParticle(Particle particle) {
		if (emitting == 0 || !(particle instanceof SingleQuadParticle quad)) return;
		var colours = (SingleQuadParticleAccessor) quad;
		float bright = Math.max(colours.pandorical$rCol(), Math.max(colours.pandorical$gCol(), colours.pandorical$bCol()));
		quad.setColor(
			((emitting >> 16) & 0xFF) / 255F * bright,
			((emitting >> 8) & 0xFF) / 255F * bright,
			(emitting & 0xFF) / 255F * bright);
	}

	/**
	 * Take a colour, or drop one when {@code argb} is zero, and get the block redrawn.
	 *
	 * <p>The redraw is the part that is easy to leave out and impossible to notice leaving out on
	 * the machine it was written on: a colour lands in the map, and the face keeps the colour it
	 * was baked with until something else in that section happens to change. Chunk meshes are
	 * built once and kept.
	 */
	public static void paint(long pos, int argb) {
		if (argb == 0) {
			painted.remove(pos);
		} else {
			painted.put(pos, argb);
		}
		redraw(BlockPos.of(pos));
	}

	public static void clear() {
		painted.clear();
	}

	private static void redraw(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) return;

		// With neighbours, because a section boundary runs through the middle of plenty of
		// portals and half a recoloured portal is worse than none of one.
		client.level.setSectionDirtyWithNeighbors(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getY()),
			SectionPos.blockToSectionCoord(pos.getZ()));
	}
}
