package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes a {@link BlockState} as a block id string plus a property-name/property-value string
 * map, rather than as a registry-sync-dependent int id. This works for any block already known
 * to {@link BuiltInRegistries#BLOCK} on both ends, including Pandorical-registered custom
 * blocks, since those are registered into the same real registry once content sync completes;
 * without depending on both sides having assigned the same numeric id in the same order.
 */
public final class BlockStateCodec {
    private BlockStateCodec() {}

    private static final StreamCodec<ByteBuf, Map<String, String>> PROPS_CODEC =
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    public static final StreamCodec<ByteBuf, BlockState> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, BlockStateCodec::blockId,
        PROPS_CODEC, BlockStateCodec::properties,
        BlockStateCodec::fromParts
    );

    private static String blockId(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null ? id.toString() : "minecraft:air";
    }

    private static Map<String, String> properties(BlockState state) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            map.put(property.getName(), nameOf(property, state));
        }
        return map;
    }

    private static <T extends Comparable<T>> String nameOf(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    private static BlockState fromParts(String blockId, Map<String, String> properties) {
        Identifier id = Identifier.tryParse(blockId);
        Block block = id != null ? BuiltInRegistries.BLOCK.getValue(id) : null;
        if (block == null) return Blocks.AIR.defaultBlockState();

        BlockState state = block.defaultBlockState();
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = definition.getProperty(entry.getKey());
            if (property != null) {
                state = applyProperty(state, property, entry.getValue());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
