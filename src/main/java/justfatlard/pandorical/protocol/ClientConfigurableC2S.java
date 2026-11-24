package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Which of the client's own mods have a config screen, declared for Mod Menu, the mods screen can open. */
public record ClientConfigurableC2S(List<String> mods) implements CustomPacketPayload {
    public static final Type<ClientConfigurableC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_configurable"));

    public static final StreamCodec<ByteBuf, ClientConfigurableC2S> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(512)).map(ClientConfigurableC2S::new, ClientConfigurableC2S::mods);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
