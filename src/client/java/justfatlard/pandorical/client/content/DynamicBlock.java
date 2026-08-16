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
                return shape; // Full cube: use normal occlusion
            }
            return Shapes.empty(); // Non-full: don't occlude
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

    /**
     * Rebuild one state property from what the server put on the wire.
     *
     * <p>The wire wins. A same-named property on the declared base block, or the
     * well-known vanilla property for that name, is only ever reused when it is
     * genuinely equivalent to what the server described; otherwise the property is
     * synthesized to the server's exact value set. Preferring the base block's
     * version outright (the old behaviour) made declaring an intuitively-correct base
     * block WORSE than declaring none: a slab of {@code torchflower_crop} carries
     * ages 0-2 while vanilla's own crop carries 0-1, so the client built a 4-state
     * block against a 6-state server block and the whole block had to be papered over
     * with a fallback state. The same trap sat in the name table below, where e.g.
     * {@code level} always resolved to the 0-15 vanilla property whatever range the
     * server sent.
     *
     * <p>Equivalent candidates are still preferred over synthesized ones: they carry
     * vanilla's own value names, which is what makes blockstate JSON variant strings
     * match.
     */
    public static Property<?> resolveProperty(String name, Block baseBlock, int valueCount, int intMin, String propType, String enumValues) {
        List<String> wireEnumNames = ("e".equals(propType) && enumValues != null && !enumValues.isEmpty())
            ? List.of(enumValues.split(","))
            : null;
        // Older wire forms can carry no usable value set at all; with nothing to check
        // against, a candidate is still better than nothing.
        boolean wireDescribesValues = wireEnumNames != null || valueCount > 0 || "b".equals(propType);

        if (baseBlock != null) {
            for (Property<?> prop : baseBlock.getStateDefinition().getProperties()) {
                if (!prop.getName().equals(name)) continue;
                if (!wireDescribesValues || matchesWire(prop, propType, valueCount, intMin, wireEnumNames)) {
                    return prop;
                }
                justfatlard.pandorical.Pandorical.LOGGER.debug(
                    "Base block property '{}' ({} values) does not match the server's ({} values) — rebuilding from the wire",
                    name, prop.getPossibleValues().size(),
                    wireEnumNames != null ? wireEnumNames.size() : valueCount);
                break;
            }
        }

        Property<?> named = vanillaPropertyByName(name, valueCount);
        if (named != null && (!wireDescribesValues || matchesWire(named, propType, valueCount, intMin, wireEnumNames))) {
            return named;
        }

        if (wireEnumNames != null) return NamedIntegerProperty.create(name, wireEnumNames);
        if ("b".equals(propType)) return BooleanProperty.create(name);
        if (valueCount > 0) return IntegerProperty.create(name, intMin, intMin + valueCount - 1);
        return null;
    }

    /** True when {@code prop} carries exactly the value set the server described. */
    private static boolean matchesWire(Property<?> prop, String propType, int valueCount, int intMin,
                                       List<String> wireEnumNames) {
        List<?> values = prop.getPossibleValues();
        if (wireEnumNames != null) {
            if (values.size() != wireEnumNames.size()) return false;
            for (int i = 0; i < values.size(); i++) {
                if (!wireEnumNames.get(i).equals(valueName(prop, values.get(i)))) return false;
            }
            return true;
        }
        if ("b".equals(propType)) return prop instanceof BooleanProperty;
        if (valueCount > 0) {
            if (values.size() != valueCount) return false;
            if (prop instanceof IntegerProperty intProp) {
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                for (Integer v : intProp.getPossibleValues()) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                return min == intMin && max == intMin + valueCount - 1;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property prop, Object value) {
        try {
            return prop.getName((Comparable) value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** The well-known vanilla property for a property name, or null. */
    private static Property<?> vanillaPropertyByName(String name, int valueCount) {
        // Value count disambiguates same-named variants
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

        return switch (name) {
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
    }
}
