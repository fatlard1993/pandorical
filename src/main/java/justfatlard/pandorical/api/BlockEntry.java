package justfatlard.pandorical.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A single block within a structure: its position relative to the structure's origin,
 * and the full {@link BlockState} (including properties) to render there.
 */
public record BlockEntry(RelPos pos, BlockState state) {
}
