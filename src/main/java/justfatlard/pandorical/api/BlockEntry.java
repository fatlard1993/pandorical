package justfatlard.pandorical.api;

import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One block of a structure. With a {@code drawing}, a client with the {@code "structure_cells"}
 * capability draws that build in its place; every other client, and everything but the drawing,
 * takes the block. {@link StructureDrawings#entry} attaches whatever drawing a mod registered.
 */
public record BlockEntry(RelPos pos, BlockState state, @Nullable CellsDrawing drawing) {
    public BlockEntry(RelPos pos, BlockState state) {
        this(pos, state, null);
    }
}
