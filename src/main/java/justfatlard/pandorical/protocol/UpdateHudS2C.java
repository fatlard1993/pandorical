package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record UpdateHudS2C(
    String overlayId,
    List<ComponentUpdate> updates
) implements CustomPacketPayload {
    public static final Type<UpdateHudS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "update_hud"));

    public static final StreamCodec<ByteBuf, UpdateHudS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, UpdateHudS2C::overlayId,
        ComponentUpdate.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateHudS2C::updates,
        UpdateHudS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
