package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Full replacement set of vanilla HUD element ids this player's client should stop
 * drawing (e.g. {@code minecraft:food_bar}). Always absolute, never a delta: a mod
 * that wants its suppression lifted sends the set without its entries.
 */
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
