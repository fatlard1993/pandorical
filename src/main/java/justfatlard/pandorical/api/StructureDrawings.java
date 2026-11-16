package justfatlard.pandorical.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Blocks a structure draws as something other than themselves, answered by the mod that owns the
 * block, so the mod building the structure need not know it: a ship asks for each block it carries,
 * and a miniature aboard answers with its build.
 *
 * <p>Asked on the server, once per block as an entry is made, never per tick. Registered at mod
 * init; order is registration order and the first answer wins.
 */
public interface StructureDrawings {
    /**
     * Answer which blocks draw as a build.
     *
     * @param provider handed each block's state and the block entity data saved with it, if any;
     *                 answers {@link Optional#empty()} for every block it does not own. One that
     *                 throws is logged once and skipped, not passed up to the structure's owner.
     */
    void register(Provider provider);

    /** The drawing the first provider gives this block, if any does. */
    Optional<CellsDrawing> find(BlockState state, @Nullable CompoundTag blockEntityData);

    /** An entry for this block, carrying the drawing the first provider gives it, if any does. */
    default BlockEntry entry(RelPos pos, BlockState state, @Nullable CompoundTag blockEntityData) {
        return new BlockEntry(pos, state, find(state, blockEntityData).orElse(null));
    }

    @FunctionalInterface
    interface Provider {
        /**
         * @param blockEntityData as {@code BlockEntity#saveWithId} writes it, or null for a block
         *                        without one; a copy is not made, so do not change it
         */
        Optional<CellsDrawing> draw(BlockState state, @Nullable CompoundTag blockEntityData);
    }
}
