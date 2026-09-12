package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** The whole set of vanilla HUD element ids to hide, never a delta. */
public record SetVanillaHudElementsS2C(List<String> hiddenElements) implements CustomPacketPayload {
    public static final Type<SetVanillaHudElementsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "set_vanilla_hud_elements"));

    public static final StreamCodec<ByteBuf, SetVanillaHudElementsS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SetVanillaHudElementsS2C::hiddenElements,
        SetVanillaHudElementsS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
