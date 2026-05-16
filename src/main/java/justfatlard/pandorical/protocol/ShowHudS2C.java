package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record ShowHudS2C(
    String overlayId,
    String anchor,
    int offsetX,
    int offsetY,
    List<ComponentDef> components
) implements CustomPacketPayload {
    public static final Type<ShowHudS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "show_hud"));

    public static final StreamCodec<ByteBuf, ShowHudS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ShowHudS2C::overlayId,
        ByteBufCodecs.STRING_UTF8, ShowHudS2C::anchor,
        ByteBufCodecs.VAR_INT, ShowHudS2C::offsetX,
        ByteBufCodecs.VAR_INT, ShowHudS2C::offsetY,
        ComponentDef.STREAM_CODEC.apply(ByteBufCodecs.list()), ShowHudS2C::components,
        ShowHudS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
