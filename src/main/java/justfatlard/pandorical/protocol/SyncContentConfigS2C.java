package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Configuration-phase content sync payload.
 * Sends block/item definitions BEFORE Fabric's registry sync,
 * so the client can register them and Fabric assigns IDs correctly.
 *
 * Also includes identifier lists for other registry types (entity types,
 * block entity types, villager professions, POI types, menu types,
 * recipe book categories) so the client can register stubs.
 */
public record SyncContentConfigS2C(
    List<SyncContentS2C.BlockEntry> blocks,
    List<SyncContentS2C.ItemEntry> items,
    int expectedAssetChunks,
    List<String> entityTypes,
    List<String> blockEntityTypes,
    List<String> villagerProfessions,
    List<String> poiTypes,
    List<String> menuTypes,
    List<String> recipeBookCategories
) implements CustomPacketPayload {
    public static final Type<SyncContentConfigS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "sync_content_config"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, SyncContentConfigS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncContentConfigS2C decode(ByteBuf buf) {
            var blocks = SyncContentS2C.BlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            var items = SyncContentS2C.ItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            int expectedAssetChunks = ByteBufCodecs.VAR_INT.decode(buf);
            var entityTypes = STRING_LIST_CODEC.decode(buf);
            var blockEntityTypes = STRING_LIST_CODEC.decode(buf);
            var villagerProfessions = STRING_LIST_CODEC.decode(buf);
            var poiTypes = STRING_LIST_CODEC.decode(buf);
            var menuTypes = STRING_LIST_CODEC.decode(buf);
            var recipeBookCategories = STRING_LIST_CODEC.decode(buf);
            return new SyncContentConfigS2C(blocks, items, expectedAssetChunks,
                entityTypes, blockEntityTypes, villagerProfessions, poiTypes, menuTypes, recipeBookCategories);
        }

        @Override
        public void encode(ByteBuf buf, SyncContentConfigS2C value) {
            SyncContentS2C.BlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.blocks());
            SyncContentS2C.ItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.items());
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
