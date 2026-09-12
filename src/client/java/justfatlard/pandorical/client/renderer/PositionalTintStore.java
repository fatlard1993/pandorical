package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.client.mixin.SingleQuadParticleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PositionalTintStore {
	private PositionalTintStore() {}

	private static final int UNTINTED = -1;

	private static final Map<Long, Integer> painted = new HashMap<>();

	/** A zero fallback means untinted. */
	public static BlockTintSource source(int fallback) {
		int unpainted = fallback == 0 ? UNTINTED : fallback;
		return new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				return unpainted;
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				Integer argb = painted.get(pos.asLong());
				return argb == null ? unpainted : argb;
			}
		};
	}

	private static final Set<Block> positional =
		Collections.newSetFromMap(new IdentityHashMap<>());

	public static void track(Block... blocks) {
		positional.addAll(List.of(blocks));
	}

	/** Set around a block's animateTick, read as each of its particles is created. Zero is none. */
	private static int emitting;

	public static void emitFrom(BlockState state, BlockPos pos) {
		emitting = positional.contains(state.getBlock()) ? painted.getOrDefault(pos.asLong(), 0) : 0;
	}

	public static void doneEmitting() {
		emitting = 0;
	}

	/** Replaces the colour, keeping its brightest channel; multiplying leaves purple purple. */
	public static void tintParticle(Particle particle) {
		if (emitting == 0 || !(particle instanceof SingleQuadParticle quad)) return;
		var colours = (SingleQuadParticleAccessor) quad;
		float bright = Math.max(colours.pandorical$rCol(), Math.max(colours.pandorical$gCol(), colours.pandorical$bCol()));
		quad.setColor(
			((emitting >> 16) & 0xFF) / 255F * bright,
			((emitting >> 8) & 0xFF) / 255F * bright,
			(emitting & 0xFF) / 255F * bright);
	}

	/** Zero removes. Chunk meshes keep the colour they were baked with until redrawn. */
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

		client.level.setSectionDirtyWithNeighbors(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getY()),
			SectionPos.blockToSectionCoord(pos.getZ()));
	}
}
