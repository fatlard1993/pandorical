package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A skin the server wants worn, sent as the image itself.
 *
 * <p>The bytes travel rather than a link to them because a link does not work: a client resolves a
 * profile's skin through authlib, which checks the texture URL against a list of domains it fetches
 * from Mojang, so a server offering its own skin file is refused and the player falls back to Steve.
 * Nothing a server says about that URL can change the answer. Handing over the picture is the way
 * round it - the client registers the image directly and never asks authlib anything.
 *
 * <p>An empty image means "stop overriding this player", which is how a skin is taken back off.
 *
 * @param slim the three-pixel-arm model, as opposed to the four-pixel default
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
