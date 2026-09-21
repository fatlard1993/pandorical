package justfatlard.pandorical.client.content;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.rail.RailCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import java.util.*;

public class DynamicBlock extends Block {
    private final List<Property<?>> dynamicProperties;
    private Map<BlockState, VoxelShape> outlineShapes;
    private Map<BlockState, VoxelShape> collisionShapes;

    public DynamicBlock(Properties props, List<Property<?>> properties) {
        super(props);
        this.dynamicProperties = properties;

        // Every flag off, rather than whatever stateDefinition.any() lands on.
        //
        // any() takes the first value of each property, and BooleanProperty lists its values
        // List.of(true, false) - so a stand-in defaulted to snowy, waterlogged, powered, lit and
        // open all at once. The server's real state arrives a tick later and corrects it, so the
        // only thing this is ever seen as is a single wrong frame at the moment of placing: the
        // client predicts with the default, and a grass slab flashed white because snowy=true
        // picks the snow model, which is a white texture with no tint on it.
        //
        // DynamicSlabBlock met this first and answered it by leaving SlabBlock's own default
        // alone; this is the same answer for a block that has no vanilla class to inherit one
        // from. False is the right guess: it is what virtually every vanilla block defaults
        // these flags to, and a flag that starts off looks right until the truth arrives.
        BlockState defaultState = this.stateDefinition.any();
        for (Property<?> property : properties) {
            if (property instanceof BooleanProperty flag) {
                defaultState = defaultState.setValue(flag, false);
            }
        }
        this.registerDefaultState(defaultState);
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
        VoxelShape floor = RailCollision.shapeFor(state, context);
        if (floor != null) return floor;
        if (collisionShapes != null) {
            VoxelShape shape = collisionShapes.get(state);
            if (shape != null) return shape;
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        // Only a full-cube outline occludes.
        if (outlineShapes != null) {
            VoxelShape shape = outlineShapes.get(state);
            if (shape != null && !Shapes.joinIsNotEmpty(Shapes.block(), shape, BooleanOp.NOT_SAME)) {
                return shape;
            }
            return Shapes.empty();
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

    /** createBlockStateDefinition runs inside the super constructor, before any field is set. */
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
     * Runs while states are built, before the block can list them, so the index is computed:
     * states are the product of the properties in name order, the last varying fastest.
     */
    public static int lightFor(BlockState state, byte[] light) {
        int index = 0;
        for (Property<?> property : state.getProperties()) {
            var values = property.getPossibleValues();
            Comparable<?> mine = state.getValue(property);
            int at = 0;
            for (var value : values) {
                if (value.equals(mine)) break;
                at++;
            }
            index = index * values.size() + at;
        }
        return index < light.length ? light[index] & 0xFF : 0;
    }

    public static void applyShapeData(Block block, byte[] shapeData) {
        if (shapeData == null || shapeData.length == 0) return;
        if (VanillaShapedBlocks.isOne(block)) return;

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
            Pandorical.LOGGER.warn("Failed to deserialize shapes for {}: {}",
                block, e.getMessage());
            return;
        }

        if (block instanceof DynamicBlock db) {
            db.setShapes(outlineMap, collisionMap);
        } else if (block instanceof DynamicSlabBlock dsb) {
            dsb.setShapes(outlineMap, collisionMap);
        } else {
            Pandorical.LOGGER.warn(
                "Block {} is a {} and cannot hold server shapes — it keeps stand-in geometry",
                block, block.getClass().getSimpleName());
            return;
        }

        // BlockStateBase caches what it derives from the shape (full-block collision, occlusion,
        // sturdy faces, light) when the state is built, before these shapes arrived.
        for (BlockState state : states) state.initCache();
    }

    /**
     * Read before the block is built: collision is also a {@code Properties} flag, copied from the
     * base block and fixed when the state cache is built during registration. The first state
     * speaks for the block.
     */
    public static boolean declaresCollision(byte[] shapeData) {
        if (shapeData == null || shapeData.length == 0) return true;

        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(shapeData));
            int outlineBoxes = dis.readByte() & 0xFF;
            dis.skipBytes(outlineBoxes * BYTES_PER_BOX);
            return (dis.readByte() & 0xFF) > 0;
        } catch (IOException e) {
            // Unknown is solid: a block you fall through is worse than one you bump into.
            return true;
        }
    }

    private static final int BYTES_PER_BOX = 24;

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

    public static Property<?> resolveProperty(String name, Block baseBlock, int valueCount, String propType) {
        return resolveProperty(name, baseBlock, valueCount, 0, propType, null);
    }

    /**
     * A base-block or vanilla property is reused only when its value set matches the wire exactly,
     * or the state count would differ from the server's. A match beats a synthesized property
     * because its value names are what blockstate JSON variants use.
     */
    public static Property<?> resolveProperty(String name, Block baseBlock, int valueCount, int intMin, String propType, String enumValues) {
        List<String> wireEnumNames = ("e".equals(propType) && enumValues != null && !enumValues.isEmpty())
            ? List.of(enumValues.split(","))
            : null;
        // Older wire forms carry no value set; then any candidate is accepted.
        boolean wireDescribesValues = wireEnumNames != null || valueCount > 0 || "b".equals(propType);

        if (baseBlock != null) {
            for (Property<?> prop : baseBlock.getStateDefinition().getProperties()) {
                if (!prop.getName().equals(name)) continue;
                if (!wireDescribesValues || matchesWire(prop, propType, valueCount, intMin, wireEnumNames)) {
                    return prop;
                }
                Pandorical.LOGGER.debug(
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

    private static Property<?> vanillaPropertyByName(String name, int valueCount) {
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
