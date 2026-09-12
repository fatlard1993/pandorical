package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The image itself, not a URL: authlib fetches skins only from Mojang's domains. An empty image
 * clears the override.
 *
 * @param slim the three-pixel-arm model
 */
public record SkinOverrideS2C(UUID subject, byte[] png, boolean slim) implements CustomPacketPayload {

    public static final Type<SkinOverrideS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "skin_override"));

    public static final StreamCodec<ByteBuf, SkinOverrideS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), SkinOverrideS2C::subject,
        ByteBufCodecs.BYTE_ARRAY, SkinOverrideS2C::png,
        ByteBufCodecs.BOOL, SkinOverrideS2C::slim,
        SkinOverrideS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
