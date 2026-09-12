package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent before any content. A client that cannot receive this type is too old for the current
 * content format; one that can compares these fields with its own version.
 *
 * @param minimumProtocol   the oldest client protocol the server still understands
 * @param serverModVersion  the version to tell the player to install
 */
public record RequirementS2C(int protocolVersion, int minimumProtocol, String serverModVersion)
    implements CustomPacketPayload {

    public static final Type<RequirementS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "requirement"));

    public static final StreamCodec<ByteBuf, RequirementS2C> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RequirementS2C decode(ByteBuf buf) {
                return new RequirementS2C(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, RequirementS2C value) {
                ByteBufCodecs.VAR_INT.encode(buf, value.protocolVersion());
                ByteBufCodecs.VAR_INT.encode(buf, value.minimumProtocol());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.serverModVersion());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
