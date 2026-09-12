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
    List<String> recipeBookCategories,
    boolean solidRails
) implements CustomPacketPayload, SyncedContent {
    public static final Type<SyncContentS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "sync_content"));

    public record BlockEntry(
        String id,
        String baseBlockId,
        List<String> stateProperties,
        String modelId,
        List<Integer> stateIds,
        byte[] shapeData,
        /*
         * Light given off, one byte per state in the block's own state order.
         *
         * <p>Light is the client's to compute, from its own copy of the block, and a stand-in
         * copied from a base block gives off what the base does: nothing, for nearly all of
         * them. A torch sharing a slab's block was dark on every client while the server
         * believed it lit. Sent per state because that is how the game caches it - each state
         * fixes its emission as it is built - and the stand-in's states are built from this.
         */
        byte[] lightData,
        /*
         * Whether this block carries players up it.
         *
         * <p>Climbing is decided by the client, off {@code #minecraft:climbable}, and a tag is a
         * poor thing to depend on for a block the client only learns about here: tag membership
         * travels as numeric registry ids against a registry these stand-ins are appended to at
         * connection time. Sending the fact outright costs a bit and needs no such agreement.
         */
        boolean climbable,
        /*
         * Whether a right-click on this block is the server's business.
         *
         * <p>Without it the client predicts a block placement against anything it has no
         * behaviour for, which is every synced block. See {@code BlockRegistration#interactive}.
         */
        boolean interactive,
        /*
         * How long this block takes to break, or a negative number to keep the base block's.
         *
         * <p>Sent because breaking is predicted on the client, off the stand-in's properties, while
         * everything the server decides is measured against the real block. A stand-in whose
         * hardness differs breaks at a different speed than the server thinks it does - the two
         * disagree for the whole dig, and any progress bar drawn from the server's side disagrees
         * with the player's own screen.
         */
        float destroyTime,
        /*
         * Whether the client should apply the wrong-tool penalty, or -1 to keep the base block's.
         *
         * <p>Its own field rather than part of the base block, because it is the larger of the two
         * mining mismatches: a stand-in that wants a pickaxe predicts roughly five times the dig
         * a server that does not care will actually perform. Sent as a tri-state so a block that
         * has no opinion still inherits, which is nearly all of them.
         */
        int requiresCorrectTool
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
                var lightData = ByteBufCodecs.BYTE_ARRAY.decode(buf);
                boolean climbable = ByteBufCodecs.BOOL.decode(buf);
                boolean interactive = ByteBufCodecs.BOOL.decode(buf);
                float destroyTime = ByteBufCodecs.FLOAT.decode(buf);
                int requiresCorrectTool = ByteBufCodecs.VAR_INT.decode(buf) - 1;
                return new BlockEntry(id, baseBlockId, stateProperties, modelId, stateIds, shapeData,
                    lightData, climbable, interactive, destroyTime, requiresCorrectTool);
            }

            @Override
            public void encode(ByteBuf buf, BlockEntry value) {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.baseBlockId());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, value.stateProperties());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.modelId());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, value.stateIds());
                ByteBufCodecs.BYTE_ARRAY.encode(buf, value.shapeData());
                ByteBufCodecs.BYTE_ARRAY.encode(buf, value.lightData());
                ByteBufCodecs.BOOL.encode(buf, value.climbable());
                ByteBufCodecs.BOOL.encode(buf, value.interactive());
                ByteBufCodecs.FLOAT.encode(buf, value.destroyTime());
                // Shifted by one so the "no opinion" case is zero rather than a negative, which
                // VAR_INT spends five bytes on.
                ByteBufCodecs.VAR_INT.encode(buf, value.requiresCorrectTool() + 1);
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
        String toolType,
        String foodSpec
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
                ByteBufCodecs.STRING_UTF8.encode(buf, value.foodSpec());
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
            boolean solidRails = ByteBufCodecs.BOOL.decode(buf);
            return new SyncContentS2C(blocks, items, expectedAssetChunks,
                entityTypes, blockEntityTypes, villagerProfessions, poiTypes, menuTypes, recipeBookCategories,
                solidRails);
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
            ByteBufCodecs.BOOL.encode(buf, value.solidRails());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
