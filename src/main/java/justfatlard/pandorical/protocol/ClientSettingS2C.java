package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A client-side setting the player changed from the mod menu, handed back to the client to apply. */
public record ClientSettingS2C(String modId, String key, String value) implements CustomPacketPayload {
    public static final Type<ClientSettingS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_setting"));

    public static final StreamCodec<ByteBuf, ClientSettingS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ClientSettingS2C::modId,
        ByteBufCodecs.STRING_UTF8, ClientSettingS2C::key,
        ByteBufCodecs.STRING_UTF8, ClientSettingS2C::value,
        ClientSettingS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
