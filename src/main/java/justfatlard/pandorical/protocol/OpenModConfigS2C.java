package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Open one of the client's own mods' config screens, over the mods screen, while that screen is open. */
public record OpenModConfigS2C(String mod) implements CustomPacketPayload {
    public static final Type<OpenModConfigS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "open_mod_config"));

    public static final StreamCodec<ByteBuf, OpenModConfigS2C> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(64).map(OpenModConfigS2C::new, OpenModConfigS2C::mod);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
