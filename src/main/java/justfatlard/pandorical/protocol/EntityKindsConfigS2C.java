package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Which registered entity types are drawn as which vanilla ones, and dressed how. Sent in the
 * configuration phase ahead of the content sync, because the client builds each type's stand-in
 * as it registers it, and builds it from the base. Empty strings for what was not given.
 */
public record EntityKindsConfigS2C(List<Entry> kinds) implements CustomPacketPayload {

    public record Entry(String type, String base, String model, String texture, String babyTexture,
            float drawScale) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Entry::type,
            ByteBufCodecs.STRING_UTF8, Entry::base,
            ByteBufCodecs.STRING_UTF8, Entry::model,
            ByteBufCodecs.STRING_UTF8, Entry::texture,
            ByteBufCodecs.STRING_UTF8, Entry::babyTexture,
            ByteBufCodecs.FLOAT, Entry::drawScale,
            Entry::new);
    }

    public static final Type<EntityKindsConfigS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "entity_kinds"));

    public static final StreamCodec<ByteBuf, EntityKindsConfigS2C> STREAM_CODEC = StreamCodec.composite(
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), EntityKindsConfigS2C::kinds,
        EntityKindsConfigS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
