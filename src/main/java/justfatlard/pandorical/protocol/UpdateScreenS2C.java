package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record UpdateScreenS2C(
    String screenId,
    List<ComponentUpdate> updates
) implements CustomPacketPayload {
    public static final Type<UpdateScreenS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "update_screen"));

    public static final StreamCodec<ByteBuf, UpdateScreenS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, UpdateScreenS2C::screenId,
        ComponentUpdate.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateScreenS2C::updates,
        UpdateScreenS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
