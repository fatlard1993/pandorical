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

public class DynamicSlabBlock extends SlabBlock {
    private final List<Property<?>> extraProperties;
    private Map<BlockState, VoxelShape> outlineShapes;
    private Map<BlockState, VoxelShape> collisionShapes;

    private DynamicSlabBlock(Properties props, List<Property<?>> extraProperties) {
        super(props);
        this.extraProperties = extraProperties;
        // No registerDefaultState: SlabBlock pinned type=bottom, waterlogged=false, and
        // stateDefinition.any() would be a waterlogged top slab, which placement prediction uses.
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
        super.createBlockStateDefinition(builder);
        List<Property<?>> extras = PENDING_EXTRA_PROPERTIES.get();
        if (extras != null) {
            for (Property<?> prop : extras) {
                builder.add(prop);
            }
        }
    }

    private static final ThreadLocal<List<Property<?>>> PENDING_EXTRA_PROPERTIES = new ThreadLocal<>();

    /** {@code extraProperties} must not include type or waterlogged: SlabBlock adds those. */
    public static DynamicSlabBlock create(Properties blockProps, List<Property<?>> extraProperties) {
        PENDING_EXTRA_PROPERTIES.set(extraProperties);
        try {
            return new DynamicSlabBlock(blockProps, extraProperties);
        } finally {
            PENDING_EXTRA_PROPERTIES.remove();
        }
    }
}
