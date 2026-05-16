package justfatlard.pandorical.client.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.*;
import java.util.*;

/**
 * A block that dynamically creates its state definition from a list of property names.
 * Properties are cloned from a base block if available, or created as standard types.
 * Supports server-provided VoxelShapes for correct collision and selection.
 */
public class DynamicBlock extends Block {
    private final List<Property<?>> dynamicProperties;
    private Map<BlockState, VoxelShape> outlineShapes;
    private Map<BlockState, VoxelShape> collisionShapes;

    public DynamicBlock(Properties props, List<Property<?>> properties) {
        super(props);
        this.dynamicProperties = properties;
        BlockState defaultState = this.stateDefinition.any();
        this.registerDefaultState(defaultState);
    }

    /** Apply deserialized shapes from server. Called after construction. */
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
    protected VoxelShape getOcclusionShape(BlockState state) {
        // If the block has a non-full-cube outline shape, it shouldn't occlude adjacent faces.
        if (outlineShapes != null) {
            VoxelShape shape = outlineShapes.get(state);
            if (shape != null && !Shapes.joinIsNotEmpty(Shapes.block(), shape, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME)) {
                return shape; // Full cube — use normal occlusion
            }
            return Shapes.empty(); // Non-full — don't occlude
        }
        return super.getOcclusionShape(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        List<Property<?>> props = PENDING_PROPERTIES.get();
        if (props != null) {
            for (Property<?> prop : props) {
                builder.add(prop);
            }
        }
    }

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPERTIES = new ThreadLocal<>();

    public static DynamicBlock create(Properties blockProps, List<Property<?>> stateProps) {
        PENDING_PROPERTIES.set(stateProps);
        try {
            return new DynamicBlock(blockProps, stateProps);
        } finally {
            PENDING_PROPERTIES.remove();
        }
    }

    /**
     * Deserialize shape data from server into per-state shape maps.
     * Format per state: [numOutlineBoxes:byte][boxes...][numCollisionBoxes:byte][boxes...]
     * Each box: [minX:float][minY:float][minZ:float][maxX:float][maxY:float][maxZ:float]
     */
    public static void applyShapeData(Block block, byte[] shapeData) {
        if (shapeData == null || shapeData.length == 0) return;

        var states = block.getStateDefinition().getPossibleStates();
        Map<BlockState, VoxelShape> outlineMap = new IdentityHashMap<>();
        Map<BlockState, VoxelShape> collisionMap = new IdentityHashMap<>();

        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(shapeData));
            for (BlockState state : states) {
                outlineMap.put(state, readShape(dis));
                collisionMap.put(state, readShape(dis));
            }
        } catch (IOException e) {
            justfatlard.pandorical.Pandorical.LOGGER.warn("Failed to deserialize shapes for {}: {}",
                block, e.getMessage());
            return;
        }

        if (block instanceof DynamicBlock db) {
            db.setShapes(outlineMap, collisionMap);
        } else if (block instanceof DynamicSlabBlock dsb) {
            dsb.setShapes(outlineMap, collisionMap);
        }
    }

    private static VoxelShape readShape(DataInputStream dis) throws IOException {
        int numBoxes = dis.readByte() & 0xFF;
        if (numBoxes == 0) return Shapes.empty();

        VoxelShape shape = null;
        for (int i = 0; i < numBoxes; i++) {
            double minX = dis.readFloat();
            double minY = dis.readFloat();
            double minZ = dis.readFloat();
            double maxX = dis.readFloat();
            double maxY = dis.readFloat();
            double maxZ = dis.readFloat();
            VoxelShape box = Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
            shape = (shape == null) ? box : Shapes.or(shape, box);
        }
        return shape.optimize();
    }

    // ========================================================================
    // Property resolution (unchanged)
    // ========================================================================

    public static Property<?> resolveProperty(String name, Block baseBlock, int valueCount, String propType) {
        return resolveProperty(name, baseBlock, valueCount, 0, propType, null);
    }

    public static Property<?> resolveProperty(String name, Block baseBlock, int valueCount, int intMin, String propType, String enumValues) {
        // Try to find on the base block first
        if (baseBlock != null) {
            for (Property<?> prop : baseBlock.getStateDefinition().getProperties()) {
                if (prop.getName().equals(name)) {
                    return prop;
                }
            }
        }

        // For enum properties with explicit values from server, ALWAYS use NamedIntegerProperty
        if ("e".equals(propType) && enumValues != null && !enumValues.isEmpty()) {
            List<String> names = List.of(enumValues.split(","));
            return NamedIntegerProperty.create(name, names);
        }

        // Use value count to disambiguate variants
        if (name.equals("facing")) {
            if (valueCount == 6) return BlockStateProperties.FACING;
            return BlockStateProperties.HORIZONTAL_FACING;
        }
        if (name.equals("age")) {
            return switch (valueCount) {
                case 2 -> BlockStateProperties.AGE_1;
                case 3 -> BlockStateProperties.AGE_2;
                case 4 -> BlockStateProperties.AGE_3;
                case 6 -> BlockStateProperties.AGE_5;
                case 8 -> BlockStateProperties.AGE_7;
                case 16 -> BlockStateProperties.AGE_15;
                case 26 -> BlockStateProperties.AGE_25;
                default -> BlockStateProperties.AGE_7;
            };
        }

        Property<?> vanilla = switch (name) {
            case "waterlogged" -> BlockStateProperties.WATERLOGGED;
            case "powered" -> BlockStateProperties.POWERED;
            case "lit" -> BlockStateProperties.LIT;
            case "open" -> BlockStateProperties.OPEN;
            case "half" -> BlockStateProperties.HALF;
            case "type" -> BlockStateProperties.SLAB_TYPE;
            case "level" -> BlockStateProperties.LEVEL;
            case "moisture" -> BlockStateProperties.MOISTURE;
            case "snowy" -> BlockStateProperties.SNOWY;
            case "axis" -> BlockStateProperties.AXIS;
            case "attached" -> BlockStateProperties.ATTACHED;
            case "enabled" -> BlockStateProperties.ENABLED;
            case "inverted" -> BlockStateProperties.INVERTED;
            case "in_wall" -> BlockStateProperties.IN_WALL;
            case "has_bottle_0" -> BlockStateProperties.HAS_BOTTLE_0;
            case "has_bottle_1" -> BlockStateProperties.HAS_BOTTLE_1;
            case "has_bottle_2" -> BlockStateProperties.HAS_BOTTLE_2;
            case "triggered" -> BlockStateProperties.TRIGGERED;
            case "crafting" -> BlockStateProperties.CRAFTING;
            default -> null;
        };
        if (vanilla != null) return vanilla;

        if ("b".equals(propType)) {
            return BooleanProperty.create(name);
        } else if ("e".equals(propType) && enumValues != null && !enumValues.isEmpty()) {
            List<String> names = List.of(enumValues.split(","));
            return NamedIntegerProperty.create(name, names);
        } else if (valueCount > 0) {
            return IntegerProperty.create(name, intMin, intMin + valueCount - 1);
        }
        return null;
    }
}
