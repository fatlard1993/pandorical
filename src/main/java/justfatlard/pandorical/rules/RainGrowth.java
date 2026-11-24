package justfatlard.pandorical.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rain on an open field brings the crop on faster: a crop under the sky in the rain gets extra random
 * ticks, so everything vanilla weighs (the light, the farmland's moisture, how the rows are spaced)
 * still decides whether one comes to anything. Rain makes the roll come round more often; it does not
 * make a crop in the dark grow.
 *
 * <p>What counts as a crop is the block tag {@code #pandorical:rain_grown}, not a list of classes,
 * because a mod's crops rarely share an ancestor: a slab crop extends {@code CropBlock}, a slab stem
 * {@code VegetationBlock}, sugar cane {@code Block}.
 */
public final class RainGrowth {
	private RainGrowth() {}

	public static final TagKey<Block> RAIN_GROWN = TagKey.create(Registries.BLOCK,
		Identifier.fromNamespaceAndPath("pandorical", "rain_grown"));

	/** The extra tick is the block's own random tick, which lands back here; per thread, as worlds tick at once. */
	private static final ThreadLocal<Boolean> GRANTING = ThreadLocal.withInitial(() -> false);

	/** After every random tick in the game, so the cheapest questions go first. */
	public static void afterRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.isRaining() || GRANTING.get() || !state.is(RAIN_GROWN)) return;
		if (!WorldRules.rainGrowth(level.getServer())) return;
		int ticks = WorldRules.rainTicks(level.getServer());
		// Under open sky, and the weather here falls as rain rather than snow
		if (ticks <= 0 || !level.isRainingAt(pos)) return;
		GRANTING.set(true);
		try {
			for (int i = 0; i < ticks; i++) {
				// Re-read each time: a grown crop is a different state, and another block may have taken the spot
				BlockState current = level.getBlockState(pos);
				if (!current.is(state.getBlock())) return;
				current.randomTick(level, pos, random);
			}
		} finally {
			GRANTING.set(false);
		}
	}
}
