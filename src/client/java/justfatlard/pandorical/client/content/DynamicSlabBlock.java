package justfatlard.pandorical.client.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/**
 * A slab block that supports additional dynamic state properties beyond
 * the standard TYPE and WATERLOGGED. Extends SlabBlock so it inherits
 * correct slab collision shapes, placement behavior, and rendering.
 *
 * Used for blocks like grass_slab (has snowy), farmland_slab (has moisture), etc.
 */
public class DynamicSlabBlock extends SlabBlock {
    private final List<Property<?>> extraProperties;
    private Map<BlockState, VoxelShape> outlineShapes;
    private Map<BlockState, VoxelShape> collisionShapes;

    private DynamicSlabBlock(Properties props, List<Property<?>> extraProperties) {
        super(props);
        this.extraProperties = extraProperties;
        this.registerDefaultState(this.stateDefinition.any());
    }

    public void setShapes(Map<BlockState, VoxelShape> outline, Map<BlockState, VoxelShape> collision) {
        this.outlineShapes = outline;
        this.collisionShapes = collision;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (outlineShapes != null) {
            VoxelShape shape = outlineShapes.get(state);
            if (shape != null) return shape;
        }
        return super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (collisionShapes != null) {
            VoxelShape shape = collisionShapes.get(state);
            if (shape != null) return shape;
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // First add SlabBlock's own properties (TYPE, WATERLOGGED)
        super.createBlockStateDefinition(builder);
        // Then add extra properties passed via thread-local
        List<Property<?>> extras = PENDING_EXTRA_PROPERTIES.get();
        if (extras != null) {
            for (Property<?> prop : extras) {
                builder.add(prop);
            }
        }
    }

    private static final ThreadLocal<List<Property<?>>> PENDING_EXTRA_PROPERTIES = new ThreadLocal<>();

    /**
     * Create a DynamicSlabBlock with extra properties beyond TYPE and WATERLOGGED.
     * The extra properties list should NOT include type or waterlogged — those are
     * added by SlabBlock automatically.
     */
    public static DynamicSlabBlock create(Properties blockProps, List<Property<?>> extraProperties) {
        PENDING_EXTRA_PROPERTIES.set(extraProperties);
        try {
            return new DynamicSlabBlock(blockProps, extraProperties);
        } finally {
            PENDING_EXTRA_PROPERTIES.remove();
        }
    }
}
