package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The icon of a mod the server runs and the player's game does not, as the PNG in its jar, for the
 * mods screen to draw at {@code pandorical:mod_icon/<mod>}.
 */
public record ModIconS2C(String mod, byte[] png) implements CustomPacketPayload {
    public static final int MOST_BYTES = 64 * 1024;

    public static final Type<ModIconS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "mod_icon"));

    public static final StreamCodec<ByteBuf, ModIconS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(64), ModIconS2C::mod,
        ByteBufCodecs.byteArray(MOST_BYTES), ModIconS2C::png,
        ModIconS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
