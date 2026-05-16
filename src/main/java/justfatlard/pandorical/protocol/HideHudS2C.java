package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HideHudS2C(
    String overlayId
) implements CustomPacketPayload {
    public static final Type<HideHudS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "hide_hud"));

    public static final StreamCodec<ByteBuf, HideHudS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, HideHudS2C::overlayId,
        HideHudS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
