package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Registered entities with no base: everything a client needs to build one from nothing. Sent in
 * the configuration phase ahead of the content sync, like {@link EntityKindsConfigS2C}, which
 * carries the ones that stand on a vanilla mob. Its own channel, so a client from before entities
 * could have no base is never sent a shape it cannot read.
 *
 * <p>{@code layout} is the serializer id of every value the entity syncs, in id order; {@code tier}
 * is the most specific of the client's generic classes the entity is, whose own values lead it.
 * Empty strings for what was not given.
 */
public record EntityBodiesConfigS2C(List<Entry> bodies) implements CustomPacketPayload {

    public record Entry(String type, String tier, List<Integer> layout, float width, float height,
            float eyeHeight, String category, int trackingRange, int updateInterval, boolean fireImmune,
            String model, String texture, float drawScale) {

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
            (buf, e) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, e.type);
                ByteBufCodecs.STRING_UTF8.encode(buf, e.tier);
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, e.layout);
                buf.writeFloat(e.width);
                buf.writeFloat(e.height);
                buf.writeFloat(e.eyeHeight);
                ByteBufCodecs.STRING_UTF8.encode(buf, e.category);
                ByteBufCodecs.VAR_INT.encode(buf, e.trackingRange);
                ByteBufCodecs.VAR_INT.encode(buf, e.updateInterval);
                buf.writeBoolean(e.fireImmune);
                ByteBufCodecs.STRING_UTF8.encode(buf, e.model);
                ByteBufCodecs.STRING_UTF8.encode(buf, e.texture);
                buf.writeFloat(e.drawScale);
            },
            buf -> new Entry(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readFloat()));
    }

    public static final Type<EntityBodiesConfigS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "entity_bodies"));

    public static final StreamCodec<ByteBuf, EntityBodiesConfigS2C> STREAM_CODEC = StreamCodec.composite(
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), EntityBodiesConfigS2C::bodies,
        EntityBodiesConfigS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
