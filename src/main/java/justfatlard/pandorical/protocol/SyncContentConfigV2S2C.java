package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SyncContentConfigS2C}, packed. The same content, but a block's shapes go as a table of
 * its distinct shapes (see {@link BlockShapeTable}) and a block whose table matches one already in
 * this chunk names that one instead, so sixteen dyed copies of a block carry its shapes once. A run
 * of state ids goes as its first id and length, and light the same in every state as one byte.
 *
 * <p>Sent to a client that registers this channel; any other gets {@link SyncContentConfigS2C}.
 */
public record SyncContentConfigV2S2C(SyncContentConfigS2C content) implements CustomPacketPayload {
    public static final Type<SyncContentConfigV2S2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "sync_content_config_v2"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    /**
     * One entry as it would weigh alone, with no table of an earlier block to name: what a chunk
     * budget should count, since an entry may open a chunk.
     */
    public static final StreamCodec<ByteBuf, SyncContentS2C.BlockEntry> BLOCK_ENTRY_ALONE = new StreamCodec<>() {
        @Override
        public SyncContentS2C.BlockEntry decode(ByteBuf buf) {
            return decodeBlock(buf, new ArrayList<>());
        }

        @Override
        public void encode(ByteBuf buf, SyncContentS2C.BlockEntry value) {
            encodeBlock(buf, value, new HashMap<>());
        }
    };

    public static final StreamCodec<ByteBuf, SyncContentConfigV2S2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncContentConfigV2S2C decode(ByteBuf buf) {
            int blockCount = ByteBufCodecs.VAR_INT.decode(buf);
            List<SyncContentS2C.BlockEntry> blocks = new ArrayList<>(blockCount);
            List<BlockShapeTable> tables = new ArrayList<>();
            for (int i = 0; i < blockCount; i++) blocks.add(decodeBlock(buf, tables));
            var items = SyncContentS2C.ItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            int chunkIndex = ByteBufCodecs.VAR_INT.decode(buf);
            int totalChunks = ByteBufCodecs.VAR_INT.decode(buf);
            int expectedAssetChunks = ByteBufCodecs.VAR_INT.decode(buf);
            var entityTypes = STRING_LIST_CODEC.decode(buf);
            var blockEntityTypes = STRING_LIST_CODEC.decode(buf);
            var villagerProfessions = STRING_LIST_CODEC.decode(buf);
            var poiTypes = STRING_LIST_CODEC.decode(buf);
            var menuTypes = STRING_LIST_CODEC.decode(buf);
            var recipeBookCategories = STRING_LIST_CODEC.decode(buf);
            boolean solidRails = ByteBufCodecs.BOOL.decode(buf);
            return new SyncContentConfigV2S2C(new SyncContentConfigS2C(blocks, items, chunkIndex, totalChunks,
                expectedAssetChunks, entityTypes, blockEntityTypes, villagerProfessions, poiTypes, menuTypes,
                recipeBookCategories, solidRails));
        }

        @Override
        public void encode(ByteBuf buf, SyncContentConfigV2S2C value) {
            SyncContentConfigS2C content = value.content();
            ByteBufCodecs.VAR_INT.encode(buf, content.blocks().size());
            Map<BlockShapeTable, Integer> tables = new HashMap<>();
            for (SyncContentS2C.BlockEntry block : content.blocks()) encodeBlock(buf, block, tables);
            SyncContentS2C.ItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, content.items());
            ByteBufCodecs.VAR_INT.encode(buf, content.chunkIndex());
            ByteBufCodecs.VAR_INT.encode(buf, content.totalChunks());
            ByteBufCodecs.VAR_INT.encode(buf, content.expectedAssetChunks());
            STRING_LIST_CODEC.encode(buf, content.entityTypes());
            STRING_LIST_CODEC.encode(buf, content.blockEntityTypes());
            STRING_LIST_CODEC.encode(buf, content.villagerProfessions());
            STRING_LIST_CODEC.encode(buf, content.poiTypes());
            STRING_LIST_CODEC.encode(buf, content.menuTypes());
            STRING_LIST_CODEC.encode(buf, content.recipeBookCategories());
            ByteBufCodecs.BOOL.encode(buf, content.solidRails());
        }
    };

    /** A table already written is named by its place among the tables written, plus one; zero writes a new one. */
    private static void encodeBlock(ByteBuf buf, SyncContentS2C.BlockEntry value, Map<BlockShapeTable, Integer> tables) {
        ByteBufCodecs.STRING_UTF8.encode(buf, value.id());
        ByteBufCodecs.STRING_UTF8.encode(buf, value.baseBlockId());
        STRING_LIST_CODEC.encode(buf, value.stateProperties());
        ByteBufCodecs.STRING_UTF8.encode(buf, value.modelId());
        encodeStateIds(buf, value.stateIds());

        Integer written = tables.get(value.shapes());
        if (written != null) {
            ByteBufCodecs.VAR_INT.encode(buf, written + 1);
        } else {
            ByteBufCodecs.VAR_INT.encode(buf, 0);
            BlockShapeTable.STREAM_CODEC.encode(buf, value.shapes());
            tables.put(value.shapes(), tables.size());
        }

        encodeLight(buf, value.lightData());
        ByteBufCodecs.BOOL.encode(buf, value.climbable());
        ByteBufCodecs.BOOL.encode(buf, value.interactive());
        ByteBufCodecs.FLOAT.encode(buf, value.destroyTime());
        ByteBufCodecs.VAR_INT.encode(buf, value.requiresCorrectTool() + 1);
    }

    private static SyncContentS2C.BlockEntry decodeBlock(ByteBuf buf, List<BlockShapeTable> tables) {
        String id = ByteBufCodecs.STRING_UTF8.decode(buf);
        String baseBlockId = ByteBufCodecs.STRING_UTF8.decode(buf);
        var stateProperties = STRING_LIST_CODEC.decode(buf);
        String modelId = ByteBufCodecs.STRING_UTF8.decode(buf);
        List<Integer> stateIds = decodeStateIds(buf);

        int named = ByteBufCodecs.VAR_INT.decode(buf);
        BlockShapeTable shapes;
        if (named == 0) {
            shapes = BlockShapeTable.STREAM_CODEC.decode(buf);
            tables.add(shapes);
        } else {
            shapes = tables.get(named - 1);
        }

        byte[] lightData = decodeLight(buf);
        boolean climbable = ByteBufCodecs.BOOL.decode(buf);
        boolean interactive = ByteBufCodecs.BOOL.decode(buf);
        float destroyTime = ByteBufCodecs.FLOAT.decode(buf);
        int requiresCorrectTool = ByteBufCodecs.VAR_INT.decode(buf) - 1;
        return new SyncContentS2C.BlockEntry(id, baseBlockId, stateProperties, modelId, stateIds, shapes,
            lightData, climbable, interactive, destroyTime, requiresCorrectTool);
    }

    /** Count, then whether the ids run on by one; a run is its first id, anything else every id. */
    private static void encodeStateIds(ByteBuf buf, List<Integer> ids) {
        ByteBufCodecs.VAR_INT.encode(buf, ids.size());
        if (ids.isEmpty()) return;
        boolean run = true;
        for (int i = 1; i < ids.size() && run; i++) run = ids.get(i) == ids.get(0) + i;
        buf.writeBoolean(run);
        if (run) {
            ByteBufCodecs.VAR_INT.encode(buf, ids.get(0));
        } else {
            for (int stateId : ids) ByteBufCodecs.VAR_INT.encode(buf, stateId);
        }
    }

    private static List<Integer> decodeStateIds(ByteBuf buf) {
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        List<Integer> ids = new ArrayList<>(count);
        if (count == 0) return ids;
        if (buf.readBoolean()) {
            int first = ByteBufCodecs.VAR_INT.decode(buf);
            for (int i = 0; i < count; i++) ids.add(first + i);
        } else {
            for (int i = 0; i < count; i++) ids.add(ByteBufCodecs.VAR_INT.decode(buf));
        }
        return ids;
    }

    /** Length, then whether every state gives the same light; if so that one byte, else every byte. */
    private static void encodeLight(ByteBuf buf, byte[] light) {
        ByteBufCodecs.VAR_INT.encode(buf, light.length);
        if (light.length == 0) return;
        boolean uniform = true;
        for (int i = 1; i < light.length && uniform; i++) uniform = light[i] == light[0];
        buf.writeBoolean(uniform);
        if (uniform) {
            buf.writeByte(light[0]);
        } else {
            buf.writeBytes(light);
        }
    }

    private static byte[] decodeLight(ByteBuf buf) {
        byte[] light = new byte[ByteBufCodecs.VAR_INT.decode(buf)];
        if (light.length == 0) return light;
        if (buf.readBoolean()) {
            Arrays.fill(light, buf.readByte());
        } else {
            buf.readBytes(light);
        }
        return light;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
