package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A word the client could not act on, sent back to the server that said it.
 *
 * <p>Without this the whole arrangement has a blind spot. A mod is written on the server and drawn
 * on clients the author will never see, so every "unknown component type" and "unreadable spec"
 * lands in a player's log, where nobody who could fix it is looking. The mod appears to work and
 * quietly does not.
 *
 * @param kind  what sort of word it was, from {@link justfatlard.pandorical.api.NotUnderstood}
 * @param value the word itself, as the server said it
 */
public record NotUnderstoodC2S(String kind, String value) implements CustomPacketPayload {
    public NotUnderstoodC2S {
        kind = Wire.fit(kind, 64, "a not-understood kind");
        value = Wire.fit(value, 256, "a not-understood value");
    }

    public static final Type<NotUnderstoodC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "not_understood"));

    public static final StreamCodec<ByteBuf, NotUnderstoodC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(64), NotUnderstoodC2S::kind,
        ByteBufCodecs.stringUtf8(256), NotUnderstoodC2S::value,
        NotUnderstoodC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
