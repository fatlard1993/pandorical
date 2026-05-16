package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Syncs custom block and item definitions to the client on join.
 * Also includes identifier lists for other registry types (entity types,
 * block entity types, villager professions, POI types, menu types,
 * recipe book categories) so the client can register stubs.
 */
public record SyncContentS2C(
    List<BlockEntry> blocks,
    List<ItemEntry> items,
    int expectedAssetChunks,
    List<String> entityTypes,
    List<String> blockEntityTypes,
    List<String> villagerProfessions,
    List<String> poiTypes,
    List<String> menuTypes,
    List<String> recipeBookCategories
) implements CustomPacketPayload {
    public static final Type<SyncContentS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "sync_content"));

    public record BlockEntry(
        String id,
        String baseBlockId,
        List<String> stateProperties,
        String modelId,
        List<Integer> stateIds,
        byte[] shapeData
    ) {
        public static final StreamCodec<ByteBuf, BlockEntry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public BlockEntry decode(ByteBuf buf) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                String baseBlockId = ByteBufCodecs.STRING_UTF8.decode(buf);
                var stateProperties = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf);
                String modelId = ByteBufCodecs.STRING_UTF8.decode(buf);
                var stateIds = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf);
                var shapeData = ByteBufCodecs.BYTE_ARRAY.decode(buf);
                return new BlockEntry(id, baseBlockId, stateProperties, modelId, stateIds, shapeData);
            }

            @Override
            public void encode(ByteBuf buf, BlockEntry value) {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.baseBlockId());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, value.stateProperties());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.modelId());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, value.stateIds());
                ByteBufCodecs.BYTE_ARRAY.encode(buf, value.shapeData());
            }
        };
    }

    public record ItemEntry(
        String id,
        String modelId,
        int maxStackSize,
        int maxDamage,
        boolean hasGlint,
        String equipSlot,
        String toolType
    ) {
        public static final StreamCodec<ByteBuf, ItemEntry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ItemEntry decode(ByteBuf buf) {
                return new ItemEntry(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
                );
            }

            @Override
            public void encode(ByteBuf buf, ItemEntry value) {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.modelId());
                ByteBufCodecs.VAR_INT.encode(buf, value.maxStackSize());
                ByteBufCodecs.VAR_INT.encode(buf, value.maxDamage());
                ByteBufCodecs.BOOL.encode(buf, value.hasGlint());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.equipSlot());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.toolType());
            }
        };
    }

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, SyncContentS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncContentS2C decode(ByteBuf buf) {
            var blocks = BlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            var items = ItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            int expectedAssetChunks = ByteBufCodecs.VAR_INT.decode(buf);
            var entityTypes = STRING_LIST_CODEC.decode(buf);
            var blockEntityTypes = STRING_LIST_CODEC.decode(buf);
            var villagerProfessions = STRING_LIST_CODEC.decode(buf);
            var poiTypes = STRING_LIST_CODEC.decode(buf);
            var menuTypes = STRING_LIST_CODEC.decode(buf);
            var recipeBookCategories = STRING_LIST_CODEC.decode(buf);
            return new SyncContentS2C(blocks, items, expectedAssetChunks,
                entityTypes, blockEntityTypes, villagerProfessions, poiTypes, menuTypes, recipeBookCategories);
        }

        @Override
        public void encode(ByteBuf buf, SyncContentS2C value) {
            BlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.blocks());
            ItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.items());
            ByteBufCodecs.VAR_INT.encode(buf, value.expectedAssetChunks());
            STRING_LIST_CODEC.encode(buf, value.entityTypes());
            STRING_LIST_CODEC.encode(buf, value.blockEntityTypes());
            STRING_LIST_CODEC.encode(buf, value.villagerProfessions());
            STRING_LIST_CODEC.encode(buf, value.poiTypes());
            STRING_LIST_CODEC.encode(buf, value.menuTypes());
            STRING_LIST_CODEC.encode(buf, value.recipeBookCategories());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
