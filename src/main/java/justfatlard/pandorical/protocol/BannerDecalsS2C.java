package justfatlard.pandorical.protocol;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Deltas: only the positions here change, and an entry with no layers clears its position. In
 * blocks from the anchor block: {@code lift} up from its bottom, {@code fromHead} in from the face
 * {@code toHead} names, then {@code length} along and {@code width} across.
 */
public record BannerDecalsS2C(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<BannerDecalsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "banner_decals"));

    public record Entry(long pos, byte toHead, float lift, float fromHead, float length, float width,
            BannerPatternLayers layers) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Entry decode(RegistryFriendlyByteBuf buf) {
                long pos = buf.readLong();
                byte toHead = buf.readByte();
                float lift = buf.readFloat();
                float fromHead = buf.readFloat();
                float length = buf.readFloat();
                float width = buf.readFloat();
                BannerPatternLayers layers = BannerPatternLayers.STREAM_CODEC.decode(buf);
                return new Entry(pos, toHead, lift, fromHead, length, width, layers);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Entry value) {
                buf.writeLong(value.pos());
                buf.writeByte(value.toHead());
                buf.writeFloat(value.lift());
                buf.writeFloat(value.fromHead());
                buf.writeFloat(value.length());
                buf.writeFloat(value.width());
                BannerPatternLayers.STREAM_CODEC.encode(buf, value.layers());
            }
        };
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BannerDecalsS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BannerDecalsS2C decode(RegistryFriendlyByteBuf buf) {
            return new BannerDecalsS2C(Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BannerDecalsS2C value) {
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.entries());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
